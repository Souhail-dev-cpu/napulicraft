package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;

/** Timetables report the actual route vehicle and weather delay, rather than a fictitious fixed arrival. */
final class CityTransit {
    static final List<Integer> STOPS=List.of(-180,-120,-60,0,60,120,180);
    static BlockPos stop(double z,boolean south){return new BlockPos(south?-344:-334,64,Math.clamp((int)Math.round(z/60)*60,-180,180));}
    static long eta(ServerLevel city,BlockPos stop){
        if(CityEvents.active(city)&&CityEvents.event(NapoliBroadcast.day(city))==CityEvents.Event.STRIKE)return -1;
        double best=Double.MAX_VALUE;for(var entity:city.getAllEntities())if(entity instanceof CityVehicle bus&&bus.kind()==1)best=Math.min(best,bus.distanceToStop(stop)/(.11*(city.isRaining()?.8:1))+bus.remainingStop());
        return best==Double.MAX_VALUE?-1:(long)Math.ceil(best);
    }
    static int menu(ServerPlayer p){if(p.level().dimension()!=NapoliMap.LEVEL)return 0;var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();
        body.add(new PlainMessage(Component.literal("Linea Partenope · stime dai mezzi realmente in servizio. Pioggia, traffico e scioperi possono ritardare il passaggio."),270));
        for(boolean south:new boolean[]{true,false})for(int z:STOPS){var stop=stop(z,south);long eta=eta(p.level(),stop);body.add(new PlainMessage(Component.literal("Fermata "+z+" · direzione "+(south?"corti":"botteghe")+": "+(eta<0?"mezzo non disponibile; percorso a piedi":Math.max(1,(eta+19)/20)+" s stimati")),270));}
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Fermate e passaggi"),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),List.of(CitySocial.button("Servizi del quartiere","/servizi")),Optional.empty(),1)));return 1;
    }
    static void register(){CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("servizi").then(Commands.literal("trasporti").executes(c->menu(c.getSource().getPlayerOrException())))));}
    private CityTransit(){}
}
