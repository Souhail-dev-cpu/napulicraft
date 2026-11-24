package dev.napuli;

import dev.napuli.Music.Situation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Everything that reacts to what you do: the right song for the moment, Mammà, the vigile, the paesani. */
public final class Situazioni {
	private Situazioni() {}

	private static final Map<UUID, Long> MAMMA_DEADLINE = new HashMap<>();
	private static final Map<UUID, String> LAST_DIM = new HashMap<>();
	private static final String[] PAESANI = {"napuli.paesano.0", "napuli.paesano.1", "napuli.paesano.2", "napuli.paesano.3",
			"napuli.paesano.4", "napuli.paesano.5", "napuli.paesano.6", "napuli.paesano.7"};

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
			if (e instanceof ServerPlayer p) Music.play(p, Situation.MORTE);
			if (e.getType() == EntityTypes.WITHER || e.getType() == Napuli.GIGANTE)
				for (ServerPlayer p : ((ServerLevel) e.level()).getPlayers(pl -> pl.distanceToSqr(e) < 96 * 96)) Music.play(p, Situation.VITTORIA);
		});
		// the dragon doesn't "die" normally: it is removed as KILLED at the end of its animation
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e.getType() == EntityTypes.ENDER_DRAGON && e.getRemovalReason() == Entity.RemovalReason.KILLED)
				for (ServerPlayer p : level.players()) Music.play(p, Situation.VITTORIA);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, src, base, dealt, blocked) -> {
			if (dealt <= 0) return;
			Entity atk = src.getEntity();
			if (victim instanceof ServerPlayer v && atk instanceof ServerPlayer a && v != a) {
				Music.play(v, Situation.LITE);
				Music.play(a, Situation.LITE);
			} else if (victim instanceof Villager && atk instanceof ServerPlayer a) {
				a.sendSystemMessage(Component.translatable("napuli.paesano.botte"));
				Music.play(a, Situation.LITE);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer p)) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (entity instanceof Animal a && !a.isBaby() && a.isFood(stack) && a.canFallInLove()) Music.play(p, Situation.AMORE);
			if (entity instanceof Villager && p.getRandom().nextFloat() < 0.35f)
				p.sendSystemMessage(Component.translatable(PAESANI[p.getRandom().nextInt(PAESANI.length)]));
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer p && state.is(BlockItemTags.DIAMOND_ORES.block())) Music.play(p, Situation.DIAMANTI);
		});
		EntitySleepEvents.STOP_SLEEPING.register((e, pos) -> {
			if (e instanceof ServerPlayer p && p.level().isBrightOutside()) Music.play(p, Situation.RISVEGLIO);
		});
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || !p.isAlive()) continue;
			ServerLevel level = p.level();
			// changed dimension
			String dim = level.dimension().identifier().toString();
			String before = LAST_DIM.put(p.getUUID(), dim);
			if (before != null && !before.equals(dim) && level.dimension() == Level.NETHER) Music.play(p, Situation.NETHER);
			// almost dead
			if (p.getHealth() <= 6f) Music.play(p, Situation.OSPEDALE);
			if (level.dimension() == Level.OVERWORLD) {
				long day = level.getOverworldClockTime() % 24000;
				if (day >= 13000 && day < 13020) Music.play(p, Situation.NOTTE);
				mamma(p, level, day);
			}
			if (server.getTickCount() % 800 == 0 && p.getVehicle() instanceof Motorino m && m.getFirstPassenger() == p) casco(p, level);
		}
	}

	// ---------------------------------------------------------------- Mammà
	private static final Map<UUID, Long> MAMMA_DAY = new HashMap<>();

	/** Sunset: Mammà calls you for dinner. 90 s later: at home = 2 pizzas, still out = the slipper. Once a day. */
	private static void mamma(ServerPlayer p, ServerLevel level, long day) {
		long now = level.getGameTime();
		long dayNumber = level.getOverworldClockTime() / 24000;
		if (day >= 12000 && day < 12020 && !Long.valueOf(dayNumber).equals(MAMMA_DAY.get(p.getUUID()))) {
			MAMMA_DAY.put(p.getUUID(), dayNumber);
			chiamaMamma(p, 90);
		}
		Long deadline = MAMMA_DEADLINE.get(p.getUUID());
		if (deadline == null || now < deadline) return;
		MAMMA_DEADLINE.remove(p.getUUID());
		if (isHome(p, level)) {
			p.sendSystemMessage(Component.translatable("napuli.mamma.brava"));
			p.getInventory().add(new ItemStack(Napuli.PIZZA, 2));
			CityEffects.sound(level,null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
		} else {
			lanciaCiabatta(p, level);
		}
	}

	/** Mammà calls now; you have this many seconds to be home (also used by /napuli mamma). */
	public static void chiamaMamma(ServerPlayer p, int seconds) {
		MAMMA_DEADLINE.put(p.getUUID(), p.level().getGameTime() + 20L * seconds);
		title(p, "napuli.mamma.title", "napuli.mamma.chiama");
		p.sendSystemMessage(Component.translatable("napuli.mamma.chat"));
	}

	static boolean isHome(ServerPlayer p, ServerLevel level) {
		BlockPos home = level.getRespawnData().pos();
		ServerPlayer.RespawnConfig cfg = p.getRespawnConfig();
		if (cfg != null && cfg.respawnData().dimension() == level.dimension()) home = cfg.respawnData().pos();
		return p.blockPosition().distSqr(home) < 32 * 32 || p.isSleeping();
	}

	public static void lanciaCiabatta(ServerPlayer p, ServerLevel level) {
		title(p, "napuli.ciabatta.title", "napuli.ciabatta.sub");
		Ciabatta c = new Ciabatta(level, p.getX() - p.getLookAngle().x * 6, p.getY() + 7, p.getZ() - p.getLookAngle().z * 6, new ItemStack(Napuli.CIABATTA_ITEM));
		c.shoot(p.getX() - c.getX(), p.getEyeY() - c.getY(), p.getZ() - c.getZ(), 1.3f, 0f);
		level.addFreshEntity(c);
		CityEffects.sound(level,null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.5f, 0.8f);
	}

	// ---------------------------------------------------------------- casco / vigile
	private static void casco(ServerPlayer p, ServerLevel level) {
		if (p.getRandom().nextFloat() > 0.4f) return;
		boolean helmet = !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
		if (!helmet) {
			CityEffects.sound(level,null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 2f, 2f);
			if (p.getInventory().contains(new ItemStack(Items.EMERALD))) {
				removeOne(p, Items.EMERALD);
				p.sendSystemMessage(Component.translatable("napuli.vigile.multa"));
			} else {
				p.sendSystemMessage(Component.translatable("napuli.vigile.perdono"));
			}
		} else if (!level.getEntitiesOfClass(Villager.class, new AABB(p.blockPosition()).inflate(24)).isEmpty()) {
			p.sendSystemMessage(Component.translatable("napuli.casco.risate"));
			CityEffects.sound(level,null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.5f, 1.3f);
		}
	}

	static void removeOne(Player p, net.minecraft.world.item.Item item) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) { s.shrink(1); return; }
		}
	}

	static void title(ServerPlayer p, String title, String sub) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title)));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub)));
	}
}
