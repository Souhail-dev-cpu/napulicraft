package dev.napuli.client;

import dev.napuli.Radio;
import dev.napuli.Motorino;
import dev.napuli.client.music.RadioStations;
import static dev.napuli.CityText.t;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The radio wheel (GTA V): stations in a ring around the centre, "Radio spenta" on top. Point at a station with the
 * mouse and let go of R (or click) to tune in. The centre shows the hovered station and its DJ.
 */
final class RadioWheelScreen extends Screen {
	private int hovered = -2; // -2 nothing, -1 = off, 0.. = station
	private int mouseX, mouseY;
	private int clicked = -2;
	private long lastClick;
	private int keyboardSelection = -2;

	RadioWheelScreen() { super(Component.translatable("napuli.radio.wheel")); RadioClient.reload(); }

	@Override public boolean isPauseScreen() { return false; }

	/** slot 0 is "off", then the stations clockwise */
	private int slots() { return RadioClient.stations().size() + 1; }

	private double[] slotPos(int slot, int radius) {
		double a = -Math.PI / 2 + slot * 2 * Math.PI / slots();
		return new double[] {width / 2.0 + Math.cos(a) * radius, height / 2.0 + Math.sin(a) * radius};
	}

	private int radius() { return Math.max(36, Math.min(95, Math.min(width, height) / 2 - 34)); }

	private int pick(double mx, double my) {
		double dx = mx - width / 2.0, dy = my - height / 2.0;
		if (dx * dx + dy * dy < 24 * 24) return -2;
		double a = Math.atan2(dy, dx) + Math.PI / 2;
		int n = slots();
		int slot = (int) Math.round(a / (2 * Math.PI / n));
		slot = Math.floorMod(slot, n);
		return slot - 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		if (mx != mouseX || my != mouseY) keyboardSelection = -2;
		mouseX = mx; mouseY = my;
		hovered = keyboardSelection == -2 ? pick(mx, my) : keyboardSelection;
		g.fill(0, 0, width, height, 0x88000000);
		List<dev.napuli.client.music.UserMusic.Station> st = RadioClient.stations();
		int current = RadioStations.indexOf(RadioClient.currentStation());
		int r = radius();
		// the ring
		for (int i = 0; i < 64; i++) {
			double ang = i * Math.PI / 32;
			int x = (int) (width / 2 + Math.cos(ang) * r), y = (int) (height / 2 + Math.sin(ang) * r);
			g.fill(x - 1, y - 1, x + 1, y + 1, 0x55FFFFFF);
		}
		for (int slot = 0; slot < slots(); slot++) {
			int station = slot - 1;
			double[] p = slotPos(slot, r);
			boolean hot = station == hovered, on = station == current;
			int size = hot ? 22 : 17;
			int color = station == Radio.OFF ? 0xFF555555 : st.get(station).color();
			int cx = (int) p[0], cy = (int) p[1];
			g.fill(cx - size - 2, cy - size - 2, cx + size + 2, cy + size + 2, hot ? 0xFFFFFFFF : on ? 0xFFFFD54F : 0xAA000000);
			g.fill(cx - size, cy - size, cx + size, cy + size, color);
			g.fill(cx - size + 3, cy - size + 3, cx + size - 3, cy + size - 3, darker(color));
			String logo = station == Radio.OFF ? "OFF" : initials(st.get(station).name());
			g.text(font, logo, cx - font.width(logo) / 2, cy - 4, 0xFFFFFFFF, true);
		}
		// centre: what you are pointing at
		String name, line;
		if (hovered == -2) { name = current == Radio.OFF ? t("radio.off").getString() : current >= 0 && current < st.size() ? st.get(current).name() : ""; line = t("radio.choose").getString(); }
		else if (hovered == Radio.OFF) { name = t("radio.off").getString(); line = t("radio.silence").getString(); }
		else {
			name = st.get(hovered).name();
			if (RadioStations.channelAt(hovered) == Radio.TALK) {
				var player = minecraft.player;
				String live = player != null && player.getVehicle() instanceof Motorino bike && bike.station() == Radio.TALK
						? RadioTalkClient.onAir(bike) : "";
				line = live.isEmpty() ? t("radio.talk").getString() : live;
			} else line = t("radio.station_info", st.get(hovered).dj(), st.get(hovered).songs().size()).getString();
		}
		int centerWidth = Math.max(80, r * 2 - 56);
		g.centeredText(font, NapoliScreen.fit(font, name, centerWidth), width / 2, height / 2 - 10, NapoliScreen.TEXT);
		g.centeredText(font, NapoliScreen.fit(font, line, centerWidth), width / 2, height / 2 + 4, NapoliScreen.MUTED);
		g.centeredText(font, Component.translatable("napuli.radio.wheel"), width / 2, 8, 0xFFFFD54F);
		g.centeredText(font, NapoliScreen.fit(font, t("radio.controls", RadioClient.KEY.getTranslatedKeyMessage()).getString(), width - 16), width / 2, height - 25, NapoliScreen.MUTED);
		g.centeredText(font, NapoliScreen.fit(font, Component.translatable("napuli.radio.skip_hint").getString(), width - 16), width / 2, height - 12, NapoliScreen.MUTED);
	}

	private static int darker(int c) {
		int r = (c >> 16 & 255) * 3 / 4, gg = (c >> 8 & 255) * 3 / 4, b = (c & 255) * 3 / 4;
		return 0xFF000000 | r << 16 | gg << 8 | b;
	}

	private static String initials(String name) {
		StringBuilder sb = new StringBuilder();
		for (String w : name.replace("'", " ").split(" "))
			if (!w.isEmpty() && Character.isUpperCase(w.charAt(0)) && !w.equalsIgnoreCase("Radio")) sb.append(w.charAt(0));
		String s = sb.length() == 0 ? name.substring(0, Math.min(3, name.length())) : sb.toString();
		return s.length() > 3 ? s.substring(0, 3) : s;
	}

	private void choose() {
		int pick = keyboardSelection == -2 ? pick(mouseX, mouseY) : keyboardSelection;
		if (pick != -2) {
			int channel = RadioStations.channelAt(pick);
			if (channel != RadioClient.currentStation()) RadioClient.tune(channel);
		}
		onClose();
	}
	@Override public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key >= 262 && key <= 265) {
			int current = keyboardSelection == -2 ? RadioStations.indexOf(RadioClient.currentStation()) : keyboardSelection;
			int direction = key == 263 || key == 265 ? -1 : 1;
			keyboardSelection = Math.floorMod(current + 1 + direction, slots()) - 1;
			hovered = keyboardSelection;
			triggerImmediateNarration(false);
			return true;
		}
		if (key == 257 || key == 335) { choose(); return true; }
		return super.keyPressed(event);
	}
	@Override public Component getNarrationMessage() {
		var stations = RadioClient.stations();
		Component station = hovered >= 0 && hovered < stations.size() ? Component.literal(stations.get(hovered).name()) : t("radio.off");
		return title.copy().append(". ").append(station).append(". ").append(t("radio.controls", RadioClient.KEY.getTranslatedKeyMessage()));
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (RadioClient.KEY.matches(event)) { if (clicked == -2) choose(); else onClose(); return true; }
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) return false;
		int station = pick(event.x(), event.y());
		if (station == -2) return false;
		int channel = RadioStations.channelAt(station);
		long now = System.nanoTime();
		if (station >= 0 && station == clicked && now - lastClick <= 450_000_000L) {
			RadioClient.requestSkip(channel);
			lastClick = 0;
		} else {
			lastClick = now;
			if (channel != RadioClient.currentStation()) RadioClient.tune(channel);
		}
		clicked = station;
		return true;
	}
}
