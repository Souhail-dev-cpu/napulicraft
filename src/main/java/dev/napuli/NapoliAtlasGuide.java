package dev.napuli;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;

/** A server-native atlas and saved walking directions; browsing it does not load distant districts. */
final class NapoliAtlasGuide {
    static final AttachmentType<String> TARGET=AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath().buildAndRegister(Napuli.id("atlas_destination"));
    static int menu(ServerPlayer p,int page){
        int pages=(NapoliAtlas.PLACES.size()+9)/10;page=Math.clamp(page,0,pages-1);var buttons=new ArrayList<ActionButton>();
        for(var place:NapoliAtlas.PLACES.subList(page*10,Math.min(page*10+10,NapoliAtlas.PLACES.size())))buttons.add(CitySocial.button(place.name(),"/napoli meta "+place.id()));
        if(page>0)buttons.add(CitySocial.button("← Precedenti","/napoli luoghi "+(page-1)));if(page+1<pages)buttons.add(CitySocial.button("Altri luoghi →","/napoli luoghi "+(page+1)));
        if(!p.getAttachedOrElse(TARGET,"").isEmpty())buttons.add(CitySocial.button("Interrompi indicazioni","/napoli annulla"));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Napoli · luoghi "+(page+1)+"/"+pages),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(Component.literal("Quartieri, piazze e monumenti reali in una mappa Minecraft con scala adattata. Nord in alto; mare e colline seguono la città. Scegli una meta per le indicazioni a piedi."),300)),List.of()),buttons,Optional.empty(),1)));return 1;
    }
    static int target(ServerPlayer p,String id){var place=NapoliAtlas.find(id);if(place==null||p.level().dimension()!=NapoliMap.LEVEL)return 0;p.setAttached(TARGET,id);p.closeContainer();p.sendSystemMessage(Component.literal("Meta: "+place.name()+" · "+place.district()+". Le indicazioni restano visibili durante il cammino. /napoli annulla per fermarle."));return 1;}
    static String direction(double dx,double dz){if(Math.abs(dx)>Math.abs(dz)*2)return dx>0?"est →":"ovest ←";if(Math.abs(dz)>Math.abs(dx)*2)return dz>0?"sud ↓":"nord ↑";return (dz>0?"sud":"nord")+(dx>0?"-est":"-ovest");}
    static void fixtures(ServerLevel city){var p=new net.minecraft.core.BlockPos(-337,64,-253);if(!CityProperty.playerPlaced(city,p)&&city.getBlockState(p).isAir()){city.setBlock(p.below(),Blocks.STONE_BRICKS.defaultBlockState(),2);NapoliEmploymentMap.sign(city,p,"NAPOLI · ATLANTE","Nuovi quartieri","/napoli luoghi","10 × area della città");}}
    static void register(){
        CommandRegistrationCallback.EVENT.register((d,ctx,sel)->d.register(Commands.literal("napoli")
            .then(Commands.literal("luoghi").executes(c->menu(c.getSource().getPlayerOrException(),0)).then(Commands.argument("pagina",IntegerArgumentType.integer(0)).executes(c->menu(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"pagina")))))
            .then(Commands.literal("mappa").executes(c->menu(c.getSource().getPlayerOrException(),0)))
            .then(Commands.literal("meta").then(Commands.argument("luogo",StringArgumentType.word()).suggests((c,b)->{for(var p:NapoliAtlas.PLACES)b.suggest(p.id());return b.buildFuture();}).executes(c->target(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"luogo")))))
            .then(Commands.literal("annulla").executes(c->{var p=c.getSource().getPlayerOrException();p.removeAttached(TARGET);p.sendOverlayMessage(Component.literal("Indicazioni interrotte."));return 1;}))));
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%40!=0)return;for(var p:server.getPlayerList().getPlayers()){
            if(p.level().dimension()!=NapoliMap.LEVEL)continue;var place=NapoliAtlas.find(p.getAttachedOrElse(TARGET,""));if(place==null)continue;var goal=place.arrival();double dx=goal.getX()-p.getX(),dz=goal.getZ()-p.getZ();int distance=(int)Math.hypot(dx,dz);
            if(distance<12){p.removeAttached(TARGET);p.sendSystemMessage(Component.literal("Sei arrivato: "+place.name()+" · "+place.district()));}else p.sendOverlayMessage(Component.literal(place.name()+" · "+distance+" m · "+direction(dx,dz)));
        }});
    }
    private NapoliAtlasGuide(){}
}
