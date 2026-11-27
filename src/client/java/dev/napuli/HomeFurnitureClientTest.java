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

/** Real entities must walk between the upper bedroom, bathroom, desk and separate living-room seats. */
public final class HomeFurnitureClientTest {
    private static int titles,ticks,phase,phaseTicks;
    private static volatile boolean finished;
    private static volatile String picture;
    private static NapoliMergellina.House home;
    private static NapoliResident resident,guest;
    public static void register(){
        if(!Boolean.getBoolean("napuli.homefurnituretest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(finished){mc.stop();return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){
                var settings=new LevelSettings("Routine domestiche",GameType.SPECTATOR,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("home-routines-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());
            }return;}
            mc.options.pauseOnLostFocus=false;
            if(picture!=null){String name=picture;picture=null;Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->{});}
        });
        ServerTickEvents.END_SERVER_TICK.register(HomeFurnitureClientTest::serverTick);
    }
    private static void serverTick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||finished)return;
        var player=server.getPlayerList().getPlayers().stream().filter(p->p.level()==city).findFirst().orElse(null);if(player==null)return;
        try{
            if(++ticks>4000)throw new IllegalStateException("Routine deadline exceeded");
            if(++phaseTicks>1200)throw new IllegalStateException("No progress at phase "+phase+" position="+(resident==null?"none":resident.position())+" activity="+(resident==null?"none":resident.activity()));
            if(ticks%100==0)Napuli.LOGGER.info("[HOMEFURNITURE] phase={} position={} activity={} needs={}",phase,resident==null?"none":resident.blockPosition(),resident==null?"none":resident.activity(),resident==null?"none":CitySimulation.life(city,resident.getUUID()).needs());
            switch(phase){
                case 0->{
                    if(phaseTicks<30)return;
                    home=DistrictLife.homes().stream().filter(h->h.ground()>68&&h.floors()>=3).findFirst().orElseThrow();
                    var chunks=new ArrayList<net.minecraft.world.level.chunk.LevelChunk>();
                    for(int x=(home.x()-1)>>4;x<=(home.x()+home.width())>>4;x++)for(int z=(home.z()-1)>>4;z<=(home.z()+home.depth()+1)>>4;z++){
                        var chunk=city.getChunk(x,z);NapoliExpansion.generate(city,chunk);chunks.add(chunk);
                    }
                    for(var chunk:chunks){DistrictLife.fit(city,chunk);NapoliFurniture.fit(city,chunk);NapoliHomeDecor.fit(city,chunk);}CityArchitecture.finishNew(city,home);
                    var census=NapoliCivicLife.census(city);var people=new ArrayList<>(census.people());
                    people.removeIf(c->{if(!c.property().equals(home.id()))return false;var e=city.getEntity(c.id());if(e!=null)e.discard();return true;});
                    resident=NapoliResident.create(city,NapoliResident.Role.CIVIC_MAN,home.entrance().north(2),0);
                    resident.setUUID(new UUID(0,1));city.addFreshEntity(resident);
                    // A stable UUID selects an upstairs bedroom and an upstairs desk.
                    var c=profile(city,resident,30);people.add(c);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people)));
                    needs(city,resident,8,80,80);clock(server,18000);camera(player,home.x()+11.5,home.ground()+6.2,home.z()+11.5,180);
                    next(1);
                }
                case 1->{
                    if(!resident.isSleeping())return;
                    check(resident.getY()>home.ground()+3,"The resident climbed to a real upper-floor bed");resident.setNoAi(true);
                    camera(player,resident.getX()+2.5,resident.getY()+1.5,resident.getZ()+3.5,145);next(11);
                }
                case 11->{
                    if(phaseTicks==20)picture="routine-letto";if(phaseTicks<30)return;resident.setNoAi(false);
                    needs(city,resident,80,8,5);clock(server,9000);camera(player,home.x()+5.5,home.ground()+1.2,home.z()+6.5,270);next(2);
                }
                case 2->{
                    var needs=CitySimulation.life(city,resident.getUUID()).needs();if(needs.hygiene()<70||needs.bladder()<70)return;
                    check(!resident.isSleeping(),"The resident woke and reached the bathroom downstairs");resident.setNoAi(true);
                    camera(player,home.x()+9.5,home.ground()+1.2,home.z()+7.5,45);next(12);
                }
                case 12->{
                    if(phaseTicks==20)picture="routine-bagno";if(phaseTicks<30)return;resident.setNoAi(false);
                    replaceProfile(city,resident,12);needs(city,resident,80,90,90);clock(server,9000);camera(player,home.x()+17.5,home.ground()+6.2,home.z()+9.5,180);next(3);
                }
                case 3->{
                    if(resident.activity()!=NapoliHouseholds.Activity.STUDY||resident.gesture()!=CityGestures.Gesture.WRITE||!resident.getMainHandItem().is(net.minecraft.world.item.Items.BOOK))return;
                    check(resident.getY()>home.ground()+3,"The child uses an upstairs desk");check(CityMovement.isSeat(resident.getVehicle()),"Studying uses an actual desk chair");resident.setNoAi(true);
                    camera(player,resident.getX()-2.5,resident.getY()+1,resident.getZ()+3.5,210);next(13);
                }
                case 13->{
                    if(phaseTicks==20)picture="routine-studio";if(phaseTicks<30)return;resident.setNoAi(false);
                    replaceProfile(city,resident,30);needs(city,resident,20,90,90);camera(player,home.x()+6.5,home.ground()+1.2,home.z()+12.5,180);next(4);
                }
                case 4->{
                    if(!CityMovement.isSeat(resident.getVehicle())||resident.activity()!=NapoliHouseholds.Activity.REST||resident.getVehicle().blockPosition().getY()!=home.ground())return;
                    guest=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_WOMAN,home.entrance().north(2),0);
                    var census=NapoliCivicLife.census(city);var people=new ArrayList<>(census.people());people.add(profile(city,guest,30));city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people)));needs(city,guest,25,90,90);next(5);
                }
                case 5->{
                    if(!CityMovement.isSeat(guest.getVehicle())||!CityMovement.isSeat(resident.getVehicle())||phaseTicks<80)return;
                    check(CityMovement.isSeat(resident.getVehicle())&&guest.getVehicle()!=resident.getVehicle(),"Two residents occupy different seats");picture="routine-sedute";
                    Napuli.LOGGER.info("[HOMEFURNITURE] ALL PASSED: upper bedroom, bathroom sequence, upstairs study chair and distinct living-room seats");next(6);
                }
                case 6->{if(phaseTicks>30)finished=true;}
            }
        }catch(Throwable failure){Napuli.LOGGER.error("[HOMEFURNITURE] FAILED",failure);finished=true;}
    }
    private static NapoliCivicLife.Citizen profile(ServerLevel city,NapoliResident npc,int age){return new NapoliCivicLife.Citizen(npc.getUUID(),"Residente prova",false,NapoliBroadcast.day(city)-(long)age*CityLifeConfig.settings.daysPerYear,100,-1,"",home.id(),"",true);}
    private static void replaceProfile(ServerLevel city,NapoliResident npc,int age){var census=NapoliCivicLife.census(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(npc.getUUID())?profile(city,npc,age):c).toList()));}
    private static void needs(ServerLevel city,NapoliResident npc,int sleep,int hygiene,int bladder){var l=CitySimulation.fresh(npc.getUUID());CitySimulation.put(city,npc.getUUID(),l.withNeeds(new CitySimulation.Needs(90,sleep,hygiene,30,70,bladder),8,city.getGameTime(),0,0));}
    private static void clock(net.minecraft.server.MinecraftServer server,int time){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set "+time);}
    private static void camera(ServerPlayer p,double x,double y,double z,float yaw){p.setGameMode(GameType.SPECTATOR);p.teleportTo(p.level(),x,y,z,Set.of(),yaw,12,true);}
    private static void next(int value){phase=value;phaseTicks=0;Napuli.LOGGER.info("[HOMEFURNITURE] phase {}",value);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
