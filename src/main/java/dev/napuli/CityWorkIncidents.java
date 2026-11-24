package dev.napuli;

import com.mojang.serialization.*;
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
import net.minecraft.world.item.Items;

/** Maintenance has a saved cause, location, cost and completion; repairing actually restores service. */
final class CityWorkIncidents {
    record Incident(String id,String line,BlockPos position,long day,boolean solved){
        static final Codec<Incident> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(Incident::id),Codec.STRING.fieldOf("line").forGetter(Incident::line),BlockPos.CODEC.fieldOf("position").forGetter(Incident::position),Codec.LONG.fieldOf("day").forGetter(Incident::day),Codec.BOOL.fieldOf("solved").forGetter(Incident::solved)).apply(i,Incident::new));
    }
    static final AttachmentType<Map<String,Incident>> ISSUES=AttachmentRegistry.<Map<String,Incident>>builder().persistent(Codec.unboundedMap(Codec.STRING,Incident.CODEC)).buildAndRegister(Napuli.id("urban_maintenance_incidents"));
    static boolean stopped(ServerLevel city,String line){return city.getAttachedOrElse(ISSUES,Map.of()).values().stream().anyMatch(i->!i.solved()&&i.line().equals(line));}
    static Incident near(ServerLevel city,BlockPos around){return city.getAttachedOrElse(ISSUES,Map.of()).values().stream().filter(i->!i.solved()).min(Comparator.comparingDouble(i->i.position().distSqr(around))).orElse(null);}
    static Incident get(ServerLevel city,String id){var i=city.getAttachedOrElse(ISSUES,Map.of()).get(id);return i==null||i.solved()?null:i;}
    static boolean report(ServerLevel city,String line,BlockPos at){
        if(CityNetwork.line(line)==null||stopped(city,line))return false;long day=NapoliBroadcast.day(city);String id=line+"_"+day;
        var all=new HashMap<>(city.getAttachedOrElse(ISSUES,Map.of()));if(all.containsKey(id)||all.values().stream().filter(i->!i.solved()).count()>=3)return false;
        all.put(id,new Incident(id,line,at.immutable(),day,false));all.values().removeIf(i->i.solved()&&day-i.day()>14);city.setAttached(ISSUES,Map.copyOf(all));return true;
    }
    static boolean resolve(ServerLevel city,String id){var issue=get(city,id);if(issue==null)return false;var all=new HashMap<>(city.getAttachedOrElse(ISSUES,Map.of()));all.put(id,new Incident(id,issue.line(),issue.position(),issue.day(),true));city.setAttached(ISSUES,Map.copyOf(all));
        city.players().stream().filter(p->p.blockPosition().distSqr(issue.position())<128*128).forEach(p->p.sendSystemMessage(Component.literal("La linea "+issue.line()+" è tornata in servizio.")));return true;
    }
    static int repair(ServerPlayer player,String id){var city=player.level();var issue=get(city,id);if(city.dimension()!=NapoliMap.LEVEL||!player.isAlive()||issue==null||player.isSpectator()||Carabinieri.isJailed(player)||issue.position().distToCenterSqr(player.position())>64)return 0;
        if(CooperativeJobs.reserved(city,id))return CityCompanion.fail(player,CityText.t("coop.reserved"));
        if(WorkEconomy.fund(city)<20){player.sendSystemMessage(Component.literal("La riparazione attende i fondi per il compenso."));return 0;}
        if(!CityEconomy.take(player,Items.IRON_INGOT,2)){player.sendSystemMessage(Component.literal("Servono due lingotti di ferro per il ricambio."));return 0;}
        if(resolve(city,id)){WorkEconomy.wage(player,20);player.sendSystemMessage(Component.literal("Riparazione completata: 20 €."));return 1;}return 0;
    }
    static int menu(ServerPlayer p){var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();var buttons=new ArrayList<ActionButton>();
        for(var issue:p.level().getAttachedOrElse(ISSUES,Map.of()).values())if(!issue.solved()){
            var at=issue.position();body.add(new PlainMessage(Component.literal("Linea "+issue.line()+" ferma per manutenzione · "+at.getX()+", "+at.getY()+", "+at.getZ()+". Ricambi: 2 ferro; compenso: 20 €."),280));
            buttons.add(CitySocial.button("Ripara "+issue.line()+" (sul posto)","/problemi ripara "+issue.id()));
        }
        if(body.isEmpty())body.add(new PlainMessage(Component.literal("Nessun guasto segnalato ai trasporti. Per le carenze di cibo, consulta le botteghe."),280));
        buttons.add(CitySocial.button("Chiudi","/problemi chiudi"));p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Problemi del quartiere"),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),buttons,Optional.empty(),1)));return 1;
    }
    static void register(){
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("problemi").executes(c->menu(c.getSource().getPlayerOrException()))
                .then(Commands.literal("ripara").then(Commands.argument("id",StringArgumentType.word()).executes(c->repair(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id")))))
                .then(Commands.literal("chiudi").executes(c->1))));
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;long day=NapoliBroadcast.day(city);if(day<2)return;
            for(var entity:city.getAllEntities())if(entity instanceof CityVehicle v&&!v.networkKey().isEmpty()&&v.atStop()&&v.getPassengers().isEmpty()){
                String line=v.networkKey().split("/")[0];if(Math.floorMod(day+line.hashCode(),7)==0)report(city,line,v.blockPosition().east(4));
            }
        });
    }
    private CityWorkIncidents(){}
}
