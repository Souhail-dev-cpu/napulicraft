package dev.napuli;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.level.ServerLevel;

/** Streams the entire generated address/junction catalogue on an isolated dedicated server. */
public final class CityAuditRun implements ModInitializer {
    private ServerLevel city;private int ticks;private boolean finished;
    public void onInitialize(){if(!Boolean.getBoolean("napuli.accessaudit"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(s->{city=s.getLevel(NapoliMap.LEVEL);NapoliMap.ensureBuilt(city);CityAccessibility.start(city,true);Napuli.LOGGER.info("[CITYAUDIT] Started {} real approaches and junctions",CityAccessibility.remaining(city));});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(city==null||finished)return;if(++ticks>36000){finished=true;Napuli.LOGGER.error("[CITYAUDIT] FAILED: deadline exceeded");s.halt(false);return;}
            if(!CityAccessibility.running(city)){finished=true;var results=CityAccessibility.results(city);long blocked=results.stream().filter(r->r.state().equals("BLOCCATO")).count();
                for(var result:results)if(result.state().equals("BLOCCATO"))Napuli.LOGGER.error("[CITYAUDIT] Blocked {} at {}",result.id(),result.position());
                if(blocked==0)Napuli.LOGGER.info("[CITYAUDIT] ALL PASSED: {} physical approaches, ticks={}",results.size(),ticks);
                else Napuli.LOGGER.error("[CITYAUDIT] FAILED: {} of {} approaches blocked",blocked,results.size());s.halt(false);
            }
        });
    }
}
