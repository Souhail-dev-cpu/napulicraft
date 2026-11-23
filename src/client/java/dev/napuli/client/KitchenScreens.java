package dev.napuli.client;

import dev.napuli.KitchenMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class KitchenScreens {
    private KitchenScreens() {}

    static void register() {
        MenuScreens.<net.minecraft.world.inventory.FurnaceMenu, HeatScreen>register(KitchenMenus.OVEN,
                (menu, inventory, title) -> new HeatScreen((KitchenMenus.Heat) menu, inventory, title));
        MenuScreens.<net.minecraft.world.inventory.FurnaceMenu, HeatScreen>register(KitchenMenus.FRYER,
                (menu, inventory, title) -> new HeatScreen((KitchenMenus.Heat) menu, inventory, title));
        MenuScreens.<net.minecraft.world.inventory.CraftingMenu, GridScreen>register(KitchenMenus.PIZZA_TABLE,
                (menu, inventory, title) -> new GridScreen((KitchenMenus.Grid) menu, inventory, title));
        MenuScreens.<net.minecraft.world.inventory.CraftingMenu, GridScreen>register(KitchenMenus.COFFEE,
                (menu, inventory, title) -> new GridScreen((KitchenMenus.Grid) menu, inventory, title));
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int border, int body) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xff252026);
        g.fill(x, y, x + width, y + height, border);
        g.fill(x + 3, y + 3, x + width - 3, y + height - 3, body);
        g.fill(x + 4, y + 17, x + width - 4, y + 19, border);
    }

    private static void well(GuiGraphicsExtractor g, int x, int y, int size, int rim, int inset) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, rim);
        g.fill(x, y, x + size, y + size, inset);
    }

    private static void inventory(GuiGraphicsExtractor g, int x, int y, int rim, int inset) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            well(g, x + 8 + col * 18, y + 84 + row * 18, 16, rim, inset);
        for (int col = 0; col < 9; col++) well(g, x + 8 + col * 18, y + 142, 16, rim, inset);
    }

    private static final class HeatScreen extends FurnaceScreen {
        private final KitchenMenus.Heat station;

        HeatScreen(KitchenMenus.Heat menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            station = menu;
        }

        @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = leftPos, y = topPos;
            boolean wood = station.woodOven();
            int frame = wood ? 0xff914a39 : 0xff446b73;
            int body = wood ? 0xffedceb2 : 0xffbbd2cd;
            int dark = wood ? 0xff573931 : 0xff29444b;
            int slot = wood ? 0xffffe9c9 : 0xffe6f3e8;
            int bright = wood ? 0xffff9a3d : 0xffdca649;
            panel(g, x, y, imageWidth, imageHeight, frame, body);
            g.fill(x + 49, y + 20, x + 78, y + 67, frame);
            if (wood) {
                for (int j = 26; j <= 62; j += 9) g.fill(x + 49, y + j, x + 78, y + j + 1, body);
            } else {
                g.fill(x + 52, y + 32, x + 75, y + 64, bright);
            }
            well(g, x + 56, y + 17, 16, dark, slot);
            well(g, x + 56, y + 53, 16, dark, slot);
            well(g, x + 108, y + 31, 24, frame, slot);
            int flame = Math.round(14 * station.getLitProgress());
            g.fill(x + 58, y + 50 - flame, x + 70, y + 50, bright);
            g.fill(x + 82, y + 39, x + 103, y + 44, dark);
            g.fill(x + 83, y + 40, x + 83 + Math.round(19 * station.getBurnProgress()), y + 43, bright);
            inventory(g, x, y, frame, slot);
            var font = Minecraft.getInstance().font;
            g.text(font, Component.translatable(wood ? "napuli.station.logs" : "napuli.station.oil"),
                    x + 81, y + 55, dark, false);
        }
    }

    private static final class GridScreen extends CraftingScreen {
        private final boolean coffee;

        GridScreen(KitchenMenus.Grid menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            coffee = menu.coffee();
        }

        @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = leftPos, y = topPos;
            int frame = coffee ? 0xff774c3d : 0xff42685c;
            int body = coffee ? 0xffdbb895 : 0xffd6dfc8;
            int slot = coffee ? 0xfff3debe : 0xfff1f4dd;
            panel(g, x, y, imageWidth, imageHeight, frame, body);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
                well(g, x + 30 + col * 18, y + 17 + row * 18, 16, frame, slot);
            well(g, x + 120, y + 30, 24, frame, slot);
            g.fill(x + 89, y + 40, x + 110, y + 44, frame);
            g.fill(x + 106, y + 37, x + 111, y + 47, frame);
            inventory(g, x, y, frame, slot);
        }
    }
}
