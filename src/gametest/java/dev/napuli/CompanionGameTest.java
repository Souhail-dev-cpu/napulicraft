package dev.napuli;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class CompanionGameTest {
    @GameTest public void destinationCommandsAcceptNamespacedAtlasIds(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();
        try {
            for(String command:List.of("compagno preferito luogo/vanvitelli","compagno luogo fermata/sannazaro","compagno segui bottega/centro","compagno fissa pizza")) {
                var parsed=h.getLevel().getServer().getCommands().getDispatcher().parse(command,p.createCommandSourceStack());
                h.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),"The command parser accepts complete atlas/service ids: "+command);
            }
        } finally {h.getLevel().getServer().getPlayerList().remove(p);}h.succeed();
    }
    @GameTest public void searchHandlesAccentsAndMultipleWords(GameTestHelper h) {
        var entry=new CompanionBoard.Entry("Caffè di quartiere","Bottega · Sanità · aperto","");
        h.assertTrue(CompanionBoard.matches(entry,"CAFFE sanita"),"Accents and case do not prevent finding a service");
        h.assertTrue(!CompanionBoard.matches(entry,"caffe chiuso"),"Every search term must match");
        h.assertTrue(CompanionBoard.matches(entry,"  "),"An empty query shows all entries");h.succeed();
    }
    @GameTest public void shoppingCountsRepeatedIngredientsAndUpdatesAfterPickup(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();
        try {
            var r=new NapoliKitchen.Recipe("test_bread",Items.BREAD,1,List.of(Items.WHEAT,Items.WHEAT,Items.WHEAT),NapoliKitchen.Kind.TABLE,false);
            p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.WHEAT,2));
            h.assertTrue(CompanionRecipes.ingredients(r).get(Items.WHEAT)==3,"Repeated ingredient slots require three wheat, not one");
            h.assertTrue(CompanionRecipes.summary(p,r).contains("2/3"),"The list reports actual inventory contents");
            p.getInventory().add(new ItemStack(Items.WHEAT));
            h.assertTrue(CompanionRecipes.summary(p,r).contains("3/3"),"Pickup immediately changes the shopping list");
            p.getInventory().removeItem(0,2);
            h.assertTrue(CompanionRecipes.summary(p,r).contains("1/3"),"Spending ingredients is reflected as well");
        } finally {h.getLevel().getServer().getPlayerList().remove(p);}h.succeed();
    }
    @GameTest public void invitationsRequireTheCorrectRecipientAndExpire(GameTestHelper h) {
        var recipient=UUID.randomUUID();var goal=new CityCompanion.Goal("Piazza Dante",new BlockPos(0,64,-181));
        var invite=new CompanionMeetups.Invitation(UUID.randomUUID(),UUID.randomUUID(),recipient,goal,2400);
        h.assertTrue(CompanionMeetups.mayAccept(invite,recipient,2399),"The intended recipient can accept an active invitation");
        h.assertTrue(!CompanionMeetups.mayAccept(invite,UUID.randomUUID(),100),"Another player cannot claim the invitation");
        h.assertTrue(!CompanionMeetups.mayAccept(invite,recipient,2400),"An expired invitation cannot change a destination");
        h.assertTrue(!CompanionMeetups.mayAccept(null,recipient,100),"Missing invitations are safe");
        h.assertTrue(CompanionMeetups.uuid("not-a-token")==null,"Malformed invitation ids are rejected");h.succeed();
    }
    @GameTest public void menuPayloadPreservesActionsAndLocalizedText(GameTestHelper h) {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var entry=new CompanionBoard.Entry(Component.literal("Piazza Vanvitelli"),Component.literal("Fermata · M1"),"compagno luogo fermata/vanvitelli","★","compagno preferito fermata/vanvitelli");
            var packet=new CompanionBoard("atlante",Component.literal("Atlante"),List.of(entry));
            CompanionBoard.CODEC.encode(buf,packet);var restored=CompanionBoard.CODEC.decode(buf);
            h.assertTrue(restored.equals(packet),"The client receives intact labels, actions and favorite controls");
        } finally {buf.release();}h.succeed();
    }
    @GameTest public void departureDirectionsFollowTheNextActualStop(GameTestHelper h) {
        for(var line:CityNetwork.lines()){
            var frames=CityNetwork.plan(line).frames();
            for(int i=0;i<frames.size();i++)if(!frames.get(i).stop().isEmpty()){
                var next=CompanionTravel.nextStop(line,i);
                h.assertTrue(CityNetwork.stop(next)!=null,"A departure points to an actual stop");
                h.assertTrue(!next.equals(frames.get(i).stop()),"Both directions advance to another stop, including terminal turnarounds");
            }
        }h.succeed();
    }
    @GameTest public void invalidRecipeAndNavigationNeverSpendOrMoveThePlayer(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();
        try {
            var before=p.position();int wallet=NapoliStreet.cash(p);var inventory=p.getInventory().getItem(0).copy();
            h.assertTrue(CompanionRecipes.pin(p,"does_not_exist")==0,"Invalid recipes cannot become tracked recipes");
            h.assertTrue(CityCompanion.follow(p,"luogo/dante")==0,"A player outside Napoli cannot start a city route");
            h.assertTrue(p.position().equals(before)&&NapoliStreet.cash(p)==wallet&&ItemStack.matches(inventory,p.getInventory().getItem(0)),"Read-only navigation and rejected actions do not change position, wallet or inventory");
        } finally {h.getLevel().getServer().getPlayerList().remove(p);}h.succeed();
    }
}
