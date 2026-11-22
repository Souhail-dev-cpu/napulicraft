package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A compact, explorable interpretation of the city inside its own dimension. */
public final class NapoliMap {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Napuli.id("napoli"));
    static final int STREET = 64;
    static final BlockPos CAFE = new BlockPos(16, 64, -25);
    static final BlockPos GALLERY = NapoliDistrict.GALLERIA; // the complete Galleria Umberto I (v3)
    static final BlockPos CRYPT = new BlockPos(-53, 58, -34);
    static final BlockPos WATERFRONT = new BlockPos(2, 64, 53);
    private static final AttachmentType<GlobalPos> RETURN = AttachmentRegistry.<GlobalPos>builder()
            .persistent(GlobalPos.CODEC).copyOnDeath().buildAndRegister(Napuli.id("napoli_return"));
    private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL).buildAndRegister(Napuli.id("napoli_built"));
    private static final BlockPos ENTRANCE = new BlockPos(0, 64, -37);
    private static final Block[] FACADES = {Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA,
            block("orange_terracotta"), block("yellow_terracotta"), block("white_terracotta")};

    private final ServerLevel world;

    private NapoliMap(ServerLevel world) { this.world = world; }

    /** a city built by an older version (or a world that starts inside the city) gets the new content too */
    /** used by the east district to put up more homes */
    static void buildHome(ServerLevel world, int x, int z, int index) { new NapoliMap(world).home(x, z, index); }

    static void upgradeIfBuilt(ServerLevel city) {
        if (city.getAttachedOrElse(BUILT, false)) NapoliInteriors.upgrade(city);
    }

    static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d) {
        d.register(net.minecraft.commands.Commands.literal("napoli")
                .executes(c -> visit(c.getSource().getPlayerOrException()))
                .then(net.minecraft.commands.Commands.literal("esci").executes(c -> leave(c.getSource().getPlayerOrException()))));
    }

    public static int visit(ServerPlayer player) {
        ServerLevel city = player.level().getServer().getLevel(LEVEL);
        if (city == null) {
            player.sendSystemMessage(Component.translatable("napuli.city.unavailable"));
            return 0;
        }
        if (player.level().dimension() == LEVEL) {
            player.sendSystemMessage(Component.translatable("napuli.city.guide"));
            return 1;
        }
        new NapoliMap(city).build();
        var origin = GlobalPos.of(player.level().dimension(), player.blockPosition());
        Music.stop(player);
        if (!player.teleportTo(city, ENTRANCE.getX() + 0.5, ENTRANCE.getY(), ENTRANCE.getZ() + 0.5,
                Set.of(), 0, 0, true)) return 0;
        player.setAttached(RETURN, origin);
        NapoliScenes.motorino(player);
        player.sendSystemMessage(Component.translatable("napuli.city.guide"));
        return 1;
    }

    private static final AttachmentType<Boolean> WELCOMED = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id("napoli_welcomed"));

    /**
     * You start in Napoli: the first time a player joins a world they are taken straight into the city,
     * and after dying without a bed you wake up in Napoli again (not in the overworld).
     */
    /** players waiting to be taken to Napoli, and the server tick when it's safe to do it */
    private static final java.util.Map<java.util.UUID, Integer> SEND_TO_NAPOLI = new java.util.HashMap<>();

    static void registerSpawn() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player.getAttachedOrElse(WELCOMED, false)) return;
            player.setAttached(WELCOMED, true);
            SEND_TO_NAPOLI.put(player.getUUID(), server.getTickCount() + 5);
        });
        // after a death without a bed you wake up in Napoli. Wait a moment: teleporting while the respawn
        // is still being set up makes the server add the player twice ("UUID already exists").
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            if (alive || player.getRespawnConfig() != null || player.level().dimension() == LEVEL) return;
            SEND_TO_NAPOLI.put(player.getUUID(), player.level().getServer().getTickCount() + 10);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (SEND_TO_NAPOLI.isEmpty()) return;
            var it = SEND_TO_NAPOLI.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (server.getTickCount() < e.getValue()) continue;
                it.remove();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null && p.isAlive() && p.level().dimension() != LEVEL) visit(p);
            }
        });
    }

    static int leave(ServerPlayer player) {
        if (player.level().dimension() != LEVEL) return 0;
        var origin = player.getAttached(RETURN);
        var server = player.level().getServer();
        ServerLevel destination = origin == null ? null : server.getLevel(origin.dimension());
        BlockPos spawn;
        if (destination == null || destination == player.level()) {
            destination = server.overworld();
            spawn = returnSpawn(destination);
        } else {
            spawn = origin.pos();
            if (!destination.getBlockState(spawn).getCollisionShape(destination, spawn).isEmpty()
                    || !destination.getBlockState(spawn.above()).getCollisionShape(destination, spawn.above()).isEmpty()) {
                destination = server.overworld();
                spawn = returnSpawn(destination);
            }
        }
        Music.stop(player);
        if (!player.teleportTo(destination, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                Set.of(), 0, 0, true)) return 0;
        player.removeAttached(RETURN);
        return 1;
    }

    private static BlockPos returnSpawn(ServerLevel level) {
        var data = level.getRespawnData();
        if (data.globalPos().dimension() == level.dimension()) return data.pos();
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
    }

    private void build() {
        if (world.getAttachedOrElse(BUILT, false)) { NapoliInteriors.upgrade(world); return; }
        Napuli.LOGGER.info("Building Napoli");
        fill(-73, 63, -66, 73, 63, 56, Blocks.STONE_BRICKS);
        fill(-112, 60, 57, 112, 63, 161, Blocks.WATER);
        fill(-73, 63, 48, 73, 63, 56, Blocks.SMOOTH_STONE);
        fill(-9, 63, -66, 9, 63, 56, Blocks.POLISHED_DEEPSLATE);
        fill(-73, 63, -4, 73, 63, 4, Blocks.POLISHED_DEEPSLATE);
        fill(-33, 63, 8, 31, 63, 46, Blocks.SMOOTH_STONE);
        for (int z = -64; z < -6; z += 6) fill(0, 63, z, 0, 63, z + 2, Blocks.CALCITE);
        for (int z = 12; z <= 43; z += 5) {
            fill(-30, 63, z, 27, 63, z, Blocks.POLISHED_ANDESITE);
            lamp(-32, z);
            lamp(31, z);
        }

        // The alleys: plaster, shutters, balconies and clotheslines.
        for (int row = 0; row < 3; row++) {
            int z = -62 + row * 18;
            for (int house = 0; house < 4; house++) {
                int x = -70 + house * 14;
                home(x, z, row * 4 + house);
                if (house < 3) {
                    fill(x + 11, 70, z + 3, x + 14, 70, z + 3, block("iron_chain"));
                    put(x + 12, 69, z + 3, house % 2 == 0 ? block("blue_wool") : block("red_wool"));
                    put(x + 13, 69, z + 3, block("white_wool"));
                }
            }
        }
        for (int rowZ : new int[]{-62, -44}) {
            for (int x : new int[]{-66, -52, -38, -24}) laundry(x, rowZ + 11, rowZ + 18);
        }
        for (int z = -62; z <= -43; z += 18)
            for (int x = 14; x <= 57; x += 14) home(x, z, x + z);
        for (int x = 49; x <= 65; x += 14)
            for (int z = 10; z <= 29; z += 18) home(x, z, x + z);

        cafe();
        gallery();
        piazza();
        presepio();
        crypt();
        waterfront();
        sign(0, 64, -42, "NAPULE", "Quartieri  ←", "Plebiscito  ↑", "Galleria  →");
        sign(11, 64, 47, "LUNGOMARE", "Castel dell'Ovo", "sul pontile");
        sign(-48, 64, -8, "NAPOLI", "SOTTERRANEA", "Scala nei vicoli");
        NapoliResident.spawn(world, NapoliResident.Role.TERESA, CAFE, 20);
        NapoliResident.spawn(world, NapoliResident.Role.LUCIA, GALLERY, 130);
        world.setAttached(BUILT, true);
        NapoliInteriors.upgrade(world);
        Napuli.LOGGER.info("Napoli built");
    }

    private void laundry(int x, int z1, int z2) {
        BlockState wire = block("iron_chain").defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Z);
        for (int z = z1; z <= z2; z++)
            world.setBlock(new BlockPos(x, 72, z), wire, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        put(x, 71, z1 + 2, block("white_wool"));
        put(x, 71, z1 + 3, block("white_wool"));
        put(x, 70, z1 + 3, block("white_wool"));
        put(x, 71, z1 + 5, block("blue_wool"));
    }

    private void home(int x, int z, int index) {
        int height = 12 + Math.floorMod(index, 4) * 3;
        Block facade = FACADES[Math.floorMod(index, FACADES.length)];
        fill(x, 64, z, x + 10, 63 + height, z + 11, facade);
        fill(x + 1, 64, z + 1, x + 9, 62 + height, z + 10, Blocks.AIR);
        fill(x - 1, 64 + height, z - 1, x + 11, 64 + height, z + 12, Blocks.BRICKS);
        fill(x + 4, 64, z + 11, x + 6, 66, z + 11, Blocks.AIR);
        put(x + 5, 66, z + 2, Blocks.LANTERN);
        for (int y = 68; y < 63 + height; y += 4) {
            for (int dx : new int[]{2, 5, 8}) {
                put(x + dx, y, z + 11, block("light_blue_stained_glass"));
                put(x + dx + 1, y, z + 11, Blocks.DARK_OAK_PLANKS);
            }
            for (int side : new int[]{x, x + 10}) {
                for (int dz : new int[]{3, 7}) {
                    put(side, y, z + dz, block("light_blue_stained_glass"));
                    put(side, y - 1, z + dz, Blocks.SMOOTH_SANDSTONE);
                }
            }
            fill(x + 1, y - 1, z + 12, x + 9, y - 1, z + 13, Blocks.BRICK_SLAB);
            fill(x + 1, y, z + 13, x + 9, y, z + 13, Blocks.IRON_BARS);
            put(x + 2, y, z + 12, Blocks.POTTED_FERN);
            put(x + 8, y, z + 12, Blocks.FLOWER_POT);
        }
    }

    private void cafe() {
        fill(13, 64, -32, 24, 70, -20, block("yellow_terracotta"));
        fill(14, 64, -31, 23, 69, -21, Blocks.AIR);
        fill(15, 64, -20, 20, 67, -20, Blocks.AIR);
        for (int x = 13; x <= 24; x++)
            fill(x, 68, -20, x, 68, -17, x % 2 == 0 ? block("red_wool") : block("white_wool"));
        fill(19, 64, -30, 22, 64, -28, Blocks.DARK_OAK_PLANKS);
        put(21, 65, -29, Blocks.CAKE);
        put(17, 65, -31, Blocks.LANTERN);
        for (int z = -30; z <= -22; z += 4) {
            fill(13, 66, z, 13, 67, z + 1, block("brown_stained_glass"));
            fill(13, 65, z, 13, 65, z + 1, Blocks.SMOOTH_SANDSTONE);
        }
        sign(13, 64, -18, "BAR 'O SOLE", "Tazzulella", "'e cafè");
    }

    private void gallery() {
        // Four iron barrel vaults meet above the mosaic under the central octagonal dome.
        int cx = 31, cz = -7;
        fill(13, 63, -16, 50, 63, 2, Blocks.POLISHED_DIORITE);
        fill(24, 63, -23, 38, 63, 7, Blocks.POLISHED_DIORITE);
        for (int x = 13; x <= 50; x++) {
            if (Math.abs(x - cx) <= 8) continue;
            for (int z : new int[]{-16, 2}) galleryBay(x, z, (x - 13) % 5, true);
            for (int d = -9; d <= 9; d++) {
                int roof = 76 + (int)(6 * Math.sqrt(1 - d * d / 81.0));
                put(x, roof, cz + d, (x - 13) % 5 == 0 || Math.abs(d) % 3 == 0
                        ? Blocks.IRON_BLOCK : Blocks.GLASS);
            }
        }
        for (int z = -23; z <= 7; z++) {
            if (Math.abs(z - cz) <= 8) continue;
            for (int x : new int[]{24, 38}) galleryBay(x, z, (z + 23) % 5, false);
            for (int d = -7; d <= 7; d++) {
                int roof = 76 + (int)(6 * Math.sqrt(1 - d * d / 49.0));
                put(cx + d, roof, z, (z + 23) % 5 == 0 || Math.abs(d) % 3 == 0
                        ? Blocks.IRON_BLOCK : Blocks.GLASS);
            }
        }
        // Squared-off clerestory over the crossing, with glass between the solid corner piers.
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
            int octagon = Math.max(Math.abs(dx), Math.abs(dz)) + Math.min(Math.abs(dx), Math.abs(dz)) / 2;
            if (octagon < 9 && (dx * dx + dz * dz) % 11 == 0)
                put(cx + dx, 63, cz + dz, block("cut_copper"));
            if (octagon == 10) {
                boolean support = Math.abs(dx) >= 7 && Math.abs(dz) >= 7;
                fill(cx + dx, support ? 64 : 79, cz + dz, cx + dx, 82, cz + dz,
                        support ? Blocks.CALCITE : block("light_blue_stained_glass"));
            }
            int distance = dx * dx + dz * dz;
            if (octagon <= 10 && distance < 98) {
                int height = 82 + (int)(10 * Math.sqrt(1 - distance / 98.0));
                boolean rib = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz) || octagon == 10;
                put(cx + dx, height, cz + dz, rib ? Blocks.IRON_BLOCK : block("light_blue_stained_glass"));
            }
            if (distance >= 25 && distance <= 34) put(cx + dx, 63, cz + dz, Blocks.POLISHED_BLACKSTONE);
            if ((dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) && distance <= 25)
                put(cx + dx, 63, cz + dz, Blocks.CHISELED_QUARTZ_BLOCK);
        }
        put(cx, 93, cz, block("lightning_rod"));
        for (int z = -15; z <= 1; z++) {
            int distance = Math.abs(z - cz);
            int arch = distance <= 6 ? 72 + (int)Math.sqrt(36 - distance * distance) : 64;
            fill(13, arch, z, 13, 79, z, Blocks.SMOOTH_SANDSTONE);
            put(12, arch, z, Blocks.CALCITE);
            put(13, 80, z, Blocks.CALCITE);
        }
        sign(15, 64, -6, "GALLERIA", "UMBERTO I");
    }

    private void galleryBay(int x, int z, int bay, boolean eastWest) {
        Block wall = bay == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.SMOOTH_SANDSTONE;
        fill(x, 64, z, x, 77, z, wall);
        if (bay == 0) {
            put(x, 69, z, Blocks.CHISELED_QUARTZ_BLOCK);
            put(x, 75, z, Blocks.CHISELED_QUARTZ_BLOCK);
            return;
        }
        if (bay != 4) {
            fill(x, 64, z, x, bay == 2 ? 68 : 67, z, block("brown_stained_glass"));
            fill(x, 71, z, x, bay == 2 ? 74 : 73, z, block("brown_stained_glass"));
        }
        int inwardX = eastWest ? x : x + (x < 31 ? 1 : -1);
        int inwardZ = eastWest ? z + (z < -7 ? 1 : -1) : z;
        put(inwardX, 70, inwardZ, Blocks.QUARTZ_SLAB);
        if (bay == 2) put(inwardX, 65, inwardZ, Blocks.LANTERN);
    }

    private void piazza() {
        // San Francesco di Paola: a low sweeping colonnade, a tall temple front and three domes.
        for (int z = 2; z <= 50; z++) {
            double curve = Math.pow((z - 26) / 24.0, 2);
            int x = -48 + (int)(17 * curve);
            fill(x - 2, 63, z, x + 2, 63, z, Blocks.POLISHED_DIORITE);
            fill(x - 2, 72, z, x + 2, 73, z, Blocks.SMOOTH_QUARTZ);
            put(x + 2, 74, z, Blocks.QUARTZ_SLAB);
            if ((z - 2) % 4 == 0) {
                fill(x, 64, z, x, 71, z, Blocks.QUARTZ_PILLAR);
                put(x, 71, z, Blocks.CHISELED_QUARTZ_BLOCK);
            }
        }
        fill(-66, 64, 14, -48, 75, 38, Blocks.SMOOTH_QUARTZ);
        fill(-65, 64, 15, -49, 74, 37, Blocks.AIR);
        fill(-48, 64, 24, -48, 70, 28, Blocks.AIR);
        fill(-47, 63, 16, -40, 63, 36, Blocks.POLISHED_DIORITE);
        fill(-46, 64, 17, -42, 64, 35, Blocks.QUARTZ_SLAB);
        for (int z = 17; z <= 35; z += 3) {
            fill(-43, 64, z, -43, 74, z, Blocks.QUARTZ_PILLAR);
            put(-43, 74, z, Blocks.CHISELED_QUARTZ_BLOCK);
        }
        for (int z = 15; z <= 37; z++) {
            int pediment = 76 + (11 - Math.abs(z - 26)) / 2;
            fill(-46, 75, z, -42, pediment, z, Blocks.SMOOTH_QUARTZ);
            put(-41, pediment, z, Blocks.QUARTZ_SLAB);
        }
        churchDome(-57, 26, 76, 12, 10);
        for (int z : new int[]{13, 39}) {
            fill(-62, 64, z - 5, -51, 73, z + 5, Blocks.SMOOTH_QUARTZ);
            churchDome(-56, z, 74, 5, 5);
        }
        put(-57, 87, 26, block("lightning_rod"));

        // Palazzo Reale: pale stone base, rose plaster, an upper cornice and the central balcony.
        fill(34, 64, 8, 47, 82, 47, block("terracotta"));
        fill(35, 64, 9, 46, 81, 46, Blocks.AIR);
        fill(33, 64, 8, 34, 69, 47, Blocks.SMOOTH_SANDSTONE);
        for (int y : new int[]{69, 75, 81}) fill(33, y, 8, 34, y, 47, Blocks.CALCITE);
        for (int z = 11; z <= 44; z += 4) {
            fill(34, 64, z, 34, 68, z + 1, Blocks.AIR);
            fill(33, 64, z, 33, 67, z + 1, Blocks.AIR);
            put(33, 68, z, Blocks.CHISELED_SANDSTONE);
            for (int y : new int[]{71, 77}) {
                fill(34, y, z, 34, y + 2, z + 1, block("black_stained_glass"));
                fill(33, y - 1, z, 33, y - 1, z + 1, Blocks.SMOOTH_SANDSTONE);
                put(33, y + 2, z, Blocks.SANDSTONE_SLAB);
            }
        }
        fill(31, 70, 22, 33, 70, 33, Blocks.SMOOTH_QUARTZ);
        fill(31, 71, 22, 31, 71, 33, Blocks.IRON_BARS);
        fill(32, 82, 7, 48, 82, 48, Blocks.SMOOTH_SANDSTONE);
        for (int tier = 0; tier < 4; tier++) fill(33 + tier, 83 + tier, 8 + tier, 47 - tier, 83 + tier, 47 - tier, Blocks.BRICKS);
        sign(29, 64, 16, "PALAZZO", "REALE");
        sign(-25, 64, 10, "PIAZZA DEL", "PLEBISCITO");
        statue(-14, 22);
        statue(-14, 34);
    }

    private void churchDome(int cx, int cz, int base, int radius, int height) {
        for (int y = 0; y <= height; y++) {
            double ring = radius * Math.sqrt(1 - y * y / (double)(height * height));
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.hypot(dx, dz);
                if (distance <= ring && distance >= ring - 1.6) {
                    boolean rib = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
                    put(cx + dx, base + y, cz + dz, y == 0 || rib ? Blocks.CALCITE : Blocks.POLISHED_ANDESITE);
                }
            }
        }
    }

    private void statue(int x, int z) {
        fill(x - 2, 64, z - 2, x + 2, 64, z + 2, Blocks.SMOOTH_QUARTZ);
        fill(x - 1, 65, z - 1, x + 1, 66, z + 1, Blocks.POLISHED_ANDESITE);
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) put(x + dx, 67, z + dz, Blocks.POLISHED_BLACKSTONE_WALL);
        }
        fill(x - 1, 68, z - 1, x + 1, 68, z + 1, Blocks.POLISHED_BLACKSTONE);
        put(x, 69, z - 1, Blocks.POLISHED_BLACKSTONE);
        put(x, 69, z - 2, Blocks.POLISHED_BLACKSTONE_SLAB);
        fill(x, 69, z + 1, x, 70, z + 1, Blocks.POLISHED_BLACKSTONE_WALL);
        put(x, 71, z + 1, Blocks.POLISHED_BLACKSTONE);
    }

    private void presepio() {
        fill(-33, 64, -12, -23, 64, -8, Blocks.SPRUCE_PLANKS);
        for (int x : new int[]{-33, -23}) fill(x, 65, -12, x, 68, -12, Blocks.SPRUCE_FENCE);
        fill(-34, 68, -13, -22, 68, -10, Blocks.BRICK_SLAB);
        fill(-32, 65, -12, -24, 66, -12, Blocks.MUD_BRICKS);
        put(-28, 65, -9, Blocks.HAY_BLOCK);
        put(-29, 65, -10, block("blue_wool"));
        put(-27, 65, -10, block("yellow_wool"));
        put(-29, 66, -10, Blocks.PLAYER_HEAD);
        put(-27, 66, -10, Blocks.PLAYER_HEAD);
        sign(-29, 64, -6, "PRESEPIO", "Te piace", "'o presepio?");
    }

    private void crypt() {
        fill(-64, 57, -40, -43, 62, -18, Blocks.TUFF_BRICKS);
        fill(-63, 58, -39, -44, 61, -19, Blocks.AIR);
        for (int step = 0; step <= 6; step++) {
            int y = 63 - step;
            int z = -12 - step;
            fill(-53, y, z, -51, y, z, Blocks.POLISHED_TUFF);
            fill(-53, y + 1, z, -51, Math.max(63, y + 3), z, Blocks.AIR);
        }
        fill(-53, 58, -19, -51, 61, -18, Blocks.AIR);
        for (int z = -36; z <= -23; z += 4) {
            fill(-62, 58, z, -62, 58, z + 1, Blocks.POLISHED_TUFF);
            put(-62, 59, z, Blocks.SKELETON_SKULL);
            put(-46, 58, z, Blocks.SOUL_LANTERN);
        }
        fill(-56, 58, -37, -50, 58, -35, Blocks.CHISELED_TUFF_BRICKS);
        put(-53, 59, -36, Blocks.SOUL_LANTERN);
        sign(-52, 64, -10, "SOTTERRANEA", "Anime pezzentelle");
    }

    private void waterfront() {
        // Via Partenope curls around the sea wall and opens towards the causeway.
        for (int x = -73; x <= 73; x++) {
            int edge = 55 + (int)(9 * Math.pow(Math.abs(x) / 73.0, 1.6));
            fill(x, 63, edge - 10, x, 63, edge, Blocks.SMOOTH_STONE);
            fill(x, 61, edge + 1, x, 63, edge + 1, Blocks.COBBLESTONE);
            if (Math.abs(x) > 5) put(x, 64, edge, Blocks.STONE_BRICK_WALL);
            if ((x + 73) % 10 == 0) lamp(x, edge - 3);
        }
        fill(-4, 63, 55, 4, 63, 82, Blocks.POLISHED_ANDESITE);
        fill(-5, 62, 56, -5, 63, 82, Blocks.COBBLESTONE);
        fill(5, 62, 56, 5, 63, 82, Blocks.COBBLESTONE);
        for (int z = 59; z <= 77; z += 3) {
            put(-5, 64, z, Blocks.STONE_BRICK_WALL);
            put(5, 64, z, Blocks.STONE_BRICK_WALL);
        }

        // Megaride rises in irregular rock terraces; the fort steps up away from the bridge.
        for (int z = 77; z <= 111; z++) {
            int width = z < 87 ? 10 + (z - 77) : 21 - (z - 87) / 3;
            int center = (z - 77) / 6;
            fill(center - width, 61, z, center + width, 64, z, Blocks.COBBLESTONE);
            fill(center - width + 2, 65, z, center + width - 2, 65, z, Blocks.ANDESITE);
        }
        castleWard(-14, 82, 12, 94, 65, 75, true);
        castleWard(-5, 93, 20, 106, 67, 81, false);
        castleWard(4, 97, 17, 105, 81, 85, false);
        fill(-3, 65, 76, 3, 65, 92, Blocks.SMOOTH_SANDSTONE);
        for (int step = 0; step <= 2; step++) fill(-3, 65 + step, 92 + step, 3, 65 + step, 94 + step, Blocks.SMOOTH_SANDSTONE);
        sign(-2, 64, 74, "CASTEL", "DELL'OVO");

        // Borgo Marinari: a narrow quay, little façades, moorings and boats in the lee of the rock.
        fill(5, 63, 74, 19, 63, 76, Blocks.STONE_BRICKS);
        fill(18, 63, 73, 42, 63, 77, Blocks.STONE_BRICKS);
        sign(22, 64, 76, "BORGO", "MARINARI");
        for (int x = 22; x <= 36; x += 7) {
            fill(x, 64, 78, x + 5, 69, 82, FACADES[(x - 22) / 7 + 1]);
            fill(x + 1, 64, 79, x + 4, 68, 81, Blocks.AIR);
            fill(x + 2, 64, 78, x + 3, 66, 78, Blocks.AIR);
            for (int dx : new int[]{1, 4}) put(x + dx, 68, 78, block("black_stained_glass"));
            fill(x - 1, 70, 77, x + 6, 70, 83, Blocks.BRICK_SLAB);
        }
        for (int x = 28; x <= 48; x += 6) {
            int z = 68 - (x % 5);
            fill(x - 1, 63, z - 2, x + 1, 63, z + 2, Blocks.SPRUCE_SLAB);
            put(x, 64, z - 3, Blocks.SPRUCE_SLAB);
            put(x, 64, z + 3, Blocks.SPRUCE_SLAB);
            put(x, 64, z, Blocks.SPRUCE_FENCE);
        }
        // The broad profile of Vesuvius on the far side of the Gulf.
        for (int y = 64; y <= 95; y++) {
            int width = (int)(37 * Math.pow(1 - (y - 64) / 37.0, 1.25)) + 3;
            for (int z = 119; z <= 152; z++) {
                int w = width - Math.abs(z - 136);
                if (w >= 0) fill(57 - w, y, z, 57 + w, y, z,
                        y < 83 ? Blocks.MOSS_BLOCK : Blocks.TUFF);
            }
        }
    }

    private void castleWard(int x1, int z1, int x2, int z2, int floor, int top, boolean gate) {
        fill(x1, floor, z1, x2, top, z2, Blocks.CUT_SANDSTONE);
        fill(x1 + 2, floor + 1, z1 + 2, x2 - 2, top - 1, z2 - 2, Blocks.AIR);
        for (int x = x1; x <= x2; x += 3) {
            put(x, top + 1, z1, Blocks.SANDSTONE_WALL);
            put(x, top + 1, z2, Blocks.SANDSTONE_WALL);
        }
        for (int z = z1; z <= z2; z += 3) {
            put(x1, top + 1, z, Blocks.SANDSTONE_WALL);
            put(x2, top + 1, z, Blocks.SANDSTONE_WALL);
        }
        if (gate) {
            fill(-3, floor + 1, z1, 3, floor + 4, z2, Blocks.AIR);
            fill(-2, floor + 5, z1, 2, floor + 5, z1 + 1, Blocks.AIR);
        } else {
            fill(Math.max(x1 + 2, -2), floor + 1, z1, Math.max(x1 + 4, 2), floor + 4, z1 + 2, Blocks.AIR);
        }
    }

    private void lamp(int x, int z) {
        fill(x, 64, z, x, 67, z, Blocks.DEEPSLATE_BRICK_WALL);
        put(x, 68, z, Blocks.LANTERN);
    }

    private void sign(int x, int y, int z, String... lines) {
        BlockPos pos = new BlockPos(x, y, z);
        world.setBlock(pos, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8), Block.UPDATE_CLIENTS);
        if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            var text = SignText.EMPTY.asMutable();
            for (int i = 0; i < lines.length; i++) text.setLine(i, Component.literal(lines[i]));
            sign.setText(text.asImmutable(), SignTextSlot.FRONT);
            sign.setText(text.asImmutable(), SignTextSlot.BACK);
            sign.setChanged();
        }
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
    }

    private void put(int x, int y, int z, Block block) {
        world.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        BlockState state = block.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2))
            world.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
