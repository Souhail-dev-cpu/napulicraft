package dev.napuli;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;

public final class HousingCapacityGameTest {
    private static int sequence;

    @GameTest public void playersAndNpcsShareFivePlacesAndSixthArrivalWaitsOutside(GameTestHelper helper) {
        withPlayers(helper, 2, players -> {
            var city = helper.getLevel();
            var floor = helper.absolutePos(new BlockPos(0, 40, 0));
            fixture(helper, floor);
            var home = new CityProperty.Home(floor, floor.offset(6, 5, 6));
            var npcs = new ArrayList<NapoliResident>();
            try {
                for (int i = 0; i < 4; i++) {
                    var npc = NapoliResident.spawn(city, NapoliResident.Role.CIVIC_MAN, floor.offset(i + 1, 1, 2), 0);
                    npc.setNoAi(true); npcs.add(npc);
                }
                var incumbent = players.getFirst();
                var arrival = players.getLast();
                incumbent.snapTo(floor.getX() + 2.5, floor.getY() + 1, floor.getZ() + 3.5, 0, 0);
                arrival.snapTo(floor.getX() + 2.5, floor.getY() + 1, floor.getZ() + 8.5, 0, 0);
                HousingCapacity.enforce(city);
                helper.assertTrue(!HousingCapacity.canEnter(city, floor.above(), arrival.getUUID()), "Four NPCs and one player fill the house");
                helper.assertTrue(HousingCapacity.canEnter(city, floor.above(), incumbent.getUUID()), "An occupant does not count twice when moving inside");
                arrival.snapTo(floor.getX() + 3.5, floor.getY() + 1, floor.getZ() + 3.5, 0, 0);
                HousingCapacity.enforce(city);
                helper.assertTrue(!home.contains(arrival.blockPosition()), "Teleporting a sixth occupant inside is rejected");
                helper.assertTrue(home.contains(incumbent.blockPosition()) && npcs.stream().allMatch(n -> home.contains(n.blockPosition())), "The original five occupants keep their places");
                npcs.getFirst().discard();
                helper.assertTrue(HousingCapacity.canEnter(city, floor.above(), arrival.getUUID()), "An exit immediately frees a place");
                arrival.snapTo(floor.getX() + 3.5, floor.getY() + 1, floor.getZ() + 3.5, 0, 0);
                HousingCapacity.enforce(city);
                helper.assertTrue(home.contains(arrival.blockPosition()), "The waiting player can enter once a place is free");
            } finally { for (var npc : npcs) npc.discard(); }
        });
        helper.succeed();
    }

    @GameTest public void sixNpcsCannotCrowdAHouseAndOtherHousesKeepTheirOwnCapacity(GameTestHelper helper) {
        var city = helper.getLevel();
        var floor = helper.absolutePos(new BlockPos(0, 70, 0));
        fixture(helper, floor);
        var second = floor.east(20); fixture(helper, second);
        var home = new CityProperty.Home(floor, floor.offset(6, 5, 6));
        var npcs = new ArrayList<NapoliResident>();
        try {
            for (int i = 0; i < 6; i++) {
                var npc = NapoliResident.spawn(city, NapoliResident.Role.CIVIC_WOMAN, floor.offset(1 + i % 3, 1, 1 + i / 3), 0);
                npc.setNoAi(true); npcs.add(npc);
            }
            HousingCapacity.enforce(city);
            helper.assertTrue(npcs.stream().filter(n -> home.contains(n.blockPosition())).count() == 5, "Spawned crowds are reduced to five NPCs");
            helper.assertTrue(HousingCapacity.canEnter(city, second.above(), npcs.getLast().getUUID()), "A full house does not block another house");
            helper.assertTrue(!HousingCapacity.canEnter(city, floor.above(), npcs.getLast().getUUID()), "The rejected NPC cannot immediately re-enter");
        } finally { for (var npc : npcs) npc.discard(); }
        helper.succeed();
    }

    @GameTest public void birthCapacityIncludesVisitorsWithoutCountingResidentsTwice(GameTestHelper helper) {
        withPlayers(helper, 2, players -> {
            var city = helper.getLevel(); var house = CityLivingHomes.PUBLIC_HOMES.getFirst();
            var npc = NapoliResident.spawn(city, NapoliResident.Role.CIVIC_MAN, house.inside(), 0);
            npc.setNoAi(true);
            try {
                long day = NapoliBroadcast.day(city);
                var family = new ArrayList<NapoliCivicLife.Citizen>();
                for (int i = 0; i < 4; i++) family.add(new NapoliCivicLife.Citizen(i == 0 ? npc.getUUID() : UUID.randomUUID(),
                        "Resident " + i, false, day - 22, 10, -1, "", house.id(), "", i == 0));
                var inside = house.inside();
                for (var player : players) player.snapTo(inside.getX() + .5, inside.getY(), inside.getZ() + .5, 0, 0);
                helper.assertTrue(HousingCapacity.reservedOccupants(city, house, family) == 6, "Four residents plus two guests reserve six places, even with one resident materialized");
                players.getLast().snapTo(house.entrance().getX() + .5, 64, house.entrance().getZ() + 1.5, 0, 0);
                helper.assertTrue(HousingCapacity.reservedOccupants(city, house, family) == HousingCapacity.MAX_OCCUPANTS, "Four residents and one guest leave no room for another birth");
            } finally { npc.discard(); }
        });
        helper.succeed();
    }

    @GameTest public void legacyLodgingAndBorgoHomesAlsoRejectTheSixthNpc(GameTestHelper helper) {
        var city = helper.getLevel();
        var positions = List.of(NapoliWestQuarter.WORKER_HOME.inside(), new BlockPos(22, 68, 87));
        var forced = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (var inside : positions) {
            var chunk = new net.minecraft.world.level.ChunkPos(Math.floorDiv(inside.getX(), 16), Math.floorDiv(inside.getZ(), 16));
            if (city.setChunkForced(chunk.x(), chunk.z(), true)) forced.add(chunk);
            city.getChunkAt(inside);
        }
        // These legacy buildings are far outside the test arena; wait for entity visibility.
        helper.runAfterDelay(20, () -> {
            try {
                for (var inside : positions) {
                    var npcs = new ArrayList<NapoliResident>();
                    try {
                        for (int i = 0; i < 5; i++) {
                            var npc = NapoliResident.spawn(city, NapoliResident.Role.PASSERBY, inside, 0);
                            npc.setNoAi(true); npcs.add(npc);
                        }
                        helper.assertTrue(!HousingCapacity.canEnter(city, inside, UUID.randomUUID()), "Existing home rejects a sixth occupant at " + inside);
                    } finally { for (var npc : npcs) npc.discard(); }
                }
            } finally { for (var chunk : forced) city.setChunkForced(chunk.x(), chunk.z(), false); }
            helper.succeed();
        });
    }

    private static void fixture(GameTestHelper helper, BlockPos floor) {
        var city = helper.getLevel();
        for (var pos : BlockPos.betweenClosed(floor.offset(-2, 0, -2), floor.offset(10, 0, 10))) city.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        CityProperty.recordHome(city, floor, floor.offset(6, 5, 6));
    }

    static void withPlayers(GameTestHelper helper, int count, Consumer<List<ServerPlayer>> body) {
        var players = new ArrayList<ServerPlayer>(); var channels = new ArrayList<EmbeddedChannel>();
        try {
            for (int i = 0; i < count; i++) {
                var city = helper.getLevel(); var server = city.getServer();
                var profile = new GameProfile(UUID.randomUUID(), "HousingTest" + sequence++);
                var player = new ServerPlayer(server, city, profile, ClientInformation.createDefault());
                var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection));
                server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
                players.add(player);
            }
            body.accept(players);
        } finally {
            for (var player : players) helper.getLevel().getServer().getPlayerList().remove(player);
            for (var channel : channels) channel.finishAndReleaseAll();
        }
    }
}
