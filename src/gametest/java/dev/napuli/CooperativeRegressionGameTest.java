package dev.napuli;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;

public final class CooperativeRegressionGameTest {
    @GameTest public void successorCanCancelAndRefundOnlyTheOriginalContributors(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,4,ps->{var city=h.getLevel();var saved=city.getAttachedOrElse(CooperativeJobs.PROJECTS,Map.of());var groups=city.getAttachedOrElse(CompanionMeetups.GROUPS,Map.of());var fund=city.getAttached(WorkEconomy.FUND);
            try {
                var a=ps.get(0);var b=ps.get(1);var successor=ps.get(2);var other=ps.get(3);
                var group=new CompanionMeetups.Group(UUID.randomUUID(),a.getUUID(),new CityCompanion.Goal("Work",BlockPos.ZERO),ps.stream().map(ServerPlayer::getUUID).toList(),Set.of(),1);
                CompanionMeetups.put(city,group);city.setAttached(WorkEconomy.FUND,76);
                var materials=new ItemStack(Items.WHEAT,3);var project=new CooperativeJobs.Project(UUID.randomUUID(),group.id(),a.getUUID(),"supply","forno",BlockPos.ZERO,16,24,"active",List.of(a.getUUID(),b.getUUID()),Map.of(a.getUUID().toString(),new CooperativeJobs.Contribution(3,List.of(materials),false,false)));
                CooperativeJobs.put(city,project);CompanionMeetups.leave(city,a.getUUID());CompanionMeetups.leave(city,b.getUUID());
                h.assertTrue(CooperativeJobs.managed(successor,project)&&CooperativeJobs.visible(successor,project),"The successor can see the old project even without joining its original roster");
                h.assertTrue(CooperativeJobs.execute(other,"annulla/"+project.id())==0,"A regular new member cannot cancel another member's project");
                h.assertTrue(CooperativeJobs.execute(successor,"annulla/"+project.id())==1,"The legitimate successor can cancel through the actual action dispatcher");
                var cancelled=city.getAttachedOrElse(CooperativeJobs.PROJECTS,Map.of()).get(project.id().toString());
                h.assertTrue(cancelled.state().equals("cancelled")&&WorkEconomy.fund(city)==100,"Cancellation releases the originally reserved municipal reward");
                CooperativeJobs.execute(successor,"annulla/"+project.id());h.assertTrue(WorkEconomy.fund(city)==100,"Repeated cancellation cannot refund the fund twice");
                h.assertTrue(!CooperativeJobs.claim(successor,cancelled)&&CooperativeJobs.claim(a,cancelled),"The successor gains management rights while original contributors keep their own refunds");
                h.assertTrue(a.getInventory().countItem(Items.WHEAT)==3&&!CooperativeJobs.claim(a,cancelled),"The original participant reclaims materials once after leaving the group");
                h.assertTrue(CooperativeJobs.board(successor,0).entries().stream().anyMatch(e->e.command().equals("compagno cooperativa crea/supply")),"After cancellation the successor can start another job");
            } finally {city.setAttached(CooperativeJobs.PROJECTS,saved);city.setAttached(CompanionMeetups.GROUPS,groups);if(fund==null)city.removeAttached(WorkEconomy.FUND);else city.setAttached(WorkEconomy.FUND,fund);}
        });h.succeed();
    }

    @GameTest public void successorManagementDoesNotGrantDeliveryOrRewardEligibility(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,2,ps->{var city=h.getLevel();var saved=city.getAttachedOrElse(CooperativeJobs.PROJECTS,Map.of());var groups=city.getAttachedOrElse(CompanionMeetups.GROUPS,Map.of());
            try {
                var leader=ps.get(0);var other=ps.get(1);var original=UUID.randomUUID();var group=new CompanionMeetups.Group(UUID.randomUUID(),leader.getUUID(),new CityCompanion.Goal("Work",WorkResources.DEPOT),List.of(leader.getUUID(),other.getUUID()),Set.of(),1);CompanionMeetups.put(city,group);
                var project=new CooperativeJobs.Project(UUID.randomUUID(),group.id(),original,"courier","garage",WorkResources.DEPOT,1,8,"active",List.of(original),Map.of());CooperativeJobs.put(city,project);
                var at=WorkResources.DEPOT;leader.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                h.assertTrue(CooperativeJobs.execute(leader,"ritira/"+project.id())==0&&!city.getAttachedOrElse(CooperativeJobs.PROJECTS,Map.of()).get(project.id().toString()).contribution(leader.getUUID()).carrying(),"The successor cannot collect a parcel reserved for the original participant");
                h.assertTrue(CooperativeJobs.execute(leader,"contribuisci/"+project.id())==0,"Managing the project does not change its saved contributor roster");
                var completed=project.with(original,new CooperativeJobs.Contribution(1,List.of(),false,false)).state("completed");CooperativeJobs.put(city,completed);
                h.assertTrue(!CooperativeJobs.claim(leader,completed),"The successor cannot claim another player's earned reward");
            } finally {city.setAttached(CooperativeJobs.PROJECTS,saved);city.setAttached(CompanionMeetups.GROUPS,groups);}
        });h.succeed();
    }

    @GameTest public void largeCooperativeHistoryPagesRoundTripWithoutLosingProjectsOrClaims(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,2,ps->{var p=ps.getFirst();var city=h.getLevel();var saved=city.getAttachedOrElse(CooperativeJobs.PROJECTS,Map.of());var groups=city.getAttachedOrElse(CompanionMeetups.GROUPS,Map.of());
            try {
                var group=new CompanionMeetups.Group(UUID.randomUUID(),p.getUUID(),new CityCompanion.Goal("Work",BlockPos.ZERO),ps.stream().map(ServerPlayer::getUUID).toList(),Set.of(),1);CompanionMeetups.put(city,group);
                for(int count:new int[]{0,511,1305}) {
                    var projects=new HashMap<String,CooperativeJobs.Project>();
                    for(int i=0;i<count;i++) {var id=new UUID(0,i+1);projects.put(id.toString(),new CooperativeJobs.Project(id,group.id(),p.getUUID(),"supply","forno",BlockPos.ZERO,16,24,"completed",group.members(),Map.of(p.getUUID().toString(),new CooperativeJobs.Contribution(16,List.of(),false,i!=count-1))));}
                    city.setAttached(CooperativeJobs.PROJECTS,Map.copyOf(projects));
                    int pages=Math.max(1,(count+CooperativeJobs.PAGE_SIZE-1)/CooperativeJobs.PAGE_SIZE);var seen=new HashSet<String>();
                    for(int page=0;page<pages;page++) {
                        var board=CooperativeJobs.board(p,page);h.assertTrue(board.entries().size()<=CompanionBoard.MAX_ENTRIES,"Every history page stays inside the actual packet limit");
                        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),city.registryAccess());
                        try {CompanionBoard.CODEC.encode(buffer,board);h.assertTrue(CompanionBoard.CODEC.decode(buffer).equals(board),"The client decodes the complete page and all its actions");} finally {buffer.release();}
                        for(var entry:board.entries())if(entry.command().startsWith("compagno cooperativa vedi/"))h.assertTrue(seen.add(entry.command().substring("compagno cooperativa vedi/".length())),"Each project appears exactly once across stable pages");
                        if(page+1<pages){final String command="compagno cooperative "+(page+1);h.assertTrue(board.entries().stream().anyMatch(e->e.command().equals(command)),"History exposes a next-page action");var parsed=city.getServer().getCommands().getDispatcher().parse(command,p.createCommandSourceStack());h.assertTrue(!parsed.getReader().canRead()&&parsed.getExceptions().isEmpty(),"The server accepts the actual page navigation command");}
                    }
                    h.assertTrue(seen.equals(projects.keySet())&&city.getAttachedOrElse(CooperativeJobs.PROJECTS,Map.of()).size()==count,"Pagination retains all saved projects, including old completed jobs");
                    h.assertTrue(CooperativeJobs.board(p,Integer.MAX_VALUE).equals(CooperativeJobs.board(p,pages-1))&&CooperativeJobs.board(p,-1).equals(CooperativeJobs.board(p,0)),"Stale or out-of-range page requests resolve to valid boundaries");
                    if(count>0) {NapoliStreet.credit(p,-NapoliStreet.cash(p));var due=projects.get(new UUID(0,count).toString());h.assertTrue(CooperativeJobs.claim(p,due)&&NapoliStreet.cash(p)==24&&!CooperativeJobs.claim(p,due),"An old unclaimed reward remains payable exactly once after browsing the history");}
                }
            } finally {city.setAttached(CooperativeJobs.PROJECTS,saved);city.setAttached(CompanionMeetups.GROUPS,groups);}
        });h.succeed();
    }

    @GameTest public void companionBoardCodecRejectsOversizedPacketsBeforeWriting(GameTestHelper h) {
        var entries=Collections.nCopies(CompanionBoard.MAX_ENTRIES+1,new CompanionBoard.Entry("Item","Detail",""));var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            boolean rejected=false;try {CompanionBoard.CODEC.encode(buffer,new CompanionBoard("test",Component.literal("Test"),entries));}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected&&buffer.writerIndex()==0,"The encoder cannot emit a packet that its own decoder rejects");
            buffer.writeUtf("test",64);net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer,Component.literal("Test"));buffer.writeVarInt(CompanionBoard.MAX_ENTRIES+1);
            rejected=false;try {CompanionBoard.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected,"The client keeps its existing bound on incoming menu sizes");
        } finally {buffer.release();}h.succeed();
    }
}
