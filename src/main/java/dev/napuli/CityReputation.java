package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;

/** A story travels through actual conversations. Its source and every intermediate teller remain known. */
final class CityReputation {
    record Knowledge(int confidence,String via,long heard,long told) {
        static final Codec<Knowledge> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("confidence").forGetter(Knowledge::confidence),Codec.STRING.fieldOf("via").forGetter(Knowledge::via),Codec.LONG.fieldOf("heard").forGetter(Knowledge::heard),Codec.LONG.fieldOf("told").forGetter(Knowledge::told)).apply(i,Knowledge::new));
    }
    record Story(UUID subject,UUID source,String sourceName,String kind,String detail,int impact,long at,Map<String,Knowledge> knowers) {
        static final Codec<Story> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("subject").forGetter(Story::subject),UUIDUtil.CODEC.fieldOf("source").forGetter(Story::source),Codec.STRING.fieldOf("source_name").forGetter(Story::sourceName),
                Codec.STRING.fieldOf("kind").forGetter(Story::kind),Codec.STRING.fieldOf("detail").forGetter(Story::detail),Codec.INT.fieldOf("impact").forGetter(Story::impact),Codec.LONG.fieldOf("at").forGetter(Story::at),
                Codec.unboundedMap(Codec.STRING,Knowledge.CODEC).fieldOf("knowers").forGetter(Story::knowers)).apply(i,Story::new));
        Story with(Map<String,Knowledge> people){return new Story(subject,source,sourceName,kind,detail,impact,at,Map.copyOf(people));}
    }
    static final AttachmentType<Map<String,Story>> STORIES=AttachmentRegistry.<Map<String,Story>>builder().persistent(Codec.unboundedMap(Codec.STRING,Story.CODEC)).buildAndRegister(Napuli.id("neighborhood_stories"));
    static final AttachmentType<Set<String>> NOTEBOOK=AttachmentRegistry.<Set<String>>builder().persistent(Codec.STRING.listOf().xmap(Set::copyOf,List::copyOf)).copyOnDeath().buildAndRegister(Napuli.id("heard_stories"));
    static String record(ServerLevel city,UUID subject,UUID source,String kind,String detail,int impact){
        long now=city.getGameTime();String id=source+"/"+subject+"/"+kind+"/"+(now/1200);
        var all=new HashMap<>(city.getAttachedOrElse(STORIES,Map.of()));if(all.containsKey(id))return id;
        var npc=city.getEntity(source);var profile=NapoliCivicLife.profile(city,source);String name=npc!=null?npc.getName().getString():profile!=null?profile.name():"un vicino";
        var knowers=new HashMap<String,Knowledge>();knowers.put(source.toString(),new Knowledge(100,source.toString(),now,-1200));
        var player=city.getServer().getPlayerList().getPlayer(subject);
        if(npc instanceof NapoliResident&&player!=null&&player.level()==city)for(var observer:city.getEntitiesOfClass(NapoliResident.class,npc.getBoundingBox().inflate(16),n->n.isAlive()&&!n.role().spirit()))
            if(observer.hasLineOfSight(npc)&&observer.hasLineOfSight(player))knowers.put(observer.getUUID().toString(),new Knowledge(100,source.toString(),now,-1200));
        all.put(id,new Story(subject,source,name,kind,detail,Math.clamp(impact,-20,20),now,Map.copyOf(knowers)));
        all.entrySet().removeIf(e->now-e.getValue().at()>14L*24000);
        while(all.size()>256){var oldest=all.entrySet().stream().min(Comparator.comparingLong(e->e.getValue().at())).orElseThrow();all.remove(oldest.getKey());}
        city.setAttached(STORIES,Map.copyOf(all));return id;
    }
    static int influence(ServerLevel city,UUID observer,UUID subject){
        int effect=0;long now=city.getGameTime();
        for(var s:city.getAttachedOrElse(STORIES,Map.of()).values()){
            var k=s.knowers().get(observer.toString());if(k==null||s.source().equals(observer)||!s.subject().equals(subject)||now-s.at()>14L*24000)continue;
            effect+=s.impact()*k.confidence()/100;
        }return Math.clamp(effect,-20,20);
    }
    static int trust(ServerLevel city,UUID observer,UUID subject,int direct){return Math.clamp(direct+influence(city,observer,subject),0,100);}
    static int priceAdjustment(ServerLevel city,UUID observer,UUID subject){int effect=influence(city,observer,subject);return effect<=-10?10:effect>=6?-5:0;}
    static void spread(ServerLevel city,long now){
        var all=new HashMap<>(city.getAttachedOrElse(STORIES,Map.of()));int budget=8;
        for(var entry:List.copyOf(all.entrySet())){
            var story=entry.getValue();if(now-story.at()>14L*24000){all.remove(entry.getKey());continue;}
            var people=new HashMap<>(story.knowers());boolean changed=false;
            for(var teller:story.knowers().entrySet()){
                var knowledge=teller.getValue();if(now-knowledge.heard()<600||now-knowledge.told()<1200||knowledge.confidence()<40)continue;
                var entity=city.getEntity(UUID.fromString(teller.getKey()));if(!(entity instanceof NapoliResident speaker)||speaker.isSleeping()||speaker.activity()==NapoliHouseholds.Activity.WORK)continue;
                for(var listener:city.getEntitiesOfClass(NapoliResident.class,speaker.getBoundingBox().inflate(6),n->n!=speaker&&n.isAlive()&&!n.isSleeping()&&!n.role().spirit())){
                    if(!speaker.hasLineOfSight(listener))continue;
                    var a=NapoliCivicLife.profile(city,speaker.getUUID());var b=NapoliCivicLife.profile(city,listener.getUUID());
                    boolean linked=a!=null&&b!=null&&(!a.property().isEmpty()&&a.property().equals(b.property())||CitySimulation.life(city,a.id()).relations().getOrDefault(b.id().toString(),0)>=60);
                    if(!linked&&(speaker.activity()!=NapoliHouseholds.Activity.SOCIAL||listener.activity()!=NapoliHouseholds.Activity.SOCIAL))continue;
                    int confidence=knowledge.confidence()*3/4;var previous=people.get(listener.getUUID().toString());if(previous!=null&&previous.confidence()>=confidence)continue;
                    people.put(listener.getUUID().toString(),new Knowledge(confidence,teller.getKey(),now,-1200));people.put(teller.getKey(),new Knowledge(knowledge.confidence(),knowledge.via(),knowledge.heard(),now));changed=true;budget--;break;
                }if(budget==0)break;
            }if(changed)all.put(entry.getKey(),story.with(people));if(budget==0)break;
        }city.setAttached(STORIES,Map.copyOf(all));
    }
    static String tell(ServerPlayer p,UUID speaker){
        var chosen=p.level().getAttachedOrElse(STORIES,Map.of()).entrySet().stream().filter(e->e.getValue().knowers().containsKey(speaker.toString())).max(Comparator.<Map.Entry<String,Story>>comparingInt(e->e.getValue().subject().equals(p.getUUID())?1:0).thenComparingLong(e->e.getValue().at())).orElse(null);
        if(chosen==null)return "";var s=chosen.getValue();var k=s.knowers().get(speaker.toString());
        var notes=new HashSet<>(p.getAttachedOrElse(NOTEBOOK,Set.of()));notes.add(chosen.getKey());if(notes.size()>128)notes.retainAll(p.level().getAttachedOrElse(STORIES,Map.of()).keySet());p.setAttached(NOTEBOOK,Set.copyOf(notes));
        if(k.confidence()==100)return "Ho visto che "+s.detail();
        var via=NapoliCivicLife.profile(p.level(),UUID.fromString(k.via()));String name=via!=null?via.name():k.via().equals(s.source().toString())?s.sourceName():"un conoscente";
        return "Secondo "+name+", "+s.detail()+" È un racconto passato tra vicini.";
    }
    static void notebook(ServerPlayer p){
        var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();var all=p.level().getAttachedOrElse(STORIES,Map.of());
        for(var id:p.getAttachedOrElse(NOTEBOOK,Set.of())){var s=all.get(id);if(s!=null)body.add(new PlainMessage(Component.literal(s.detail()+"\nPrima testimonianza: "+s.sourceName()),270));}
        if(body.isEmpty())body.add(new PlainMessage(Component.literal("Parla con i vicini: qui conserverai solo le notizie che ti sono state raccontate."),270));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Voci del quartiere"),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),List.of(CitySocial.button("Chiudi","/quartiere chiudi")),Optional.empty(),1)));
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)spread(city,city.getGameTime());}});
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("quartiere").then(Commands.literal("voci").executes(c->{notebook(c.getSource().getPlayerOrException());return 1;})).then(Commands.literal("chiudi").executes(c->1))));
    }
    private CityReputation(){}
}
