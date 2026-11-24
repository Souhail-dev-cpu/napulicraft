package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class CityConvenienceGameTest {
    @GameTest public void navigatorRoutesAroundWallsAndNeverLoadsItsRemoteGoal(GameTestHelper h){
        var city=h.getLevel();var base=h.absolutePos(new BlockPos(0,200,0));
        for(var at:BlockPos.betweenClosed(base,base.offset(12,0,12)))city.setBlock(at,Blocks.STONE.defaultBlockState(),18);
        for(var at:BlockPos.betweenClosed(base.above(),base.offset(12,4,12)))city.setBlock(at,Blocks.AIR.defaultBlockState(),18);
        for(var at:BlockPos.betweenClosed(base.offset(6,1,0),base.offset(6,3,9)))city.setBlock(at,Blocks.STONE.defaultBlockState(),18);
        var start=base.offset(2,1,3);var goal=base.offset(10,1,3);var result=PlayerNavigator.search(city,start,goal,4096);
        h.assertTrue(result.reached(),"The navigator finds a real detour instead of pointing through the wall");
        h.assertTrue(result.path().stream().anyMatch(p->p.getZ()>=base.getZ()+10),"The route uses the opening at the wall's end");
        for(var p:result.path())h.assertTrue(PlayerNavigator.free(city,p),"Every route position has support and head clearance");
        var remote=new BlockPos(3000000,start.getY(),3000000);h.assertTrue(!city.hasChunkAt(remote),"The remote destination starts unloaded");
        PlayerNavigator.search(city,start,remote,128);h.assertTrue(!city.hasChunkAt(remote),"Navigation does not generate a distant chunk");h.succeed();
    }
    @GameTest public void navigatorUsesStairsAndRejectsHeadObstructions(GameTestHelper h){
        var city=h.getLevel();var base=h.absolutePos(new BlockPos(0,210,0));
        for(var p:BlockPos.betweenClosed(base,base.offset(8,6,2)))city.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        for(int x=0;x<=8;x++)for(int z=0;z<=2;z++)for(int y=0;y<=Math.min(3,x/2);y++)city.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),18);
        var start=base.offset(0,1,1);var goal=base.offset(8,4,1);var path=PlayerNavigator.search(city,start,goal,1024);
        h.assertTrue(path.reached()&&path.path().stream().anyMatch(p->p.getY()>start.getY()),"A stepped route reaches the upper landing");
        city.setBlock(goal.above(),Blocks.STONE.defaultBlockState(),18);
        h.assertTrue(!PlayerNavigator.search(city,start,goal,1024).reached(),"A blocked head space cannot be advertised as an accessible arrival");h.succeed();
    }
    @GameTest public void walletHistoryRecordsActualAmountsAndSurvivesSerialization(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();try{
            NapoliStreet.credit(p,-99999);p.setAttached(PlayerLedger.HISTORY,List.of());
            NapoliStreet.credit(p,Integer.MAX_VALUE,"wage");NapoliStreet.credit(p,100,"wage");NapoliStreet.credit(p,-50,"fine");
            var history=p.getAttachedOrElse(PlayerLedger.HISTORY,List.of());
            h.assertTrue(history.size()==2&&history.getFirst().amount()==-50&&history.getFirst().balance()==99949,"Only actual wallet changes create records");
            h.assertTrue(history.get(1).amount()==99999&&history.get(1).reason().equals("wage"),"The cap and transaction reason are preserved");
            var encoded=PlayerLedger.Transaction.CODEC.listOf().encodeStart(JsonOps.INSTANCE,history).getOrThrow();
            h.assertTrue(PlayerLedger.Transaction.CODEC.listOf().parse(JsonOps.INSTANCE,encoded).getOrThrow().equals(history),"Saved history restores exact amounts and resulting balances");
            for(int i=0;i<110;i++)NapoliStreet.credit(p,-1,"purchase");h.assertTrue(p.getAttachedOrElse(PlayerLedger.HISTORY,List.of()).size()==100,"History stays bounded");
        }finally{h.getLevel().getServer().getPlayerList().remove(p);}h.succeed();
    }
    @GameTest public void groupSavePreservesMembersDestinationAndLeaderSuccession(GameTestHelper h){
        var city=h.getLevel();var first=UUID.randomUUID();var second=UUID.randomUUID();var group=new CompanionMeetups.Group(UUID.randomUUID(),first,new CityCompanion.Goal("Dante",new BlockPos(0,64,-181)),List.of(first,second),Set.of(second),4);
        try{
            var encoded=CompanionMeetups.Group.CODEC.encodeStart(JsonOps.INSTANCE,group).getOrThrow();var restored=CompanionMeetups.Group.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
            CompanionMeetups.put(city,restored);h.assertTrue(CompanionMeetups.group(city,second).equals(group),"Restart serialization preserves membership, goal, arrivals and revision");
            var token=UUID.randomUUID();var invites=new HashMap<>(city.getAttachedOrElse(CompanionMeetups.INVITES,Map.of()));invites.put(token.toString(),new CompanionMeetups.Invitation(token,first,UUID.randomUUID(),group.goal(),city.getGameTime()+2400));city.setAttached(CompanionMeetups.INVITES,Map.copyOf(invites));
            CompanionMeetups.leave(city,first);var remaining=CompanionMeetups.group(city,second);
            h.assertTrue(!city.getAttachedOrElse(CompanionMeetups.INVITES,Map.of()).containsKey(token.toString()),"Leaving cancels outgoing invites so they cannot silently re-add the former leader");
            h.assertTrue(remaining.leader().equals(second)&&!remaining.members().contains(first)&&remaining.goal().equals(group.goal()),"Leadership moves to a remaining member without losing the route");
            CompanionMeetups.leave(city,second);h.assertTrue(CompanionMeetups.group(city,second)==null,"The final member leaving removes the group");
        }finally{var all=new HashMap<>(city.getAttachedOrElse(CompanionMeetups.GROUPS,Map.of()));all.remove(group.id().toString());city.setAttached(CompanionMeetups.GROUPS,Map.copyOf(all));}h.succeed();
    }
    @GameTest public void housingQuotesExpireAndRejectChangedConditions(GameTestHelper h){
        var q=new CompanionHousing.Quote("token","home","mutuo",20,"snapshot",1200);
        h.assertTrue(CompanionHousing.valid(q,"token",1199,"snapshot"),"An unchanged quote can be confirmed before its deadline");
        h.assertTrue(!CompanionHousing.valid(q,"another",100,"snapshot")&&!CompanionHousing.valid(q,"token",1200,"snapshot")&&!CompanionHousing.valid(q,"token",100,"changed"),"Wrong tokens, expiry and ownership/account changes prevent stale confirmations");
        for(var house:NapoliMergellina.PROPERTIES){var terms=CompanionHousing.terms(house,false);h.assertTrue(terms.total()==terms.deposit()+terms.principal()&&terms.installment()*12>=terms.principal(),"The displayed mortgage total includes the financed interest and covers its installments");}h.succeed();
    }
    @GameTest public void aPaidShiftDoesNotDirectThePlayerBackToClockIn(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();try{long day=NapoliBroadcast.day(p.level());p.setAttached(NapoliEmployment.WORK,new NapoliEmployment.Work("carpenter",day,0,day));
            h.assertTrue(CompanionWork.finished(p)&&CompanionWork.summary(p).equals(CityText.t("work.finished")),"A paid shift explicitly says to return tomorrow");
            p.setAttached(NapoliEmployment.WORK,new NapoliEmployment.Work("carpenter",day,0,day-1));h.assertTrue(!CompanionWork.finished(p),"Yesterday's receipt does not block the next shift");
        }finally{h.getLevel().getServer().getPlayerList().remove(p);}h.succeed();
    }
    @GameTest public void oldSavedWaypointsMigrateWithoutChangingTheirCoordinates(GameTestHelper h){
        var json=com.google.gson.JsonParser.parseString("{\"name\":\"Dante\",\"position\":[0,64,-181]}");
        var goal=CityCompanion.Goal.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();h.assertTrue(goal.position().equals(new BlockPos(0,64,-181))&&goal.label().getString().equals("Dante"),"Waypoints saved before translated labels remain readable");h.succeed();
    }
    @GameTest public void unloadedLocalTaskGiversStillHaveJournalDestinations(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();try{for(var job:NapoliLocalJobs.Job.values())h.assertTrue(NapoliLocalJobs.journalTarget(p,job)!=null,"Every local task can guide the player to its authored address even before the NPC loads");}finally{h.getLevel().getServer().getPlayerList().remove(p);}h.succeed();
    }
}
