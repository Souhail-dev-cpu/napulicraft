package dev.napuli;

import net.minecraft.network.chat.Component;

/** Keep translated components intact until the receiving client's language is known. */
public final class CityText {
    public static Component t(String key, Object... args) { return Component.translatable("napuli.ui." + key, args); }
    private CityText() {}
}
