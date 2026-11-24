package dev.napuli.client;

import dev.napuli.Hud;
import static dev.napuli.CityText.t;
import dev.napuli.NapoliEmployment;
import dev.napuli.Napuli;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Right side of the screen, GTA/RP style:
 *  - the panel: your name, your money, carabinieri stars, reputation with both clans, your clan and rank in it
 *  - under it the mission tracker: the objective, an arrow towards it and how far it is
 * Key H hides/shows it.
 */
final class HudClient {
	private HudClient() {}

	static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.hud",
			InputConstants.KEY_H, KeyMapping.Category.register(Napuli.id("hud"))));
	private static Hud.State state;
    private static dev.napuli.CompanionHud companion;
	private static boolean hidden;
	private static int shownCash = -1;
	private static int cashFlash, cashDelta;

	/** clan ranks by reputation with your clan */
	static final int[] RANK_AT = {4, 12, 24, 40, 60, 85};
	static final String[] RANKS = {"Muschillo", "Guaglione", "Soldato", "Capozona", "Braccio destro", "'O Boss"};

	static String rank(int rep) {
		String r = null;
		for (int i = 0; i < RANK_AT.length; i++) if (rep >= RANK_AT[i]) r = RANKS[i];
		return r;
	}

	static int nextRankAt(int rep) {
		for (int at : RANK_AT) if (rep < at) return at;
		return -1;
	}

	static void register() {
        ClientPlayNetworking.registerGlobalReceiver(dev.napuli.CompanionHud.TYPE,(s,ctx)->companion=s);
		ClientPlayNetworking.registerGlobalReceiver(Hud.State.TYPE, (s, ctx) -> {
			if (shownCash >= 0 && s.cash() != shownCash) { cashDelta = s.cash() - shownCash; cashFlash = 60; }
			shownCash = s.cash();
			state = s;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (TOGGLE.consumeClick()) hidden = !hidden;
			if (cashFlash > 0) cashFlash--;
			if (mc.level == null) { state = null; companion=null; shownCash = -1; }
		});
		HudElementRegistry.addLast(Napuli.id("napoli_panel"), HudClient::draw);
	}

	static Hud.State state() { return state; }
    static Component companionText() { return companion==null?Component.empty():companion.text(); }

	private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Hud.State s = state;
		if (hidden || s == null || mc.player == null || mc.gui.screen() instanceof CompanionScreen || mc.gui.screen() instanceof FootballMovesScreen) return;
        float scale = CompanionOptions.current.scale() * Math.min(1f, Math.max(.75f, g.guiHeight() / 240f));
        g.pose().pushMatrix();
        g.pose().scale(scale);
        try { drawScaled(g, s, mc, scale); } finally { g.pose().popMatrix(); }
    }

    private static void drawScaled(GuiGraphicsExtractor g, Hud.State s, Minecraft mc, float scale) {
        var preferences = CompanionOptions.current;
        if (preferences.kitchen) drawKitchen(g, mc.font, s, scale);
		if (!s.inCity()) return;
		Font f = mc.font;
		int canvasWidth = (int)(g.guiWidth() / scale);
		int w = Math.clamp(canvasWidth / 3, 116, 170), x = canvasWidth - w - 4;
        int y = Math.max(54, (int)Math.ceil(NapoliNoticeToast.occupiedHeight() / scale) + 10);
		int lh = 11;

		// ---------------- the panel
        if (preferences.city) {
		int accent = 0xff58c4ed;
		boolean reputation = !preferences.compact && (s.lanterns() != 0 || s.tides() != 0 || s.ally() >= 0);
		int lines = 1 + (reputation ? 7 + (s.ally() >= 0 ? 1 : 0) : 2) + (s.stars() > 0 ? 1 : 0);
		card(g, x - 5, y - 5, x + w, y + lines * lh + 2, accent);
		g.text(f, mc.player.getName().getString(), x, y, 0xFFFFFFFF, true);
		y += lh + 2;
		g.text(f, "€ " + s.cash(), x, y, 0xFF66BB6A, true);
		if (cashFlash > 0) {
			String d = (cashDelta > 0 ? "+" : "") + cashDelta;
			g.text(f, d, x + f.width("€ " + s.cash()) + 6, y, (cashDelta > 0 ? 0x66BB6A : 0xEF5350) | (Math.min(255, cashFlash * 8) << 24), true);
		}
		y += lh;
		g.text(f, Component.translatable("napuli.hud.clock", Hud.clock(s.cityTime())), x, y, 0xffb5dfed, false);
		y += lh;
		if (s.stars() > 0) {
			StringBuilder st = new StringBuilder();
			for (int i = 0; i < 3; i++) st.append(i < s.stars() ? '★' : '☆');
			boolean blink = CompanionOptions.current.reducedFlashes || (mc.player.tickCount / 8) % 2 == 0;
			g.text(f, st + " " + s.wantedLeft() + "s", x, y, blink ? 0xFFFF5252 : 0xFF448AFF, true);
			y += lh;
		}
		if (reputation) {
		y += 3;
		g.text(f, t("hud.reputation"), x, y, 0xFFBDBDBD, false);
		y += lh;
		bar(g, f, x, y, w - 4, "Lanterne", s.lanterns(), 0xFFFFB300);
		y += lh;
		bar(g, f, x, y, w - 4, "Maree", s.tides(), 0xFF29B6F6);
		y += lh + 2;
		if (s.ally() >= 0) {
			int rep = s.ally() == 0 ? s.lanterns() : s.tides();
			String clan = s.ally() == 0 ? "'E Lanterne" : "'E Maree";
			g.text(f, clan, x, y, s.ally() == 0 ? 0xFFFFB300 : 0xFF29B6F6, true);
			y += lh;
			int next = nextRankAt(rep);
			String r = rank(rep) + (next > 0 ? "  (" + rep + "/" + next + ")" : "");
			g.text(f, t("hud.rank",r), x, y, 0xFFFFFFFF, true);
		} else {
			g.text(f, t("hud.unaligned"), x, y, 0xFF9E9E9E, true);
		}
		y += lh;
		}
        }
		y += 8;
        if (!preferences.tracker && s.jail() <= 0) return;
		if (s.jail() <= 0 && s.tx() == Hud.NO_TARGET && s.objective().equals("none")) return;

		// ---------------- the mission tracker
		String title, line;
		boolean employment = s.objective().startsWith("napuli.work.") || s.objective().startsWith("qol.work:");
		boolean jailed = s.jail() > 0;
		if (jailed) {
			title = t("hud.jailed").getString();
			line = t("hud.release",s.jail()).getString();
		} else {
			title = Component.translatable(employment ? "napuli.hud.work" : "napuli.hud.route").getString();
			String objective = s.objective();
			String key = objective.startsWith("napuli.local.") || objective.startsWith("napuli.work.") || objective.startsWith("napuli.guide.") ? objective : "napuli.hud.obj." + objective;
			line = objective.startsWith("qol:") || objective.startsWith("qol.work:") ? objective.substring(objective.indexOf(':') + 1) : Component.translatable(key, s.cooldown()).getString();
            if(companion!=null&&companion.objective().equals(objective))line=companion.text().getString();
		}
		var allLines = f.split(Component.literal(line), w - 6);
        int availableLines = Math.max(1, ((int)(g.guiHeight() / scale) - y - 52) / 10);
        var wrapped = allLines.subList(0, Math.min(allLines.size(), availableLines));
		boolean shift = employment && !jailed;
		int th = 14 + wrapped.size() * 10 + (shift ? 11 : 0) + (s.tx() != Hud.NO_TARGET && !jailed ? 12 : 0);
				if (employment && !jailed) {
			g.fill(x - 3, y - 3, x + w + 2, y + th + 2, 0x6605070c);
			g.fill(x - 5, y - 5, x + w, y + th, 0xee122927);
			g.fill(x - 5, y - 5, x - 2, y + th, 0xffdfab65);
			g.fill(x - 2, y + 9, x + w, y + 10, 0x88dfab65);
		} else {
			card(g, x - 5, y - 5, x + w, y + th, jailed ? 0xffef5350 : 0xffe6b65c);
		}
		g.text(f, title, x, y, jailed ? 0xFFEF5350 : employment ? 0xffffe1a9 : 0xFFFFD54F, true);
		y += 12;
		for (var l : wrapped) { g.text(f, l, x, y, 0xFFFFFFFF, true); y += 10; }
        if (shift) {
            boolean open = s.cityTime() >= NapoliEmployment.OPEN && s.cityTime() < NapoliEmployment.CLOSE;
            g.text(f, Component.translatable("napuli.hud.shift", Hud.clock(NapoliEmployment.OPEN),
                    Hud.clock(NapoliEmployment.CLOSE)), x, y, open ? 0xff8add91 : 0xffffad81, false);
            y += 11;
        }
		if (s.tx() != Hud.NO_TARGET && !jailed) {
			double dx = s.tx() + 0.5 - mc.player.getX(), dz = s.tz() + 0.5 - mc.player.getZ();
			int dist = (int) Math.round(Math.hypot(dx, dz));
			// angle of the target relative to where you look: 0 = straight ahead
			double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
			double rel = net.minecraft.util.Mth.wrapDegrees(targetYaw - mc.player.getYRot());
			String arrow = dist < 4 ? "●" : ARROWS[(int) Math.floorMod(Math.round(rel / 45.0), 8)];
			g.text(f, arrow + " " + (dist < 4 ? t("hud.arrived").getString() : dist + " m"), x, y + 1, dist < 4 ? 0xFF66BB6A : 0xFFFFD54F, true);
		}
	}

	private static void drawKitchen(GuiGraphicsExtractor g, Font font, Hud.State state, float scale) {
		int x = 6, y = 8, width = Math.clamp((int)(g.guiWidth() / scale) / 4, 85, 108);
		boolean pizzas = state.pizzas() > 0, hot = state.heat() > 0;
		int bottom = y + 20 + (pizzas ? 11 : 0) + (hot ? 13 : 0) + (state.inCity() ? 13 : 0);
		card(g, x - 4, y - 4, x + width + 5, bottom, 0xffe6b65c);
		g.text(font, Component.translatable("napuli.kitchen.hunger"), x, y, 0xFFFFD180, true);
		g.fill(x, y + 10, x + width, y + 14, 0xFF383838);
		g.fill(x, y + 10, x + Math.clamp(state.fullness(), 0, 60) * width / 60, y + 14,
				state.fullness() >= 50 ? 0xFFEF5350 : 0xFFFFB74D);
		y += 17;
		if (pizzas) {
			g.text(font, Component.translatable("napuli.kitchen.pizzas", state.pizzas()), x, y, 0xFFFFFFFF, false);
			y += 11;
		}
		if (hot) {
			g.text(font, Component.translatable("napuli.kitchen.heat"), x, y, 0xFFBDBDBD, false);
			g.fill(x + 40, y + 2, x + width, y + 6, 0xFF383838);
			g.fill(x + 40, y + 2, x + 40 + Math.clamp(state.heat(), 0, 100) * (width - 40) / 100, y + 6, 0xFFFF7043);
			y += 13;
		}
		if (state.inCity()) {
			g.text(font, Component.translatable("napuli.hygiene.label"), x, y, 0xffb5dfed, false);
			int hygiene = Math.clamp(state.hygiene(), 0, 100);
			g.fill(x + 40, y + 2, x + width, y + 6, 0xff354b58);
			g.fill(x + 40, y + 2, x + 40 + (width - 40) * hygiene / 100, y + 6,
					hygiene <= 25 ? 0xffec7857 : 0xff59c7da);
		}
	}

	private static void card(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int accent) {
		g.fill(left + 2, top + 2, right + 2, bottom + 2, 0x6605070c);
		g.fill(left, top, right, bottom, 0xe815222e);
		g.fill(left, top, right, top + 2, accent);
		g.fill(left, bottom - 1, right, bottom, 0xff456071);
	}

	// straight ahead, ahead-right, right, behind-right, behind, behind-left, left, ahead-left
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static void bar(GuiGraphicsExtractor g, Font f, int x, int y, int w, String name, int rep, int color) {
		g.text(f, name, x, y, 0xFFE0E0E0, false);
		int bx = x + 52, bw = w - 52 - 26, mid = bx + bw / 2;
		g.fill(bx, y + 2, bx + bw, y + 7, 0xFF333333);
		int len = (int) Math.round(Math.abs(rep) / 100.0 * (bw / 2.0));
		if (rep >= 0) g.fill(mid, y + 2, mid + len, y + 7, color);
		else g.fill(mid - len, y + 2, mid, y + 7, 0xFFEF5350);
		g.fill(mid, y + 1, mid + 1, y + 8, 0xFFFFFFFF);
		String v = String.valueOf(rep);
		g.text(f, v, x + w - f.width(v), y, rep >= 0 ? 0xFFFFFFFF : 0xFFEF5350, false);
	}
}
