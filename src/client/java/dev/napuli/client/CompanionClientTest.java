package dev.napuli.client;

import dev.napuli.Napuli;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Exercises actual network menus and widgets in a disposable graphical Minecraft world. */
final class CompanionClientTest {
    private static boolean previousBob,previousLightning;
    private static double previousTilt,previousFov,previousScreen;
    private static int titleTicks,ticks,phase,age,last=-1,totalTicks,clearOverlayTicks;
    private static volatile boolean done,failed,localeReady;
    static void register(){if(!Boolean.getBoolean("napuli.qoltest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done||failed){mc.stop();return;}
            clearOverlayTicks=mc.gui.overlay()==null?clearOverlayTicks+1:0;
            if(++totalTicks>7200){failed=true;Napuli.LOGGER.error("[QOLTEST] FAILED: timed out before phase {} on {}",phase,mc.gui.screen());return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titleTicks==40){
                var settings=new LevelSettings("Qualità di vita",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("napoli-qol-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());
            }return;}
            var state=HudClient.state();if(state==null||!state.inCity())return;
            try{
                mc.options.pauseOnLostFocus=false;
                if(++ticks>3600)throw new AssertionError("Companion client test timed out at phase "+phase);
                if(last!=phase){last=phase;age=0;Napuli.LOGGER.info("[QOLTEST] phase {}",phase);}age++;
                if(phase==0&&age==30){mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.getConnection().sendCommand("compagno");phase=1;}
                else if(phase==1&&age>20&&mc.gui.screen() instanceof CompanionScreen){shot(mc,"qol-menu");check(press(mc,"Atlante e preferiti"),"The city hub opens the atlas");phase=2;}
                else if(phase==2&&age>15&&mc.gui.screen() instanceof CompanionScreen){search(mc,"vanvitelli");check(press(mc,"☆"),"A searched place can be added to favorites");phase=3;}
                else if(phase==3&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"★"),"The server returns the saved favorite");shot(mc,"qol-atlante");check(press(mc,"Piazza Vanvitelli"),"The place details can be opened");phase=4;}
                else if(phase==4&&age>15&&mc.gui.screen() instanceof CompanionScreen){check(press(mc,"Segui a piedi"),"Walking navigation can be selected");phase=5;}
                else if(phase==5&&age>30){check(HudClient.state().objective().startsWith("qol:"),"The pinned destination overrides automatic objectives");shot(mc,"qol-tracker");mc.getConnection().sendCommand("compagno ricette");phase=6;}
                else if(phase==6&&age>20&&mc.gui.screen() instanceof CompanionScreen){search(mc,"pizza");check(press(mc,"+"),"A searched recipe can be pinned");phase=7;}
                else if(phase==6&&age%100==0){Napuli.LOGGER.info("[QOLTEST] waiting for recipe board; screen {}",mc.gui.screen());mc.getConnection().sendCommand("compagno ricette");}
                else if(phase==7&&age>30){check(HudClient.companionText().getString().contains("→"),"The recipe tracker includes the required workstation");mc.getConnection().sendCommand("compagno trasporti");phase=8;}
                else if(phase==8&&age>20&&mc.gui.screen() instanceof CompanionScreen){shot(mc,"qol-trasporti");check(press(mc,"Opzioni"),"Interface settings can be opened");phase=9;}
                else if(phase==9&&age>15&&mc.gui.screen() instanceof CompanionScreen){check(press(mc,"Modalità compatta"),"The compact HUD option is interactive");check(CompanionOptions.current.compact,"The option changed");shot(mc,"qol-opzioni");
                    var saved=CompanionOptions.current;CompanionOptions.load();check(CompanionOptions.current.compact,"HUD preferences survive configuration reload");
                    check(press(mc,"Testo HUD"),"HUD text size is configurable");
                    // Return to normal preferences after the isolated check.
                    saved.compact=false;saved.size=1;saved.save();CompanionOptions.current=saved;
                    mc.gui.setScreen(null);mc.getConnection().sendCommand("compagno carriera");phase=10;
                }
                else if(phase==10&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"Il tuo turno"),"The work dashboard shows the live shift");shot(mc,"qol-carriera");server(mc,dev.napuli.CityConvenienceClientFixture::prepare);phase=11;}
                else if(phase==11&&age>20&&dev.napuli.CityConvenienceClientFixture.ready){mc.getConnection().sendCommand("compagno casa "+dev.napuli.CityConvenienceClientFixture.house);phase=12;}
                else if(phase==12&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(press(mc,"Acquista senza mutuo"),"Housing management offers an explicit purchase quote");phase=13;}
                else if(phase==13&&age>20&&mc.gui.screen() instanceof CompanionScreen){server(mc,dev.napuli.CityConvenienceClientFixture::beforeConfirmation);shot(mc,"qol-preventivo");check(press(mc,"Conferma questa operazione"),"The purchase requires confirmation");phase=14;}
                else if(phase==14&&age>30&&mc.gui.screen() instanceof CompanionScreen){server(mc,dev.napuli.CityConvenienceClientFixture::afterConfirmation);shot(mc,"qol-casa");mc.getConnection().sendCommand("compagno diario");phase=15;}
                else if(phase==15&&age>20&&dev.napuli.CityConvenienceClientFixture.checked&&mc.gui.screen() instanceof CompanionScreen){shot(mc,"qol-diario");mc.getConnection().sendCommand("compagno spese");phase=16;}
                else if(phase==16&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"Acquisto casa"),"The expense history names the housing purchase");shot(mc,"qol-spese");language(mc,"en_us");phase=17;}
                else if(phase==17&&localeReady&&age>20){mc.getConnection().sendCommand("compagno");phase=18;}
                else if(phase==18&&age>20&&clearOverlayTicks>=10&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"Atlas and favorites"),"The server-sent menu is translated into the client's English locale");shot(mc,"qol-english");phase=181;}
                else if(phase==181&&age>10){search(mc,"cooperative");check(button(mc,"Cooperative jobs"),"New multiplayer menu entries use English");shot(mc,"qol-improvements-english");search(mc,"");language(mc,"nap_it");phase=19;}
                else if(phase==19&&localeReady&&age>20){mc.getConnection().sendCommand("compagno");phase=20;}
                else if(phase==20&&age>20&&clearOverlayTicks>=10&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"Atlante e preferite"),"The same menu is translated into Neapolitan");shot(mc,"qol-napoletano");phase=201;}
                else if(phase==201&&age>10){search(mc,"scagne");check(button(mc,"Scagne fra jucature"),"New trading menu entries use Neapolitan");shot(mc,"qol-improvements-napoletano");language(mc,"it_it");phase=21;}
                else if(phase==21&&localeReady&&age>20&&clearOverlayTicks>=10){mc.getConnection().sendCommand("compagno economia");phase=22;}
                else if(phase==22&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"Saldo attuale"),"The economy guide opens with the current budget");shot(mc,"qol-economia");mc.getConnection().sendCommand("compagno cooperative");phase=23;}
                else if(phase==23&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"gruppo di almeno due"),"The cooperative board explains its group requirement");shot(mc,"qol-cooperative");mc.getConnection().sendCommand("compagno scambi");phase=24;}
                else if(phase==24&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"Avvicinati a un giocatore"),"The trade board explains how to find a partner");shot(mc,"qol-scambi");server(mc,dev.napuli.CityConvenienceClientFixture::scooter);phase=25;}
                else if(phase==25&&age>20){mc.getConnection().sendCommand("compagno motorini");phase=26;}
                else if(phase==26&&age>20&&mc.gui.screen() instanceof CompanionScreen){check(button(mc,"Ultimo motorino"),"The garage lists the saved last scooter");shot(mc,"qol-motorini");previousBob=mc.options.bobView().get();previousTilt=mc.options.damageTiltStrength().get();previousFov=mc.options.fovEffectScale().get();previousLightning=mc.options.hideLightningFlash().get();previousScreen=mc.options.screenEffectScale().get();check(press(mc,"Opzioni"),"Accessibility settings open");phase=27;}
                else if(phase==27&&age>15){for(var label:new String[]{"Preset cittadino leggero","Riduci particelle","Riduci lampeggi","Riduci movimento","Riduci rumori"})check(option(mc,label),"Accessible setting is reachable by keyboard: "+label);check(CompanionOptions.current.lightPreset&&CompanionOptions.current.reducedMotion&&CompanionOptions.current.reducedFlashes&&CompanionOptions.current.reducedParticles&&CompanionOptions.current.quietAmbience,"All personal effect settings are enabled");check(!mc.options.bobView().get()&&mc.options.damageTiltStrength().get()==0&&mc.options.fovEffectScale().get()==0&&mc.options.hideLightningFlash().get()&&mc.options.screenEffectScale().get()==0,"Reduced motion and flashes update actual camera options");shot(mc,"qol-accessibilita");phase=28;}
                else if(phase==28&&age==25){shot(mc,"qol-accessibilita");}
                else if(phase==28&&age==30){server(mc,dev.napuli.CityConvenienceClientFixture::effects);}
                else if(phase==28&&age>40&&dev.napuli.CityConvenienceClientFixture.effectsChecked){for(var label:new String[]{"Preset cittadino leggero","Riduci particelle","Riduci lampeggi","Riduci movimento","Riduci rumori"})check(option(mc,label),"Effect settings can be restored");check(mc.options.bobView().get()==previousBob&&mc.options.damageTiltStrength().get()==previousTilt&&mc.options.fovEffectScale().get()==previousFov&&mc.options.hideLightningFlash().get()==previousLightning&&mc.options.screenEffectScale().get()==previousScreen,"Disabling accessibility modes restores original camera preferences");Napuli.LOGGER.info("[QOLTEST] ALL PASSED: navigation, journal, housing quote, career, ledger, economy, cooperative and trade boards, scooter tracking, personal effects, camera restoration and three languages");done=true;}
            }catch(Throwable e){failed=true;Napuli.LOGGER.error("[QOLTEST] FAILED",e);}
        });
    }
    private static void language(Minecraft mc,String language){localeReady=false;mc.options.languageCode=language;mc.getLanguageManager().setSelected(language);mc.reloadResourcePacks().thenRun(()->localeReady=true);}
    private static void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var server=mc.getSingleplayerServer();server.execute(()->{try{action.accept(server.getPlayerList().getPlayer(mc.player.getUUID()));}catch(Throwable e){failed=true;Napuli.LOGGER.error("[QOLTEST] FAILED on server",e);}});}
    private static Stream<GuiEventListener> descendants(GuiEventListener widget){return widget instanceof ContainerEventHandler container?Stream.concat(Stream.of(widget),container.children().stream().flatMap(CompanionClientTest::descendants)):Stream.of(widget);}
    private static boolean button(Minecraft mc,String text){return descendants(mc.gui.screen()).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).anyMatch(b->b.getMessage().getString().contains(text));}
    private static boolean press(Minecraft mc,String text){var button=descendants(mc.gui.screen()).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).filter(b->b.getMessage().getString().contains(text)).findFirst();button.ifPresent(b->b.onPress(new KeyEvent(257,0,0)));return button.isPresent();}
    private static boolean option(Minecraft mc,String text){for(int n=0;n<4;n++){if(press(mc,text))return true;press(mc,"→");}for(int n=0;n<4;n++)press(mc,"←");for(int n=0;n<4;n++){if(press(mc,text))return true;press(mc,"→");}return false;}
    private static void search(Minecraft mc,String text){descendants(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(text);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->{});}
    private CompanionClientTest(){}
}
