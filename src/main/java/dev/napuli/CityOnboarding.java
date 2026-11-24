package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import static dev.napuli.CityText.t;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

/** A resumable introduction made of normal interactions; no parallel tutorial economy or fake citizens. */
final class CityOnboarding {
    static final int TALK=1,JOB=2,TRANSIT=4,MEAL=8;
    static final AttachmentType<Integer> STEPS=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).copyOnDeath().buildAndRegister(Napuli.id("welcome_steps"));
    static final AttachmentType<Boolean> ACTIVE=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id("welcome_active"));
    static final AttachmentType<Boolean> SHOWN=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id("welcome_shown"));
    static final AttachmentType<Boolean> PAID=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id("welcome_paid"));
    static void event(ServerPlayer player,int step){if(player.level().dimension()==NapoliMap.LEVEL)record(player,step);}
    static void record(ServerPlayer player,int step){int previous=player.getAttachedOrElse(STEPS,0);if((previous&step)!=0)return;int next=previous|step;player.setAttached(STEPS,next);
        if(player.getAttachedOrElse(ACTIVE,false))player.sendSystemMessage(t("intro.progress",Integer.bitCount(next)));
        if(next==15){player.setAttached(ACTIVE,false);player.sendSystemMessage(t("intro.complete"));}
    }
    static NapoliFactions.Objective objective(ServerPlayer p){if(p.level().dimension()!=NapoliMap.LEVEL||!p.getAttachedOrElse(ACTIVE,false))return null;int steps=p.getAttachedOrElse(STEPS,0);
        if((steps&TALK)==0){var census=NapoliCivicLife.census(p.level());if(census==null)return null;var near=census.people().stream().filter(c->p.level().getEntity(c.id())!=null).min(Comparator.comparingDouble(c->p.level().getEntity(c.id()).distanceToSqr(p))).orElse(null);return new NapoliFactions.Objective("napuli.guide.talk",near==null?NapoliMap.CAFE:p.level().getEntity(near.id()).blockPosition());}
        if((steps&JOB)==0)return new NapoliFactions.Objective("napuli.guide.job",NapoliEmployment.CLERK_POST);
        if((steps&TRANSIT)==0){var stop=CityNetwork.stops().stream().filter(s->CityNetwork.lines().stream().anyMatch(l->l.stops().contains(s.id()))).min(Comparator.comparingDouble(s->s.entrance().distToCenterSqr(p.position()))).orElseThrow();return new NapoliFactions.Objective("napuli.guide.transit",stop.entrance());}
        if((steps&MEAL)==0){var d=DistrictLife.districts().stream().min(Comparator.comparingDouble(q->q.business().entrance().distToCenterSqr(p.position()))).orElseThrow();return new NapoliFactions.Objective("napuli.guide.meal",d.business().entrance());}
        return null;
    }
    static int menu(ServerPlayer p){int steps=p.getAttachedOrElse(STEPS,0);var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();
        for(int i=0;i<4;i++)body.add(new PlainMessage(t("intro.step",(steps&(1<<i))!=0?"✓":"○",t("intro.task."+i)),290));
        body.add(new PlainMessage(t("intro.help"),290));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(t("intro.title"),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),List.of(CitySocial.button(t("intro.follow"),"/inizio segui"),CitySocial.button(t("intro.pause"),"/inizio pausa"),CitySocial.button(t("close"),"/inizio chiudi")),Optional.empty(),1)));return 1;
    }
    static void register(){CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("inizio").executes(c->menu(c.getSource().getPlayerOrException()))
            .then(Commands.literal("segui").executes(c->{var p=c.getSource().getPlayerOrException();p.setAttached(ACTIVE,true);p.setAttached(CityCompanion.TRACK,"guida");return 1;}))
            .then(Commands.literal("pausa").executes(c->{var p=c.getSource().getPlayerOrException();p.setAttached(ACTIVE,false);if(p.getAttachedOrElse(CityCompanion.TRACK,"auto").equals("guida"))p.setAttached(CityCompanion.TRACK,"auto");return 1;}))
            .then(Commands.literal("chiudi").executes(c->1))));
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%40!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;
            for(var p:city.players())if(!p.getAttachedOrElse(SHOWN,false)){p.setAttached(SHOWN,true);p.sendSystemMessage(t("intro.welcome"));}
        });
    }
    private CityOnboarding(){}
}
