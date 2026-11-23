package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Fictional, game-calendar events: no dependency on real fixtures or real-world results. */
final class CityEvents {
    enum Event { MARKET, CONCERT, FESTIVAL, CHARITY, STRIKE, PROTEST, FIRE_DRILL, FLOOD_ALERT, ROAD_WORKS, OPENING }
    private static final AttachmentType<Long> ANNOUNCED=AttachmentRegistry.<Long>builder().persistent(Codec.LONG).buildAndRegister(Napuli.id("city_event_day"));
    private static final AttachmentType<Integer> REPUTATION=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).buildAndRegister(Napuli.id("city_reputation"));
    static Event event(long day) { return Event.values()[Math.floorMod(day,Event.values().length)]; }
    static boolean active(ServerLevel city) { return NapoliBroadcast.time(city)>=8500&&NapoliBroadcast.time(city)<11500; }
    static boolean danger(ServerLevel city) { return active(city)&&(event(NapoliBroadcast.day(city))==Event.FIRE_DRILL||event(NapoliBroadcast.day(city))==Event.FLOOD_ALERT); }
    static boolean celebrating(ServerLevel city) { var match=NapoliBroadcast.match(NapoliBroadcast.day(city));return match!=null&&match.win()&&NapoliBroadcast.time(city)>=NapoliBroadcast.finalTime(NapoliBroadcast.day(city))&&NapoliBroadcast.time(city)<19000; }
    static int price(ServerLevel city,int base) { return Math.max(1,(base*(celebrating(city)?120:active(city)&&event(NapoliBroadcast.day(city))==Event.MARKET?85:100)+99)/100); }
    static String label(Event e) {return switch(e){case MARKET->"Mercato di quartiere";case CONCERT->"Concerto in piazza";case FESTIVAL->"Festa patronale";case CHARITY->"Raccolta di beneficenza";case STRIKE->"Sciopero dei trasporti";case PROTEST->"Assemblea cittadina";case FIRE_DRILL->"Esercitazione antincendio";case FLOOD_ALERT->"Allerta pioggia e allagamenti";case ROAD_WORKS->"Lavori stradali";case OPENING->"Inaugurazione negozi";};}
    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%200!=0)return;var city=server.getLevel(NapoliMap.LEVEL);
            if(city==null||city.players().isEmpty()||!active(city))return;
            long day=NapoliBroadcast.day(city);
            if(city.getAttachedOrElse(ANNOUNCED,Long.MIN_VALUE)!=day) {
                city.setAttached(ANNOUNCED,day);
                int rep=city.getAttachedOrElse(REPUTATION,50);
                city.setAttached(REPUTATION,Math.clamp(rep+(danger(city)?-2:1),0,100));
                for(var p:city.players())NapoliNotice.show(p, Component.literal("Notizie di Napoli: "+label(event(day))+". Le routine e i prezzi si adeguano all'evento."));
            }
        });
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("citta")
                .executes(c->{var p=c.getSource().getPlayerOrException();var data=NapoliCivicLife.census(p.level());
                    NapoliNotice.show(p, Component.literal("Abitanti censiti: "+(data==null?0:data.people().size())+" · Professioni: "+NapoliCivicLife.Job.values().length+" · Case: "+NapoliMergellina.PROPERTIES.size()));
                    NapoliNotice.show(p, Component.literal("Giorno "+NapoliBroadcast.day(p.level())+" · "+label(event(NapoliBroadcast.day(p.level())))+" · Reputazione città: "+p.level().getAttachedOrElse(REPUTATION,50)));return 1;})));
    }
    private CityEvents() {}
}
