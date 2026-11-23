package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NapoliFactions {
    enum Side {
        LANTERNE("lanterne"), MAREE("maree");
        final String id;
        Side(String id) { this.id = id; }
        Component label() { return Component.translatable("napuli.factions." + id); }

        static Side of(NapoliResident.Role role) {
            return switch (role) {
                case ADA, MICHELE, NICO -> LANTERNE;
                case SALVATORE, ROSA, ANTONIO -> MAREE;
                default -> null;
            };
        }
    }

    private record Progress(int lanterns, int tides, int side, int stage, long nextMission) {
        static final Progress EMPTY = new Progress(0, 0, -1, 0, 0);
        static final Codec<Progress> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.INT.fieldOf("lanterns").forGetter(Progress::lanterns),
            Codec.INT.fieldOf("tides").forGetter(Progress::tides),
            Codec.INT.fieldOf("side").forGetter(Progress::side),
            Codec.INT.fieldOf("stage").forGetter(Progress::stage),
            Codec.LONG.fieldOf("next_mission").forGetter(Progress::nextMission)
        ).apply(builder, Progress::new));
        Side selected() { return side == 0 ? Side.LANTERNE : side == 1 ? Side.MAREE : null; }
    }

    private static final AttachmentType<Progress> DATA = AttachmentRegistry.<Progress>builder()
            .persistent(Progress.CODEC).copyOnDeath().buildAndRegister(Napuli.id("faction_progress"));
    private static final int SUPPLY = 1;
    private static final int CLASH = 2;
    private static final int REPORT = 3;
    private static final int REPORT_CLASH = 4;
    private static final int LEVY = 5, CACHE = 6, REPORT_LEVY = 7, REPORT_CACHE = 8;
    private static final int MINE = 9, SURVEY = 10, REPORT_MINE = 11, REPORT_SURVEY = 12;
    private static final int ORE_QUOTA = 4;
    private static final AttachmentType<Integer> ORE_COUNT = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT).copyOnDeath().buildAndRegister(Napuli.id("mission_ore_count"));
    static final int LEVY_REPUTATION = 4, CACHE_REPUTATION = 8;
    private static final AttachmentType<Long> NEXT_REACTION = AttachmentRegistry.create(Napuli.id("rival_reaction"));
    private static final AttachmentType<Integer> DISTRICT = AttachmentRegistry.create(Napuli.id("recognized_district"));

    private NapoliFactions() {}

    static void register() {} // Register the attachment before any player data is read.

    private static Progress get(ServerPlayer player) { return player.getAttachedOrElse(DATA, Progress.EMPTY); }

    static Component status(ServerPlayer player) {
        Progress p = get(player);
        return Component.translatable("napuli.factions.reputation", p.lanterns(), p.tides());
    }

    static int reputation(ServerPlayer player, Side side) {
        Progress p = get(player);
        return side == Side.LANTERNE ? p.lanterns() : p.tides();
    }

    static Side ally(ServerPlayer player) {
        int l = reputation(player, Side.LANTERNE), m = reputation(player, Side.MAREE);
        if (Math.max(l, m) < LEVY_REPUTATION || l == m) return null;
        return l > m ? Side.LANTERNE : Side.MAREE;
    }

    static void recognize(ServerPlayer player) {
        Side district = player.getBlockX() < -20 && player.getBlockZ() < 5 ? Side.LANTERNE
                : player.getBlockZ() >= 64 && player.getBlockX() >= 12 ? Side.MAREE : null;
        if (district == null) { player.removeAttached(DISTRICT); return; }
        if (reputation(player, district) >= 0 || player.getAttachedOrElse(DISTRICT, -1) == district.ordinal()) return;
        player.setAttached(DISTRICT, district.ordinal());
        say(player, "recognized", district.label());
        long now = player.level().getGameTime();
        if (reputation(player, district) > -4 || player.isCreative()
                || now < player.getAttachedOrElse(NEXT_REACTION, 0L)) return;
        if (NapoliStreet.confront(player, district)) player.setAttached(NEXT_REACTION, now + 20 * 90);
    }

    static boolean onErrand(ServerPlayer player, boolean cache) {
        return get(player).stage() == (cache ? CACHE : LEVY);
    }

    static int advanced(ServerPlayer player, Side side, boolean cache) {
        int needed = cache ? CACHE_REPUTATION : LEVY_REPUTATION;
        if (side == null || reputation(player, side) < needed) { say(player, "locked", needed); return 0; }
        int result = start(player, side, cache ? CACHE : LEVY);
        if (result == 1) say(player, cache ? "route_cache" : "route_levy");
        return result;
    }

    static int errand(ServerPlayer player, boolean cache) {
        Progress p = get(player);
        if (!onErrand(player, cache) || p.selected() == null) return 0;
        player.setAttached(DATA, new Progress(p.lanterns(), p.tides(), p.side(), cache ? REPORT_CACHE : REPORT_LEVY, 0));
        say(player, "report_" + p.selected().id);
        return 1;
    }

    static int districtJob(ServerPlayer player, Side side, boolean mining) {
        int accepted = start(player, side, mining ? MINE : SURVEY);
        if (accepted != 0) {
            player.removeAttached(ORE_COUNT);
            say(player, mining ? (side == Side.LANTERNE ? "mine_iron" : "mine_copper") : "survey");
        }
        return accepted;
    }

    static void mine(ServerPlayer player, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        Progress p = get(player);
        if (p.stage() != MINE || p.selected() == null || !NapoliMunicipio.vein(pos, block)) return;
        var expected = p.selected() == Side.LANTERNE ? net.minecraft.world.level.block.Blocks.IRON_ORE
                : net.minecraft.world.level.block.Blocks.COPPER_ORE;
        if (block != expected) return;
        int total = player.getAttachedOrElse(ORE_COUNT, 0) + 1;
        player.setAttached(ORE_COUNT, total);
        say(player, "ore_progress", total, ORE_QUOTA);
        if (total >= ORE_QUOTA) districtReady(player, p, REPORT_MINE);
    }

    static void explore(ServerPlayer player) {
        Progress p = get(player);
        if (p.stage() == SURVEY && p.selected() != null
                && player.blockPosition().distSqr(survey(p.selected())) < 36) districtReady(player, p, REPORT_SURVEY);
    }

    private static net.minecraft.core.BlockPos survey(Side side) {
        return side == Side.LANTERNE ? NapoliMunicipio.CASTLE : NapoliMunicipio.PIER;
    }

    private static void districtReady(ServerPlayer player, Progress p, int report) {
        player.setAttached(DATA, new Progress(p.lanterns(), p.tides(), p.side(), report, 0));
        say(player, "report_" + p.selected().id);
    }

    static int accept(ServerPlayer player, Side side, boolean clash) {
        int result = start(player, side, clash ? CLASH : SUPPLY);
        if (result == 1) {
            if (clash) say(player, "route_clash", side.label());
            else say(player, "route_" + side.id);
        }
        return result;
    }

    private static int start(ServerPlayer player, Side side, int stage) {
        Progress p = get(player);
        long now = player.level().getGameTime();
        if (side == null) return 0;
        if (p.stage() != 0) { say(player, "busy"); return 0; }
        if (now < p.nextMission()) { say(player, "cooldown"); return 0; }
        player.setAttached(DATA, new Progress(p.lanterns(), p.tides(), side.ordinal(), stage, 0));
        KitchenProgress.questStarted(player);
        return 1;
    }

    static int cancel(ServerPlayer player, Side side) {
        Progress p = get(player);
        if (side == null || p.selected() != side || p.stage() == 0) return 0;
        player.setAttached(DATA, new Progress(p.lanterns(), p.tides(), -1, 0, 0));
        say(player, "cancelled");
        return 1;
    }

    static int handoff(ServerPlayer player, Side side) {
        Progress p = get(player);
        if (side == null || p.selected() != side || p.stage() != SUPPLY) { say(player, "wrong_contact"); return 0; }
        player.setAttached(DATA, new Progress(p.lanterns(), p.tides(), p.side(), REPORT, 0));
        say(player, "report_" + side.id);
        return 1;
    }

    static void rivalDefeated(ServerPlayer player, Side rival) {
        Progress p = get(player);
        if (p.stage() != CLASH || rival == null || p.selected() == rival) return;
        player.setAttached(DATA, new Progress(p.lanterns(), p.tides(), p.side(), REPORT_CLASH, 0));
        say(player, "report_" + p.selected().id);
    }

    static int finish(ServerPlayer player, Side side) {
        Progress p = get(player);
        if (side == null || p.selected() != side || (p.stage() != REPORT && p.stage() != REPORT_CLASH && p.stage() != REPORT_LEVY && p.stage() != REPORT_CACHE && p.stage() != REPORT_MINE && p.stage() != REPORT_SURVEY)) {
            say(player, "unfinished");
            return 0;
        }
        int reputation = switch (p.stage()) { case REPORT_CACHE -> 9; case REPORT_CLASH, REPORT_LEVY -> 7; case REPORT_MINE -> 6; case REPORT_SURVEY -> 5; default -> 4; };
        int payout = switch (p.stage()) { case REPORT_CACHE -> 32; case REPORT_LEVY -> 25; case REPORT_CLASH -> 20; case REPORT_MINE -> 22; case REPORT_SURVEY -> 18; default -> 16; };
        int lanterns = Math.clamp(p.lanterns() + (side == Side.LANTERNE ? reputation : -2), -100, 100);
        int tides = Math.clamp(p.tides() + (side == Side.MAREE ? reputation : -2), -100, 100);
        player.setAttached(DATA, new Progress(lanterns, tides, -1, 0, player.level().getGameTime() + 20 * 35));
        NapoliStreet.credit(player, payout);
        say(player, "payout", payout, reputation, side.label());
        return 1;
    }

    /** what the mission tracker shows: the objective's text key and where to go (null = nowhere in particular) */
    record Objective(String key, net.minecraft.core.BlockPos target) {}

    static final net.minecraft.core.BlockPos ADA = new net.minecraft.core.BlockPos(-33, 64, -3), MICHELE = new net.minecraft.core.BlockPos(-48, 64, -6),
            TORE = new net.minecraft.core.BlockPos(26, 64, 75), ROSA = new net.minecraft.core.BlockPos(32, 64, 75), MUOLO = new net.minecraft.core.BlockPos(13, 64, 52);

    static Objective objective(ServerPlayer player) {
        Progress p = get(player);
        Side s = p.selected();
        boolean l = s == Side.LANTERNE;
        return switch (p.stage()) {
            case SUPPLY -> new Objective(l ? "supply_lanterne" : "supply_maree", l ? MICHELE : ROSA);
            case CLASH -> new Objective("clash", MUOLO);
            case LEVY -> new Objective("levy", NapoliDistrict.PUTECA);
            case CACHE -> new Objective("cache", NapoliDistrict.SARTORIA);
            case MINE -> new Objective(l ? "mine_iron" : "mine_copper", NapoliMunicipio.MINE);
            case SURVEY -> new Objective(l ? "survey_castle" : "survey_pier", survey(s));
            case REPORT, REPORT_CLASH, REPORT_LEVY, REPORT_CACHE, REPORT_MINE, REPORT_SURVEY -> new Objective(l ? "report_lanterne" : "report_maree", l ? ADA : TORE);
            default -> new Objective(player.level().getGameTime() < p.nextMission() ? "cooldown" : "none", null);
        };
    }

    static int cooldownSeconds(ServerPlayer player) {
        return (int) Math.max(0, (get(player).nextMission() - player.level().getGameTime()) / 20);
    }

    /** dev play-tests only: put a player in a clan, on a mission stage */
    public static void debugSet(ServerPlayer player, int lanterns, int tides, int side, int stage) {
        player.setAttached(DATA, new Progress(lanterns, tides, side, stage, 0));
    }

    private static void say(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("napuli.factions." + key, args));
    }
}
