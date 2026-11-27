package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;

/** Radio dispatch shares observed locations and leaves surplus officers on patrol. */
final class PoliceDispatch {
    record Officer(UUID id,BlockPos position){}
    record Alert(UUID id,BlockPos position,int gravity){}
    record Assignment(UUID suspect,int role){}
    private record Frame(long time,Map<UUID,Assignment> orders,List<NapoliResident> officers){}
    private static final AttachmentType<Frame> FRAME=AttachmentRegistry.create(Napuli.id("police_dispatch"));
    static int capacity(int gravity){return gravity>=3?5:3;}
    static Map<UUID,Assignment> assign(List<Officer> officers,List<Alert> alerts){
        var orders=new LinkedHashMap<UUID,Assignment>();
        var priority=alerts.stream().sorted(Comparator.comparingInt(Alert::gravity).reversed().thenComparing(Alert::id)).toList();
        for(int role=0;role<5;role++)for(var alert:priority){
            if(role>=capacity(alert.gravity()))continue;
            var officer=officers.stream().filter(o->!orders.containsKey(o.id())&&o.position().distSqr(alert.position())<=PoliceTactics.RANGE*PoliceTactics.RANGE)
                    .min(Comparator.comparingDouble((Officer o)->o.position().distSqr(alert.position())).thenComparing(Officer::id)).orElse(null);
            if(officer!=null)orders.put(officer.id(),new Assignment(alert.id(),role));
        }
        return Map.copyOf(orders);
    }
    private static Frame frame(ServerLevel city){
        var old=city.getAttached(FRAME);long now=city.getGameTime();if(old!=null&&now-old.time()<20)return old;
        var officers=new ArrayList<NapoliResident>();
        for(var entity:city.getAllEntities())if(entity instanceof NapoliResident npc&&eligible(npc))officers.add(npc);
        var alerts=city.players().stream().filter(p->Carabinieri.isWanted(p)&&!Carabinieri.isJailed(p)&&p.isAlive()&&!p.isSpectator())
                .map(p->new Alert(p.getUUID(),Carabinieri.lastKnown(p),Carabinieri.wantedStars(p))).toList();
        var orders=assign(officers.stream().map(n->new Officer(n.getUUID(),n.blockPosition())).toList(),alerts);
        var frame=new Frame(now,orders,List.copyOf(officers));city.setAttached(FRAME,frame);return frame;
    }
    static boolean eligible(NapoliResident npc){return npc.role()==NapoliResident.Role.CARABINIERE&&npc.isAlive()&&!npc.isNoAi()
            &&!npc.isPassenger()&&!Carabinieri.npcJailed(npc.getUUID())&&!CityPublicOrder.jailed((ServerLevel)npc.level(),npc.getUUID());}
    static Assignment order(NapoliResident npc){return frame((ServerLevel)npc.level()).orders().get(npc.getUUID());}
    static List<NapoliResident> team(ServerPlayer p){var frame=frame(p.level());return frame.officers().stream().filter(PoliceDispatch::eligible)
            .filter(n->{var a=frame.orders().get(n.getUUID());return a!=null&&a.suspect().equals(p.getUUID());})
            .sorted(Comparator.comparingInt(n->frame.orders().get(n.getUUID()).role())).toList();}
    static void invalidate(ServerLevel city){city.removeAttached(FRAME);}
    static void register(){}
    private PoliceDispatch(){}
}
