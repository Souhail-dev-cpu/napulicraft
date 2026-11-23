package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Items;

/** Personal requests have a named beneficiary, a persistent outcome and one settlement. */
final class CityPersonalGoals {
    record Goal(String kind,String home,long opened,String helper,boolean complete,int contributions,long promised) {
        Goal(String kind,String home,long opened,String helper,boolean complete,int contributions){this(kind,home,opened,helper,complete,contributions,-1);}
        static final Codec<Goal> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Goal::kind),
                Codec.STRING.fieldOf("home").forGetter(Goal::home),Codec.LONG.fieldOf("opened").forGetter(Goal::opened),
                Codec.STRING.fieldOf("helper").forGetter(Goal::helper),Codec.BOOL.fieldOf("complete").forGetter(Goal::complete),
                Codec.INT.fieldOf("contributions").forGetter(Goal::contributions),Codec.LONG.optionalFieldOf("promised",-1L).forGetter(Goal::promised)).apply(i,Goal::new));
    }
    static final AttachmentType<Map<String,Goal>> GOALS=AttachmentRegistry.<Map<String,Goal>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Goal.CODEC)).buildAndRegister(Napuli.id("personal_goals"));
    static Goal goal(ServerLevel city,UUID id){return city.getAttachedOrElse(GOALS,Map.of()).get(id.toString());}
    static void put(ServerLevel city,UUID id,Goal goal){var all=new HashMap<>(city.getAttachedOrElse(GOALS,Map.of()));all.put(id.toString(),goal);city.setAttached(GOALS,Map.copyOf(all));}
    static boolean ovenBroken(ServerLevel city){return city.getAttachedOrElse(GOALS,Map.of()).values().stream().anyMatch(g->g.kind().equals("oven")&&!g.complete());}
    static String description(Goal g){
        if(g==null)return "";if(g.complete())return "Obiettivo raggiunto: "+switch(g.kind()){case "oven"->"il forno è ripartito.";case "work"->"ha trovato un impiego.";default->"la famiglia si è trasferita.";};
        return switch(g.kind()){case "oven"->"Il forno è guasto. Mi servono 4 lingotti di ferro per ripararlo.";case "work"->"Cerco lavoro. Accompagnami al municipio e presentami un'offerta.";default->"Risparmio per cambiare casa. Puoi contribuire con 5 € alla famiglia.";};
    }
    static void assign(ServerLevel city,NapoliCivicLife.Citizen c,String kind){put(city,c.id(),new Goal(kind,c.property(),NapoliBroadcast.day(city),"",false,0));}
    static void tick(ServerLevel city){
        var census=NapoliCivicLife.census(city);if(census==null)return;long day=NapoliBroadcast.day(city);
        boolean broken=ovenBroken(city);
        for(var c:census.people()){
            var g=goal(city,c.id());
            if(g==null){
                int age=c.age(day);if(age<18||age>=CityLifeConfig.settings.retirementAge)continue;
                if(c.profession()==null)assign(city,c,"work");
                else if(!broken&&day%10==3&&c.profession().category==CityProfessions.Category.FOOD){assign(city,c,"oven");broken=true;}
                else if(c.property().startsWith("public_")&&!c.partner().isEmpty()&&Math.floorMod(c.id().hashCode(),4)==0)assign(city,c,"home");
            }else if(!g.complete()){
                if(!g.kind().equals("home")&&!g.helper().isEmpty()&&g.promised()>=0&&day-g.promised()>=3){
                    CityReputation.record(city,UUID.fromString(g.helper()),c.id(),"promise","un vicino ha promesso di aiutarmi, ma non si è più fatto vedere.",-6);
                    put(city,c.id(),new Goal(g.kind(),g.home(),g.opened(),"",false,g.contributions(),-1));continue;
                }
                if(g.kind().equals("home")&&!c.property().isEmpty()&&!c.property().startsWith("public_")&&!c.property().equals(g.home()))finish(city,c,g,false);
                else if(g.kind().equals("work")&&c.profession()!=null)finish(city,c,g,false);
                else if(g.kind().equals("oven")&&day-g.opened()>=3&&c.balance()>=24){
                    money(city,c,-24,c.job());finish(city,c,g,false);
                }
            }
        }
    }
    private static void money(ServerLevel city,NapoliCivicLife.Citizen c,int change,int job){
        var census=NapoliCivicLife.census(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(p->p.id().equals(c.id())?p.withMoney(Math.clamp(p.balance()+change,0,99999),job):p).toList()));
    }
    private static void finish(ServerLevel city,NapoliCivicLife.Citizen c,Goal g,boolean pay){
        if(g.complete())return;put(city,c.id(),new Goal(g.kind(),g.home(),g.opened(),g.helper(),true,g.contributions(),g.promised()));
        var l=CitySimulation.life(city,c.id());CitySimulation.put(city,c.id(),l.career(l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),CitySimulation.memory(l,description(goal(city,c.id())))));
        if(!g.helper().isEmpty()){
            var helper=city.getServer().getPlayerList().getPlayer(UUID.fromString(g.helper()));
            if(pay||g.contributions()>0)CitySocial.helped(city,c.id(),UUID.fromString(g.helper()),NapoliBroadcast.day(city));
            if(helper!=null&&pay){int reward=g.kind().equals("oven")?16:8;NapoliStreet.credit(helper,reward);helper.sendSystemMessage(Component.literal(c.name()+": Grazie! Obiettivo completato, +"+reward+" €."));}
        }
    }
    static int act(ServerPlayer p,UUID id,String action){
        var c=CitySocial.nearby(p,id);if(c==null)return 0;var g=goal(p.level(),id);if(g==null||g.complete())return 0;
        if(!g.helper().isEmpty()&&!g.helper().equals(p.getUUID().toString())){p.sendOverlayMessage(Component.literal("Un altro vicino sta già aiutando questa persona."));return 0;}
        var accepted=new Goal(g.kind(),g.home(),g.opened(),p.getUUID().toString(),false,g.contributions(),g.promised()<0?NapoliBroadcast.day(p.level()):g.promised());
        if(action.equals("accetta")){put(p.level(),id,accepted);p.sendSystemMessage(Component.literal(c.name()+": "+description(g)+(g.kind().equals("home")?"":" Ti aspetto entro tre giorni.")));return 1;}
        if(g.kind().equals("home")&&action.equals("contribuisci")){
            if(NapoliStreet.cash(p)<5)return 0;NapoliStreet.credit(p,-5);money(p.level(),c,5,c.job());
            put(p.level(),id,new Goal(g.kind(),g.home(),g.opened(),p.getUUID().toString(),false,g.contributions()+5,g.promised()));return 1;
        }
        if(!action.equals("completa")||!g.helper().equals(p.getUUID().toString()))return 0;
        if(g.kind().equals("oven")){
            if(!CityEconomy.take(p,Items.IRON_INGOT,4))return 0;finish(p.level(),c,g,true);return 1;
        }
        if(g.kind().equals("work")&&p.blockPosition().distSqr(workplace())<=36&&p.level().getEntity(id).blockPosition().distSqr(workplace())<=36&&CityServices.municipalityReady(p.level(),c)){
            var census=NapoliCivicLife.census(p.level());for(var job:NapoliCivicLife.Job.values())if(CityProfessions.eligible(job,c,CitySimulation.life(p.level(),id),NapoliBroadcast.day(p.level()))&&census.people().stream().filter(n->n.job()==job.ordinal()).count()<3){money(p.level(),c,0,job.ordinal());CityServices.completeMunicipality(p.level(),c);finish(p.level(),c,g,true);return 1;}
        }return 0;
    }
    static BlockPos workplace(){return CityProfessions.Category.PUBLIC.site().offset(8,0,7);}
    static boolean move(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c){
        var g=goal(city,c.id());if(g==null||g.complete()||!g.kind().equals("work")||g.helper().isEmpty()||CityPublicOrder.jailed(city,c.id()))return false;
        var helper=city.getServer().getPlayerList().getPlayer(UUID.fromString(g.helper()));if(helper==null||helper.level()!=city)return false;
        var needs=CitySimulation.life(city,c.id());if(needs.health()<45||needs.needs().sleep()<15||needs.needs().hunger()<15)return false;
        CityMovement.stand(npc);if(npc.isSleeping())npc.stopSleeping();npc.setActivity(NapoliHouseholds.Activity.COMMUTE);CityDomestic.move(npc,helper.distanceToSqr(npc)>64?helper.blockPosition():CityRoutines.localGoal(city,npc,workplace()));return true;
    }
    static void addButtons(List<ActionButton> buttons,NapoliCivicLife.Citizen c,Goal g){
        if(g==null||g.complete())return;buttons.add(CitySocial.button("Aiuta con l'obiettivo","/abitanti accetta "+c.id()));
        buttons.add(CitySocial.button(g.kind().equals("home")?"Contribuisci · 5 €":"Completa l'aiuto", "/abitanti "+(g.kind().equals("home")?"contribuisci ":"completa ")+c.id()));
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%600==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)tick(city);}});
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var root=Commands.literal("abitanti").executes(c->{var p=c.getSource().getPlayerOrException();var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();
                p.level().getAttachedOrElse(GOALS,Map.of()).forEach((id,g)->{if(g.helper().equals(p.getUUID().toString())){var npc=NapoliCivicLife.profile(p.level(),UUID.fromString(id));if(npc!=null)body.add(new PlainMessage(Component.literal(npc.name()+": "+description(g)),270));}});
                if(body.isEmpty())body.add(new PlainMessage(Component.literal("Parla con i cittadini per conoscere i loro obiettivi."),270));
                p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Persone che stai aiutando"),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),List.of(CitySocial.button("Chiudi","/abitanti chiudi")),Optional.empty(),1)));return 1;});
            for(var action:List.of("accetta","completa","contribuisci"))root.then(Commands.literal(action).then(Commands.argument("persona",StringArgumentType.word()).executes(c->{try{return act(c.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(c,"persona")),action);}catch(IllegalArgumentException invalid){return 0;}})));
            root.then(Commands.literal("chiudi").executes(c->1));d.register(root);
        });
    }
    private CityPersonalGoals(){}
}
