package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Real damage, a delayed victim call, officer pursuit, rear-only theft and next-day identity recovery. */
public final class ResidentSafetyClientTest {
    private static int titles,ticks,phase,age,remaining;
    private static volatile boolean done;
    private static volatile String picture;
    private static NapoliResident victim,officer;
    private static UUID identity;
    private static Vec3 officerStart;
    private static BlockPos assault;
    public static void register(){
        if(!Boolean.getBoolean("napuli.residentsafetytest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done){mc.stop();return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){var settings=new LevelSettings("Sicurezza dei residenti",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("resident-safety-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
            mc.options.pauseOnLostFocus=false;if(mc.gui.screen()!=null)mc.gui.setScreen(null);if(picture!=null){var name=picture;picture=null;Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,message->{});}
        });ServerTickEvents.END_SERVER_TICK.register(ResidentSafetyClientTest::tick);
    }
    private static void tick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||done)return;var p=server.getPlayerList().getPlayers().stream().filter(a->a.level()==city).findFirst().orElse(null);if(p==null)return;
        try{
            if(++ticks>2000||++age>500)throw new IllegalStateException("Safety deadline phase="+phase+" calls="+city.getAttachedOrElse(CityResidentSafety.CALLS,Map.of()));
            if(ticks%100==0)Napuli.LOGGER.info("[RESIDENTSAFETY] phase={} age={} stars={} victim={} calls={} returns={}",phase,age,Carabinieri.wantedStars(p),victim==null?"none":victim.position(),city.getAttachedOrElse(CityResidentSafety.CALLS,Map.of()).size(),city.getAttachedOrElse(CityResidentSafety.RETURNS,Map.of()).size());
            switch(phase){
                case 0->{
                    if(age<30)return;DistrictLife.seed(city);CityLifeConfig.settings.policePatrolLimit=0;CityLifeConfig.settings.nightThieves=0;var actors=new ArrayList<NapoliResident>();for(var e:city.getAllEntities())if(e instanceof NapoliResident npc)actors.add(npc);actors.forEach(NapoliResident::discard);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of()));
                    p.setGameMode(GameType.SURVIVAL);p.setPermanentlyInvulnerable(true);p.teleportTo(city,-338.5,64,97.5,Set.of(),180,0,true);clock(server,5000);victim=NapoliResident.spawn(city,NapoliResident.Role.PESCATORE,new BlockPos(-339,64,95),0);victim.setCustomName(net.minecraft.network.chat.Component.literal("Ciro pescatore"));identity=victim.getUUID();assault=p.blockPosition();
                    check(victim.hurtServer(city,city.damageSources().playerAttack(p),3)&&victim.getHealth()==17,"A real human NPC loses health after a player hit");check(!Carabinieri.isWanted(p)&&city.getAttachedOrElse(CityResidentSafety.CALLS,Map.of()).containsKey(identity.toString()),"The unwitnessed assault starts a real victim call before issuing wanted stars");next(1);
                }
                case 1->{
                    if(!Carabinieri.isWanted(p))return;check(age>=40&&Carabinieri.wantedStars(p)==3,"The victim's delayed report creates the assault search");check(Carabinieri.lastKnown(p).equals(assault),"The report transmits the observed attack location");
                    officer=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,new BlockPos(-339,64,79),0);officerStart=officer.position();PoliceDispatch.invalidate(city);picture="npc-denuncia";next(2);
                }
                case 2->{
                    if(age<35)return;check(officer.position().distanceToSqr(officerStart)>1&&Carabinieri.chasing(city,officer),"A real officer walks after the reported attacker");officer.discard();PoliceDispatch.invalidate(city);victim.setNoAi(true);victim.setYRot(0);victim.setYHeadRot(0);var at=victim.position();
                    p.teleportTo(city,at.x,at.y,at.z+2,Set.of(),180,0,true);check(!Pickpocket.open(p,victim),"The NPC cannot be pickpocketed from the front");p.teleportTo(city,at.x,at.y,at.z-2,Set.of(),0,0,true);check(Pickpocket.open(p,victim),"The actual personal bag opens from behind");var token=p.getAttached(Pickpocket.OPEN).token();var bag=Pickpocket.inventory(victim);var daily=Pickpocket.daily(victim,bag);int slot=daily.choices().getFirst().slot();check(Pickpocket.take(p,token,slot),"One real item is transferred from the fisher's finite bag");victim.setYHeadRot(180);int count=bag.stream().limit(9).mapToInt(s->s.get().getCount()).sum();check(!Pickpocket.take(p,token,slot),"Turning around rejects an existing theft session");remaining=count;p.removeAttached(Pickpocket.OPEN);
                    victim.setInvulnerableTime(0);check(victim.hurtServer(city,city.damageSources().playerAttack(p),1000)&&!victim.isAlive(),"The NPC can actually be killed");check(CityResidentSafety.absent(city,identity)&&Carabinieri.wantedStars(p)==4,"The victim is scheduled to return and killing escalates the offence");next(3);
                }
                case 3->{
                    if(age<45)return;check(city.getEntity(identity)==null&&CityResidentSafety.absent(city,identity),"The victim stays absent on the death day");p.teleportTo(city,-332.5,65,96.5,Set.of(),100,6,true);clock(server,25000);next(4);
                }
                case 4->{
                    if(!(city.getEntity(identity) instanceof NapoliResident restored))return;check(restored.isAlive()&&restored.getHealth()==20&&restored.role()==NapoliResident.Role.PESCATORE&&restored.getName().getString().equals("Ciro pescatore"),"The same named NPC returns alive on the following day");check(Pickpocket.inventory(restored).stream().limit(9).mapToInt(s->s.get().getCount()).sum()==remaining,"The returned NPC still has the depleted personal bag");check(!CityResidentSafety.absent(city,identity),"The recovery queue is consumed exactly once");picture="npc-ritorno";next(5);
                }
                case 5->{if(age<35)return;Napuli.LOGGER.info("[RESIDENTSAFETY] ALL PASSED: real damage, victim phone call, officer pursuit, rear-only theft with head-turn rejection, homicide and same-identity next-day return without bag refill");done=true;}
            }
        }catch(Throwable failure){Napuli.LOGGER.error("[RESIDENTSAFETY] FAILED",failure);done=true;}
    }
    private static void clock(net.minecraft.server.MinecraftServer s,int time){s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"time set "+time);}
    private static void next(int value){phase=value;age=0;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private ResidentSafetyClientTest(){}
}
