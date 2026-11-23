package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.server.level.ServerLevel;

/** After installing this version, changing time scale preserves every citizen's age. */
final class CityCalendar {
    static final AttachmentType<Integer> RATIO=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).buildAndRegister(Napuli.id("calendar_days_per_year"));
    static void sync(ServerLevel city){
        int current=CityLifeConfig.settings.daysPerYear,previous=city.getAttachedOrElse(RATIO,current);
        var census=NapoliCivicLife.census(city);
        if(census!=null&&previous!=current){
            long day=NapoliBroadcast.day(city);var people=new ArrayList<NapoliCivicLife.Citizen>();
            for(var c:census.people()){
                long elapsed=Math.max(0,day-c.birth());
                long youth=c.childhoodDays()>0?(long)c.childhoodDays()*NapoliCivicLife.ADULT_AGE:0;
                // A child's own growth rate is fixed at birth; only the adult years use the global rate.
                long birth=c.birth();
                if(elapsed>=youth){
                    long adult=elapsed-youth,years=adult/Math.max(1,previous),remainder=adult%Math.max(1,previous);
                    birth=day-youth-years*current-remainder*current/Math.max(1,previous);
                }
                people.add(new NapoliCivicLife.Citizen(c.id(),c.name(),c.woman(),birth,c.balance(),c.job(),c.partner(),c.property(),c.parents(),c.spawned(),c.childhoodDays()));
            }city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people)));
        }city.setAttached(RATIO,current);
    }
    static void register(){}
    private CityCalendar(){}
}
