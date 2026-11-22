package dev.napuli.client;

import dev.napuli.Radio;
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

	RadioWheelScreen() { super(Component.translatable("napuli.radio.wheel")); RadioClient.reload(); }

	@Override public boolean isPauseScreen() { return false; }

	/** slot 0 is "off", then the stations clockwise */
	private int slots() { return RadioClient.stations().size() + 1; }

	private double[] slotPos(int slot, int radius) {
		double a = -Math.PI / 2 + slot * 2 * Math.PI / slots();
		return new double[] {width / 2.0 + Math.cos(a) * radius, height / 2.0 + Math.sin(a) * radius};
	}

	private int radius() { return Math.min(95, Math.min(width, height) / 2 - 30); }

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
		mouseX = mx; mouseY = my;
		hovered = pick(mx, my);
		g.fill(0, 0, width, height, 0x88000000);
		List<dev.napuli.client.music.UserMusic.Station> st = RadioClient.stations();
		int current = RadioClient.currentStation();
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
		if (hovered == -2) { name = current == Radio.OFF ? "Radio spenta" : current < st.size() ? st.get(current).name() : ""; line = "Punta 'na stazione e lassa R"; }
		else if (hovered == Radio.OFF) { name = "Radio spenta"; line = "Nu poco 'e silenzio"; }
		else { name = st.get(hovered).name(); line = st.get(hovered).dj() + "  ·  " + st.get(hovered).songs().size() + " canzoni"; }
		g.centeredText(font, name, width / 2, height / 2 - 10, 0xFFFFFFFF);
		g.centeredText(font, line, width / 2, height / 2 + 4, 0xFFBBBBBB);
		g.centeredText(font, Component.translatable("napuli.radio.wheel"), width / 2, 8, 0xFFFFD54F);
		g.centeredText(font, Component.translatable("napuli.radio.skip_hint"), width / 2, height - 14, 0xFFEEEEEE);
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
		int pick = pick(mouseX, mouseY);
		if (pick != -2 && pick != RadioClient.currentStation()) RadioClient.tune(pick);
		onClose();
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
		long now = System.nanoTime();
		if (station >= 0 && station == clicked && now - lastClick <= 450_000_000L) {
			RadioClient.requestSkip(station);
			lastClick = 0;
		} else {
			lastClick = now;
			if (station != RadioClient.currentStation()) RadioClient.tune(station);
		}
		clicked = station;
		return true;
	}
}
