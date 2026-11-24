package dev.napuli;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MotorinoGameTest {
    @GameTest
    public void boardingOrderAndDriverPromotion(GameTestHelper helper) {
        withPlayers(helper, 4, players -> {
            Motorino bike = helper.spawn(Napuli.MOTORINO, 1, 2, 1);
            for (int i = 0; i < 3; i++) {
                check(helper, bike.mobInteract(players.get(i), InteractionHand.MAIN_HAND).consumesAction(), "Board rider " + i);
                check(helper, bike.getPassengers().get(i) == players.get(i), "Boarding order " + i);
                check(helper, bike.getControllingPassenger() == players.getFirst(), "First rider keeps control");
            }
            check(helper, !players.get(3).startRiding(bike), "A fourth rider cannot mount directly");
            bike.mobInteract(players.get(3), InteractionHand.MAIN_HAND);
            check(helper, !players.get(3).isPassenger() && bike.getPassengers().size() == 3, "Fourth interaction is rejected");
            assertPacketOrder(helper, bike, players.subList(0, 3));
            check(helper, Radio.tune(players.getFirst(), 4), "Driver can tune");
            check(helper, !Radio.tune(players.get(1), 8) && !Radio.tune(players.get(2), Radio.OFF), "Passengers cannot tune");
            check(helper, !Radio.tune(players.get(3), 8), "Bystander cannot tune");
            check(helper, !Radio.tune(players.getFirst(), Radio.PARADE) && !Radio.tune(players.getFirst(), -2)
                    && !Radio.tune(players.getFirst(), Radio.MAX_STATIONS), "Invalid stations are rejected");
            check(helper, bike.station() == 4, "Invalid input leaves station unchanged");

            players.getFirst().stopRiding();
            check(helper, bike.getControllingPassenger() == players.get(1), "Second rider becomes driver");
            check(helper, bike.station() == 4 && Radio.tune(players.get(1), 6), "New driver inherits the radio");
            check(helper, !Radio.tune(players.getFirst(), 2), "Former driver loses authority");
            check(helper, players.get(3).startRiding(bike), "New rider joins at the back");
            assertPacketOrder(helper, bike, List.of(players.get(1), players.get(2), players.get(3)));
            players.get(2).stopRiding();
            check(helper, bike.getPassengers().equals(List.of(players.get(1), players.get(3))), "Middle dismount preserves order");
            // Entity removal is also the vanilla path used when a player disconnects.
            players.get(1).remove(Entity.RemovalReason.DISCARDED);
            check(helper, bike.getControllingPassenger() == players.get(3), "Disconnect promotes the remaining rider");
            players.get(3).stopRiding();
            check(helper, bike.station() == Radio.OFF && bike.getControllingPassenger() == null, "Empty bike turns off");
            bike.discard();
        });
        helper.succeed();
    }

    @GameTest
    public void seatsRotateInTheSameOrder(GameTestHelper helper) {
        withPlayers(helper, 3, players -> {
            Motorino bike = helper.spawn(Napuli.MOTORINO, 1, 2, 1);
            for (ServerPlayer player : players) player.startRiding(bike);
            for (float yaw : new float[] {0, 90, 180, -90}) {
                bike.setYRot(yaw);
                for (ServerPlayer player : players) bike.positionRider(player);
                double radians = Math.toRadians(yaw);
                double previous = Double.POSITIVE_INFINITY;
                for (ServerPlayer player : players) {
                    double along = (player.getX() - bike.getX()) * -Math.sin(radians)
                            + (player.getZ() - bike.getZ()) * Math.cos(radians);
                    check(helper, along < previous, "Driver, middle and rear stay ordered at yaw " + yaw);
                    check(helper, Math.abs(player.getY() - bike.getY() - 0.30) < 0.001, "Seat height");
                    previous = along;
                }
            }
            bike.discard();
        });
        helper.succeed();
    }

    @GameTest
    public void pickupCannotEjectOrLoseTheBike(GameTestHelper helper) {
        withPlayers(helper, 2, players -> {
            Motorino bike = helper.spawn(Napuli.MOTORINO, 1, 2, 1);
            ServerPlayer driver = players.getFirst(), bystander = players.get(1);
            driver.startRiding(bike);
            bystander.setShiftKeyDown(true);
            bike.mobInteract(bystander, InteractionHand.MAIN_HAND);
            check(helper, !bike.isRemoved() && driver.getVehicle() == bike, "Occupied bike cannot be picked up");
            driver.stopRiding();
            bike.mobInteract(bystander, InteractionHand.MAIN_HAND);
            check(helper, !bike.isRemoved(), "A bystander cannot pack up an owned bike");
            driver.setShiftKeyDown(true); driver.getAbilities().instabuild = false;
            for (int i = 0; i < 36; i++) driver.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            bike.mobInteract(driver, InteractionHand.MAIN_HAND);
            check(helper, !bike.isRemoved(), "Full inventory does not delete the bike");
            driver.getInventory().setItem(0, ItemStack.EMPTY);
            bike.mobInteract(driver, InteractionHand.MAIN_HAND);
            check(helper, bike.isRemoved() && driver.getInventory().getItem(0).is(Napuli.MOTORINO_ITEM), "Empty bike is returned to its owner exactly once");
        });
        helper.succeed();
    }

    @GameTest
    public void paradeStateIsSynchronized(GameTestHelper helper) {
        withPlayers(helper, 1, players -> {
            Motorino parade = helper.spawn(Napuli.MOTORINO, 1, 2, 1);
            Motorino replica = new Motorino(Napuli.MOTORINO, helper.getLevel());
            parade.startParade();
            replica.getEntityData().assignValues(parade.getEntityData().getNonDefaultValues());
            check(helper, replica.inParade() && replica.station() == Radio.PARADE, "Tracking clients receive parade and radio state");
            check(helper, !players.getFirst().startRiding(parade), "Players cannot join a scripted parade");
            check(helper, parade.getControllingPassenger() == null, "Scripted parade has no player driver");
            parade.discard();
        });
        helper.succeed();
    }

    private static void assertPacketOrder(GameTestHelper helper, Motorino bike, List<ServerPlayer> expected) {
        int[] ids = new ClientboundSetPassengersPacket(bike).getPassengers();
        check(helper, ids.length == expected.size(), "Passenger packet count");
        for (int i = 0; i < ids.length; i++) check(helper, ids[i] == expected.get(i).getId(), "Network seat order " + i);
    }

    private static void withPlayers(GameTestHelper helper, int count, java.util.function.Consumer<List<ServerPlayer>> test) {
        List<ServerPlayer> players = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) players.add(helper.makeMockServerPlayerInLevel());
            test.accept(players);
        } finally {
            for (ServerPlayer player : players) {
                player.stopRiding();
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
