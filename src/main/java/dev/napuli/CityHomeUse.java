package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import static dev.napuli.NapoliHouseholds.Activity;

/** Physical domestic actions. Progress requires the real fixture, arrival, and exclusive admission. */
final class CityHomeUse {
    enum Step { TOILET, HANDS, SHOWER }
    private static final class Session {
        final String home;final Activity action;
        Step step;CityHomeStations.Station station;BlockPos resource;
        int progress;long last,finished=-1,recovered;
        boolean atFixture;
        Session(String home,Activity action,long now){this.home=home;this.action=action;last=now;}
    }
    private static final AttachmentType<Session> SESSION=AttachmentRegistry.create(Napuli.id("home_furniture_session"));
    static void register() {}
    static boolean handles(Activity action){return action==Activity.WASH||action==Activity.SLEEP||action==Activity.STUDY||action==Activity.REST||action==Activity.MATCH;}
    static void cancel(NapoliResident npc,ServerLevel city) {
        var state=npc.removeAttached(SESSION);
        if(state!=null&&state.resource!=null)CityMovement.release(city,state.resource,npc.getUUID());
        if(state!=null)CityMovement.stand(npc);
    }
    static void prepare(NapoliResident npc,ServerLevel city,Activity action) {
        var state=npc.getAttached(SESSION);
        if(state!=null&&(state.action!=action||city.getGameTime()-state.last>240))cancel(npc,city);
        if(action!=Activity.SLEEP&&npc.isSleeping())npc.stopSleeping();
    }
    static boolean tick(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen citizen,NapoliMergellina.House home,Activity action) {
        prepare(npc,city,action);
        if(!handles(action)||home==null){cancel(npc,city);return false;}
        var state=npc.getAttached(SESSION);
        if(state!=null&&!state.home.equals(home.id())){cancel(npc,city);state=null;}
        if(state==null){state=new Session(home.id(),action,city.getGameTime());npc.setAttached(SESSION,state);}
        int elapsed=(int)Math.clamp(city.getGameTime()-state.last,0,80);state.last=city.getGameTime();
        if(!HousingCapacity.canEnter(city,home.inside(),npc.getUUID())){release(npc,city,state);state.station=null;state.progress=0;state.atFixture=false;CityArrivals.waitOutside(npc,city,home);return true;}
        if(action==Activity.SLEEP)return sleep(npc,city,citizen,home);
        if(action==Activity.WASH)return wash(npc,city,citizen,home,state,elapsed);
        return leisure(npc,city,citizen,home,state,action);
    }
    private static boolean sleep(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliMergellina.House home) {
        CityMovement.stand(npc);
        if(npc.isSleeping()){
            if(npc.getSleepingPos().filter(p->home.contains(p)&&city.getBlockState(p).getBlock() instanceof BedBlock).isPresent())return true;
            npc.stopSleeping();
        }
        var preferred=CityHomeStations.preferredBed(home,c,NapoliBroadcast.day(city));
        var bed=CityBeds.choose(npc,city,home,preferred);
        if(bed==null){npc.setActivity(Activity.WAIT);npc.getNavigation().stop();return true;}
        var stand=bed.relative(city.getBlockState(bed).getValue(BedBlock.FACING).getOpposite());
        var station=new CityHomeStations.Station(CityLivingHomes.Furniture.BED,bed,stand,null);
        if(CityHomeStations.reached(npc,station)){npc.getNavigation().stop();npc.startSleeping(bed);npc.setActivity(Activity.SLEEP);}
        else move(npc,city,home,stand);
        return true;
    }
    private static boolean wash(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliMergellina.House home,Session state,int elapsed) {
        if(state.step!=Step.TOILET)leaveSeat(npc);
        if(state.finished>=0){npc.setActivity(Activity.REST);return true;}
        if(state.step==null)state.step=CitySimulation.life(city,c.id()).needs().bladder()<35?Step.TOILET:Step.SHOWER;
        var kind=switch(state.step){case TOILET->CityLivingHomes.Furniture.TOILET;case HANDS->CityLivingHomes.Furniture.SINK;default->CityLivingHomes.Furniture.SHOWER;};
        var station=CityHomeStations.fixture(home,kind);
        if(state.step==Step.TOILET)station=new CityHomeStations.Station(kind,station.block(),station.stand(),station.block());
        if(!CityHomeStations.valid(city,station)) {
            release(npc,city,state);state.progress=0;state.atFixture=false;npc.setActivity(Activity.WAIT);npc.getNavigation().stop();return true;
        }
        state.station=station;
        if(!home.contains(npc.blockPosition())||Math.abs(npc.getY()-station.stand().getY())>1.5
                ||npc.blockPosition().distSqr(station.stand())>100){
            release(npc,city,state);state.progress=0;state.atFixture=false;move(npc,city,home,station.stand());return true;
        }
        state.resource=CityLivingHomes.use(home,CityLivingHomes.Furniture.TOILET).block();
        if(!CityMovement.acquire(city,state.resource,npc.getUUID(),240)||bathOccupied(npc,city,home)) {
            state.progress=0;state.atFixture=false;npc.setActivity(Activity.WAIT);
            move(npc,city,home,CityLivingHomes.use(home,CityLivingHomes.Furniture.SOFA).stand());return true;
        }
        if(!CityHomeStations.reached(npc,station)){state.progress=0;state.atFixture=false;move(npc,city,home,station.stand());return true;}
        npc.getNavigation().stop();npc.setActivity(Activity.WASH);look(npc,station.block());closeBathroom(npc,city,home);
        if(state.step==Step.TOILET&&!CityMovement.sit(npc,station.block())){state.progress=0;state.atFixture=false;npc.setActivity(Activity.WAIT);return true;}
        npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        npc.animate(state.step==Step.TOILET?CityGestures.Gesture.NONE:CityGestures.Gesture.WASH,45);
        if(state.atFixture)state.progress+=elapsed;
        state.atFixture=true;
        if(state.progress<(state.step==Step.HANDS?40:120))return true;
        state.progress=0;state.atFixture=false;
        if(state.step==Step.TOILET){state.step=Step.HANDS;return true;}
        var life=CitySimulation.life(city,c.id());var needs=life.needs();
        boolean hands=state.step==Step.HANDS;
        CitySimulation.put(city,c.id(),life.withNeeds(needs.change(0,0,hands?15:70,0,0,hands?85:0),life.food(),life.last(),life.water()+1,life.energy()+(hands?0:1)));
        if(hands&&needs.hygiene()<35){state.step=Step.SHOWER;return true;}
        if(!hands)CityTraces.soiled(city,home.id());
        state.finished=city.getGameTime();release(npc,city,state);
        // Leave the bathroom available to the next family member.
        move(npc,city,home,CityLivingHomes.use(home,CityLivingHomes.Furniture.SOFA).stand());return true;
    }
    private static boolean leisure(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliMergellina.House home,Session state,Activity action) {
        boolean study=action==Activity.STUDY;
        if(state.station!=null&&(!CityHomeStations.valid(city,state.station)
                ||state.station.chair()!=null&&CityHomeStations.occupied(city,state.station.chair(),npc))){CityMovement.stand(npc);release(npc,city,state);state.station=null;}
        if(state.station==null){
            var options=study?CityHomeStations.desks(city,home):CityHomeStations.seats(city,home,action==Activity.MATCH);
            int start=study?Math.floorMod(c.id().hashCode(),Math.max(1,options.size())):0;
            for(int i=0;i<options.size();i++){
                var choice=options.get((start+i)%options.size());
                if(choice.chair()!=null&&CityHomeStations.occupied(city,choice.chair(),npc)||!CityMovement.available(city,choice.block(),npc.getUUID()))continue;
                state.station=choice;state.resource=choice.block();break;
            }
        }
        if(state.station==null){npc.setActivity(Activity.WAIT);npc.getNavigation().stop();return true;}
        if(!CityMovement.acquire(city,state.resource,npc.getUUID(),240)){npc.setActivity(Activity.WAIT);return true;}
        if(!CityHomeStations.reached(npc,state.station)){move(npc,city,home,state.station.stand());return true;}
        npc.getNavigation().stop();
        if(state.station.chair()!=null&&!CityMovement.sit(npc,state.station.chair())){release(npc,city,state);state.station=null;return true;}
        npc.setActivity(action);
        npc.setItemSlot(EquipmentSlot.MAINHAND,study?new ItemStack(Items.BOOK):ItemStack.EMPTY);
        if(study){look(npc,state.station.block());npc.animate(CityGestures.Gesture.WRITE,45);}
        else if(action==Activity.MATCH){
            var tv=CityLivingHomes.use(home,CityLivingHomes.Furniture.TV).block();look(npc,tv);NapoliHouseholds.paintTelevision(city,tv);
            if(NapoliBroadcast.channel(city,tv)==NapoliBroadcast.Channel.OFF)npc.setActivity(Activity.REST);
        }
        if(city.getGameTime()-state.recovered>=120){
            var life=CitySimulation.life(city,c.id());
            CitySimulation.put(city,c.id(),life.withNeeds(life.needs().change(0,study?0:4,0,study?5:8,0,0),life.food(),life.last(),life.water(),life.energy()));
            state.recovered=city.getGameTime();
        }
        return true;
    }
    private static boolean bathOccupied(NapoliResident npc,ServerLevel city,NapoliMergellina.House h) {
        var bounds=new AABB(h.x()+6.25,h.ground(),h.z()+1,h.x()+10,h.ground()+2.5,h.z()+10.75);
        return !city.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,bounds,e->e!=npc&&e.isAlive()&&!e.isSpectator()&&!CityMovement.isSeat(e)).isEmpty();
    }
    private static void closeBathroom(NapoliResident npc,ServerLevel city,NapoliMergellina.House home) {
        for(int z:new int[]{4,7}){var p=new BlockPos(home.x()+6,home.ground(),home.z()+z);if(!city.hasChunkAt(p))continue;var state=city.getBlockState(p);
            if(state.getBlock() instanceof DoorBlock door&&state.getValue(DoorBlock.OPEN))door.setOpen(npc,city,state,p,false);}
    }
    private static void move(NapoliResident npc,ServerLevel city,NapoliMergellina.House home,BlockPos goal) {
        if(!CityMovement.isSeat(npc.getVehicle())){if(npc.activity()!=Activity.WAIT)npc.setActivity(Activity.COMMUTE);CityDomestic.move(npc,home.contains(npc.blockPosition())?CityHomeStations.waypoint(npc,home,goal):CityRoutines.localGoal(city,npc,goal));}
    }
    private static void leaveSeat(NapoliResident npc){var marker=npc.getVehicle();if(CityMovement.isSeat(marker)){npc.stopRiding();marker.discard();}}
    private static void look(NapoliResident npc,BlockPos at){npc.getLookControl().setLookAt(at.getX()+.5,at.getY()+.7,at.getZ()+.5);}
    private static void release(NapoliResident npc,ServerLevel city,Session state){if(state.resource!=null){CityMovement.release(city,state.resource,npc.getUUID());state.resource=null;}}
    private CityHomeUse() {}
}
