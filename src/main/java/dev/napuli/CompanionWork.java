package dev.napuli;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import static dev.napuli.CityText.t;

final class CompanionWork {
    static boolean finished(ServerPlayer p){var work=p.getAttached(NapoliEmployment.WORK);return work!=null&&work.paid()>=NapoliBroadcast.day(p.level());}
    static Component summary(ServerPlayer p){
        var work=p.getAttached(NapoliEmployment.WORK);var c=WorkCareer.selected(p);if(work==null||c==null)return t("work.choose");
        if(finished(p))return t("work.finished");
        var card=NapoliEmployment.timecard(p,work.day());int ticks=Math.max(0,card.ticks());
        var objective=NapoliEmployment.objective(p);var next=objective==null?t("work.choose"):Component.translatable(objective.key());
        if(work.stage()==1&&work.day()==NapoliBroadcast.day(p.level())&&c.supplies!=null)
            next=next.copy().append(" · ").append(t("recipe.count",c.supplies.getDefaultInstance().getHoverName(),Math.min(1,WorkResources.count(p,c.supplies)),1));
        return t("work.summary",next,ticks/1000,(ticks%1000)*60/1000,ticks/1000*WorkCareer.hourly(p,c));
    }
    static int menu(ServerPlayer p){
        var entries=new ArrayList<CompanionBoard.Entry>();var c=WorkCareer.selected(p);
        entries.add(new CompanionBoard.Entry(t("work.shift"),summary(p),"compagno obiettivo lavoro"));
        entries.add(new CompanionBoard.Entry(t("work.offers"),t("work.choose"),"lavoro"));
        if(c!=null){var a=WorkCareer.get(p,c);
            entries.add(new CompanionBoard.Entry(t("work.career",c.nameLabel(),t("work.rank."+a.rank())),t("work.career_stats",a.xp(),a.shifts(),a.earned(),WorkCareer.hourly(p,c),a.tier()+1),""));
            if(a.rank()<3){int next=a.rank()+1;entries.add(new CompanionBoard.Entry(t("work.next",t("work.rank."+next)),t("work.requirements",a.xp(),WorkCareer.threshold(next),a.shifts(),next*2,next*20),"lavoro rango"));}
            entries.add(new CompanionBoard.Entry(t("work.station"),t("work.station_help"),"compagno banco_carriera"));
            entries.add(new CompanionBoard.Entry(t("work.upgrade"),a.tier()<2?t("work.upgrade_cost",WorkCareer.toolCost(a),WorkCareer.material(a).getDefaultInstance().getHoverName(),t("work.rank."+(a.tier()+1))):t("work.max"),"lavoro potenzia"));
            entries.add(new CompanionBoard.Entry(t("work.repair"),t("work.repair_cost",3+a.tier()*3),"lavoro ripara"));
            entries.add(new CompanionBoard.Entry(t("work.replace"),t("work.replace_cost",6+a.tier()*8),"lavoro attrezzi"));
        }
        return CityCompanion.show(p,"carriera",t("work.title"),entries);
    }
    static int station(ServerPlayer p){var c=WorkCareer.selected(p);if(c==null||!CityCompanion.inCity(p))return CityCompanion.fail(p,t("work.choose"));CityCompanion.pin(p,new CityCompanion.Goal(c.nameLabel().getString(),WorkCareer.station(p.level(),c),c.nameLabel()));return 1;}
    private CompanionWork(){}
}
