package dev.napuli;

import java.util.*;
import static dev.napuli.CityText.t;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;

/** Departure estimates use the same delayed vehicle clock used to move and board vehicles. */
final class CompanionTravel {
    private static final AttachmentType<String> LAST_WARNING=AttachmentRegistry.create(Napuli.id("companion_travel_warning"));
    record Departure(String direction, long ticks, String vehicle) {}
    static String nextStop(CityNetwork.Line line, int frameIndex) {
        var frames = CityNetwork.plan(line).frames();
        for(int step=1;step<=frames.size();step++) {
            var next = frames.get((frameIndex+step)%frames.size());
            if(!next.stop().isEmpty() && !next.stop().equals(frames.get(frameIndex).stop())) return next.stop();
        }
        return "";
    }
    static List<Departure> departures(ServerLevel city, CityNetwork.Line line, String stop) {
        if(!CityNetwork.open(city,line)) return List.of();
        var plan = CityNetwork.plan(line); var byDirection = new LinkedHashMap<String,Departure>();
        for(int slot=0;slot<plan.fleet();slot++) {
            int phase = CityNetwork.position(city,line,slot).phase();
            for(int i=0;i<plan.frames().size();i++) {
                var frame = plan.frames().get(i); if(!frame.stop().equals(stop)) continue;
                long wait = phase>=frame.start() && phase<frame.end() ? 0 : Math.floorMod(frame.start()-phase,plan.cycle());
                String direction = nextStop(line,i); if(direction.isEmpty() || direction.equals(stop)) continue;
                var before = byDirection.get(direction);
                if(before==null || wait<before.ticks()) byDirection.put(direction,new Departure(direction,wait,CityNetwork.vehicleKey(line,slot)));
            }
        }
        return byDirection.values().stream().sorted(Comparator.comparingLong(Departure::ticks)).toList();
    }
    static Component unavailableText(ServerLevel city,CityNetwork.Line line){return t(CityWorkIncidents.stopped(city,line.id())?"travel.maintenance":CityEvents.active(city)&&CityEvents.event(NapoliBroadcast.day(city))==CityEvents.Event.STRIKE?"travel.strike":"travel.closed");}
    static String unavailable(ServerLevel city,CityNetwork.Line line){return unavailableText(city,line).getString();}
    static int nearest(ServerPlayer p){if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));return board(p,CityNetwork.nearest(p.blockPosition()).id());}
    static int board(ServerPlayer p,String id){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var stop=CityNetwork.stop(id);if(stop==null)return CityCompanion.fail(p,t("travel.unknown"));var entries=new ArrayList<CompanionBoard.Entry>();
        entries.add(new CompanionBoard.Entry(t("travel.reach"),t("travel.entrance",stop.entrance().toShortString()),"compagno segui fermata/"+id));
        entries.add(new CompanionBoard.Entry(t("travel.book"),t("travel.book_help"),"trasporti fermata "+id));
        for(var line:CityNetwork.lines())if(line.stops().contains(id)){
            if(line.mode()==CityNetwork.Mode.METRO)entries.add(new CompanionBoard.Entry(t("atlas.platform",line.id(),stop.name()),t("atlas.platform_help"),"compagno segui banchina/"+line.id()+"/"+id));
            var departures=departures(p.level(),line,id);if(departures.isEmpty())entries.add(new CompanionBoard.Entry(CityNetwork.lineLabel(line),unavailableText(p.level(),line),"problemi"));
            for(var departure:departures){var vehicle=CityNetwork.vehicle(p.level(),departure.vehicle());var seats=vehicle==null?t("travel.capacity",line.capacity()):t("travel.seats",Math.max(0,line.capacity()-vehicle.getPassengers().size()),line.capacity());
                entries.add(new CompanionBoard.Entry(t("travel.direction",line.id(),CityNetwork.stop(departure.direction()).name()),t("travel.departure",departure.ticks()==0?t("travel.here"):t("travel.eta",(departure.ticks()+19)/20),seats),"trasporti linea "+line.id()));
            }
        }return CityCompanion.show(p,"corse/"+id,t("travel.title",stop.name()),entries);
    }
    static Component journeyText(ServerPlayer p){
        var ticket=p.getAttached(CityNetwork.TICKET);if(ticket==null||ticket.legs().isEmpty())return Component.empty();var leg=ticket.legs().getFirst();var destination=CityNetwork.stop(leg.to());if(destination==null)return Component.empty();
        var changes=t("travel.changes",Math.max(0,ticket.legs().size()-1));
        if(ticket.state().equals("riding")){
            long left=Math.max(0,ticket.due()+Math.max(0,CityNetwork.delay(p.level(),ticket.vehicle())-ticket.delay())-p.level().getGameTime());var line=CityNetwork.line(leg.line());String next=destination.name();
            if(line!=null&&ticket.vehicle().contains("/")){int slot=Integer.parseInt(ticket.vehicle().split("/")[1]);int phase=CityNetwork.position(p.level(),line,slot).phase();long best=Long.MAX_VALUE;
                for(var frame:CityNetwork.plan(line).frames())if(!frame.stop().isEmpty()&&!(phase>=frame.start()&&phase<frame.end())){long wait=Math.floorMod(frame.start()-phase,CityNetwork.plan(line).cycle());if(wait<best){best=wait;next=CityNetwork.stop(frame.stop()).name();}}}
            return t("travel.riding",leg.line(),next,destination.name(),(left+19)/20,changes);
        }return t("travel.waiting",leg.line(),destination.name(),t(ticket.state().equals("waiting")?"travel.wait":ticket.state().equals("lift")?"travel.platform":"travel.transfer"),changes);
    }
    static String journey(ServerPlayer p){return journeyText(p).getString();}
    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%20!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;
            for(var p:city.players()) {
                var ticket=p.getAttached(CityNetwork.TICKET);
                if(ticket==null || ticket.legs().isEmpty()){p.removeAttached(LAST_WARNING);continue;}
                if(!ticket.state().equals("riding"))continue;
                var leg=ticket.legs().getFirst();
                long left=ticket.due()+Math.max(0,CityNetwork.delay(city,ticket.vehicle())-ticket.delay())-city.getGameTime();
                String key=ticket.vehicle()+"/"+leg.to()+"/"+ticket.created();
                if(left<=100 && !p.getAttachedOrElse(LAST_WARNING,"").equals(key)) {
                    p.setAttached(LAST_WARNING,key);
                    var destination=CityNetwork.stop(leg.to());
                    if(destination!=null)NapoliNotice.show(p,t("travel.alight",destination.name(),t(ticket.legs().size()>1?"travel.transfer":"travel.destination")));
                }
            }
        });
    }
    private CompanionTravel() {}
}
