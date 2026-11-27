package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;

/** Answers use the speaker's own state and dated sightings, never an omniscient census lookup. */
final class CityDialogue {
    record Sighting(BlockPos position,long at,String activity){static final Codec<Sighting> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("position").forGetter(Sighting::position),Codec.LONG.fieldOf("at").forGetter(Sighting::at),Codec.STRING.fieldOf("activity").forGetter(Sighting::activity)).apply(i,Sighting::new));}
    static final AttachmentType<Map<String,Sighting>> SIGHTINGS=AttachmentRegistry.<Map<String,Sighting>>builder().persistent(Codec.unboundedMap(Codec.STRING,Sighting.CODEC)).buildAndRegister(Napuli.id("neighbor_sightings"));
    private static final Map<ServerLevel,Integer> OBSERVERS=new WeakHashMap<>();
    private static void prune(Map<String,Sighting> all,long now){
        all.values().removeIf(s->now-s.at()>48000);
        var perPerson=new HashMap<String,Integer>();var keep=new HashSet<String>();
        all.entrySet().stream().sorted(Comparator.<Map.Entry<String,Sighting>>comparingLong(e->e.getValue().at()).reversed().thenComparing(Map.Entry::getKey)).limit(8192)
                .forEach(e->{String observer=e.getKey().split("/",2)[0];if(perPerson.merge(observer,1,Integer::sum)<=12)keep.add(e.getKey());});
        all.keySet().retainAll(keep);
    }
    static void remember(ServerLevel city,UUID observer,UUID subject,BlockPos position,String activity){
        if(observer.equals(subject))return;var all=new HashMap<>(city.getAttachedOrElse(SIGHTINGS,Map.of()));long now=city.getGameTime();all.put(observer+"/"+subject,new Sighting(position.immutable(),now,activity));prune(all,now);city.setAttached(SIGHTINGS,Map.copyOf(all));
    }
    static String where(ServerLevel city,UUID speaker,UUID subject){
        var sighting=city.getAttachedOrElse(SIGHTINGS,Map.of()).get(speaker+"/"+subject);if(sighting==null||city.getGameTime()-sighting.at()>48000)return "Non lo so, non l'ho visto di recente.";
        var c=NapoliCivicLife.profile(city,subject);long minutes=Math.max(0,(city.getGameTime()-sighting.at())*3/50);return "Ho visto "+(c==null?"quella persona":c.name())+" "+(minutes==0?"poco fa":minutes+" minuti di gioco fa")+", "+place(sighting.position())+". Adesso potrebbe essersi spostato.";
    }
    static String place(BlockPos pos){
        var home=CityLivingHomes.at(pos);if(home!=null)return "vicino a "+home.name();
        var service=Arrays.stream(CityProfessions.Category.values()).filter(c->c.site().offset(8,0,7).distSqr(pos)<30*30).findFirst().orElse(null);if(service!=null)return "presso "+CityProfessions.name(service);
        return NapoliAtlas.ORIGINAL.contains(pos.getX(),pos.getZ())?(pos.getX()<-315?"nel Rione Partenope":"in centro"):"nel quartiere "+NapoliAtlas.nearest(pos.getX(),pos.getZ()).district();
    }
    static String whereNamed(ServerLevel city,UUID speaker,String name){
        String wanted=CityConversation.normalize(name);var known=new ArrayList<UUID>();
        for(var entry:city.getAttachedOrElse(SIGHTINGS,Map.of()).entrySet())if(entry.getKey().startsWith(speaker+"/")&&city.getGameTime()-entry.getValue().at()<=48000){
            var id=UUID.fromString(entry.getKey().substring(37));var person=NapoliCivicLife.profile(city,id);
            if(person!=null&&(CityConversation.normalize(person.name()).equals(wanted)||CityConversation.normalize(person.name()).startsWith(wanted+" ")))known.add(id);
        }
        if(known.size()>1)return "Ho incontrato più persone con quel nome. Chiedimi il nome e il cognome, oppure scegli la persona nel dialogo.";
        return known.isEmpty()?"Non lo so, non ho visto di recente una persona con quel nome.":where(city,speaker,known.getFirst());
    }
    static String answer(ServerLevel city,NapoliCivicLife.Citizen c,String topic){
        var life=CitySimulation.life(city,c.id());var needs=life.needs();
        if(topic.equals("stai")){var grief=CityRelationships.recent(city,c.id(),"grief");if(!grief.isEmpty())return grief;var birthday=CityRelationships.recent(city,c.id(),"birthday");if(!birthday.isEmpty())return birthday;var separation=CityRelationships.recent(city,c.id(),"separation");if(!separation.isEmpty())return separation;}
        if(topic.equals("stai"))return life.health()<45?"Non mi sento bene, ho bisogno dell'ambulatorio.":needs.sleep()<35?"Sono stanco, oggi cerco di riposare.":needs.hunger()<35?"Ho fame, devo preparare qualcosa.":needs.social()<35?"Mi farebbe piacere un po' di compagnia.":needs.mood()<40?"Non è una bella giornata. Ho bisogno di un po' di tempo.":"Sto bene, grazie di avermelo chiesto!";
        if(topic.equals("chiuso")){
            if(c.profession()==null)return "In questo periodo cerco un impiego.";
            if(life.health()<45)return "Oggi sono malato: ho dovuto lasciare il lavoro.";
            if(CityNightlife.vendor(c))return CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city))?"Tengo aperta la bottega per chi esce la sera. Puoi comprare finché ci sono scorte.":"Faccio il turno serale: apro alle 18:00 e chiudo alle 04:30.";
            if(!CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city)))return "Ora sono fuori turno. Torno "+(CityProfessions.night(c.profession())?"per il servizio serale.":"nel prossimo orario di apertura.");
            if(c.profession().category==CityProfessions.Category.FOOD){if(CityPersonalGoals.ovenBroken(city))return "Il forno è guasto: aspettiamo la riparazione.";var stock=CityEconomy.stock(city,"forno");if(stock.flour()+stock.grain()==0)return "Sono finite le materie prime. Aspetto un rifornimento.";}
            return "Il servizio è aperto, ma potresti dover aspettare il tuo turno.";
        }
        var goal=CityPersonalGoals.goal(city,c.id());if(goal!=null&&!goal.complete()&&!goal.helper().isEmpty())return CityPersonalGoals.description(goal);
        var work=CityOccupations.description(city,c.id());var entity=city.getEntity(c.id());if(entity instanceof NapoliResident npc&&npc.activity()==NapoliHouseholds.Activity.WORK&&!work.isEmpty())return work;
        return CityHabits.reason(city,c)+" "+CityCharacter.describe(city,c);
    }
    static int ask(ServerPlayer p,UUID id,String topic,UUID subject){
        if(!topic.equals("visto"))return CityConversation.talk(p,id,topic);
        var citizen=CitySocial.nearby(p,id);if(citizen==null||!(p.level().getEntity(id) instanceof NapoliResident npc)||!npc.hasLineOfSight(p))return 0;
        if(npc.isSleeping())return 0;
        CitySocial.meet(p.level(),id,p.getUUID(),NapoliBroadcast.day(p.level()));String reply=topic.equals("visto")?where(p.level(),id,subject):answer(p.level(),citizen,topic);
        npc.getLookControl().setLookAt(p,30,30);if(npc.gesture()==CityGestures.Gesture.NONE)npc.animate(CityGestures.Gesture.WAVE,45);
        NpcSpeech.reply(p,npc,Component.literal(reply));CityConversation.show(p,citizen,reply);return 1;
    }
    static void observe(ServerLevel city){
        var all=new HashMap<>(city.getAttachedOrElse(SIGHTINGS,Map.of()));long now=city.getGameTime();var observers=new ArrayList<NapoliResident>();
        for(var entity:city.getAllEntities())if(entity instanceof NapoliResident npc&&npc.isAlive()&&!npc.isSleeping()&&NapoliCivicLife.profile(city,npc.getUUID())!=null)observers.add(npc);
        observers.sort(Comparator.comparing(NapoliResident::getUUID));int start=observers.isEmpty()?0:Math.floorMod(OBSERVERS.getOrDefault(city,0),observers.size()),count=Math.min(16,observers.size());
        for(int n=0;n<count;n++){
            var observer=observers.get((start+n)%observers.size());var neighbors=city.getEntitiesOfClass(NapoliResident.class,observer.getBoundingBox().inflate(12),other->other!=observer&&other.isAlive()&&NapoliCivicLife.profile(city,other.getUUID())!=null);
            neighbors.sort(Comparator.comparing(NapoliResident::getUUID));if(neighbors.isEmpty())continue;
            int first=Math.floorMod(now/200+observer.getUUID().hashCode(),neighbors.size());
            for(int j=0;j<Math.min(4,neighbors.size());j++){var other=neighbors.get((first+j)%neighbors.size());if(observer.hasLineOfSight(other))all.put(observer.getUUID()+"/"+other.getUUID(),new Sighting(other.blockPosition().immutable(),now,other.activity().name()));}
        }
        OBSERVERS.put(city,start+count);prune(all,now);city.setAttached(SIGHTINGS,Map.copyOf(all));
    }
    static void register(){
        CityConversation.register();
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%200==0){var city=server.getLevel(NapoliMap.LEVEL);if(city!=null)observe(city);}});
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var person=Commands.argument("persona",StringArgumentType.string()).suggests((context,builder)->{var p=context.getSource().getPlayer();if(p!=null)for(var npc:p.level().getEntitiesOfClass(NapoliResident.class,p.getBoundingBox().inflate(6))) {var c=CitySocial.nearby(p,npc.getUUID());if(c!=null)builder.suggest(StringArgumentType.escapeIfRequired(c.name()));}return builder.buildFuture();})
                    .executes(c->command(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"persona"),"vicino"));
            person.then(Commands.argument("messaggio",StringArgumentType.greedyString()).executes(c->command(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"persona"),StringArgumentType.getString(c,"messaggio"))));
            d.register(Commands.literal("parla").executes(c->{var p=c.getSource().getPlayerOrException();var npc=p.level().getEntitiesOfClass(NapoliResident.class,p.getBoundingBox().inflate(6),n->CitySocial.nearby(p,n.getUUID())!=null&&n.hasLineOfSight(p)).stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);return npc==null?0:command(p,npc.getUUID().toString(),"vicino");}).then(person));
        });
    }
    private static int command(ServerPlayer p,String id,String message){
        UUID target;
        try{target=UUID.fromString(id);}catch(IllegalArgumentException invalid){var matches=p.level().getEntitiesOfClass(NapoliResident.class,p.getBoundingBox().inflate(6),n->n.getName().getString().equalsIgnoreCase(id)&&CitySocial.nearby(p,n.getUUID())!=null);if(matches.size()!=1){p.sendSystemMessage(Component.literal("Avvicinati alla persona e usa /parla, oppure scegli un nome univoco dai suggerimenti."));return 0;}target=matches.getFirst().getUUID();}
        if(message.equals("vicino")){var c=CitySocial.nearby(p,target);if(c==null||!(p.level().getEntity(target) instanceof NapoliResident npc)||!npc.hasLineOfSight(p))return 0;CitySocial.open(p,c);return 1;}
        if(message.startsWith("visto "))try{return ask(p,target,"visto",UUID.fromString(message.substring(6).trim()));}catch(IllegalArgumentException invalid){return 0;}
        if(message.startsWith("dice "))message=message.substring(5);
        return CityConversation.talk(p,target,message);
    }
    private CityDialogue(){}
}
