package dev.napuli;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A bounded set of street patrols around visited districts; never loads remote chunks. */
final class PolicePatrols {
    static final String TAG="napuli_district_patrol";
    static final int PER_ZONE=2, ACTIVE_RANGE=128;
    record Zone(String id,BlockPos center){}
    private record Route(Zone zone,int waypoint,long next,BlockPos target){}
    private static final AttachmentType<Route> ROUTE=AttachmentRegistry.create(Napuli.id("police_patrol_route"));
    private static final class Catalogue {static final List<Zone> ZONES=createZones();}
    static List<Zone> zones(){return Catalogue.ZONES;}
    private static List<Zone> createZones(){
        var all=new ArrayList<Zone>();
        all.add(new Zone("comando",Carabinieri.POST));
        all.add(new Zone("plebiscito",Carabinieri.PIAZZA));
        all.add(new Zone("artigiani",new BlockPos(-313,64,53)));
        all.add(new Zone("rione_partenope",new BlockPos(-347,64,150)));
        all.add(new Zone("centro_storico",new BlockPos(24,64,-93)));
        for(var place:NapoliAtlas.PLACES)addZone(all,new Zone("luogo/"+place.id(),place.arrival()));
        for(var district:DistrictLife.districts())addZone(all,new Zone("rione/"+district.id(),district.business().approach(3)));
        for(var stop:CityNetwork.stops().stream().sorted(Comparator.comparing(CityNetwork.Stop::id)).toList())
            addZone(all,new Zone("fermata/"+stop.id(),stop.entrance()));
        return List.copyOf(all);
    }
    private static void addZone(List<Zone> all,Zone zone){
        if(all.stream().noneMatch(z->horizontal(z.center(),zone.center())<72*72))all.add(zone);
    }
    static double horizontal(BlockPos a,BlockPos b){double x=a.getX()-b.getX(),z=a.getZ()-b.getZ();return x*x+z*z;}
    static UUID identity(Zone zone,int slot){return UUID.nameUUIDFromBytes(("napuli:patrol:"+zone.id()+":"+slot).getBytes(StandardCharsets.UTF_8));}
    static boolean managed(NapoliResident npc){return npc.entityTags().contains(TAG);}
    static List<Zone> nearby(List<BlockPos> players){
        return zones().stream().filter(z->players.stream().anyMatch(p->horizontal(p,z.center())<=ACTIVE_RANGE*ACTIVE_RANGE))
                .sorted(Comparator.comparingDouble(z->players.stream().mapToDouble(p->horizontal(p,z.center())).min().orElse(Double.MAX_VALUE))).toList();
    }
    static BlockPos street(ServerLevel city,BlockPos center,int radius){
        for(int r=0;r<=radius;r+=2)for(int x=-r;x<=r;x+=2)for(int z=-r;z<=r;z+=2){
            if(r>0&&Math.abs(x)!=r&&Math.abs(z)!=r)continue;
            for(int dy:new int[]{0,1,-1,2,-2,3,-3}){
                var at=center.offset(x,dy,z);
                if(!city.hasChunkAt(at)||!city.canSeeSky(at)||!NapoliResidentHomes.walkable(city,at)
                        ||CityProperty.home(city,at)||CityLivingHomes.all().stream().anyMatch(h->h.contains(at)))continue;
                if(!city.getEntitiesOfClass(NapoliResident.class,new net.minecraft.world.phys.AABB(at).inflate(.8),n->n.isAlive()).isEmpty())continue;
                return at;
            }
        }return null;
    }
    static void maintain(ServerLevel city){
        maintain(city,nearby(city.players().stream().filter(p->p.isAlive()&&!p.isSpectator()).map(p->p.blockPosition()).toList()));
    }
    static void maintain(ServerLevel city,List<Zone> activeZones){
        var players=city.players().stream().filter(p->p.isAlive()&&!p.isSpectator()).map(p->p.blockPosition()).toList();
        var officers=new ArrayList<NapoliResident>();
        for(var entity:city.getAllEntities())if(entity instanceof NapoliResident npc&&managed(npc))officers.add(npc);
        for(var npc:officers)if(!PoliceTactics.active(npc)&&players.stream().noneMatch(p->horizontal(p,npc.blockPosition())<192*192))npc.discard();
        int count=(int)officers.stream().filter(n->n.isAlive()&&!n.isRemoved()).count(),created=0;
        int limit=CityLifeConfig.settings.policePatrolLimit;
        for(var zone:activeZones)for(int slot=0;slot<PER_ZONE;slot++){
            if(count>=limit||created>=4)return;
            if(city.getEntity(identity(zone,slot))!=null||CityResidentSafety.absent(city,identity(zone,slot)))continue;
            var at=street(city,zone.center().offset(slot*3,0,0),18);if(at==null)continue;
            if(!city.areEntitiesActuallyLoadedAndTicking(net.minecraft.world.level.ChunkPos.containing(at)))continue;
            var npc=NapoliResident.create(city,NapoliResident.Role.CARABINIERE,at,0);npc.setUUID(identity(zone,slot));npc.addTag(TAG);
            npc.setAttached(ROUTE,new Route(zone,slot*2,0,null));npc.setHomeTo(at,48);
            if(city.addFreshEntity(npc)){count++;created++;}
        }
    }
    static boolean tick(NapoliResident npc,ServerLevel city){
        if(!managed(npc))return false;
        var route=npc.getAttached(ROUTE);if(route==null){npc.discard();return true;}
        if((npc.tickCount+npc.getId())%20!=0)return true;
        var zone=route.zone();
        if(city.getGameTime()<route.next())return true;
        var center=zone.center();
        if(horizontal(npc.blockPosition(),center)>40*40){CityDomestic.move(npc,CityRoutines.localGoal(city,npc,center));return true;}
        int[][] offsets={{-14,0},{0,14},{14,0},{0,-14}};
        var offset=offsets[Math.floorMod(route.waypoint(),offsets.length)];
        var target=route.target();
        if(target==null||!city.hasChunkAt(target)||!NapoliResidentHomes.walkable(city,target))target=street(city,center.offset(offset[0],0,offset[1]),6);
        if(target==null||target.distToCenterSqr(npc.position())<4){
            npc.setAttached(ROUTE,new Route(route.zone(),route.waypoint()+1,city.getGameTime()+20,null));return true;
        }
        npc.setAttached(ROUTE,new Route(route.zone(),route.waypoint(),0,target));
        CityDomestic.move(npc,CityRoutines.localGoal(city,npc,target));return true;
    }
    static void register(){}
    private PolicePatrols(){}
}
