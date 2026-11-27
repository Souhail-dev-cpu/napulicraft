package dev.napuli;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Disposable real-client fixture; never used without the explicit pickpocket test flag. */
public final class PickpocketClientFixture {
    public static volatile boolean ready;
    public static UUID npcId, victimId;
    private static NapoliResident npc;
    private static ServerPlayer victim;
    private static EmbeddedChannel channel;
    private static int npcItems;

    public static void prepare(ServerPlayer thief) {
        if (!Boolean.getBoolean("napuli.pickpockettest")) throw new IllegalStateException("Fixture disabled");
        var world = thief.level().getServer().overworld();
        for (var at : BlockPos.betweenClosed(-4, 79, -4, 5, 79, 5)) world.setBlock(at, Blocks.STONE.defaultBlockState(), 2);
        thief.setGameMode(GameType.CREATIVE); thief.getInventory().clearContent();
        thief.teleportTo(world, .5, 80, .5, Set.of(), -90, 0, true);
        npc = NapoliResident.spawn(world, NapoliResident.Role.TERESA, new BlockPos(2, 80, 0), 0); npc.setNoAi(true);
        npc.setYRot(-90);npc.setYHeadRot(-90);
        npcId = npc.getUUID(); npcItems = count(npc);
        var server = world.getServer(); var profile = new GameProfile(UUID.randomUUID(), "PocketVictim");
        victim = new ServerPlayer(server, world, profile, ClientInformation.createDefault()); victimId = victim.getUUID();
        var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, victim, CommonListenerCookie.createInitial(profile, false));
        ready = true;
    }
    public static void npcLimit(ServerPlayer thief) {
        check(Pickpocket.daily(npc, Pickpocket.inventory(npc)).stolen() == 5 && count(npc) == npcItems - 5 && count(thief) == 5,
                "The real NPC interaction transfers exactly five saved pocket items");
    }
    public static void prepareVictim(ServerPlayer thief) {
        victim.teleportTo(thief.level(), .5, 80, 2.5, Set.of(), 0, 0, true);victim.setYHeadRot(0);
        victim.getInventory().clearContent(); victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
        victim.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.EMERALD, 3));
        victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }
    public static void playerLimit(ServerPlayer thief) {
        check(Pickpocket.daily(victim, Pickpocket.inventory(victim)).stolen() == 5 && count(victim) == 63 && count(thief) == 10,
                "The real player interaction transfers five individual units and preserves the other 63");
        victim.teleportTo(victim.level(), 30.5, 80, 30.5, Set.of(), 0, 0, true);
    }
    public static void cleanup(ServerPlayer thief) {
        if (npc != null) npc.discard();
        if (victim != null) thief.level().getServer().getPlayerList().remove(victim);
        if (channel != null) channel.finishAndReleaseAll();
    }
    private static int count(net.minecraft.world.entity.LivingEntity target) { return Pickpocket.inventory(target).stream().mapToInt(s -> s.get().getCount()).sum(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private PickpocketClientFixture() {}
}
