package dev.napuli.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;

/** Real world screenshots, saved in the user's normal Minecraft screenshot directory. */
final class PhonePhoto {
    private static PhoneScreen back;
    private static int wait;
    static void capture(PhoneScreen screen){back=screen;wait=4;}
    static void openFolder(net.minecraft.client.Minecraft mc){Thread.ofVirtual().name("Napuli photos folder").start(()->{
        try{var directory=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(directory);java.awt.Desktop.getDesktop().open(directory.toFile());}
        catch(Exception ignored){mc.execute(()->{if(mc.player!=null)mc.player.sendSystemMessage(dev.napuli.CityText.t("phone.photos_help"));});}
    });}
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(mc->{if(back==null)return;if(mc.player==null){back=null;return;}if(--wait>0)return;
        var screen=back;back=null;Screenshot.grab(mc.gameDirectory,"telefono-"+System.currentTimeMillis()+".png",mc.gameRenderer.mainRenderTarget(),1,m->{});mc.gui.setScreen(screen);
    });}
    private PhonePhoto(){}
}
