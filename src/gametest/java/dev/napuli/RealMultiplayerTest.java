package dev.napuli;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Requires two independent graphical clients; no embedded or mocked connections are created here. */
public final class RealMultiplayerTest implements ModInitializer {
    private MinecraftServer server; private ServerLevel city; private int ticks,phase,age,started;
    private boolean failed,finished,disconnected,reconnected,housePrepared; private UUID firstId,seller;
    private final Set<UUID> observed=new HashSet<>(),boarded=new HashSet<>(),arrived=new HashSet<>();
    private final List<NapoliResident> occupants=new ArrayList<>();
    private String shop,home; private long disconnectedAt;
    private final int duration=Integer.getInteger("napuli.realmp.ticks",12000);
    public void onInitialize(){if(!Boolean.getBoolean("napuli.realmp"))return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("napuliqa").then(Commands.literal("observed").executes(c->{observed.add(c.getSource().getPlayerOrException().getUUID());return 1;}))));
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;city=s.getLevel(NapoliMap.LEVEL);NapoliMap.ensureBuilt(city);DistrictLife.seed(city);server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 2300");Napuli.LOGGER.info("[REALMP] READY: waiting for two graphical clients");});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(server==null||failed||finished)return;try{tick();}catch(Throwable error){failed=true;Napuli.LOGGER.error("[REALMP] FAILED phase="+phase,error);server.halt(false);}});
    }
    private List<ServerPlayer> players(){return server.getPlayerList().getPlayers().stream().filter(p->p.getName().getString().startsWith("NapuliQA_")).sorted(Comparator.comparing(p->p.getName().getString())).toList();}
    private void send(ServerPlayer p,String instruction){if(!ServerPlayNetworking.canSend(p,CityVerification.TYPE))throw new AssertionError("Client lacks QA payload");ServerPlayNetworking.send(p,new CityVerification(instruction));}
    private void both(String instruction){for(var p:players())send(p,instruction);}
    private void move(ServerPlayer p,BlockPos at){p.teleportTo(city,at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0,true);city.getChunkSource().move(p);}
    private void next(){phase++;age=0;Napuli.LOGGER.info("[REALMP] phase {} at tick {}",phase,ticks);}
    private void tick()throws Exception{
        ticks++;age++;if(phase==0?ticks>6000:ticks-started>duration+6000)throw new AssertionError("Graphical multiplayer deadline exceeded");var ps=players();
        if(phase==0){if(ps.size()!=2)return;started=ticks;firstId=ps.getFirst().getUUID();var d=DistrictLife.districts().getFirst();shop=d.id();seller=DistrictLife.identity(d,0);
            city.removeAttached(HouseGuests.INVITATIONS);
            for(var p:ps){p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);NapoliStreet.credit(p,100-NapoliStreet.cash(p));move(p,DistrictLife.work(NapoliCivicLife.profile(city,seller)).south(3));}
            next();return;
        }
        if(phase==1){if(age<100||ps.size()!=2)return;var d=DistrictLife.byId(shop);var at=DistrictLife.work(NapoliCivicLife.profile(city,seller));
            if(!(city.getEntity(seller) instanceof NapoliResident)){var npc=NapoliResident.create(city,NapoliResident.Role.CIVIC_MAN,at,0);npc.setUUID(seller);city.addFreshEntity(npc);}
            var npc=(NapoliResident)city.getEntity(seller);npc.setNoAi(true);npc.relocate(at,0);for(var p:ps)move(p,at.south(3));
            var ledger=DistrictShops.ledger(city,shop);var goods=new HashMap<>(ledger.goods());goods.put("mele",1);DistrictShops.put(city,shop,new DistrictShops.Ledger(Map.copyOf(goods),ledger.till(),NapoliBroadcast.day(city)));
            both("observe|"+seller);next();return;
        }
        if(phase==2){if(observed.size()!=2){if(age>300)throw new AssertionError("Both graphical clients must render the same real NPC");return;}both("command|negozio compra "+shop+" mele");next();return;}
        if(phase==3){if(age<100)return;check(ps.size()==2,"Two graphical clients stay connected");check(ps.stream().mapToInt(p->WorkResources.count(p,Items.APPLE)).sum()==1,"Competing purchases deliver one final apple");check(DistrictShops.quantity(city,DistrictLife.byId(shop),"mele")==0,"No negative or duplicated stock");check(ps.stream().mapToInt(NapoliStreet::cash).sum()==199,"Only the successful purchase is charged");
            var h=NapoliMergellina.PROPERTIES.getFirst();if(!housePrepared){home=h.id();var owners=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));owners.put(home,firstId);city.setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));
            move(ps.getFirst(),h.inside());move(ps.getLast(),h.entrance().south(3));
            for(int i=0;i<4;i++){var n=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,h.inside().east(i%2).south(i/2),0);n.setNoAi(true);occupants.add(n);}
            housePrepared=true;age=0;return;}
            check(!HousingCapacity.canEnter(city,h.inside(),ps.getLast().getUUID()),"The ordinary house is full before invitation");send(ps.getFirst(),"command|casa invita "+home+" "+ps.getLast().getName().getString());next();return;
        }
        if(phase==4){if(age<80)return;check(HouseGuests.invited(city,home,ps.getLast().getUUID()),"Real client invitation reaches the server");var h=CityLivingHomes.find(home);check(HousingCapacity.canEnter(city,h.inside(),ps.getLast().getUUID()),"Invitation admits the sixth occupant");move(ps.getLast(),h.inside());both("shot|multiplayer-ospiti");next();return;}
        if(phase==5){if(age<80)return;occupants.forEach(NapoliResident::discard);var stop=CityNetwork.stop("augusto");CityNetwork.ensureStation(city,stop);for(var p:ps){p.removeAttached(CityNetwork.PASS);NapoliStreet.credit(p,100-NapoliStreet.cash(p));move(p,stop.boarding(CityNetwork.line("M6")));}both("command|trasporti corsa M6 municipio");next();return;}
        if(phase==6){for(var p:ps){var t=p.getAttached(CityNetwork.TICKET);if(t!=null&&t.state().equals("riding")){boarded.add(p.getUUID());check(NapoliStreet.cash(p)==98,"One fare per real client");}if(boarded.contains(p.getUUID())&&t==null){arrived.add(p.getUUID());check(NapoliStreet.cash(p)==98,"Reconnection must not charge the fare twice");}}
            if(boarded.size()==2&&!disconnected){var p=ps.stream().filter(x->x.getUUID().equals(firstId)).findFirst().orElseThrow();p.connection.disconnect(Component.literal("Verifica automatica della riconnessione"));disconnected=true;disconnectedAt=ticks;}
            if(disconnected&&ticks-disconnectedAt>30&&ps.stream().anyMatch(p->p.getUUID().equals(firstId))){reconnected=true;}
            if(arrived.size()==2&&reconnected){both("shot|multiplayer-metro");next();}if(age>4500)throw new AssertionError("Both real clients must finish their paid metro trip");return;
        }
        if(phase==7){if(age==80){for(var p:ps)move(p,CityLivingHomes.PUBLIC_HOMES.getFirst().entrance().south(4));server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 14000");both("shot|multiplayer-quartiere-sera");}
            if(age>=800&&(age-800)%1600==0){int index=(age-800)/1600;var districts=DistrictLife.districts();var place=DistrictLife.square(districts.get(Math.floorMod(index*4,districts.size())));for(var p:ps)move(p,place.arrival().east(p==ps.getFirst()?1:3));Napuli.LOGGER.info("[REALMP] exploration {}",place.name());}
            check(ps.size()==2,"Both real clients stay connected throughout the soak");
            if(ticks-started>=duration){server.saveEverything(true,true,true);both("done|passed");next();}return;
        }
        if(phase==8&&age>=60){finished=true;Napuli.LOGGER.info("[REALMP] ALL PASSED: ticks={}, graphicalClients=2, reconnection={}, simultaneousStock=1",ticks-started,reconnected);server.halt(false);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
