package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * 'E Carabiniere. They patrol the city; if they see (or hear) you stealing, collecting the pizzo, shooting or hitting
 * one of them, they come after you. Lose them for 20 s and it's over. If they catch you: a fine for small things,
 * the cell in the caserma (and your weapons confiscated) for the serious ones.
 */
public final class Carabinieri {
	private Carabinieri() {}

	public enum Crime {
		THEFT(1, 28), ROBBERY(3, 52), PIZZO(2, 40), SHOOTING(2, 56), ASSAULT(3, 64), CASCO(1, 20), VANDALISM(1, 32);
		final int gravity, witnessRange;
		Crime(int gravity, int witnessRange) { this.gravity = gravity; this.witnessRange = witnessRange; }
		String id() { return name().toLowerCase(); }
	}

	static final BlockPos POST = NapoliDistrict.COMANDO_DOOR;    // in front of the Comando
	static final BlockPos PIAZZA = new BlockPos(0, 64, 22);
	static final java.util.List<BlockPos> CELLS = NapoliDistrict.CELLS;
	static final BlockPos RELEASE = NapoliDistrict.PRISON_GATE;
	static final int PATROL = 4;
	static final int JAIL_SECONDS = 90, FINE = 15;

	private static final class Wanted {
		int gravity; long lastSeen; int grip; Crime crime;
		Wanted(Crime c, long now) { crime = c; gravity = c.gravity; lastSeen = now; }
	}
	private record Jail(long until, GameType previous, BlockPos cell) {
		static final Codec<Jail> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("until").forGetter(Jail::until),
				GameType.CODEC.fieldOf("previous").forGetter(Jail::previous),
				BlockPos.CODEC.fieldOf("cell").forGetter(Jail::cell)).apply(i, Jail::new));
	}

	private static final Map<UUID, Wanted> WANTED = new HashMap<>();
	private static final AttachmentType<Jail> JAIL = AttachmentRegistry.<Jail>builder()
			.persistent(Jail.CODEC).copyOnDeath().buildAndRegister(Napuli.id("jail"));
	/** the scooter boys locked up after a helmet stop: let out (despawned) after a while */
	private static final Map<UUID, Long> JAILED_NPC = new HashMap<>();
	private static int nextCell;

	public static void register() {
		CityProperty.register();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof NapoliResident npc && npc.role() == NapoliResident.Role.CARABINIERE) PoliceTactics.stop(npc);
		});
		// no zombies, skeletons, creepers, phantoms... in the city: dark rooms in palaces and churches spawned them.
		// Only our own creatures (Munaciello, the giant) may haunt Naples.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.dimension() == NapoliMap.LEVEL && entity instanceof net.minecraft.world.entity.monster.Enemy
					&& !"napuli".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace()))
				entity.discard();
		});
		// opening the residents' chests and barrels = stealing
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer p && level.dimension() == NapoliMap.LEVEL && !p.isCreative()
					&& level.getBlockEntity(hit.getBlockPos()) instanceof BaseContainerBlockEntity&&!CityProperty.storageAllowed(p,hit.getBlockPos()))
				crime(p, Crime.THEFT);
			return InteractionResult.PASS;
		});
		// hitting a carabiniere
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer p && entity instanceof NapoliResident npc && npc.role() == NapoliResident.Role.CARABINIERE) {
				crime(p, Crime.ASSAULT);
				p.sendOverlayMessage(Component.translatable("napuli.cc.assault"));
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean isWanted(ServerPlayer p) { return WANTED.containsKey(p.getUUID()); }

	/** for the HUD: 0 = not wanted, else the gravity (stars) */
	static int wantedStars(ServerPlayer p) { Wanted w = WANTED.get(p.getUUID()); return w == null ? 0 : w.gravity; }
	/** seconds before the carabinieri give up (they lose you after 20 s out of sight) */
	static int wantedSecondsLeft(ServerPlayer p) {
		Wanted w = WANTED.get(p.getUUID());
		return w == null ? 0 : (int) Math.max(0, 20 - (p.level().getGameTime() - w.lastSeen) / 20);
	}
	static int jailSecondsLeft(ServerPlayer p) {
		Jail j = p.getAttached(JAIL);
		return j == null ? 0 : (int) Math.max(0, (j.until() - p.level().getGameTime()) / 20);
	}

	/** is this carabiniere busy chasing someone (then he doesn't wander around) */
	static boolean chasing(ServerLevel level, NapoliResident cc) {
		if (PoliceTactics.active(cc)) return true;
		for (ServerPlayer p : level.players())
			if (WANTED.containsKey(p.getUUID()) && p.distanceToSqr(cc) < PoliceTactics.RANGE * PoliceTactics.RANGE) return true;
		return false;
	}
	public static boolean isJailed(ServerPlayer p) { return p.hasAttached(JAIL); }

	/** A crime happened. Was it seen? Then they're coming. */
	public static void crime(ServerPlayer p, Crime c) {
		if (!(p.level() instanceof ServerLevel level) || level.dimension() != NapoliMap.LEVEL || isJailed(p)) return;
		long now = level.getGameTime();
		boolean seen = !witnesses(level, p, c.witnessRange).isEmpty();
		boolean called = !seen && p.getRandom().nextFloat() < 0.3f; // "na vecchia ha chiammato 'o 112"
		if (!seen && !called) return;
		Wanted w = WANTED.get(p.getUUID());
		if (w == null) {
			WANTED.put(p.getUUID(), new Wanted(c, now));
			title(p, "napuli.cc.title", seen ? "napuli.cc.seen." + c.id() : "napuli.cc.called");
			siren(level, p);
			if (called) reinforcements(level, p);
		} else if (c.gravity > w.gravity) {
			w.gravity = c.gravity;
			w.crime = c;
			w.lastSeen = now;
			NapoliNotice.show(p, Component.translatable("napuli.cc.worse"));
		} else {
			w.lastSeen = now;
		}
	}

	static List<NapoliResident> carabinieri(ServerLevel level, BlockPos around, double range) {
		return level.getEntitiesOfClass(NapoliResident.class, new AABB(around).inflate(range),
				r -> r.role() == NapoliResident.Role.CARABINIERE && r.isAlive());
	}

	private static List<NapoliResident> witnesses(ServerLevel level, ServerPlayer p, int range) {
		List<NapoliResident> out = new ArrayList<>();
		for (NapoliResident cc : carabinieri(level, p.blockPosition(), range))
			if (range >= 48 || cc.hasLineOfSight(p)) out.add(cc); // gunshots are heard, everything else must be seen
		return out;
	}

	private static void reinforcements(ServerLevel level, ServerPlayer p) {
		for (int i = 0; i < 2; i++) {
			BlockPos at = Spawner.groundNear(level, p, 22, 30);
			if (at != null) NapoliResident.spawn(level, NapoliResident.Role.CARABINIERE, at, 0);
		}
	}

	// ---------------------------------------------------------------- every tick
	public static void tick(MinecraftServer server) {
		ServerLevel city = server.getLevel(NapoliMap.LEVEL);
		if (city == null) return;
		long now = city.getGameTime();
		if (now % 40 == 0 && !city.players().isEmpty()) NapoliMap.upgradeIfBuilt(city);
		// A logout, death, server restart or dimension change must not erase a prison sentence.
		for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
			Jail j = p.getAttached(JAIL);
			if (j != null && p.isAlive()) serve(city, p, j, now);
		}
		for (ServerPlayer p : List.copyOf(city.players())) {
			if (isJailed(p)) continue;
			Wanted w = WANTED.get(p.getUUID());
			if (w != null) chase(city, p, w, now);
		}
		// players who left the city stop being wanted (they got away), jail time keeps counting if they log out
		WANTED.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null
				|| server.getPlayerList().getPlayer(id).level() != city);
		for (var entity : city.getAllEntities()) if (entity instanceof NapoliResident cc && cc.role() == NapoliResident.Role.CARABINIERE) {
			boolean pursuing = false;
			for (ServerPlayer player : city.players()) if (WANTED.containsKey(player.getUUID()) && PoliceTactics.assignedTo(cc, player)) { pursuing = true; break; }
			if (!pursuing) PoliceTactics.stop(cc);
		}
		if (now % 100 == 0 && !city.players().isEmpty()) keepPatrol(city);
		if (now % 20 == 0) helmetCheck(city);
		if (now % 100 == 0) JAILED_NPC.entrySet().removeIf(e -> {
			if (now < e.getValue()) return false;
			var npc = city.getEntity(e.getKey());
			if (npc != null) npc.discard();
			return true;
		});
	}

	private static void chase(ServerLevel city, ServerPlayer p, Wanted w, long now) {
		List<NapoliResident> near = carabinieri(city, p.blockPosition(), PoliceTactics.RANGE);
		boolean caught = false;
		for (NapoliResident cc : near) {
			if (!PoliceTactics.assignedTo(cc, p)) continue;
			PoliceTactics.pursue(cc, p, now);
			boolean visible = cc.hasLineOfSight(p);
			if (visible || PoliceTactics.breaching(cc)) w.lastSeen = now;
            if (PoliceTactics.canGrab(cc, p)) caught = true;
		}
		if (now % 40 == 0) siren(city, p);
		w.grip = caught ? w.grip + 1 : Math.max(0, w.grip - 2);
		if (w.grip >= 25) { arrest(city, p, w); return; }
		if (now - w.lastSeen > 20 * 20) {
			WANTED.remove(p.getUUID());
			title(p, "napuli.cc.lost.title", "napuli.cc.lost");
		}
	}

	private static void arrest(ServerLevel city, ServerPlayer p, Wanted w) {
		WANTED.remove(p.getUUID());
		for (NapoliResident cc : carabinieri(city, p.blockPosition(), 36)) cc.getNavigation().stop();
		NapoliNotice.show(p, Component.translatable("napuli.cc.reason", Component.translatable("napuli.cc.crime." + w.crime.id())));
		if (w.gravity <= 1 && NapoliStreet.cash(p) >= FINE) {
			NapoliStreet.credit(p, -FINE);
			title(p, "napuli.cc.fine.title", "napuli.cc.fine");
			NapoliNotice.show(p, Component.translatable("napuli.cc.fine.reason", Component.translatable("napuli.cc.crime." + w.crime.id()), FINE));
			return;
		}
		// jail: weapons confiscated, into the cell, adventure mode so the walls can't be dug
		int taken = 0;
		for (Item weapon : new Item[] {Napuli.PISTOL, Napuli.KNIFE, Napuli.KNUCKLES})
			for (int i = 0; i < p.getInventory().getContainerSize(); i++)
				if (p.getInventory().getItem(i).is(weapon)) { p.getInventory().setItem(i, ItemStack.EMPTY); taken++; }
		int seconds = JAIL_SECONDS * Math.max(1, w.gravity - 1);
		if (!imprison(city, p, seconds)) return;
		title(p, "napuli.cc.jail.title", "napuli.cc.jail");
		NapoliNotice.show(p, Component.translatable("napuli.cc.jail.chat", seconds, taken));
		Napuli.LOGGER.info("[cc] {} jailed for {} s ({} weapons taken)", p.getName().getString(), seconds, taken);
	}

	/** Tax refusal is explicit, and never confiscates weapons, armor or tools. */
	static boolean jailForTaxEvasion(ServerPlayer p) {
		ServerLevel city = p.level().getServer().getLevel(NapoliMap.LEVEL);
		if (city == null) {
			NapoliNotice.show(p, Component.translatable("napuli.city.unavailable"));
			return false;
		}
		NapoliMap.ensureBuilt(city);
		if (!imprison(city, p, JAIL_SECONDS)) return false;
		WANTED.remove(p.getUUID());
		title(p, "napuli.cc.jail.title", "napuli.tax.evasion");
		NapoliNotice.show(p, Component.translatable("napuli.tax.jailed", JAIL_SECONDS));
		return true;
	}

	private static boolean imprison(ServerLevel city, ServerPlayer p, int seconds) {
		BlockPos cell = CELLS.get(p.getRandom().nextInt(CELLS.size()));
		city.getChunkAt(cell);
		GameType previous = p.gameMode();
		if (!p.teleportTo(city, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5,
				Set.of(), cell.getX() < 136 ? -90 : 90, 0, true)) return false;
		p.setAttached(JAIL, new Jail(city.getGameTime() + 20L * seconds, previous, cell));
		p.setGameMode(GameType.ADVENTURE);
		return true;
	}

	private static void serve(ServerLevel city, ServerPlayer p, Jail j, long now) {
		if (p.level() != city || p.blockPosition().distSqr(j.cell()) > 9) // no escaping: back in the cell
			p.teleportTo(city, j.cell().getX() + 0.5, j.cell().getY(), j.cell().getZ() + 0.5, Set.of(), p.getYRot(), 0, true);
		long left = (j.until() - now) / 20;
		if (now % 20 == 0) p.sendOverlayMessage(Component.translatable("napuli.cc.jail.left", left));
		if (now < j.until()) return;
		p.removeAttached(JAIL);
		p.setGameMode(j.previous());
		p.teleportTo(city, RELEASE.getX() + 0.5, RELEASE.getY(), RELEASE.getZ() + 0.5, Set.of(), 180, 0, true);
		title(p, "napuli.cc.free.title", "napuli.cc.free");
	}

	/** keep a few patrols around: two at the caserma, two in the piazza */
	private static void keepPatrol(ServerLevel city) {
		int have = city.getEntities(Napuli.RESIDENT, r -> r.role() == NapoliResident.Role.CARABINIERE).size();
		for (int i = have; i < PATROL; i++) {
			BlockPos base = i % 2 == 0 ? POST : PIAZZA;
			if (!city.isLoaded(base)) continue;
			NapoliResident.spawn(city, NapoliResident.Role.CARABINIERE, base.offset(i, 0, 0), 180);
		}
	}

	/** for /napuli libera: out of the cell now */
	public static boolean release(ServerPlayer p) {
		Jail j = p.getAttached(JAIL);
		ServerLevel city = p.level().getServer().getLevel(NapoliMap.LEVEL);
		if (j == null || city == null) return false;
		serve(city, p, new Jail(0, j.previous(), j.cell()), city.getGameTime());
		return true;
	}

	/** for /napuli carabinieri: get yourself reported */
	public static void report(ServerPlayer p, Crime c) {
		long now = p.level().getGameTime();
		WANTED.put(p.getUUID(), new Wanted(c, now));
		title(p, "napuli.cc.title", "napuli.cc.seen." + c.id());
		if (p.level() instanceof ServerLevel level) { siren(level, p); reinforcements(level, p); }
	}

	// ---------------------------------------------------------------- 'o casco
	/** a player riding a motorino without a helmet under the eyes of a carabiniere: that's a (small) crime */
	private static void helmetCheck(ServerLevel city) {
		for (ServerPlayer p : city.players()) {
			if (isWanted(p) || isJailed(p) || p.isSpectator() || !(p.getVehicle() instanceof Motorino bike) || bike.getFirstPassenger() != p) continue;
			if (!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) continue;
			boolean seen = false;
			for (NapoliResident cc : carabinieri(city, p.blockPosition(), Crime.CASCO.witnessRange)) if (cc.hasLineOfSight(p)) seen = true;
			if (seen) crime(p, Crime.CASCO);
		}
	}

	/** the parade boys have no helmet and a carabiniere saw them: whistle, the scooter stops, the music dies */
	static void stopParade(ServerLevel level, Motorino bike, NapoliResident cc) {
		level.playSound(null, bike.getX(), bike.getY(), bike.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.HOSTILE, 3f, 2f);
		level.playSound(null, bike.getX(), bike.getY(), bike.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 2f, 1.6f);
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(bike) < 48 * 48)) {
			p.sendSystemMessage(Component.translatable("napuli.casco.alt"));
			p.sendOverlayMessage(Component.translatable("napuli.casco.alt.bar"));
		}
		Napuli.LOGGER.info("[cc] parade stopped: riders without a helmet, carabiniere at {}", cc.blockPosition());
	}

	/** the boys get off the scooter and end up in the cells of the Comando; the scooter is seized */
	static void jailRiders(ServerLevel level, Motorino bike, NapoliResident cc) {
		long now = level.getGameTime();
		int jailed = 0;
		for (net.minecraft.world.entity.Entity e : List.copyOf(bike.getPassengers())) {
			e.stopRiding();
			if (!(e instanceof NapoliResident npc)) continue;
			BlockPos cell = CELLS.get(nextCell++ % CELLS.size());
			level.getChunkAt(cell);
			npc.setNoAi(false);
			npc.relocate(cell, 0);
			npc.setHomeTo(cell, 1);
			JAILED_NPC.put(npc.getUUID(), now + 20 * 180);
			jailed++;
		}
		cc.getNavigation().stop();
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(bike) < 48 * 48)) {
			NapoliNotice.show(p, Component.translatable("napuli.casco.carcere", jailed));
			title(p, "napuli.casco.carcere.title", "napuli.casco.carcere.sub");
		}
		level.playSound(null, bike.getX(), bike.getY(), bike.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 1.5f, 0.8f);
		Napuli.LOGGER.info("[cc] {} scooter boys jailed", jailed);
	}

	public static boolean npcJailed(UUID id) { return JAILED_NPC.containsKey(id); }

	private static void siren(ServerLevel level, ServerPlayer p) {
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1.4f, 1.6f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1.4f, 1.2f);
	}

	static void title(ServerPlayer p, String title, String sub) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title)));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub)));
	}

	@SuppressWarnings("unused") private static Player unused;
}
