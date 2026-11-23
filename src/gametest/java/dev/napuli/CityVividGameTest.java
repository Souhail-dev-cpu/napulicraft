package dev.napuli;

import java.util.*;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;

public final class CityVividGameTest {
    @GameTest public void leavingAShopUsesTheDoorBeforeTurningTowardHome(GameTestHelper h){
        var city=h.getLevel();var site=CityProfessions.Category.TRADE.site();city.getChunkAt(site);var npc=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,site.offset(8,0,7),0);npc.setNoAi(true);
        try{var home=CityLivingHomes.find("viva_7").inside();var exit=CityRoutines.localGoal(city,npc,home);h.assertTrue(exit.equals(site.offset(8,0,13)),"A shopper can temporarily walk away from home to get through the shop's south-facing door");h.assertTrue(CityJourneys.route(npc.blockPosition(),home).contains(exit),"The abstract journey uses the same exit as the physical actor");}finally{npc.discard();}h.succeed();
    }
    @GameTest public void gesturesDescribeDifferentPhysicalActions(GameTestHelper h){
        h.assertTrue(CityGestures.action(NapoliHouseholds.Activity.PREPARE)==CityGestures.Gesture.CHOP&&CityGestures.action(NapoliHouseholds.Activity.COOK)==CityGestures.Gesture.STIR,"Cutting and stirring have distinct animations");
        var npc=NapoliResident.spawn(h.getLevel(),NapoliResident.Role.PASSERBY,h.absolutePos(new BlockPos(1,2,1)),0);
        try{npc.setExpression(-4,150);npc.animate(CityGestures.Gesture.WAVE,60);h.assertTrue(npc.mood()==0&&npc.vitality()==100&&npc.gesture()==CityGestures.Gesture.WAVE,"Expressions are bounded and gestures are synchronized entity data");npc.setExpression(40,10);double tired=CityGestures.pace(npc);npc.setExpression(80,100);h.assertTrue(CityGestures.pace(npc)>tired,"Fatigue affects actual walking speed");}finally{npc.discard();}h.succeed();
    }
    @GameTest public void urgentNeedsCanChangeAPersonalSchedule(GameTestHelper h){
        var city=h.getLevel();long day=NapoliBroadcast.day(city);var c=adult(UUID.randomUUID(),false,"public_0",day,NapoliCivicLife.Job.COOK.ordinal());var l=CitySimulation.fresh(c.id());
        h.assertTrue(CityHabits.choose(city,c,l,5000)==NapoliHouseholds.Activity.WORK,"A rested employed adult follows the work schedule");
        var hungry=l.withNeeds(new CitySimulation.Needs(5,80,80,70,70,80),8,l.last(),0,0);h.assertTrue(CityHabits.choose(city,c,hungry,5000)==NapoliHouseholds.Activity.EAT,"Acute hunger interrupts the usual work schedule");
        var exhausted=l.withNeeds(new CitySimulation.Needs(80,5,80,70,70,80),8,l.last(),0,0);h.assertTrue(CityHabits.choose(city,c,exhausted,5000)==NapoliHouseholds.Activity.SLEEP,"Exhaustion changes the plan instead of replaying a fixed timetable");
        h.assertTrue(CityHabits.offset(c.id())==CityHabits.offset(c.id())&&Math.abs(CityHabits.offset(c.id()))<=180,"Personal timing is reproducible across reloads");h.succeed();
    }
    @GameTest public void householdCookingUsesAbilityAndAvailability(GameTestHelper h){
        var city=h.getLevel();var census=city.removeAttached(NapoliCivicLife.CENSUS);var practice=city.removeAttached(CityHabits.PRACTICE);var agreements=city.removeAttached(CityRelationships.AGREEMENTS);long day=NapoliBroadcast.day(city);
        try{var man=adult(UUID.randomUUID(),false,"public_1",day,-1);var woman=adult(UUID.randomUUID(),true,"public_1",day,-1);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(day,List.of(man,woman)));city.setAttached(CityHabits.PRACTICE,Map.of(man.id().toString(),100));
            h.assertTrue(CityHabits.cook(city,woman,11000).equals(man.id()),"The most capable available adult cooks regardless of gender");
        }finally{restore(city,NapoliCivicLife.CENSUS,census);restore(city,CityHabits.PRACTICE,practice);restore(city,CityRelationships.AGREEMENTS,agreements);}h.succeed();
    }
    @GameTest public void aBakerReservesOneIngredientAndSettlesOneOutputAfterReload(GameTestHelper h){
        var city=h.getLevel();var stocks=city.removeAttached(CityEconomy.STOCKS);var tasks=city.removeAttached(CityOccupations.TASKS);var goals=city.removeAttached(CityPersonalGoals.GOALS);var id=UUID.randomUUID();
        try{CityEconomy.put(city,"forno",new CityEconomy.Stock(0,1,0,0,0));h.assertTrue(CityOccupations.reserveDough(city,id)&&CityOccupations.reserveDough(city,id)&&CityEconomy.stock(city,"forno").flour()==0,"Repeated task ticks cannot take another ingredient for the same dough");
            var saved=CityOccupations.Task.CODEC.encodeStart(JsonOps.INSTANCE,CityOccupations.task(city,id)).getOrThrow();var t=CityOccupations.Task.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow();CityOccupations.put(city,id,new CityOccupations.Task(t.kind(),2,120,t.cargo(),t.recipient(),t.last()));
            h.assertTrue(CityOccupations.finishBread(city,id)&&!CityOccupations.finishBread(city,id)&&CityEconomy.stock(city,"forno").bread()==1,"A completed batch produces exactly one loaf even if settlement is replayed");
        }finally{restore(city,CityEconomy.STOCKS,stocks);restore(city,CityOccupations.TASKS,tasks);restore(city,CityPersonalGoals.GOALS,goals);}h.succeed();
    }
    @GameTest public void directionsRequireAnActualDatedSighting(GameTestHelper h){
        var city=h.getLevel();var old=city.removeAttached(CityDialogue.SIGHTINGS);var speaker=UUID.randomUUID();var subject=UUID.randomUUID();
        try{h.assertTrue(CityDialogue.where(city,speaker,subject).startsWith("Non lo so"),"The speaker cannot retrieve another citizen's live location from the census");CityDialogue.remember(city,speaker,subject,new BlockPos(-417,64,-28),"WORK");
            h.assertTrue(CityDialogue.where(city,speaker,subject).contains("Ho visto")&&CityDialogue.where(city,speaker,subject).contains("potrebbe essersi spostato")&&CityDialogue.where(city,UUID.randomUUID(),subject).startsWith("Non lo so"),"The dated report belongs only to its observer and acknowledges uncertainty");
        }finally{restore(city,CityDialogue.SIGHTINGS,old);}h.succeed();
    }
    @GameTest public void aPersistentCoupleConflictNeedsSeveralDaysBeforeSeparation(GameTestHelper h){
        var city=h.getLevel();var lives=city.removeAttached(CitySimulation.LIFE);var strain=city.removeAttached(CityRelationships.STRAIN);var events=city.removeAttached(CityRelationships.EVENTS);var addresses=city.removeAttached(CityRelationships.ADDRESSES);long day=NapoliBroadcast.day(city);
        try{var a=adult(UUID.randomUUID(),false,"public_2",day,-1);var b=adult(UUID.randomUUID(),true,"public_2",day,-1);a=a.withHousehold(b.id().toString(),a.property(),100);b=b.withHousehold(a.id().toString(),b.property(),100);var people=new LinkedHashMap<UUID,NapoliCivicLife.Citizen>();people.put(a.id(),a);people.put(b.id(),b);
            for(var pair:List.of(List.of(a,b),List.of(b,a))){var person=pair.getFirst();var l=CitySimulation.fresh(person.id());CitySimulation.put(city,person.id(),new CitySimulation.Life(l.needs(),l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),l.food(),l.last(),l.personality(),Map.of(pair.getLast().id().toString(),15),l.memories(),0,0,-100));}
            for(int i=0;i<3;i++)CityRelationships.daily(city,people,day+i);h.assertTrue(!people.get(a.id()).partner().isEmpty(),"One bad conversation does not end a relationship");CityRelationships.daily(city,people,day+3);
            h.assertTrue(people.get(a.id()).partner().isEmpty()&&people.get(b.id()).partner().isEmpty()&&!people.get(a.id()).property().equals(people.get(b.id()).property()),"A sustained mutual conflict changes both partnership and residence");
        }finally{restore(city,CitySimulation.LIFE,lives);restore(city,CityRelationships.STRAIN,strain);restore(city,CityRelationships.EVENTS,events);restore(city,CityRelationships.ADDRESSES,addresses);}h.succeed();
    }
    @GameTest public void aSavedJourneyHasTravelTimeAndCannotArriveInstantly(GameTestHelper h){
        var from=new BlockPos(-417,64,-67);var to=CityProfessions.Category.PUBLIC.site().offset(8,0,7);var j=CityJourneys.plan(from,to,1000,80);
        h.assertTrue(j.arrives()>1000&&CityJourneys.sample(j,1000).equals(from),"A journey starts at the actual previous position");
        var middle=CityJourneys.sample(j,(j.departed()+j.arrives())/2);h.assertTrue(!middle.equals(to),"Changing the schedule does not teleport the resident to work");
        var json=CityJourneys.Journey.CODEC.encodeStart(JsonOps.INSTANCE,j).getOrThrow();var loaded=CityJourneys.Journey.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();h.assertTrue(loaded.equals(j)&&CityJourneys.sample(loaded,j.arrives()).equals(to),"Route and ETA survive disk serialization");h.succeed();
    }
    @GameTest public void groceriesRemainReservedUntilTheyReachTheHomePantry(GameTestHelper h){
        var city=h.getLevel();var lives=city.removeAttached(CitySimulation.LIFE);var cargo=city.removeAttached(CityTraces.CARGO);var pantry=city.removeAttached(CityTraces.PANTRIES);var journeys=city.removeAttached(CityJourneys.JOURNEYS);long day=NapoliBroadcast.day(city);var c=adult(UUID.randomUUID(),false,"public_4",day,-1);var home=CityLivingHomes.find(c.property());
        try{var l=CitySimulation.fresh(c.id());CitySimulation.put(city,c.id(),l.withNeeds(l.needs(),6,city.getGameTime(),0,0));h.assertTrue(CityTraces.shopping(city,c,6)&&!CityTraces.shopping(city,c,6)&&CitySimulation.life(city,c.id()).food()==0,"Purchased groceries are carried once instead of being duplicated in the pantry");
            var target=home.inside();CityJourneys.put(city,c.id(),new CityJourneys.Journey(List.of(target),0,0,target,0));h.assertTrue(CityTraces.unload(city,c)&&!CityTraces.unload(city,c)&&CityTraces.pantry(city,home.id())==6,"Arrival transfers all six meals to the household budget once");
        }finally{restore(city,CitySimulation.LIFE,lives);restore(city,CityTraces.CARGO,cargo);restore(city,CityTraces.PANTRIES,pantry);restore(city,CityJourneys.JOURNEYS,journeys);}h.succeed();
    }
    @GameTest public void civicServicesExplainClosureAndDifferentSchoolStages(GameTestHelper h){
        h.assertTrue(!CityServices.open("municipality",5000,5)&&CityServices.open("municipality",5000,4),"The municipality follows real opening days");
        h.assertTrue(!CityServices.schoolClass(4).equals(CityServices.schoolClass(9))&&!CityServices.schoolClass(12).equals(CityServices.schoolClass(16)),"Schooling changes as a child grows");
        h.assertTrue(CityTransit.stop(61,true).getX()!=CityTransit.stop(61,false).getX(),"Opposite bus directions have separate queues and boarding points");h.succeed();
    }
    @GameTest public void aCompletedMedicalVisitConsumesMedicineAndChargesOnlyOnce(GameTestHelper h){
        var city=h.getLevel();var server=city.getServer();long clock=city.getOverworldClockTime();var census=city.removeAttached(NapoliCivicLife.CENSUS);var lives=city.removeAttached(CitySimulation.LIFE);var visits=city.removeAttached(CityServices.VISITS);var journeys=city.removeAttached(CityJourneys.JOURNEYS);var supplies=city.removeAttached(CityOccupations.SUPPLIES);
        try{server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 5000");long day=NapoliBroadcast.day(city);var doctor=adult(UUID.randomUUID(),false,"public_4",day,NapoliCivicLife.Job.DOCTOR.ordinal());var patient=adult(UUID.randomUUID(),false,"public_5",day,-1);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(day,List.of(doctor,patient)));var l=CitySimulation.fresh(patient.id());CitySimulation.put(city,patient.id(),l.career(30,50,0,0,0,List.of()));
            for(var person:List.of(doctor,patient)){var pos=CityServices.place("doctor");CityJourneys.put(city,person.id(),new CityJourneys.Journey(List.of(pos),0,0,pos,0));}CityOccupations.supply(city,"medicine",1);
            city.setAttached(CityServices.VISITS,Map.of(patient.id().toString(),new CityServices.Visit("doctor",day,0,0,"waiting",120,doctor.id().toString(),city.getGameTime()-40)));
            h.assertTrue(CityServices.examine(city,doctor,patient)&&!CityServices.examine(city,doctor,patient),"A restored examination settles exactly once");h.assertTrue(CitySimulation.life(city,patient.id()).health()==55&&CityOccupations.supply(city,"medicine")==0&&NapoliCivicLife.profile(city,patient.id()).balance()==98&&NapoliCivicLife.profile(city,doctor.id()).balance()==102,"Treatment uses one medicine and transfers the two-euro fee without duplication");
        }finally{server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set "+clock);restore(city,NapoliCivicLife.CENSUS,census);restore(city,CitySimulation.LIFE,lives);restore(city,CityServices.VISITS,visits);restore(city,CityJourneys.JOURNEYS,journeys);restore(city,CityOccupations.SUPPLIES,supplies);}h.succeed();
    }
    private static NapoliCivicLife.Citizen adult(UUID id,boolean woman,String home,long day,int job){return new NapoliCivicLife.Citizen(id,"Vicino",woman,day-30L*CityLifeConfig.settings.daysPerYear,100,job,"",home,"",false);}
    private static <T> void restore(net.minecraft.server.level.ServerLevel city,net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> type,T old){if(old==null)city.removeAttached(type);else city.setAttached(type,old);}
}
