package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.world.level.block.Blocks;

public final class CityConversationGameTest {
    @GameTest public void onlyTheOldPopulationPresetIsUpgraded(GameTestHelper h){
        var old=new CityLifeConfig.Settings();old.population=120;old.districtPopulation=76;old.maximumPopulation=384;
        h.assertTrue(CityLifeConfig.upgradePopulation(old)&&old.districtPopulation==456&&old.maximumPopulation==1024,"Existing default configurations receive the larger population");
        h.assertTrue(!CityLifeConfig.upgradePopulation(old),"The configuration migration runs only once");
        var custom=new CityLifeConfig.Settings();custom.maximumPopulation=160;custom.districtPopulation=40;CityLifeConfig.upgradePopulation(custom);
        h.assertTrue(custom.maximumPopulation==160&&custom.districtPopulation==40,"An administrator's custom limits are preserved");h.succeed();
    }
    @GameTest public void allNewResidentsBelongToActualDistinctHouseholds(GameTestHelper h){
        var city=h.getLevel();var census=city.removeAttached(NapoliCivicLife.CENSUS);var settled=city.removeAttached(DistrictLife.SETTLED);var owners=city.removeAttached(NapoliProperties.OWNERS);var life=city.removeAttached(CitySimulation.LIFE);var journeys=city.removeAttached(CityJourneys.JOURNEYS);
        int target=CityLifeConfig.settings.districtPopulation,max=CityLifeConfig.settings.maximumPopulation;
        try{
            CityLifeConfig.settings.districtPopulation=456;CityLifeConfig.settings.maximumPopulation=1024;
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of()));
            int expected=DistrictLife.districts().size()*24;
            h.assertTrue(DistrictLife.seed(city)==expected&&DistrictLife.seed(city)==0,"Six families are seeded once in every district");
            var groups=new HashMap<String,Integer>();var ids=new HashSet<UUID>();
            for(var c:NapoliCivicLife.census(city).people()){
                h.assertTrue(ids.add(c.id())&&CityLivingHomes.find(c.property())!=null,"Every resident has one stable identity and a real registered home");
                groups.merge(c.property(),1,Integer::sum);
                if(!c.partner().isEmpty())h.assertTrue(NapoliCivicLife.profile(city,UUID.fromString(c.partner())).property().equals(c.property()),"Partners live in the same household");
                if(!c.parents().isEmpty())h.assertTrue(NapoliCivicLife.profile(city,UUID.fromString(c.parents())).property().equals(c.property()),"Children belong to the correct new family");
            }
            h.assertTrue(groups.size()==DistrictLife.districts().size()*6&&groups.values().stream().allMatch(n->n==4),"The increased population does not overcrowd existing homes");
            for(var d:DistrictLife.districts())h.assertTrue(NapoliCivicLife.profile(city,DistrictLife.identity(d,0)).property().equals(d.homes().getFirst().id()),"Legacy identities and their addresses are preserved");
        }finally{CityLifeConfig.settings.districtPopulation=target;CityLifeConfig.settings.maximumPopulation=max;restore(city,NapoliCivicLife.CENSUS,census);restore(city,DistrictLife.SETTLED,settled);restore(city,NapoliProperties.OWNERS,owners);restore(city,CitySimulation.LIFE,life);restore(city,CityJourneys.JOURNEYS,journeys);}h.succeed();
    }
    @GameTest public void italianQuestionsRecognizeIntentWithoutMatchingInsideOtherWords(GameTestHelper h){
        h.assertTrue(CityConversation.intent("Buongiorno, come stai?")==CityConversation.Topic.WELLBEING,"A sentence takes precedence over its greeting");
        h.assertTrue(CityConversation.intent("Che lavoro fai?")==CityConversation.Topic.WORK&&CityConversation.intent("Mi parli della tua famiglia?")==CityConversation.Topic.FAMILY,"Work and family are conversational topics");
        h.assertTrue(CityConversation.intent("Dov'è la fermata della metro?")==CityConversation.Topic.DIRECTIONS&&CityConversation.intent("Hai bisogno di aiuto?")==CityConversation.Topic.HELP,"Directions and actual help are understood");
        h.assertTrue(CityConversation.followUp("Perché?")&&CityConversation.followUp("Raccontami di più!"),"Accents and punctuation do not break follow-up questions");
        h.assertTrue(CityConversation.intent("travai travestimento")==CityConversation.Topic.UNKNOWN,"Short keywords do not match fragments inside unrelated words");h.succeed();
    }
    @GameTest public void expandingAnExistingCityPreservesSeparationsSavingsAndDeaths(GameTestHelper h){
        var city=h.getLevel();var census=city.removeAttached(NapoliCivicLife.CENSUS);var settled=city.removeAttached(DistrictLife.SETTLED);var owners=city.removeAttached(NapoliProperties.OWNERS);var life=city.removeAttached(CitySimulation.LIFE);var journeys=city.removeAttached(CityJourneys.JOURNEYS);
        int target=CityLifeConfig.settings.districtPopulation,max=CityLifeConfig.settings.maximumPopulation;
        try{
            CityLifeConfig.settings.districtPopulation=76;CityLifeConfig.settings.maximumPopulation=1024;
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of()));DistrictLife.seed(city);
            var before=NapoliCivicLife.census(city);var existing=before.people().getFirst();var separated=existing.withHousehold("","public_1",317);var deceased=before.people().get(1);
            var people=new ArrayList<>(before.people());people.set(0,separated);people.remove(deceased);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(before.day(),List.copyOf(people)));
            CityLifeConfig.settings.districtPopulation=456;h.assertTrue(DistrictLife.seed(city)==380,"Only the additional families are created in an old city");
            h.assertTrue(NapoliCivicLife.profile(city,existing.id()).equals(separated),"Expansion does not restore an old marriage, address or bank balance");
            h.assertTrue(NapoliCivicLife.profile(city,deceased.id())==null,"Expansion does not recreate someone who died");
        }finally{CityLifeConfig.settings.districtPopulation=target;CityLifeConfig.settings.maximumPopulation=max;restore(city,NapoliCivicLife.CENSUS,census);restore(city,DistrictLife.SETTLED,settled);restore(city,NapoliProperties.OWNERS,owners);restore(city,CitySimulation.LIFE,life);restore(city,CityJourneys.JOURNEYS,journeys);}h.succeed();
    }
    @GameTest public void householdIndexesFollowChangesOfAddress(GameTestHelper h){
        var city=h.getLevel();var saved=city.removeAttached(NapoliCivicLife.CENSUS);var c=citizen(city,UUID.randomUUID(),"Vicino mobile");
        try{
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(c)));
            h.assertTrue(NapoliCivicLife.household(city,"public_0").equals(List.of(c)),"The first household is indexed");
            var moved=c.withHousehold("","public_2",c.balance());city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(moved)));
            h.assertTrue(NapoliCivicLife.household(city,"public_0").isEmpty()&&NapoliCivicLife.household(city,"public_2").equals(List.of(moved)),"Cached domestic decisions use the current address after a move");
        }finally{restore(city,NapoliCivicLife.CENSUS,saved);}h.succeed();
    }
    @GameTest public void inputTemplateSubmitsAnActualFreeTextCommand(GameTestHelper h){
        var id=UUID.randomUUID();var button=CityConversation.inputButton(id);
        var event=button.action().orElseThrow().createAction(Map.of("messaggio",Action.ValueGetter.of("Come stai?"))).orElseThrow();
        h.assertTrue(event instanceof ClickEvent.RunCommand command&&command.command().startsWith("/parla "+id+" dice ")&&command.command().contains("Come stai?"),"The native dialog sends typed text to the correct resident");
        var encoded=net.minecraft.server.dialog.ActionButton.CODEC.encodeStart(JsonOps.INSTANCE,button).getOrThrow();
        h.assertTrue(net.minecraft.server.dialog.ActionButton.CODEC.parse(JsonOps.INSTANCE,encoded).result().isPresent(),"The input action can be serialized to a real client");h.succeed();
    }
    @GameTest public void conversationMemorySurvivesSavingAndIsPrivateToThePair(GameTestHelper h){
        var city=h.getLevel();var saved=city.removeAttached(CityConversation.MEMORIES);UUID npc=UUID.randomUUID(),visitor=UUID.randomUUID();
        try{
            CityConversation.remember(city,npc,visitor,CityConversation.Topic.FAMILY);CityConversation.remember(city,npc,visitor,CityConversation.Topic.UNKNOWN);
            var memory=CityConversation.memory(city,npc,visitor);var encoded=CityConversation.Memory.CODEC.encodeStart(JsonOps.INSTANCE,memory).getOrThrow();
            h.assertTrue(CityConversation.Memory.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().equals(memory)&&CityConversation.previous(memory)==CityConversation.Topic.FAMILY,"A clarification or save does not lose the previous subject");
            h.assertTrue(CityConversation.memory(city,npc,UUID.randomUUID())==null&&CityConversation.memory(city,UUID.randomUUID(),visitor)==null,"Other visitors and citizens have separate memories");
            CityConversation.remember(city,npc,visitor,CityConversation.Topic.WORK);h.assertTrue(CityConversation.memory(city,npc,visitor).turns()==2,"A new meaningful question advances the conversation");
        }finally{restore(city,CityConversation.MEMORIES,saved);}h.succeed();
    }
    @GameTest public void urgentNeedsAndDutiesPreventSocialDistractions(GameTestHelper h){
        var city=h.getLevel();var saved=city.removeAttached(CitySimulation.LIFE);var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.absolutePos(new BlockPos(1,2,1)),0);npc.setNoAi(true);var c=citizen(city,npc.getUUID(),"Ciro Prova");
        try{
            h.assertTrue(CityConversation.canPause(city,npc,c,NapoliHouseholds.Activity.SOCIAL),"A free resident can stop to listen");
            h.assertTrue(!CityConversation.canPause(city,npc,c,NapoliHouseholds.Activity.WORK)&&!CityNeighborhood.free(npc,city,c,NapoliHouseholds.Activity.SCHOOL),"Conversations do not interrupt work or school");
            var l=CitySimulation.fresh(c.id());CitySimulation.put(city,c.id(),l.withNeeds(new CitySimulation.Needs(10,85,85,85,85,85),8,city.getGameTime(),0,0));
            h.assertTrue(!CityConversation.canPause(city,npc,c,NapoliHouseholds.Activity.SOCIAL)&&!CityNeighborhood.free(npc,city,c,NapoliHouseholds.Activity.SOCIAL),"Hungry residents prioritize food over social scenes");
        }finally{npc.discard();restore(city,CitySimulation.LIFE,saved);}h.succeed();
    }
    @GameTest public void everyLoadedObserverEventuallyGetsATurn(GameTestHelper h){
        var city=h.getLevel();var census=city.removeAttached(NapoliCivicLife.CENSUS);var sightings=city.removeAttached(CityDialogue.SIGHTINGS);var actors=new ArrayList<NapoliResident>();
        var people=new ArrayList<NapoliCivicLife.Citizen>();var pos=h.absolutePos(new BlockPos(1,3,1));
        try{
            for(var point:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(6,3,9)))city.setBlock(point,point.getY()==pos.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            for(int i=0;i<40;i++){var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,pos.offset(i%5,0,i/5),0);npc.setNoAi(true);actors.add(npc);people.add(citizen(city,npc.getUUID(),"Vicino "+i));}
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.copyOf(people)));
            for(int i=0;i<3;i++)CityDialogue.observe(city);
            var memories=city.getAttachedOrElse(CityDialogue.SIGHTINGS,Map.of());
            for(var actor:actors)h.assertTrue(memories.keySet().stream().anyMatch(key->key.startsWith(actor.getUUID()+"/")),"Later entities are not starved by the global observation budget: "+actor.getUUID());
        }finally{actors.forEach(NapoliResident::discard);restore(city,NapoliCivicLife.CENSUS,census);restore(city,CityDialogue.SIGHTINGS,sightings);}h.succeed();
    }
    @GameTest public void sightingsUseGameMinutesAndDoNotInventNamesOrLocations(GameTestHelper h){
        var city=h.getLevel();var census=city.removeAttached(NapoliCivicLife.CENSUS);var saved=city.removeAttached(CityDialogue.SIGHTINGS);UUID speaker=UUID.randomUUID(),subject=UUID.randomUUID();
        try{
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(citizen(city,subject,"Ciro Romano"))));
            city.setAttached(CityDialogue.SIGHTINGS,Map.of(speaker+"/"+subject,new CityDialogue.Sighting(new BlockPos(-417,64,-28),city.getGameTime()-1000,"WORK")));
            h.assertTrue(CityDialogue.where(city,speaker,subject).contains("60 minuti di gioco fa"),"One thousand ticks are one game hour");
            h.assertTrue(CityDialogue.whereNamed(city,speaker,"Ciro Romano").contains("Ciro Romano")&&CityDialogue.whereNamed(city,UUID.randomUUID(),"Ciro Romano").startsWith("Non lo so"),"A natural name query uses only the speaker's own sightings");
        }finally{restore(city,NapoliCivicLife.CENSUS,census);restore(city,CityDialogue.SIGHTINGS,saved);}h.succeed();
    }
    static NapoliCivicLife.Citizen citizen(net.minecraft.server.level.ServerLevel city,UUID id,String name){return new NapoliCivicLife.Citizen(id,name,false,NapoliBroadcast.day(city)-30L*CityLifeConfig.settings.daysPerYear,64,-1,"","public_0","",true);}
    private static <T> void restore(net.minecraft.server.level.ServerLevel city,AttachmentType<T> type,T value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
}
