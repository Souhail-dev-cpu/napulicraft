package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Authored home bounds and player construction are persisted separately from the blocks themselves. */
public final class CityProperty {
    record Home(BlockPos min, BlockPos max) {
        static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(BlockPos.CODEC.fieldOf("min").forGetter(Home::min),
                BlockPos.CODEC.fieldOf("max").forGetter(Home::max)).apply(i, Home::new));
        boolean contains(BlockPos pos) { return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ(); }
    }
    private static final AttachmentType<Set<Long>> PLACED = AttachmentRegistry.<Set<Long>>builder()
            .persistent(Codec.LONG.listOf().xmap(Set::copyOf, List::copyOf)).buildAndRegister(Napuli.id("player_placed_city_blocks"));
    private static final AttachmentType<List<Home>> EXTRA_HOMES = AttachmentRegistry.<List<Home>>builder()
            .persistent(Home.CODEC.listOf()).buildAndRegister(Napuli.id("city_home_bounds"));
    private static final List<Home> CATALOGUE = catalogue();
    private CityProperty() {}

    static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel) || level.dimension() != NapoliMap.LEVEL) return;
            boolean damagedProperty = isVandalism(serverLevel, pos, state);
            forget(serverLevel, pos);
            if (damagedProperty && player instanceof ServerPlayer serverPlayer && !player.isCreative() && !player.isSpectator())
                Carabinieri.crime(serverPlayer, Carabinieri.Crime.VANDALISM);
        });
    }

    public static void placed(ServerLevel level, BlockPos pos) {
        var positions = new HashSet<>(level.getAttachedOrElse(PLACED, Set.of()));
        positions.add(pos.asLong()); level.setAttached(PLACED, Set.copyOf(positions));
    }
    public static boolean mayBuild(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (level.dimension() != NapoliMap.LEVEL) return true;
        return NapoliMergellina.PROPERTIES.stream().noneMatch(h -> h.contains(pos) && !NapoliProperties.owns(player, h));
    }
    static void forget(ServerLevel level, BlockPos pos) {
        var positions = level.getAttachedOrElse(PLACED, Set.of());
        if (!positions.contains(pos.asLong())) return;
        var updated = new HashSet<>(positions); updated.remove(pos.asLong()); level.setAttached(PLACED, Set.copyOf(updated));
    }
    static boolean playerPlaced(ServerLevel level, BlockPos pos) { return level.getAttachedOrElse(PLACED, Set.of()).contains(pos.asLong()); }
    static boolean isVandalism(ServerLevel level, BlockPos pos, BlockState broken) {
        return !broken.isAir() && !playerPlaced(level, pos) && home(level, pos);
    }
    static boolean home(ServerLevel level, BlockPos pos) {
        for (Home home : CATALOGUE) if (home.contains(pos)) return true;
        for (Home home : level.getAttachedOrElse(EXTRA_HOMES, List.of())) if (home.contains(pos)) return true;
        return false;
    }
    static List<Home> homes(ServerLevel level) {
        var homes = new ArrayList<>(CATALOGUE);
        homes.addAll(level.getAttachedOrElse(EXTRA_HOMES, List.of()));
        return homes;
    }
    static void recordHome(ServerLevel level, BlockPos min, BlockPos max) {
        Home home = new Home(min.immutable(), max.immutable());
        if (CATALOGUE.contains(home)) return;
        var existing = level.getAttachedOrElse(EXTRA_HOMES, List.of());
        if (existing.contains(home)) return;
        var updated = new ArrayList<>(existing); updated.add(home); level.setAttached(EXTRA_HOMES, List.copyOf(updated));
    }

    private static void classic(List<Home> homes, int x, int z, int index) {
        homes.add(new Home(new BlockPos(x, 63, z), new BlockPos(x + 10, 64 + 12 + Math.floorMod(index, 4) * 3, z + 11)));
    }
    private static List<Home> catalogue() {
        var homes = new ArrayList<Home>();
        for (int[] h : NapoliInteriors.homes()) classic(homes, h[0], h[1], h[2]);
        for (int[] h : new int[][] {{150,-30,3},{163,-30,6},{150,-60,9},{163,-60,2},{156,12,5},{156,30,7}}) classic(homes,h[0],h[1],h[2]);
        for (int x = 204; x <= 257; x += 17) { classic(homes,x,-50,x); classic(homes,x,-22,x+1); }
        int i = 0;
        for (int x = -18; x <= 66; x += 8) {
            if (x + 7 >= -7 && x <= 7) continue;
            homes.add(new Home(new BlockPos(x,63,-122), new BlockPos(x+7,64+(4+(i%3==0?1:0))*4,-112))); i++;
        }
        for (int x = -56; x <= 66; x += 8) {
            if (x + 7 >= -7 && x <= 7 || x + 7 >= NapoliCentro.SGA_X-9 && x <= NapoliCentro.SGA_X+9) continue;
            homes.add(new Home(new BlockPos(x,63,-138), new BlockPos(x+7,64+(4+(i%4==1?1:0))*4,-128))); i++;
        }
        i = 40;
        for (int x = 10; x <= 62; x += 8) { homes.add(new Home(new BlockPos(x,63,-111),new BlockPos(x+7,64+(3+i%2)*4,-101))); i++; }
        return List.copyOf(homes);
    }
}
