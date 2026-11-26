package dev.napuli.client;

import dev.napuli.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class PhoneClient {
    static PhonePacket state;
    static boolean dark;
    private static int ringTicks;
    static final KeyMapping OPEN=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.phone",InputConstants.KEY_P,KeyMapping.Category.register(Napuli.id("phone"))));
    static void register(){
        dark=CompanionOptions.current.phoneDark;
        ClientPlayNetworking.registerGlobalReceiver(PhonePacket.TYPE,(p,c)->{
            state=p;PhoneAudio.call(p.call()!=null&&p.call().active()?p.call().id():null);
            if(c.client().gui.screen() instanceof PhoneScreen screen)screen.update(p);
            else if(p.open())c.client().gui.setScreen(new PhoneScreen(p));
        });
        ClientPlayNetworking.registerGlobalReceiver(PhonePacket.Voice.TYPE,(p,c)->PhoneAudio.receive(p));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            while(OPEN.consumeClick())open(mc);
            if(mc.player==null){state=null;PhoneAudio.stop();}
            else if(state!=null&&java.util.stream.IntStream.range(0,36).noneMatch(i->Phones.model(mc.player.getInventory().getItem(i))!=null)&&Phones.model(mc.player.getOffhandItem())==null){
                state=null;PhoneAudio.stop();if(mc.gui.screen() instanceof PhoneScreen)mc.gui.setScreen(null);
            }
            if(state!=null&&state.call()!=null&&state.call().incoming()&&!state.call().active()){
                if(ringTicks++%60==0&&!CompanionOptions.current.phoneSilent)mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),1.4f,.25f));
            }else ringTicks=0;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{state=null;PhoneAudio.stop();});
    }
    public static boolean handleOpenKey(Minecraft mc,InputConstants.Key key){return OPEN.matches(key)&&open(mc);}
    private static boolean open(Minecraft mc){
        if(mc.player==null||mc.gui.screen()!=null||mc.gui.overlay()!=null||mc.options.keyDebugModifier.isDown()||!ClientPlayNetworking.canSend(PhonePacket.Action.TYPE))return false;
        boolean hasPhone=java.util.stream.IntStream.range(0,36).anyMatch(i->Phones.model(mc.player.getInventory().getItem(i))!=null)||Phones.model(mc.player.getOffhandItem())!=null;
        if(!hasPhone){mc.player.sendSystemMessage(dev.napuli.CityText.t("phone.need_phone"));return true;}
        request("open","","");return true;
    }
    static void request(String operation,String target,String text){if(ClientPlayNetworking.canSend(PhonePacket.Action.TYPE))ClientPlayNetworking.send(new PhonePacket.Action(state==null?PhonePacket.NIL:state.token(),operation,target,text));}
    private PhoneClient(){}
}
