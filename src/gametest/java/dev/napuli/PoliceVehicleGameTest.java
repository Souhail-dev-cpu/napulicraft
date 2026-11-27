package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class PoliceVehicleGameTest {
    @GameTest public void escapingNeedsContinuousCoverAndResetsWhenExposed(GameTestHelper h){
        int hidden=0;for(int i=0;i<600;i++)hidden=PoliceSearch.advance(hidden,false,false);h.assertTrue(hidden==0,"Running unseen on an open road never clears the search");
        for(int i=0;i<300;i++)hidden=PoliceSearch.advance(hidden,false,true);h.assertTrue(hidden<PoliceSearch.HIDE_TICKS,"Fifteen seconds of cover do not end a twenty-second search");hidden=PoliceSearch.advance(hidden,true,true);h.assertTrue(hidden==0,"A fresh sighting resets the entire concealment period");for(int i=0;i<400;i++)hidden=PoliceSearch.advance(hidden,false,true);h.assertTrue(hidden==PoliceSearch.HIDE_TICKS,"Remaining genuinely concealed for twenty seconds completes escape");h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void actualWallsConcealButAnotherOfficersViewStillExposes(GameTestHelper h){
        floor(h);HousingCapacityGameTest.withPlayers(h,1,ps->{var city=h.getLevel();var player=ps.getFirst();var at=h.absolutePos(new BlockPos(10,1,7));player.setPos(Vec3.atBottomCenterOf(at));var cop=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,h.absolutePos(new BlockPos(2,1,7)),0);cop.setNoAi(true);var second=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,h.absolutePos(new BlockPos(12,1,9)),0);second.setNoAi(true);
            try{h.assertTrue(PoliceSearch.sees(cop,player)&&!PoliceSearch.covered(player,List.of(cop),cop.blockPosition()),"A visible player cannot hide merely by standing still");for(int z=2;z<=12;z++)for(int y=1;y<=4;y++)h.setBlock(7,y,z,Blocks.STONE);h.assertTrue(!PoliceSearch.sees(cop,player)&&PoliceSearch.covered(player,List.of(cop),cop.blockPosition()),"A nearby solid wall breaks sight and provides real cover");h.assertTrue(!PoliceSearch.covered(player,List.of(cop,second),cop.blockPosition()),"One officer seeing around the wall defeats concealment");h.assertTrue(PoliceSearch.covered(player,List.of(),cop.blockPosition()),"A concealed player can escape even if the original officer leaves the loaded area");
            }finally{cop.discard();second.discard();}
        });h.succeed();
    }
    @GameTest public void policeRoutesStayOnStreetsAndUseStableDifferentVehicles(GameTestHelper h){
        var route=PoliceVehicles.path(new BlockPos(-338,64,60),new BlockPos(-338,64,150));h.assertTrue(route.size()>=3&&route.stream().allMatch(p->p.getX()>=-343&&p.getX()<=-335),"The broad avenue has a real pursuit lane");h.assertTrue(PoliceVehicles.path(new BlockPos(0,64,0),new BlockPos(100,64,50)).isEmpty(),"An old-centre alley cannot become a straight car route through buildings");h.assertTrue(PoliceVehicles.mode(0).equals("scooter")&&PoliceVehicles.mode(1).equals("car"),"Different responders use bikes and cars");var id=UUID.randomUUID();h.assertTrue(PoliceVehicles.identity(id).equals(PoliceVehicles.identity(id))&&!PoliceVehicles.identity(id).equals(CityMobility.identity(id)),"A police vehicle has a stable identity distinct from the officer's private vehicle");h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void mountedOfficersRemainAssignedButCannotArrestFromTheirSeat(GameTestHelper h){
        floor(h);HousingCapacityGameTest.withPlayers(h,1,ps->{var city=h.getLevel();var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,1,4)));ps.getFirst().setPos(at.x,at.y,at.z+8);
            for(String kind:List.of("scooter","car")){var cop=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,BlockPos.containing(at),0);var vehicle=PoliceVehicles.create(city,cop,kind);
                try{h.assertTrue(vehicle!=null&&cop.startRiding(vehicle),"An actual officer boards its "+kind);h.assertTrue(PoliceDispatch.eligible(cop)&&!PoliceTactics.canGrab(cop,ps.getFirst()),"Mounted officers remain on the team but must dismount to arrest");h.assertTrue(!ps.getFirst().startRiding(vehicle)&&ScooterGarage.get(city,vehicle.getUUID())==null,"A police vehicle cannot be claimed as a player's scooter");h.assertTrue(!CityMobility.managed(vehicle),"Emergency pursuit cannot enter the citizen fuel or parking system");}
                finally{cop.stopRiding();if(vehicle!=null)vehicle.discard();cop.discard();}
            }
        });h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void realPoliceVehiclesMoveAndStopWhenTheirSearchEnds(GameTestHelper h){
        floor(h);HousingCapacityGameTest.withPlayers(h,1,ps->{var city=h.getLevel();var suspect=ps.getFirst();var initial=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3,1,6)));suspect.setPos(initial.x+8,initial.y,initial.z+8);var existing=new HashSet<UUID>();for(var e:city.getAllEntities())if(e instanceof NapoliResident)existing.add(e.getUUID());Carabinieri.report(suspect,Carabinieri.Crime.THEFT);
            try{for(String kind:List.of("scooter","car")){var cop=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,BlockPos.containing(initial),0);var vehicle=PoliceVehicles.create(city,cop,kind);h.assertTrue(vehicle!=null&&cop.startRiding(vehicle),"The officer boards a real emergency vehicle");var end=cop.blockPosition().east(30);PoliceVehicles.course(vehicle,suspect,end,city.getGameTime(),List.of(cop.blockPosition(),end));var before=vehicle.position();
                    try{for(int i=0;i<15;i++)PoliceVehicles.drive(vehicle,city);h.assertTrue(vehicle.position().distanceToSqr(before)>16,"The "+kind+" physically moves on the server");Carabinieri.endPursuit(suspect);h.assertTrue(!cop.isPassenger()&&vehicle.isRemoved(),"Ending the pursuit releases the driver and removes the emergency vehicle");}
                    finally{cop.stopRiding();vehicle.discard();cop.discard();}Carabinieri.report(suspect,Carabinieri.Crime.THEFT);
                }
            }finally{Carabinieri.endPursuit(suspect);var added=new ArrayList<Entity>();for(var e:city.getAllEntities())if(e instanceof NapoliResident&&!existing.contains(e.getUUID()))added.add(e);added.forEach(Entity::discard);}
        });h.succeed();
    }
    @GameTest public void anUnloadedPursuitOriginDoesNotLoadDistantChunks(GameTestHelper h){
        HousingCapacityGameTest.withPlayers(h,1,ps->{var city=h.getLevel();var far=new BlockPos(28000000,100,28000000);var cop=NapoliResident.create(city,NapoliResident.Role.CARABINIERE,far,0);h.assertTrue(!city.hasChunkAt(far)&&!PoliceVehicles.pursue(cop,ps.getFirst(),far.north(80),city.getGameTime(),0)&&PoliceVehicles.create(city,cop,"car")==null&&!city.hasChunkAt(far),"Choosing or creating a pursuit vehicle cannot load an inactive street");});h.succeed();
    }
    private static void floor(GameTestHelper h){for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,0,z,Blocks.STONE);for(int y=1;y<=5;y++)h.setBlock(x,y,z,Blocks.AIR);}}
}
