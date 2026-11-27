package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class NightlifeGameTest {
    @GameTest public void morningSchoolCareDoesNotWakeTheParentReturningFromANightShift(GameTestHelper h){
        var city=h.getLevel();var saved=city.removeAttached(NapoliCivicLife.CENSUS);var agreements=city.removeAttached(CityRelationships.AGREEMENTS);
        try{var night=citizen(city,new UUID(0,1),30,NapoliCivicLife.Job.NIGHT_DOCTOR.ordinal());var parent=citizen(city,new UUID(0,2),30,NapoliCivicLife.Job.TEACHER.ordinal());city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(night,parent)));
            h.assertTrue(CityRelationships.agreement(city,night).carer().equals(parent.id().toString()),"The daytime parent handles morning school while the night worker rests");
        }finally{restore(city,NapoliCivicLife.CENSUS,saved);restore(city,CityRelationships.AGREEMENTS,agreements);}h.succeed();
    }
    @GameTest public void residentsLeavingUpperRoomsUseTheLadderBeforeTheStreet(GameTestHelper h){
        var city=h.getLevel();
        for(var home:List.of(CityLivingHomes.PUBLIC_HOMES.getFirst(),DistrictLife.homes().getFirst())){
            var landing=new BlockPos(home.x()+1,home.ground()+5,home.z()+(home.id().startsWith("atlas_")?home.depth()-3:1));city.getChunkAt(landing);var before=city.getBlockState(landing);city.setBlock(landing,Blocks.LADDER.defaultBlockState(),2);
            var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,new BlockPos(home.x()+8,home.ground()+5,home.z()+8),0);npc.setNoAi(true);
            try{h.assertTrue(CityRoutines.localGoal(city,npc,home.approach(4)).equals(landing),"An upstairs resident first reaches the real ladder column before going out: "+home.id());}
            finally{npc.discard();city.setBlock(landing,before,2);}
        }h.succeed();
    }
    @GameTest public void adultsHaveVariedBedtimesWithoutKeepingChildrenOrExhaustedPeopleAwake(GameTestHelper h){
        var city=h.getLevel();int percent=CityLifeConfig.settings.nightActivityPercent;var members=city.removeAttached(CityThieves.MEMBERS);
        try{CityLifeConfig.settings.nightActivityPercent=100;long day=NapoliBroadcast.day(city);var adult=citizen(city,new UUID(0,1),30,-1);var life=CitySimulation.fresh(adult.id());
            h.assertTrue(CityHabits.choose(city,adult,life,18000)==NapoliHouseholds.Activity.SOCIAL,"A rested adult can still go out at midnight");
            var child=citizen(city,new UUID(0,2),12,-1);h.assertTrue(CityHabits.choose(city,child,CitySimulation.fresh(child.id()),18000)==NapoliHouseholds.Activity.SLEEP,"Children retain their bedtime");
            var tired=life.withNeeds(new CitySimulation.Needs(85,5,85,75,75,85),8,city.getGameTime(),0,0);h.assertTrue(CityHabits.choose(city,adult,tired,18000)==NapoliHouseholds.Activity.SLEEP,"Exhaustion takes precedence over nightlife");
            h.assertTrue(!CityNightlife.night(6000)&&!CityNightlife.outing(city,adult,life,23000),"Even late outings finish before morning");
            var endings=new HashSet<Integer>();for(int i=0;i<60;i++)endings.add(CityNightlife.closing(citizen(city,new UUID(i,i*31L),30,-1),day));h.assertTrue(endings.size()>3,"Everyone does not return home simultaneously");
        }finally{CityLifeConfig.settings.nightActivityPercent=percent;restore(city,CityThieves.MEMBERS,members);}h.succeed();
    }
    @GameTest public void nightWorkersSleepAfterTheirShiftAndEveningShopsRequireActualStaff(GameTestHelper h){
        var city=h.getLevel();var census=city.removeAttached(NapoliCivicLife.CENSUS);var journeys=city.removeAttached(CityJourneys.JOURNEYS);var lives=city.removeAttached(CitySimulation.LIFE);var d=DistrictLife.districts().getFirst();long clock=city.getOverworldClockTime();
        try{setClock(city,19000);var vendor=new NapoliCivicLife.Citizen(DistrictLife.identity(d,8),"Turno serale",true,NapoliBroadcast.day(city)-30L*CityLifeConfig.settings.daysPerYear,100,NapoliCivicLife.Job.MERCHANT.ordinal(),"",d.homes().get(2).id(),"",false);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(vendor)));
            h.assertTrue(CityHabits.working(vendor,19000,0)&&!CityHabits.working(vendor,6000,0),"The evening vendor follows a night shift rather than the daytime merchant schedule");
            h.assertTrue(CityHabits.choose(city,vendor,CitySimulation.fresh(vendor.id()),19000)==NapoliHouseholds.Activity.WORK&&CityHabits.choose(city,vendor,CitySimulation.fresh(vendor.id()),1000)==NapoliHouseholds.Activity.SLEEP,"Staff works at night and sleeps after closing");
            CityJourneys.put(city,vendor.id(),CityJourneys.plan(NapoliCivicLife.home(vendor),NapoliCivicLife.home(vendor),city.getGameTime(),90));
            h.assertTrue(DistrictShops.staff(city,d)==null,"A vendor who is still at home does not open the night shop");CityJourneys.put(city,vendor.id(),CityJourneys.plan(DistrictLife.work(vendor),DistrictLife.work(vendor),city.getGameTime(),90));
            h.assertTrue(DistrictShops.staff(city,d)!=null&&DistrictShops.staff(city,d).id().equals(vendor.id()),"Only the vendor at its workplace makes the night shop available");
        }finally{setClock(city,clock);restore(city,NapoliCivicLife.CENSUS,census);restore(city,CityJourneys.JOURNEYS,journeys);restore(city,CitySimulation.LIFE,lives);}h.succeed();
    }
    @GameTest public void thiefIdentitiesAreBoundedStableAndExcludeChildrenAndPolice(GameTestHelper h){
        withFixture(h,f->{var city=f.city();int limit=CityLifeConfig.settings.nightThieves;
            try{CityLifeConfig.settings.nightThieves=1;CityThieves.seed(city);var first=city.getAttached(CityThieves.MEMBERS);CityThieves.seed(city);h.assertTrue(first.size()==1&&city.getAttached(CityThieves.MEMBERS).equals(first),"Selection is stable and bounded");
                h.assertTrue(!CityThieves.candidate(citizen(city,UUID.randomUUID(),12,-1),NapoliBroadcast.day(city))&&!CityThieves.candidate(citizen(city,UUID.randomUUID(),30,NapoliCivicLife.Job.CARABINIERE.ordinal()),NapoliBroadcast.day(city)),"Children and police are never recruited");
            }finally{CityLifeConfig.settings.nightThieves=limit;}});h.succeed();
    }
    @GameTest public void physicalTheftConservesMoneyAndRefundsOnlyOnce(GameTestHelper h){
        withFixture(h,f->{h.assertTrue(CityThieves.steal(f.city(),f.thief(),f.victim()),"An unseen thief in physical contact can take money");
            int cash=NapoliCivicLife.profile(f.city(),f.victim().getUUID()).balance(),reserved=f.city().getAttachedOrElse(CityThieves.LOOT,Map.of()).values().stream().mapToInt(CityThieves.Loot::amount).sum();
            h.assertTrue(cash+reserved==100&&reserved<=8&&NapoliCivicLife.profile(f.city(),f.thief().getUUID()).balance()==100,"Stolen money is held separately, never duplicated in the thief's spendable balance");
            h.assertTrue(!CityThieves.steal(f.city(),f.thief(),f.victim()),"Repeated ticks cannot take the same victim's money again");CityThieves.recover(f.city(),f.thief().getUUID());CityThieves.recover(f.city(),f.thief().getUUID());
            h.assertTrue(NapoliCivicLife.profile(f.city(),f.victim().getUUID()).balance()==100&&f.city().getAttachedOrElse(CityThieves.LOOT,Map.of()).isEmpty(),"Recovery credits the owner once");});h.succeed();
    }
    @GameTest public void wallsDistanceAndLookingAtTheThiefPreventTheft(GameTestHelper h){
        withFixture(h,f->{var city=f.city();var original=f.thief().position();f.thief().setPos(original.x,original.y,original.z-8);h.assertTrue(!CityThieves.steal(city,f.thief(),f.victim()),"No remote wallet transfer is possible");f.thief().setPos(original);
            f.victim().setYRot(180);h.assertTrue(!CityThieves.steal(city,f.thief(),f.victim()),"A victim facing the thief notices the approach");f.victim().setYRot(0);
            var wall=f.victim().blockPosition().north();for(int y=0;y<3;y++)city.setBlock(wall.above(y),Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!CityThieves.steal(city,f.thief(),f.victim()),"A wall interrupts physical contact and visibility");});h.succeed();
    }
    @GameTest public void visibleOfficersDeterTheftAndVictimsCanReportNPCs(GameTestHelper h){
        withFixture(h,f->{var officer=NapoliResident.spawn(f.city(),NapoliResident.Role.CARABINIERE,f.thief().blockPosition().east(3),0);officer.setNoAi(true);
            try{h.assertTrue(!CityThieves.steal(f.city(),f.thief(),f.victim()),"A nearby visible patrol deters the attempt");officer.discard();h.assertTrue(CityThieves.steal(f.city(),f.thief(),f.victim()),"The victim can be approached after the patrol leaves");
                h.assertTrue(f.city().getAttachedOrElse(CityPublicOrder.INCIDENTS,Map.of()).containsKey(f.thief().getUUID().toString()),"The victim reports the offender, not an unrelated player");
            }finally{officer.discard();}});h.succeed();
    }
    @GameTest public void fullWalletsRetainPendingRestitutionAndEscrowSurvivesSaving(GameTestHelper h){
        withFixture(h,f->{var city=f.city();h.assertTrue(CityThieves.steal(city,f.thief(),f.victim()),"Fixture creates a real theft");var saved=city.getAttached(CityThieves.LOOT).values().iterator().next();
            var json=CityThieves.Loot.CODEC.encodeStart(JsonOps.INSTANCE,saved).getOrThrow();h.assertTrue(CityThieves.Loot.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow().equals(saved),"Owner, offender and reserved money survive saving");
            var census=NapoliCivicLife.census(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(f.victim().getUUID())?c.withMoney(99999,c.job()):c).toList()));
            CityThieves.recover(city,f.thief().getUUID());h.assertTrue(!city.getAttachedOrElse(CityThieves.LOOT,Map.of()).isEmpty(),"A full wallet does not destroy the recovered money");
            CityOccupations.money(city,f.victim().getUUID(),-10);CityThieves.refund(city);h.assertTrue(city.getAttachedOrElse(CityThieves.LOOT,Map.of()).isEmpty(),"Restitution resumes once wallet space exists");});h.succeed();
    }
    @GameTest public void creativeAndSpectatorPlayersAreNotVictims(GameTestHelper h){
        withFixture(h,f->HousingCapacityGameTest.withPlayers(h,1,players->{var p=players.getFirst();p.setPos(f.victim().position());NapoliStreet.credit(p,100-NapoliStreet.cash(p));
            p.setGameMode(GameType.CREATIVE);h.assertTrue(!CityThieves.target(f.city(),f.thief(),p),"Creative mode is exempt");p.setGameMode(GameType.SPECTATOR);h.assertTrue(!CityThieves.target(f.city(),f.thief(),p),"Spectators are exempt");
        }));h.succeed();
    }
    private record Fixture(ServerLevel city,NapoliResident thief,NapoliResident victim){}
    private static void withFixture(GameTestHelper h,java.util.function.Consumer<Fixture> body){
        var city=h.getLevel();var types=List.<AttachmentType<?>>of(NapoliCivicLife.CENSUS,CitySimulation.LIFE,CityThieves.MEMBERS,CityThieves.LOOT,CityThieves.COOLDOWNS,CityPublicOrder.INCIDENTS);var saved=new HashMap<AttachmentType<?>,Object>();for(var type:types)saved.put(type,city.removeAttached(type));
        long clock=city.getOverworldClockTime();var at=h.absolutePos(new BlockPos(3,3,3));
        for(var p:BlockPos.betweenClosed(at.offset(-5,-1,-5),at.offset(6,3,6)))city.setBlock(p,p.getY()==at.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var thief=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,at,0);var victim=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_WOMAN,at.south(1),0);thief.setNoAi(true);victim.setNoAi(true);victim.setYRot(0);victim.setYHeadRot(0);
        try{setClock(city,19000);var a=citizen(city,thief.getUUID(),30,-1);var b=citizen(city,victim.getUUID(),30,-1);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(a,b)));city.setAttached(CityThieves.MEMBERS,Set.of(a.id().toString()));body.accept(new Fixture(city,thief,victim));}
        finally{thief.discard();victim.discard();setClock(city,clock);for(var type:types)restoreRaw(city,type,saved.get(type));}
    }
    static NapoliCivicLife.Citizen citizen(ServerLevel city,UUID id,int age,int job){return new NapoliCivicLife.Citizen(id,"Residente notturno",false,NapoliBroadcast.day(city)-(long)age*CityLifeConfig.settings.daysPerYear,100,job,"","public_0","",true);}
    private static void setClock(ServerLevel city,long time){city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set "+time);}
    private static <T> void restore(ServerLevel city,AttachmentType<T> type,T value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void restoreRaw(ServerLevel city,AttachmentType type,Object value){restore(city,type,value);}
}
