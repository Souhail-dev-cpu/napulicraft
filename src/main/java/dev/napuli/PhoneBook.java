package dev.napuli;

import static dev.napuli.CityText.t;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Personal contacts, notes and call history belong to accounts, independently of the physical handset. */
final class PhoneBook {
    static final int CONTACT_LIMIT=128;
    record Note(UUID id,String body,long time) {
        static final Codec<Note> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("id").forGetter(Note::id),Codec.STRING.fieldOf("body").forGetter(Note::body),Codec.LONG.fieldOf("time").forGetter(Note::time)).apply(i,Note::new));
    }
    record Recent(UUID peer,String name,long time,boolean incoming,String outcome,int seconds) {
        static final Codec<Recent> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("peer").forGetter(Recent::peer),Codec.STRING.fieldOf("name").forGetter(Recent::name),Codec.LONG.fieldOf("time").forGetter(Recent::time),Codec.BOOL.fieldOf("incoming").forGetter(Recent::incoming),Codec.STRING.fieldOf("outcome").forGetter(Recent::outcome),Codec.INT.fieldOf("seconds").forGetter(Recent::seconds)).apply(i,Recent::new));
    }
    private record Offer(UUID token,UUID sender,UUID recipient,long expires) {}
    private static final AttachmentType<Map<String,List<UUID>>> CONTACTS=AttachmentRegistry.<Map<String,List<UUID>>>builder().persistent(Codec.unboundedMap(Codec.STRING,UUIDUtil.CODEC.listOf())).buildAndRegister(Napuli.id("phone_contacts"));
    private static final AttachmentType<Map<String,List<Note>>> NOTES=AttachmentRegistry.<Map<String,List<Note>>>builder().persistent(Codec.unboundedMap(Codec.STRING,Note.CODEC.listOf())).buildAndRegister(Napuli.id("phone_notes"));
    private static final AttachmentType<Map<String,List<Recent>>> HISTORY=AttachmentRegistry.<Map<String,List<Recent>>>builder().persistent(Codec.unboundedMap(Codec.STRING,Recent.CODEC.listOf())).buildAndRegister(Napuli.id("phone_history"));
    private static final Map<UUID,Offer> OFFERS=new LinkedHashMap<>();
    private static final Map<UUID,Long> LAST_SHARE=new HashMap<>();

    static void register() { /* Initialize attachment types during mod registration. */ }
    private static <T> List<T> get(MinecraftServer server,AttachmentType<Map<String,List<T>>> type,UUID owner){return server.overworld().getAttachedOrElse(type,Map.of()).getOrDefault(owner.toString(),List.of());}
    private static <T> void put(MinecraftServer server,AttachmentType<Map<String,List<T>>> type,UUID owner,List<T> entries){var all=new HashMap<>(server.overworld().getAttachedOrElse(type,Map.of()));all.put(owner.toString(),List.copyOf(entries));server.overworld().setAttached(type,Map.copyOf(all));}
    static Set<UUID> saved(ServerPlayer p){return Set.copyOf(get(p.level().getServer(),CONTACTS,p.getUUID()));}
    static List<Recent> history(ServerPlayer p){return get(p.level().getServer(),HISTORY,p.getUUID());}
    static boolean known(ServerPlayer p,UUID peer){return saved(p).contains(peer)||Phones.mail(p).stream().anyMatch(m->m.peer().equals(peer))||history(p).stream().anyMatch(h->h.peer.equals(peer));}
    private static boolean room(ServerPlayer p,UUID peer){var saved=saved(p);return saved.contains(peer)||saved.size()<CONTACT_LIMIT;}
    private static void save(ServerPlayer p,UUID peer){var contacts=new LinkedHashSet<>(saved(p));contacts.add(peer);put(p.level().getServer(),CONTACTS,p.getUUID(),new ArrayList<>(contacts));}
    static void add(ServerPlayer p,String number){
        String value=PhoneNumbers.normalize(number);var peer=Phones.book(p.level().getServer()).entrySet().stream().filter(e->e.getValue().number().equals(value)).findFirst().orElse(null);
        if(peer==null||peer.getKey().equals(p.getUUID().toString())){Phones.feedback(p,t("phone.unknown_number"));return;}
        UUID id=UUID.fromString(peer.getKey());if(!room(p,id)){Phones.feedback(p,t("phone.contacts_full"));return;}
        save(p,id);Phones.feedback(p,t("phone.contact_saved",peer.getValue().name()));
    }
    static void remove(ServerPlayer p,String target){
        try{var contacts=new ArrayList<>(saved(p));contacts.remove(UUID.fromString(target));put(p.level().getServer(),CONTACTS,p.getUUID(),contacts);Phones.feedback(p,t("phone.contact_removed"));}
        catch(IllegalArgumentException ignored){}
    }
    static boolean near(ServerPlayer a,ServerPlayer b){return a!=null&&b!=null&&a!=b&&a.level()==b.level()&&a.distanceToSqr(b)<=64&&Phones.available(a)&&Phones.available(b);}
    static boolean share(ServerPlayer p,UUID target){
        var server=p.level().getServer();var other=server.getPlayerList().getPlayer(target);long now=server.overworld().getGameTime();expire(server);
        if(!near(p,other)){Phones.feedback(p,t("phone.share_near"));return false;}
        if(now-LAST_SHARE.getOrDefault(p.getUUID(),Long.MIN_VALUE/2)<60||OFFERS.values().stream().anyMatch(o->o.sender.equals(p.getUUID())&&o.recipient.equals(target))){Phones.feedback(p,t("phone.share_pending"));return false;}
        if(OFFERS.values().stream().filter(o->o.recipient.equals(target)).count()>=8){Phones.feedback(p,t("phone.share_full"));return false;}
        Phones.account(p);Phones.account(other);var offer=new Offer(UUID.randomUUID(),p.getUUID(),target,now+1200);OFFERS.put(offer.token,offer);LAST_SHARE.put(p.getUUID(),now);
        Phones.feedback(p,t("phone.share_sent",other.getGameProfile().name()));Phones.feedback(other,t("phone.share_received",p.getGameProfile().name()));
        other.sendSystemMessage(t("phone.share_command",p.getGameProfile().name()));return true;
    }
    static boolean accept(ServerPlayer p,String token,boolean accepted){
        expire(p.level().getServer());Offer offer=null;
        for(var candidate:OFFERS.values())if(candidate.recipient.equals(p.getUUID())&&(candidate.token.toString().equals(token)||candidate.sender.toString().equals(token))){offer=candidate;break;}
        if(offer==null){Phones.feedback(p,t("phone.share_expired"));return false;}
        var other=p.level().getServer().getPlayerList().getPlayer(offer.sender);
        if(!accepted){OFFERS.remove(offer.token);Phones.feedback(p,t("phone.share_declined"));return true;}
        if(!near(p,other)){Phones.feedback(p,t("phone.share_near"));return false;}
        if(!room(p,other.getUUID())||!room(other,p.getUUID())){Phones.feedback(p,t("phone.contacts_full"));return false;}
        OFFERS.remove(offer.token);save(p,other.getUUID());save(other,p.getUUID());
        Phones.feedback(p,t("phone.contact_saved",other.getGameProfile().name()));Phones.feedback(other,t("phone.contact_saved",p.getGameProfile().name()));return true;
    }
    static void expire(MinecraftServer server){long now=server.overworld().getGameTime();OFFERS.values().removeIf(o->o.expires<=now);}
    static void disconnect(UUID id){OFFERS.values().removeIf(o->o.sender.equals(id)||o.recipient.equals(id));LAST_SHARE.remove(id);}
    static void clear(){OFFERS.clear();LAST_SHARE.clear();}
    static void record(MinecraftServer server,Phones.Call call,String outcome){
        int seconds=call.active()?(int)Math.max(0,(server.overworld().getGameTime()-call.started())/20):0;
        for(UUID owner:List.of(call.caller(),call.recipient())){
            UUID peer=call.other(owner);var subscriber=Phones.book(server).get(peer.toString());var recent=new ArrayList<>(get(server,HISTORY,owner));
            recent.add(new Recent(peer,subscriber==null?"?":subscriber.name(),server.overworld().getGameTime(),owner.equals(call.recipient()),outcome,seconds));while(recent.size()>32)recent.removeFirst();put(server,HISTORY,owner,recent);
        }
    }
    static void note(ServerPlayer p,String target,String body){
        String clean=Phones.clean(body);if(clean.isBlank()||clean.length()>256){Phones.feedback(p,t("phone.invalid_text"));return;}
        var server=p.level().getServer();var notes=new ArrayList<>(get(server,NOTES,p.getUUID()));UUID id;
        if(target.isBlank()){if(notes.size()>=16){Phones.feedback(p,t("phone.notes_full"));return;}id=UUID.randomUUID();}
        else{try{id=UUID.fromString(target);}catch(IllegalArgumentException ignored){return;}if(notes.stream().noneMatch(n->n.id.equals(id)))return;notes.removeIf(n->n.id.equals(id));}
        notes.add(new Note(id,clean,server.overworld().getGameTime()));put(server,NOTES,p.getUUID(),notes);Phones.feedback(p,t("phone.note_saved"));
    }
    static void deleteNote(ServerPlayer p,String target){
        var server=p.level().getServer();var notes=new ArrayList<>(get(server,NOTES,p.getUUID()));notes.removeIf(n->n.id.toString().equals(target));put(server,NOTES,p.getUUID(),notes);Phones.feedback(p,t("phone.note_deleted"));
    }
    static PhonePacket.Personal snapshot(ServerPlayer p){
        var server=p.level().getServer();long now=server.overworld().getGameTime();
        var nearby=p.level().players().stream().filter(other->near(p,other)).sorted(Comparator.comparingDouble(p::distanceToSqr)).limit(32).map(other->new PhonePacket.Contact(other.getUUID(),other.getGameProfile().name(),"",true,false)).toList();
        var offers=OFFERS.values().stream().filter(o->o.recipient.equals(p.getUUID())&&o.expires>now).limit(8).map(o->{var a=Phones.book(server).get(o.sender.toString());return new PhonePacket.Offer(o.token,o.sender,a==null?"?":a.name(),a==null?"":a.number(),(int)((o.expires-now+19)/20));}).toList();
        var recent=history(p).stream().map(h->{var a=Phones.book(server).get(h.peer.toString());return new PhonePacket.Recent(h.peer,a==null?h.name:a.name(),a==null?"":a.number(),h.time,h.incoming,h.outcome,h.seconds);}).toList();
        var notes=get(server,NOTES,p.getUUID()).stream().map(n->new PhonePacket.Note(n.id,n.body,n.time)).toList();
        return new PhonePacket.Personal(nearby,offers,recent,notes);
    }
    private PhoneBook(){}
}
