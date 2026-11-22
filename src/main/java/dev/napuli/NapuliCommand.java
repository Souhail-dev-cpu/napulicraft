package dev.napuli;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/** /napuli ... to trigger everything on demand (and for testing). */
public final class NapuliCommand {
	private NapuliCommand() {}

	/** /napuli carabinieri furto|pizzo|spari|aggressione: get reported right now */
	private static LiteralArgumentBuilder<CommandSourceStack> carabinieri() {
		LiteralArgumentBuilder<CommandSourceStack> cc = Commands.literal("carabinieri");
		String[] names = {"furto", "pizzo", "spari", "aggressione"};
		for (int i = 0; i < names.length; i++) {
			Carabinieri.Crime crime = Carabinieri.Crime.values()[i];
			cc.then(Commands.literal(names[i]).executes(c -> { Carabinieri.report(c.getSource().getPlayerOrException(), crime); return 1; }));
		}
		return cc;
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		NapoliMap.register(d);
		LiteralArgumentBuilder<CommandSourceStack> canzone = Commands.literal("canzone").executes(c -> {
			Spawner.playSong(c.getSource().getServer());
			return 1;
		});
		for (Music.Situation s : Music.Situation.values())
			canzone.then(Commands.literal(s.id()).executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				Music.stop(p);
				boolean ok = Music.play(p, s);
				c.getSource().sendSuccess(() -> Component.literal(ok ? "♪ " + s.id() : "Aspetta nu poco (pausa tra le canzoni)"), false);
				return 1;
			}));

		d.register(Commands.literal("napuli").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(canzone)
				.then(Commands.literal("napoli").executes(c -> NapoliMap.visit(c.getSource().getPlayerOrException())))
				.then(Commands.literal("corteo").executes(c -> NapoliScenes.motorino(c.getSource().getPlayerOrException())))
				.then(Commands.literal("stop").executes(c -> { Music.stop(c.getSource().getPlayerOrException()); return 1; }))
				.then(carabinieri())
				.then(Commands.literal("meloni").executes(c -> GiorgiaMeloni.summon(c.getSource().getPlayerOrException())))
				.then(Commands.literal("peppe").executes(c -> PeppeFetish.visit(c.getSource().getPlayerOrException())))
				.then(Commands.literal("libera").executes(c -> Carabinieri.release(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("munaciello").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					boolean ok = Spawner.spawnMunaciello(p.level(), p);
					c.getSource().sendSuccess(() -> Component.literal(ok ? "'O Munaciello sta venenno..." : "Nun ce sta spazio."), false);
					return 1;
				}))
				.then(Commands.literal("gigante").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					boolean ok = Spawner.spawnGigante(p.level(), p);
					c.getSource().sendSuccess(() -> Component.literal(ok ? "Guarda attuorno..." : "Nun ce sta spazio."), false);
					return 1;
				}))
				.then(Commands.literal("mamma").executes(c -> {
					Situazioni.chiamaMamma(c.getSource().getPlayerOrException(), 20);
					return 1;
				}))
				.then(Commands.literal("ciabatta").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Situazioni.lanciaCiabatta(p, p.level());
					return 1;
				}))
				.then(Commands.literal("motorino").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					p.getInventory().add(new ItemStack(Napuli.MOTORINO_ITEM));
					return 1;
				})));
	}

	@SuppressWarnings("unused") private static EntitySpawnReason unused;
}
