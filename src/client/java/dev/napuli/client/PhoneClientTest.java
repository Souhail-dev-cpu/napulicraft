package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Native GUI, real C2S messages, accepted calls, navigation, charging and world photo capture. */
final class PhoneClientTest {
    private static int titles,total,phase,age,last=-1,model;
    private static boolean preparing;
    private static volatile boolean done,failed;
    private static String destination;
    private static String destinationName;
    private static long photos;
    static void register(){if(!Boolean.getBoolean("napuli.phonetest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done||failed){mc.stop();return;}
            try{
                if(++total>7000)throw new AssertionError("Phone client timed out at phase "+phase);
                if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==40){var settings=new LevelSettings("Telefoni a Napoli",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("napoli-phones-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
                mc.options.pauseOnLostFocus=false;
                if(mc.gui.screen() instanceof PauseScreen)mc.gui.setScreen(null);
                if(!preparing){if(mc.player.level().dimension()!=NapoliMap.LEVEL)return;preparing=true;PhoneClient.dark=false;server(mc,PhoneClientFixture::prepare);}
                if(!PhoneClientFixture.ready)return;
                if(last!=phase){last=phase;age=0;Napuli.LOGGER.info("[PHONETEST] phase {}",phase);}age++;
                var screen=mc.gui.screen() instanceof PhoneScreen phone?phone:null;
                switch(phase){
                    case 0->{if(age>30&&Phones.model(mc.player.getMainHandItem())==PhoneModel.ONEPLUS_16){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=1;}}
                    case 1->{if(screen!=null&&age>10){layouts(screen);shot(mc,"telefono-"+PhoneModel.values()[model].id+"-home");press(screen,"phone.about");phase=2;}}
                    case 2->{if(screen!=null&&screen.app().equals("about")&&age>8){layouts(screen);shot(mc,"telefono-"+PhoneModel.values()[model].id+"-hardware");press(screen,"phone.home");if(++model<5){int next=model;server(mc,p->PhoneClientFixture.model(p,next));phase=3;}else{press(screen,"phone.messages");phase=4;}}}
                    case 3->{if(screen!=null&&screen.snapshot().model()==model&&age>10){shot(mc,"telefono-"+PhoneModel.values()[model].id+"-home");press(screen,"phone.about");phase=2;}}
                    case 4->{if(screen!=null&&age>8){pressLabel(screen,"+");edit(screen).setValue(PhoneClientFixture.peerNumber);press(screen,"phone.save_write");phase=5;}}
                    case 5->{if(screen!=null&&screen.app().equals("thread")&&age>6){edit(screen).setValue("Ciao da Napoli!");press(screen,"phone.send");phase=6;}}
                    case 6->{if(screen!=null&&screen.snapshot().messages().size()==1){server(mc,PhoneClientFixture::reply);phase=7;}}
                    case 7->{if(screen!=null&&screen.snapshot().messages().size()==2&&age>8){layouts(screen);shot(mc,"telefono-chat");press(screen,"phone.call");phase=8;}}
                    case 8->{if(screen!=null&&screen.snapshot().call()!=null&&!screen.snapshot().call().active()){server(mc,PhoneClientFixture::answer);phase=9;}}
                    case 9->{if(screen!=null&&screen.snapshot().call()!=null&&screen.snapshot().call().active()&&age>10){check(PhoneAudio.muted(),"The microphone starts muted and never opens during the automatic test");layouts(screen);shot(mc,"telefono-chiamata");press(screen,"phone.end");phase=10;}}
                    case 10->{if(screen!=null&&screen.snapshot().call()==null&&age>8){server(mc,PhoneClientFixture::callBack);phase=11;}}
                    case 11->{if(screen!=null&&screen.snapshot().call()!=null&&screen.snapshot().call().incoming()&&!screen.snapshot().call().active()&&age>8){press(screen,"phone.answer");phase=12;}}
                    case 12->{if(screen!=null&&screen.snapshot().call()!=null&&screen.snapshot().call().active()&&age>8){press(screen,"phone.end");phase=13;}}
                    case 13->{if(screen!=null&&screen.snapshot().call()==null&&age>8){press(screen,"phone.home");press(screen,"phone.gps");var target=screen.snapshot().places().stream().filter(p->p.id().startsWith("luogo/")&&p.position().distSqr(mc.player.blockPosition())>40*40).findFirst().orElseThrow();destination=target.id();destinationName=target.name().getString();edit(screen).setValue(destinationName);phase=21;}}
                    case 21->{if(screen!=null&&age>8){check(edit(screen).getValue().equals(destinationName),"Search preserves its text across asynchronous filtering");pressLabel(screen,destinationName);phase=14;}}
                    case 14->{if(screen!=null&&!screen.snapshot().goal().isEmpty()&&age>10){check(hasButton(screen,"phone.stop_gps"),"Starting GPS immediately updates its stop button without resizing");layouts(screen);shot(mc,"telefono-gps");server(mc,p->PhoneClientFixture.pin(p,destination));press(screen,"phone.home");press(screen,"phone.settings");server(mc,PhoneClientFixture::emptyBattery);phase=15;}}
                    case 15->{if(screen!=null&&screen.snapshot().charge()<30&&age>10){press(screen,"phone.battery");press(screen,"phone.recharge");phase=16;}}
                    case 16->{if(screen!=null&&screen.snapshot().charge()>35&&age>10){server(mc,PhoneClientFixture::charged);pressLabel(screen,"‹");press(screen,"phone.dark_off");press(screen,"phone.airplane_off");phase=17;}}
                    case 17->{if(screen!=null&&screen.snapshot().airplane()&&age>8){check(hasButton(screen,"phone.airplane_on"),"Airplane mode immediately updates its button without resizing");layouts(screen);shot(mc,"telefono-impostazioni");server(mc,PhoneClientFixture::airplane);press(screen,"phone.airplane_on");press(screen,"phone.home");press(screen,"phone.camera");photos=photoCount(mc);shot(mc,"telefono-fotocamera");press(screen,"phone.capture");phase=18;}}
                    case 18->{if(screen!=null&&screen.app().equals("camera")&&age>10&&photoCount(mc)>photos){press(screen,"phone.home");press(screen,"phone.photos");layouts(screen);shot(mc,"telefono-foto");server(mc,PhoneClientFixture::prepareServices);phase=22;}}
                    case 22->{if(screen!=null&&PhoneClientFixture.servicesReady&&age>30){press(screen,"phone.home");press(screen,"phone.calls");press(screen,"phone.services");layouts(screen);shot(mc,"telefono-servizi");press(screen,"phone.service.pizza_button");phase=23;}}
                    case 23->{if(age%20==0&&age>20)server(mc,PhoneClientFixture::awaitPizza);if(PhoneClientFixture.pizzaReady&&screen!=null){screen.onClose();phase=24;}}
                    case 24->{var npc=mc.level.getEntity(PhoneClientFixture.pizzaActor);if(npc!=null&&age>10){shot(mc,"telefono-fattorino");mc.gameMode.interact(mc.player,npc,new net.minecraft.world.phys.EntityHitResult(npc),InteractionHand.MAIN_HAND);phase=25;}}
                    case 25->{if(age==20)server(mc,PhoneClientFixture::pizzaPaid);if(PhoneClientFixture.pizzaPaid){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=26;}}
                    case 26->{if(screen!=null&&age>10){press(screen,"phone.home");press(screen,"phone.calls");press(screen,"phone.services");press(screen,"phone.service.police_button");phase=27;}}
                    case 27->{if(age==20)server(mc,PhoneClientFixture::policeCalled);if(screen!=null&&PhoneClientFixture.policeCalled){press(screen,"phone.service.taxi_button");edit(screen).setValue(screen.snapshot().places().stream().filter(p->p.id().equals("lavoro")).findFirst().orElseThrow().name().getString());phase=28;}}
                    case 28->{if(screen!=null&&age>12){layouts(screen);shot(mc,"telefono-taxi");pressLabel(screen,screen.snapshot().places().stream().filter(p->p.id().equals("lavoro")).findFirst().orElseThrow().name().getString());phase=29;}}
                    case 29->{if(age%20==0&&age>20)server(mc,PhoneClientFixture::taxiBooked);if(screen!=null&&PhoneClientFixture.taxiBooked){screen.onClose();phase=30;}}
                    case 30->{var taxi=mc.level.getEntity(PhoneClientFixture.taxiActor);if(taxi!=null&&age>12){mc.gameMode.interact(mc.player,taxi,new net.minecraft.world.phys.EntityHitResult(taxi),InteractionHand.MAIN_HAND);phase=31;}}
                    case 31->{if(age==20)server(mc,PhoneClientFixture::taxiPaid);if(PhoneClientFixture.taxiPaid){mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));phase=19;}}
                    case 19->{if(age>20){Screenshot.grab(mc.gameDirectory,"telefoni-modelli-3d.png",mc.gameRenderer.mainRenderTarget(),1,m->{});server(mc,p->{PhoneClientFixture.cleanup(p);Napuli.LOGGER.info("[PHONETEST] ALL PASSED: five models, four GUI sizes, private calls, GPS, charger, photos, pizza delivery and payment, 112 dispatch and taxi booking and boarding");done=true;});phase=20;}}
                }
            }catch(Throwable error){failed=true;Napuli.LOGGER.error("[PHONETEST] FAILED in phase "+phase,error);}
        });
    }
    private static long photoCount(Minecraft mc)throws Exception{var dir=mc.gameDirectory.toPath().resolve("screenshots");if(!Files.isDirectory(dir))return 0;try(var files=Files.list(dir)){return files.filter(p->p.getFileName().toString().matches("telefono-[0-9]+\\.png")).count();}}
    private static void layouts(PhoneScreen screen){int w=screen.width,h=screen.height;InterfaceClientChecks.layouts(screen);screen.init(w,h);}
    private static EditBox edit(PhoneScreen screen){return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();}
    private static void press(PhoneScreen screen,String key){pressLabel(screen,t(key).getString());}
    private static boolean hasButton(PhoneScreen screen,String key){return screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).anyMatch(b->b.getMessage().equals(t(key)));}
    private static void pressLabel(PhoneScreen screen,String label){screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).filter(b->b.active&&b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing phone button: "+label+" in "+screen.app())).onPress(new KeyEvent(257,0,0));}
    private static void shot(Minecraft mc,String name){mc.gui.toastManager().clear();if(mc.gui.screen()!=null)InterfaceClientChecks.bounds(mc.gui.screen());Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->{});}
    private static void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));}catch(Throwable error){failed=true;Napuli.LOGGER.error("[PHONETEST] FAILED on server",error);}});}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private PhoneClientTest(){}
}
