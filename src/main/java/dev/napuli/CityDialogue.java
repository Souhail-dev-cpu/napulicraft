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
    static void remember(ServerLevel city,UUID observer,UUID subject,BlockPos position,String activity){
        if(observer.equals(subject))return;var all=new HashMap<>(city.getAttachedOrElse(SIGHTINGS,Map.of()));long now=city.getGameTime();all.put(observer+"/"+subject,new Sighting(position.immutable(),now,activity));all.values().removeIf(s->now-s.at()>48000);while(all.size()>512){String oldest=all.entrySet().stream().min(Comparator.comparingLong(e->e.getValue().at())).orElseThrow().getKey();all.remove(oldest);}city.setAttached(SIGHTINGS,Map.copyOf(all));
    }
    static String where(ServerLevel city,UUID speaker,UUID subject){
        var sighting=city.getAttachedOrElse(SIGHTINGS,Map.of()).get(speaker+"/"+subject);if(sighting==null||city.getGameTime()-sighting.at()>48000)return "Non lo so, non l'ho visto di recente.";
        var c=NapoliCivicLife.profile(city,subject);long minutes=Math.max(0,(city.getGameTime()-sighting.at())/1200);return "Ho visto "+(c==null?"quella persona":c.name())+" "+(minutes==0?"poco fa":minutes+" minuti di gioco fa")+", "+place(sighting.position())+". Adesso potrebbe essersi spostato.";
    }
    static String place(BlockPos pos){
        var home=CityLivingHomes.all().stream().filter(h->h.contains(pos)).findFirst().orElse(null);if(home!=null)return "vicino a "+home.name();
        var service=Arrays.stream(CityProfessions.Category.values()).filter(c->c.site().offset(8,0,7).distSqr(pos)<30*30).findFirst().orElse(null);if(service!=null)return "presso "+CityProfessions.name(service);
        return pos.getX()<-315?"nel Rione Partenope":pos.getX()>340?"a Fuorigrotta":"in centro";
    }
    static String answer(ServerLevel city,NapoliCivicLife.Citizen c,String topic){
        var life=CitySimulation.life(city,c.id());var needs=life.needs();
        if(topic.equals("stai")){var grief=CityRelationships.recent(city,c.id(),"grief");if(!grief.isEmpty())return grief;var birthday=CityRelationships.recent(city,c.id(),"birthday");if(!birthday.isEmpty())return birthday;var separation=CityRelationships.recent(city,c.id(),"separation");if(!separation.isEmpty())return separation;}
        if(topic.equals("stai"))return life.health()<45?"Non mi sento bene, ho bisogno dell'ambulatorio.":needs.sleep()<35?"Sono stanco, oggi cerco di riposare.":needs.hunger()<35?"Ho fame, devo preparare qualcosa.":needs.social()<35?"Mi farebbe piacere un po' di compagnia.":needs.mood()<40?"Non è una bella giornata. Ho bisogno di un po' di tempo.":"Sto bene, grazie di avermelo chiesto!";
        if(topic.equals("chiuso")){
            if(c.profession()==null)return "In questo periodo cerco un impiego.";
            if(life.health()<45)return "Oggi sono malato: ho dovuto lasciare il lavoro.";
            if(!CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city)))return "Ora sono fuori turno. Torno "+(CityProfessions.night(c.profession())?"per il servizio serale.":"nel prossimo orario di apertura.");
            if(c.profession().category==CityProfessions.Category.FOOD){if(CityPersonalGoals.ovenBroken(city))return "Il forno è guasto: aspettiamo la riparazione.";var stock=CityEconomy.stock(city,"forno");if(stock.flour()+stock.grain()==0)return "Sono finite le materie prime. Aspetto un rifornimento.";}
            return "Il servizio è aperto, ma potresti dover aspettare il tuo turno.";
        }
        var goal=CityPersonalGoals.goal(city,c.id());if(goal!=null&&!goal.complete()&&!goal.helper().isEmpty())return CityPersonalGoals.description(goal);
        var work=CityOccupations.description(city,c.id());var entity=city.getEntity(c.id());if(entity instanceof NapoliResident npc&&npc.activity()==NapoliHouseholds.Activity.WORK&&!work.isEmpty())return work;
        return CityHabits.reason(city,c)+" "+CityCharacter.describe(city,c);
    }
    static int ask(ServerPlayer p,UUID id,String topic,UUID subject){
        var citizen=CitySocial.nearby(p,id);if(citizen==null||!(p.level().getEntity(id) instanceof NapoliResident npc)||!npc.hasLineOfSight(p))return 0;
        CitySocial.meet(p.level(),id,p.getUUID(),NapoliBroadcast.day(p.level()));String reply=topic.equals("visto")?where(p.level(),id,subject):answer(p.level(),citizen,topic);
        npc.getLookControl().setLookAt(p,30,30);if(npc.gesture()==CityGestures.Gesture.NONE)npc.animate(CityGestures.Gesture.WAVE,45);
        var buttons=new ArrayList<ActionButton>();for(var entry:Map.of("Come stai?","stai","Che programmi hai?","piani","Perché sei chiuso?","chiuso").entrySet())buttons.add(CitySocial.button(entry.getKey(),"/parla "+id+" "+entry.getValue()));
        p.level().getAttachedOrElse(SIGHTINGS,Map.of()).entrySet().stream().filter(e->e.getKey().startsWith(id+"/")).sorted(Map.Entry.<String,Sighting>comparingByValue(Comparator.comparingLong(Sighting::at)).reversed()).limit(8).forEach(e->{var target=UUID.fromString(e.getKey().substring(37));var c=NapoliCivicLife.profile(p.level(),target);if(c!=null)buttons.add(CitySocial.button("Hai visto "+c.name()+"?","/parla "+id+" visto "+target));});
        buttons.add(CitySocial.button("Torna al dialogo","/parla "+id+" vicino"));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(citizen.name()),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(Component.literal(reply),270)),List.of()),buttons,Optional.empty(),1)));return 1;
    }
    static void observe(ServerLevel city){
        var all=new HashMap<>(city.getAttachedOrElse(SIGHTINGS,Map.of()));long now=city.getGameTime();int budget=24;
        for(var entity:city.getAllEntities())if(entity instanceof NapoliResident observer&&NapoliCivicLife.profile(city,observer.getUUID())!=null&&!observer.isSleeping()){
            for(var other:city.getEntitiesOfClass(NapoliResident.class,observer.getBoundingBox().inflate(12),n->n!=observer&&NapoliCivicLife.profile(city,n.getUUID())!=null))if(observer.hasLineOfSight(other)){
                all.put(observer.getUUID()+"/"+other.getUUID(),new Sighting(other.blockPosition().immutable(),now,other.activity().name()));if(--budget==0)break;
            }if(budget==0)break;
        }
        all.values().removeIf(s->now-s.at()>48000);while(all.size()>512){String key=all.entrySet().stream().min(Comparator.comparingLong(e->e.getValue().at())).orElseThrow().getKey();all.remove(key);}city.setAttached(SIGHTINGS,Map.copyOf(all));
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%200==0){var city=server.getLevel(NapoliMap.LEVEL);if(city!=null)observe(city);}});
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var topic=Commands.argument("argomento",StringArgumentType.word()).executes(c->command(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"persona"),StringArgumentType.getString(c,"argomento"),""));
            topic.then(Commands.argument("nome",StringArgumentType.word()).executes(c->command(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"persona"),StringArgumentType.getString(c,"argomento"),StringArgumentType.getString(c,"nome"))));
            d.register(Commands.literal("parla").then(Commands.argument("persona",StringArgumentType.word()).then(topic)));
        });
    }
    private static int command(ServerPlayer p,String id,String topic,String subject){try{var target=UUID.fromString(id);if(topic.equals("vicino")){var c=CitySocial.nearby(p,target);if(c==null)return 0;CitySocial.open(p,c);return 1;}if(!Set.of("stai","piani","chiuso","visto").contains(topic))return 0;return ask(p,target,topic,topic.equals("visto")?UUID.fromString(subject):target);}catch(IllegalArgumentException invalid){return 0;}}
    private CityDialogue(){}
}
