package dev.napuli;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.Normalizer;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.*;

/** Local, contextual conversations. No external service or invented knowledge about other residents. */
final class CityConversation {
    enum Topic { GREETING, WELLBEING, PLANS, WORK, FAMILY, TASTES, NEWS, HELP, DIRECTIONS, GOODBYE, UNKNOWN }
    record Memory(String topic,long at,int turns) {
        static final Codec<Memory> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.STRING.fieldOf("topic").forGetter(Memory::topic),Codec.LONG.fieldOf("at").forGetter(Memory::at),
                Codec.INT.fieldOf("turns").forGetter(Memory::turns)).apply(i,Memory::new));
    }
    static final AttachmentType<Map<String,Memory>> MEMORIES=AttachmentRegistry.<Map<String,Memory>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Memory.CODEC)).buildAndRegister(Napuli.id("citizen_conversations"));
    private record Session(UUID visitor,long until,long started) {}
    private static final Map<ServerLevel,Map<UUID,Session>> SESSIONS=new WeakHashMap<>();
    static void register() {}
    static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ITALIAN).replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();}
    private static boolean words(String text,String... words){for(String word:words)if((" "+text+" ").contains(" "+word+" "))return true;return false;}
    static boolean followUp(String text){return Set.of("perche","e poi","dimmi di piu","raccontami di piu","continua","in che senso").contains(normalize(text));}
    static Topic intent(String input){
        String text=normalize(input);
        if(words(text,"arrivederci","addio","a presto","a dopo","buonanotte","ciao ciao"))return Topic.GOODBYE;
        if(words(text,"metro","fermata","stazione","indicazioni","municipio","ospedale","ambulatorio","mercato","medico","dove posso","come arrivo","dove si trova"))return Topic.DIRECTIONS;
        if(words(text,"famiglia","figli","figlio","figlia","moglie","marito","genitori","mamma","papa"))return Topic.FAMILY;
        if(words(text,"aiuto","aiutarti","aiutare","bisogno","serve qualcosa"))return Topic.HELP;
        if(words(text,"chiuso","lavoro","lavori","mestiere","turno","bottega","aperto"))return Topic.WORK;
        if(words(text,"piace","piacciono","preferisci","preferito","preferita","hobby","passioni"))return Topic.TASTES;
        if(words(text,"novita","notizie","quartiere","tempo","pioggia","napoli"))return Topic.NEWS;
        if(words(text,"stai","salute","senti","come va","tutto bene"))return Topic.WELLBEING;
        if(words(text,"programmi","piani","fai","facendo","vai","giornata","dopo"))return Topic.PLANS;
        if(words(text,"ciao","salve","buongiorno","buonasera","piacere","grazie"))return Topic.GREETING;
        return Topic.UNKNOWN;
    }
    static String label(Topic topic){return switch(topic){
        case WELLBEING->"come stai";case PLANS->"i tuoi programmi";case WORK->"il lavoro";case FAMILY->"la famiglia";
        case TASTES->"le tue passioni";case NEWS->"il quartiere";case HELP->"come darti una mano";case DIRECTIONS->"le indicazioni";default->"due chiacchiere";};}
    static Memory memory(ServerLevel city,UUID citizen,UUID visitor){return city.getAttachedOrElse(MEMORIES,Map.of()).get(citizen+"/"+visitor);}
    static Topic previous(Memory memory){try{return memory==null?Topic.UNKNOWN:Topic.valueOf(memory.topic());}catch(IllegalArgumentException ignored){return Topic.UNKNOWN;}}
    static void remember(ServerLevel city,UUID citizen,UUID visitor,Topic topic){
        if(topic==Topic.UNKNOWN||topic==Topic.GOODBYE||topic==Topic.GREETING)return;
        var all=new HashMap<>(city.getAttachedOrElse(MEMORIES,Map.of()));String key=citizen+"/"+visitor;var old=all.get(key);
        all.put(key,new Memory(topic.name(),city.getGameTime(),old==null?1:Math.min(100000,old.turns()+1)));
        if(all.size()>4096){var keys=all.entrySet().stream().sorted(Comparator.comparingLong(e->e.getValue().at())).limit(all.size()-4096).map(Map.Entry::getKey).toList();keys.forEach(all::remove);}
        city.setAttached(MEMORIES,Map.copyOf(all));
    }
    static boolean canPause(ServerLevel city,NapoliResident npc,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity action){
        var l=CitySimulation.life(city,c.id());
        return !npc.isSleeping()&&!npc.isPassenger()&&npc.getTarget()==null&&!CityPublicOrder.jailed(city,c.id())
                &&c.profession()!=NapoliCivicLife.Job.CARABINIERE&&l.health()>=45&&l.needs().hunger()>=30&&l.needs().bladder()>=25
                &&l.needs().sleep()>=25&&switch(action){case SOCIAL,REST,PLAY,WAIT->true;default->false;};
    }
    static boolean hold(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity action){
        var sessions=SESSIONS.get(city);if(sessions==null)return false;var session=sessions.get(c.id());if(session==null)return false;
        var visitor=city.getServer().getPlayerList().getPlayer(session.visitor());
        if(city.getGameTime()>=session.until()||visitor==null||CitySocial.nearby(visitor,c.id())==null||!npc.hasLineOfSight(visitor)||!canPause(city,npc,c,action)){
            sessions.remove(c.id());return false;
        }
        npc.getNavigation().stop();npc.getLookControl().setLookAt(visitor,30,30);npc.setActivity(NapoliHouseholds.Activity.SOCIAL);return true;
    }
    static boolean engaged(ServerLevel city,UUID id){var session=SESSIONS.getOrDefault(city,Map.of()).get(id);return session!=null&&session.until()>city.getGameTime();}
    private static boolean attend(ServerPlayer p,NapoliResident npc,NapoliCivicLife.Citizen c){
        var city=p.level();var sessions=SESSIONS.computeIfAbsent(city,k->new HashMap<>());long now=city.getGameTime();
        sessions.entrySet().removeIf(e->e.getValue().until()<=now||city.getEntity(e.getKey())==null);
        var old=sessions.get(c.id());
        if(old!=null){var previous=city.getServer().getPlayerList().getPlayer(old.visitor());if(previous==null||CitySocial.nearby(previous,c.id())==null){sessions.remove(c.id());old=null;}}
        if(old!=null&&!old.visitor().equals(p.getUUID())){NpcSpeech.say(p,npc,Component.literal("Sto finendo di parlare con un'altra persona. Ti ascolto tra poco."));return false;}
        var action=CitySimulation.activity(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city));
        if(canPause(city,npc,c,action)){
            CityNeighborhood.leave(city,c.id());
            long started=old==null?now:old.started();sessions.put(c.id(),new Session(p.getUUID(),Math.min(now+200,started+600),started));
            npc.getNavigation().stop();npc.getLookControl().setLookAt(p,30,30);npc.animate(CityGestures.Gesture.WAVE,35);
        }
        return true;
    }
    static String reply(ServerPlayer p,NapoliCivicLife.Citizen c,Topic topic,String question,boolean follow){
        var city=p.level();var bond=CitySocial.bond(city,c.id(),p.getUUID());
        String answer=switch(topic){
            case GREETING->{var old=memory(city,c.id(),p.getUUID());yield CitySocial.greeting(bond,p.getName().getString())+(old!=null&&previous(old)!=Topic.UNKNOWN?" L'altra volta parlavamo di "+label(previous(old))+".":"");}
            case WELLBEING->CityDialogue.answer(city,c,"stai");
            case PLANS->CityDialogue.answer(city,c,"piani");
            case WORK->c.age(NapoliBroadcast.day(city))<18?"Vado ancora a scuola. Tra lezioni e compiti, aspetto il momento di giocare con gli amici.":CityDialogue.answer(city,c,"chiuso");
            case FAMILY->family(city,c,bond.trust());
            case TASTES->CityCharacter.describe(city,c);
            case HELP->{var goal=CityPersonalGoals.goal(city,c.id());yield goal!=null&&!goal.complete()?CityPersonalGoals.description(goal):"Delle provviste farebbero comodo. Dal menu del vicino puoi portarmi quattro unità di grano.";}
            case NEWS->{String heard=CityReputation.tell(p,c.id());yield !heard.isEmpty()?heard:city.isRaining()?"Oggi piove: ci ritroviamo al coperto. Con questo tempo preferisco una chiacchiera al bar.":"Qui ci incontriamo tra la bottega e la piazza. "+CityCharacter.describe(city,c);}
            case DIRECTIONS->directions(c,question);
            case GOODBYE->"A presto, "+p.getName().getString()+"! Ci vediamo in giro.";
            case UNKNOWN->"Non ho capito bene. Puoi chiedermi come sto, del lavoro, della famiglia o dove si trova una fermata, il mercato o l'ambulatorio.";
        };
        if(follow)answer+=" "+switch(topic){
            case WELLBEING->"Cerco di organizzare la giornata senza trascurare riposo, pasti e un po' di compagnia.";
            case WORK->"I miei orari e le scorte della bottega contano: non posso servire sempre e senza materiali.";
            case FAMILY->"Mi piace prendermi del tempo per le persone di casa.";
            case TASTES->CityCharacter.tastes(c.id()).quiet()?"Quando c'è confusione cerco un posto tranquillo.":"Ritrovare gli amici rende migliore anche una giornata normale.";
            case PLANS->"Se ho fame, sono stanco o qualcuno ha bisogno di me, cambio i miei programmi.";
            default->"Se vuoi, possiamo parlare anche della mia giornata o del quartiere.";};
        return answer;
    }
    static String family(ServerLevel city,NapoliCivicLife.Citizen c,int trust){
        if(trust<30)return "Preferisco parlare della mia famiglia quando ci conosceremo meglio.";
        var people=NapoliCivicLife.census(city);if(people==null)return "Per me la famiglia è importante.";
        var relatives=people.people().stream().filter(o->!o.id().equals(c.id())&&(!c.property().isEmpty()&&c.property().equals(o.property())||o.id().toString().equals(c.partner())||o.id().toString().equals(c.parents())))
                .limit(5).map(NapoliCivicLife.Citizen::name).toList();
        return relatives.isEmpty()?"In questo periodo vivo da solo. Mi fa piacere conoscere i vicini.":"La mia famiglia: "+String.join(", ",relatives)+". Cerchiamo di ritrovarci a casa dopo gli impegni della giornata.";
    }
    static String directions(NapoliCivicLife.Citizen c,String question){
        var from=NapoliCivicLife.home(c);String text=normalize(question),name;BlockPos to;
        if(words(text,"ambulatorio","ospedale","medico")){to=CityServices.place("doctor");name="l'ambulatorio";}
        else if(words(text,"municipio","comune")){to=CityServices.place("municipality");name="il municipio";}
        else if(words(text,"mercato","negozio","spesa","pane")){var local=CityCharacter.usualShop(c);to=local.business().entrance();name="la bottega di "+local.name();}
        else{var stop=CityNetwork.nearest(from);to=stop.entrance();name="la fermata "+stop.name();}
        int dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();String direction=Math.abs(dx)>Math.abs(dz)?dx>0?"est":"ovest":dz>0?"sud":"nord";
        return "Da casa mia, "+name+" è verso "+direction+", a circa "+(int)Math.hypot(dx,dz)+" metri. La trovi a X "+to.getX()+", Z "+to.getZ()+".";
    }
    static ActionButton inputButton(UUID id){
        var template=ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,new JsonPrimitive("/parla "+id+" dice $(messaggio)")).getOrThrow();
        return new ActionButton(new CommonButtonData(Component.literal("Invia il messaggio"),250),Optional.of(new CommandTemplate(template)));
    }
    static void show(ServerPlayer p,NapoliCivicLife.Citizen c,String reply){show(p,c,Component.literal(reply));}
    static void show(ServerPlayer p,NapoliCivicLife.Citizen c,Component reply){
        var buttons=new ArrayList<ActionButton>();buttons.add(inputButton(c.id()));
        for(String[] entry:List.of(new String[]{"Come stai?","stai"},new String[]{"Che programmi hai?","piani"},new String[]{"Parlami della tua famiglia","famiglia"},new String[]{"Posso darti una mano?","aiuto"}))buttons.add(CitySocial.button(entry[0],"/parla "+c.id()+" "+entry[1]));
        var old=memory(p.level(),c.id(),p.getUUID());if(old!=null)buttons.add(CitySocial.button("Raccontami di più","/parla "+c.id()+" raccontami di più"));
        p.level().getAttachedOrElse(CityDialogue.SIGHTINGS,Map.of()).entrySet().stream().filter(e->e.getKey().startsWith(c.id()+"/")&&p.level().getGameTime()-e.getValue().at()<=48000)
                .sorted(Comparator.comparingLong((Map.Entry<String,CityDialogue.Sighting> e)->e.getValue().at()).reversed()).limit(3).forEach(e->{
                    var target=NapoliCivicLife.profile(p.level(),UUID.fromString(e.getKey().substring(37)));if(target!=null)buttons.add(CitySocial.button("Hai visto "+target.name()+"?","/parla "+c.id()+" visto "+target.id()));});
        buttons.add(CitySocial.button("Servizi e favori","/parla "+c.id()+" vicino"));buttons.add(CitySocial.button("A presto","/parla "+c.id()+" arrivederci"));
        var input=new Input("messaggio",new TextInput(270,Component.literal("Scrivi una domanda (max 160 caratteri)"),true,"",160,Optional.empty()));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(c.name()),Optional.empty(),true,false,DialogAction.CLOSE,
                List.of(new PlainMessage(reply,290)),List.of(input)),buttons,Optional.empty(),2)));
    }
    static int talk(ServerPlayer p,UUID id,String message){
        if(message==null||message.isBlank()||message.length()>160||message.codePoints().anyMatch(Character::isISOControl))return 0;
        var c=CitySocial.nearby(p,id);if(c==null||!(p.level().getEntity(id) instanceof NapoliResident npc)||!npc.hasLineOfSight(p))return 0;
        if(npc.isSleeping()){NpcSpeech.say(p,npc,Component.literal("Sto dormendo. Ne parliamo domattina?"));return 0;}
        if(!attend(p,npc,c))return 0;
        CitySocial.meet(p.level(),id,p.getUUID(),NapoliBroadcast.day(p.level()));CityOnboarding.event(p,CityOnboarding.TALK);
        boolean follow=followUp(message);var old=memory(p.level(),id,p.getUUID());
        var topic=follow&&old!=null&&p.level().getGameTime()-old.at()<=48000?previous(old):intent(message);
        // Preserve button and command compatibility with earlier saves and dialog links.
        topic=switch(message){case "stai"->Topic.WELLBEING;case "piani"->Topic.PLANS;case "chiuso"->Topic.WORK;default->topic;};
        String text=normalize(message);
        String response=text.startsWith("hai visto ")?CityDialogue.whereNamed(p.level(),id,text.substring(10)):reply(p,c,topic,message,follow&&topic!=Topic.UNKNOWN);
        remember(p.level(),id,p.getUUID(),topic);
        var spoken=Component.literal(response);
        if(topic==Topic.WORK&&c.profession()!=null)spoken.append(" Il mio mestiere: ").append(Component.translatable(c.profession().key())).append(".");
        NpcSpeech.reply(p,npc,spoken);
        if(topic==Topic.GOODBYE){SESSIONS.get(p.level()).remove(id);p.connection.send(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE);}else show(p,c,spoken);
        return 1;
    }
    private CityConversation(){}
}
