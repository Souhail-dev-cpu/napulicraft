package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Per-player timers follow potion time: logout pauses them and death clears them. */
public final class KitchenEffects {
    // Keep the saved limoncello attachment IDs: all alcoholic drinks now share these counters.
    static final AttachmentType<Integer> BABA = counter("baba_rush"), CAPITONE = counter("capitone_crash"),
            DRINKS = counter("limoncello_drinks"), DRINK_WINDOW = counter("limoncello_window"),
            COOLING = counter("watermelon_cooling"), HEAT = counter("body_heat"), FRIED_STREAK = counter("fried_streak");
    private static final AttachmentType<Long> LAST_EATEN = AttachmentRegistry.<Long>builder().persistent(Codec.LONG).buildAndRegister(Napuli.id("kitchen_last_eaten"));
    private KitchenEffects() {}
    static void initialize() {} // Register persistent timers before player data is decoded on login.
    private static AttachmentType<Integer> counter(String id) { return AttachmentRegistry.<Integer>builder().persistent(Codec.intRange(0, 24000)).buildAndRegister(Napuli.id(id)); }
    public static int heat(ServerPlayer player) { return player.getAttachedOrElse(HEAT, 0); }
    static void effect(ServerPlayer player, Holder<MobEffect> effect, int seconds, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier));
    }

    public static void beforeEating(ServerPlayer player, ItemStack stack) {
        if (stack.is(Napuli.CAFE)) {
            player.removeEffect(MobEffects.MINING_FATIGUE);
            player.removeEffect(MobEffects.SLOWNESS);
        }
    }

    static int friedBonus(ServerPlayer player, ItemStack stack) {
        var food = NapoliKitchen.food(stack);
        if (food == null || !food.fried()) { player.removeAttached(FRIED_STREAK); return 0; }
        int streak = Math.min(5, player.getAttachedOrElse(FRIED_STREAK, 0) + 1);
        player.setAttached(FRIED_STREAK, streak);
        return 3 + streak * 3;
    }

    public static void eaten(ServerPlayer player, NapoliKitchen.Food food) {
        player.setAttached(LAST_EATEN, player.level().getServer().overworld().getGameTime());
        switch (food.effect()) {
            case "NONE" -> { }
            case "RANDOM_LUCK" -> effect(player, MobEffects.LUCK, 30 + player.getRandom().nextInt(61), player.getRandom().nextInt(2));
            case "RARE_LUCK" -> effect(player, MobEffects.LUCK, 120, 2);
            case "LUCK" -> effect(player, MobEffects.LUCK, 90, 0);
            case "BABA" -> {
                effect(player, MobEffects.SPEED, 15, 1); effect(player, MobEffects.JUMP_BOOST, 15, 1);
                player.setAttached(BABA, 300);
            }
            case "PASTIERA" -> { player.heal(8); effect(player, MobEffects.REGENERATION, 20, 1); }
            case "TEAM_HEAL" -> {
                for (ServerPlayer friend : player.level().players()) {
                    if (friend != player && (player.getTeam() == null || !friend.isAlliedTo(player))) continue;
                    if (!friend.isAlive() || friend.distanceToSqr(player) > 16 * 16) continue;
                    friend.heal(4); effect(friend, MobEffects.REGENERATION, 10, 0);
                }
            }
            case "RESISTANCE" -> effect(player, MobEffects.RESISTANCE, 60, 0);
            case "DUNGEON" -> {
                effect(player, MobEffects.RESISTANCE, 45, 1);
                for (Monster enemy : player.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(12),
                        monster -> !(monster instanceof Munaciello monk && monk.isTamed())))
                    enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
            }
            case "STRENGTH" -> effect(player, MobEffects.STRENGTH, 60, 0);
            case "HEAVY" -> effect(player, MobEffects.SLOWNESS, 20, 0);
            case "AGILITY" -> { effect(player, MobEffects.SPEED, 20, 1); effect(player, MobEffects.JUMP_BOOST, 20, 0); }
            case "SEA" -> { effect(player, MobEffects.DOLPHINS_GRACE, 60, 0); effect(player, MobEffects.WATER_BREATHING, 30, 0); }
            case "CLEANSE" -> {
                for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects()))
                    if (!effect.getEffect().value().isBeneficial()) player.removeEffect(effect.getEffect());
                player.heal(8); effect(player, MobEffects.REGENERATION, 10, 0);
            }
            case "EXTREME" -> {
                effect(player, MobEffects.STRENGTH, 20, 2); effect(player, MobEffects.SPEED, 20, 2); effect(player, MobEffects.RESISTANCE, 20, 1);
                if (player.getRandom().nextFloat() < .35f) player.setAttached(CAPITONE, 400);
            }
            case "SOUP" -> effect(player, MobEffects.REGENERATION, 90, 0);
            case "GATTO" -> { player.heal(6); effect(player, MobEffects.REGENERATION, 10, 0); }
            case "PUZZLE" -> {
                if (!player.getAttachedOrElse(KitchenProgress.PUZZLE_CLAIMED, false)) {
                    player.setAttached(KitchenProgress.PUZZLE_READY, true);
                    NapoliNotice.show(player, Component.translatable("napuli.kitchen.puzzle_ready"));
                }
            }
            case "LIMONCELLO", "ALCOHOL" -> {
                effect(player, MobEffects.LUCK, 45, 0);
                int drinks = player.getAttachedOrElse(DRINKS, 0) + 1;
                player.setAttached(DRINKS, Math.min(drinks, 10));
                if (player.getAttachedOrElse(DRINK_WINDOW, 0) == 0) player.setAttached(DRINK_WINDOW, 1200);
                if (drinks >= 3) {
                    effect(player, MobEffects.NAUSEA, 20, 0); effect(player, MobEffects.SLOWNESS, 10, 0);
                    effect(player, MobEffects.JUMP_BOOST, 10, 1);
                    player.sendOverlayMessage(Component.translatable("napuli.kitchen.drunk"));
                }
            }
            case "COOLING" -> {
                player.setAttached(HEAT, Math.max(0, heat(player) - 60)); player.setAttached(COOLING, 2400);
                player.clearFire(); effect(player, MobEffects.FIRE_RESISTANCE, 30, 0);
            }
            default -> throw new IllegalArgumentException("Unknown kitchen effect " + food.effect());
        }
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive()) {
                for (var type : java.util.List.of(BABA, CAPITONE, DRINKS, DRINK_WINDOW, COOLING, HEAT, FRIED_STREAK)) player.removeAttached(type);
                continue;
            }
            boolean eatingTick = player.getAttachedOrElse(LAST_EATEN, -1L) == server.overworld().getGameTime();
            if (!eatingTick) {
                if (advance(player, BABA)) { effect(player, MobEffects.NAUSEA, 10, 0); effect(player, MobEffects.SLOWNESS, 5, 0); }
                if (advance(player, CAPITONE)) {
                    NapoliStreet.addFullness(player, 40);
                    effect(player, MobEffects.SLOWNESS, 20, 2); effect(player, MobEffects.WEAKNESS, 10, 0);
                    NapoliNotice.show(player, Component.translatable("napuli.kitchen.extreme"));
                }
                if (advance(player, DRINK_WINDOW)) player.removeAttached(DRINKS);
                advance(player, COOLING);
            }
            if (server.getTickCount() % 20 != 0) continue;
            NapoliStreet.digest(player);
            if (player.isCreative() || player.isSpectator()) { player.removeAttached(HEAT); continue; }
            int before = heat(player);
            int delta = player.level().dimension() == Level.NETHER ? 3
                    : player.level().getBiome(player.blockPosition()).value().getBaseTemperature() >= 1.2f
                    && player.level().isBrightOutside() && player.level().canSeeSky(player.blockPosition()) ? 1 : -2;
            if (player.isOnFire()) delta = 8;
            if (player.isInWaterOrRain()) delta = -4;
            if (player.getAttachedOrElse(COOLING, 0) > 0) delta = Math.min(-2, delta);
            int after = Math.clamp(before + delta, 0, 100);
            player.setAttached(HEAT, after);
            if (before < 80 && after >= 80) NapoliNotice.show(player, Component.translatable("napuli.kitchen.hot"));
            if (after >= 80) effect(player, MobEffects.WEAKNESS, 2, 0);
        }
    }

    private static boolean advance(ServerPlayer player, AttachmentType<Integer> type) {
        int value = player.getAttachedOrElse(type, 0);
        if (value <= 0) return false;
        if (value == 1) { player.removeAttached(type); return true; }
        player.setAttached(type, value - 1);
        return false;
    }
}
