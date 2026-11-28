package dev.napuli;

import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Rendered smoke test of a populated district, shop dialogue and real metro passenger journey. */
public final class DistrictClientTest {
    private static final String LEGACY_MONUMENT_WORLD = System.getProperty("napuli.legacyMonumentWorld", "");
    private static final boolean LEGACY_CASTLE_CATHEDRAL = Boolean.getBoolean("napuli.legacyCastleCathedral");
    private static final boolean LEGACY_OVO_MUSEUM = Boolean.getBoolean("napuli.legacyOvoMuseum");
    private static final boolean LEGACY_CERTOSA = Boolean.getBoolean("napuli.legacyCertosa");
    private static final boolean LEGACY_RESIDENCES = Boolean.getBoolean("napuli.legacyResidences");
    private static final boolean LEGACY_CAPODIMONTE_SANITA = Boolean.getBoolean("napuli.legacyCapodimonteSanita");
    private static final boolean LEGACY_COURTYARDS = Boolean.getBoolean("napuli.legacyCourtyards");
    private static final boolean LEGACY_ALBERGO_PIETRARSA = Boolean.getBoolean("napuli.legacyAlbergoPietrarsa");
    private static final boolean LEGACY_MUSEUMS = Boolean.getBoolean("napuli.legacyMuseums");
    private static final boolean LEGACY_LANDMARK_WALK = Boolean.getBoolean("napuli.legacyLandmarkWalk");
    private static final boolean LEGACY_CIVIC_SITES = Boolean.getBoolean("napuli.legacyCivicSites");
    private static final boolean LEGACY_GROUND_EVENTS = Boolean.getBoolean("napuli.legacyGroundEvents");
    private static final boolean LEGACY_HOUSES_MEMES = Boolean.getBoolean("napuli.legacyHousesMemes");
    private static final boolean LEGACY_FOOTBALL_STATS = Boolean.getBoolean("napuli.legacyFootballStats");
    private static int walkStage, walkTicks;
    private static int legacyLoadTicks;
    private static int museumScene, museumReadyTicks;
    private static boolean legacyBackupAccepted;
    private static int titles,tick;private static boolean starting;private static volatile boolean ready,failed,done,boarded,arrived;private static String shop;private static int palaceAt;
    public static void register(){if(!Boolean.getBoolean("napuli.districttest"))return;ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(failed||done){mc.stop();return;}
        if (!LEGACY_MONUMENT_WORLD.isEmpty()) { legacyMonuments(mc); return; }
        if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==80){var settings=new LevelSettings("Quartieri e trasporti test",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("napoli-district-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
        if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
        if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen)mc.gui.setScreen(null);
        if(!starting){starting=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();server(mc,p->{var city=p.level();city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set 2300");DistrictLife.seed(city);var d=DistrictLife.districts().getFirst();shop=d.id();var h=d.business();for(int x=(h.x()-20)>>4;x<=(h.x()+40)>>4;x++)for(int z=(h.z()-20)>>4;z<=(h.z()+45)>>4;z++){var c=city.getChunk(x,z);NapoliExpansion.generate(city,c);DistrictLife.fit(city,c);CityRailway.fit(city,c);}p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.teleportTo(city,h.x()+11.5,h.ground()+5,h.z()+38.5,Set.of(),180,18,true);ready=true;});}if(!ready)return;
        try{tick++;
            if(tick==15)server(mc,p->{
                var home=DistrictLife.byId(shop).homes().getFirst();
                for(int x=home.x()>>4;x<=(home.x()+21)>>4;x++)for(int z=home.z()>>4;z<=(home.z()+23)>>4;z++){
                    var chunk=p.level().getChunk(x,z);
                    NapoliExpansion.generate(p.level(),chunk);
                    NapoliExpansion.upgradeInteriors(p.level(),chunk);
                    DistrictLife.fit(p.level(),chunk);
                    NapoliFurniture.fit(p.level(),chunk);
                    NapoliHomeDecor.fit(p.level(),chunk);
                }
                var sofa=CityLivingHomes.use(home,CityLivingHomes.Furniture.SOFA).block();
                var sofaHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(sofa), net.minecraft.core.Direction.NORTH, sofa, false);
                var sofaState=p.level().getBlockState(sofa);
                check(sofaState.is(NapoliFurniture.DIVANO),"Generated sofa gets the interactive model");
                sofaState.useWithoutItem(p.level(), p, sofaHit);
                check(CityMovement.isSeat(p.getVehicle()),"Clicking the sofa seats the player");
                var seat=p.getVehicle();
                p.stopRiding();
                seat.discard();
                var fridge=CityLivingHomes.use(home,CityLivingHomes.Furniture.FRIDGE).block();
                var fridgeEntity=p.level().getBlockEntity(fridge);
                check(fridgeEntity instanceof net.minecraft.world.Container,"Fridge has a persistent inventory");
                var fridgeHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(fridge), net.minecraft.core.Direction.SOUTH, fridge, false);
                var fridgeState=p.level().getBlockState(fridge);
                check(fridgeState.is(NapoliFurniture.FRIGORIFERO),"Generated fridge has the new model");
                fridgeState.useWithoutItem(p.level(),p,fridgeHit);
                check(p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu,"Fridge opens native slots");
                p.closeContainer();
                var microwave=new net.minecraft.core.BlockPos(home.x()+1,home.ground()+1,home.z()+5);
                var prev=p.level().getBlockState(microwave);
                p.level().setBlockAndUpdate(microwave,NapoliFurniture.MICROONDE.defaultBlockState());
                var microwaveHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(microwave), net.minecraft.core.Direction.NORTH, microwave, false);
                var potato=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTATO);
                p.level().getBlockState(microwave).useItemOn(potato,p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND,microwaveHit);
                check(p.level().getBlockState(microwave).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),"Microwave starts an animated cycle");
                check(p.getInventory().countItem(net.minecraft.world.item.Items.BAKED_POTATO)==1,"Microwave cooks a potato");
                p.level().setBlockAndUpdate(microwave,prev);
                checkAccessories(p, new net.minecraft.core.BlockPos(home.x()+8, home.ground(), home.z()+17));
                var coffeeTable=new net.minecraft.core.BlockPos(home.x()+3,home.ground(),home.z()+8);
                check(p.level().getBlockState(coffeeTable).is(NapoliFurniture.COFFEE_TABLE),"Living room has a low table");
                var stoveApproach=CityLivingHomes.use(home,CityLivingHomes.Furniture.STOVE).stand();
                check(p.level().isEmptyBlock(stoveApproach),"Kitchen work aisle stays clear");
                var sink=CityLivingHomes.use(home,CityLivingHomes.Furniture.SINK).block();
                var desk=CityLivingHomes.use(home,CityLivingHomes.Furniture.DESK).block();
                var lamp=desk.above();
                check(p.level().getBlockState(sink).is(NapoliFurniture.SINK),"Ceramic sink replaces its legacy fixture");
                check(p.level().getBlockState(desk).is(NapoliFurniture.WORKTOP),"Worktop replaces its legacy fixture");
                var state=p.level().getBlockState(lamp);
                check(state.is(NapoliFurniture.LAMP),"Lamp rests on the worktop");
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(lamp),net.minecraft.core.Direction.UP,lamp,false);
                state.useWithoutItem(p.level(),p,hit);
                check(p.level().getBlockState(lamp).getLightEmission()==14,"Lamp turns on");
                NapoliHygiene.wash(p,sink,20);
                var sinkHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(sink),net.minecraft.core.Direction.UP,sink,false);
                p.level().getBlockState(sink).useWithoutItem(p.level(),p,sinkHit);
                check(NapoliHygiene.value(p)==55,"Ceramic sink restores hygiene");
                var stair=new net.minecraft.core.BlockPos(home.x()+16,home.ground(),home.z()+16);
                check(p.level().getBlockState(stair).is(net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS),"The district house has an interior staircase");
                p.teleportTo(p.level(),sofa.getX()+.5,sofa.getY(),sofa.getZ()-1.5,Set.of(),0,0,true);
                sofaState.useWithoutItem(p.level(), p, sofaHit);
                check(CityMovement.isSeat(p.getVehicle()),"Clicking the sofa seats the player");
            });
            if(tick==50){
                check(mc.player.getVehicle() instanceof net.minecraft.world.entity.decoration.ArmorStand marker && marker.isInvisible(),"The seat mount is synced to the real client");
                shot(mc,"interni-divano");
                server(mc,p->{
                    var seat=p.getVehicle();
                    p.stopRiding();
                    if(CityMovement.isSeat(seat)) seat.discard();
                    var home=DistrictLife.byId(shop).homes().getFirst();
                    p.teleportTo(p.level(),home.x()+7.5,home.ground()+.1,home.z()+13.5,Set.of(),180,5,true);
                });
            }
            if(tick==65)shot(mc,"interni-basso");
            if(tick==68)server(mc,p->{
                var home=DistrictLife.byId(shop).homes().getFirst();
                p.getAbilities().flying=false;
                p.onUpdateAbilities();
                p.teleportTo(p.level(),home.x()+16.5,home.ground()+.1,home.z()+15.2,Set.of(),0,0,true);
            });
            if(tick==73)mc.options.keyUp.setDown(true);
            if(tick==105)mc.options.keyUp.setDown(false);
            if(tick==108)server(mc,p->{
                var home=DistrictLife.byId(shop).homes().getFirst();
                check(p.getY()>=home.ground()+4.95,"The client can walk all the way up the staircase");
                var shower=CityLivingHomes.use(home,CityLivingHomes.Furniture.SHOWER).block();
                check(NapoliShower.isColumn(p.level(),shower.above()),"The bathroom has the usable shower model");
                p.teleportTo(p.level(),home.x()+15.5,home.ground()+5.1,home.z()+11.5,Set.of(),180,12,true);
            });
            if(tick==112)shot(mc,"interni-piano-superiore");
            if(tick==118)server(mc,p->{
                var roof=new net.minecraft.core.BlockPos(-24,70,51);
                check(p.level().getBlockState(roof).is(net.minecraft.world.level.block.Blocks.BRICK_SLAB),
                        "The MCP fishing harbour template installs its roof");
                var counter=new net.minecraft.core.BlockPos(-26,64,51);
                check(p.level().getBlockState(counter).is(NapoliKitchen.BLOCKS.get("banca_frittura")),
                        "The refit preserves the working frying counter");
                Napuli.LOGGER.info("[DISTRICTTEST] MCP market roof and counter verified");
                var awning=new net.minecraft.core.BlockPos(22,68,76);
                check(p.level().getBlockState(awning).is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_wool"))),
                        "The MCP Borgo stall module installs its canopy");
                Napuli.LOGGER.info("[DISTRICTTEST] Borgo stall canopy verified");
                for(var pos:java.util.List.of(new net.minecraft.core.BlockPos(365,68,5),new net.minecraft.core.BlockPos(354,68,18)))
                    check(p.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ_SLAB),
                            "The sports facility canopy is installed at "+pos);
                Napuli.LOGGER.info("[DISTRICTTEST] Fuorigrotta canopies verified");
                for(var pos:java.util.List.of(new net.minecraft.core.BlockPos(131,64,5),new net.minecraft.core.BlockPos(141,64,5)))
                    check(p.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE),
                            "A civic lamp base is placed outside the prison gate at "+pos);
                Napuli.LOGGER.info("[DISTRICTTEST] Civic lamps verified");
                for(var pos:java.util.List.of(new net.minecraft.core.BlockPos(388,68,66),new net.minecraft.core.BlockPos(413,68,66)))
                    check(p.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SPRUCE_SLAB),
                            "A gazebo roof is installed at "+pos);
                Napuli.LOGGER.info("[DISTRICTTEST] Fuorigrotta gazebos verified");
                var entry=new net.minecraft.core.BlockPos(-117,64,39);
                var canopy=new net.minecraft.core.BlockPos(-117,69,41);
                check(p.level().getBlockState(entry).isAir()&&p.level().getBlockState(entry.above()).isAir(),
                        "The refurbished employment office entrance remains walkable");
                check(p.level().getBlockState(canopy).is(net.minecraft.world.level.block.Blocks.DARK_PRISMARINE_SLAB),
                        "The employment office has its new portico");
                check(p.level().getBlockState(new net.minecraft.core.BlockPos(-122,65,31)).is(net.minecraft.world.level.block.Blocks.GLASS),
                        "The employment office has its new side windows");
                Napuli.LOGGER.info("[DISTRICTTEST] Employment office facade and entrance verified");
                var home=NapoliMergellina.PROPERTIES.getFirst();
                var portal=home.entrance();
                check(p.level().getBlockState(portal.west(2)).is(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE),
                        "The registered residence receives the MCP portal");
                check(p.level().getBlockState(portal).isAir()&&p.level().getBlockState(portal.north()).getBlock() instanceof net.minecraft.world.level.block.DoorBlock,
                        "The new portal preserves the entrance and its door");
                Napuli.LOGGER.info("[DISTRICTTEST] Shared residence refit verified");
                var business=DistrictLife.byId(shop).business();
                p.teleportTo(p.level(),business.x()+11.5,business.ground()+5,business.z()+38.5,Set.of(),180,18,true);
            });
            if(tick==160)shot(mc,"district-bottega");if(tick==180)server(mc,p->DistrictShops.menu(p,shop));if(tick==220){check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,"Shop dialogue opens on the real client");shot(mc,"district-shop-menu");mc.gui.setScreen(null);server(mc,p->CityNetwork.menu(p));}if(tick==260){check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,"Transport dialogue opens on the real client");shot(mc,"district-transport-menu");mc.gui.setScreen(null);server(mc,p->{var line=CityNetwork.line("M6");var stop=CityNetwork.stop("augusto");CityNetwork.ensureStation(p.level(),stop);var pos=stop.boarding(line);p.teleportTo(p.level(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),90,0,true);NapoliStreet.credit(p,100);check(CityNetwork.book(p,"lala","M6")==1,"Metro trip can be booked");});}
            if(tick>260&&tick%10==0)server(mc,p->{var t=p.getAttached(CityNetwork.TICKET);if(t!=null&&t.state().equals("riding")){boarded=true;check(p.getVehicle() instanceof CityVehicle,"Real client is a vehicle passenger");}if(boarded&&t==null&&!arrived){arrived=true;check(p.getBlockY()==11,"Metro arrives on an underground platform");}});
            if(tick==400)shot(mc,"district-metro-ride");if(arrived&&tick>430&&palaceAt==0){
                shot(mc,"district-metro-arrival");
                palaceAt=tick;
                server(mc,p->{
                    p.teleportTo(p.level(),38.5,71.1,34.5,Set.of(),0,2,true);
                    checkPlebiscitoInteriors(p);
                });
            }
            if(palaceAt>0&&tick==palaceAt+45){
                shot(mc,"interni-palazzo-reale");
                server(mc,p->p.teleportTo(p.level(),-57.5,64.1,26.5,Set.of(),90,-5,true));
            }
            if(palaceAt>0&&tick>=palaceAt+115){
                shot(mc,"interni-san-francesco");
                Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: rendered district, metro ride and Plebiscito interiors");
                done=true;
            }
            if(tick>2000)throw new AssertionError("Metro trip did not complete");
        }catch(Throwable e){failed=true;Napuli.LOGGER.error("[DISTRICTTEST] FAILED",e);}
    });}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action){var s=mc.getSingleplayerServer();s.execute(()->{try{action.accept(s.getPlayerList().getPlayers().getFirst());}catch(Throwable e){failed=true;Napuli.LOGGER.error("[DISTRICTTEST] FAILED on server",e);}});}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[DISTRICTTEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

    private static void legacyMonuments(Minecraft mc) {
        if (mc.player == null) {
            if (++legacyLoadTicks > 2400) {
                Napuli.LOGGER.error("[DISTRICTTEST] Old save did not open; current screen: {}", mc.gui.screen());
                failed = true;
            } else if (!legacyBackupAccepted && mc.gui.screen() instanceof BackupConfirmScreen confirmation) {
                // This run opens an already isolated copy of the save.
                var skipBackup = Component.translatable("selectWorld.backupJoinSkipButton");
                confirmation.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getMessage().equals(skipBackup)).findFirst()
                        .ifPresent(button -> {
                            legacyBackupAccepted = true;
                            button.onPress(null);
                        });
            } else if (mc.gui.screen() instanceof TitleScreen && ++titles == 80) {
                mc.createWorldOpenFlows().openWorld(LEGACY_MONUMENT_WORLD, () -> { failed = true; });
            }
            return;
        }
        if (mc.getSingleplayerServer() == null) return;
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) mc.gui.setScreen(null);
        if (!starting) {
            starting = true;
            mc.options.pauseOnLostFocus = false;
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            server(mc, p -> {
                if (p.level().dimension() != NapoliMap.LEVEL) NapoliMap.visit(p);
                ready = true;
            });
        }
        if (!ready || mc.player.level().dimension() != NapoliMap.LEVEL) return;
        if (Boolean.getBoolean("napuli.legacyCoroglio")) { legacyCoroglio(mc); return; }
        if (Boolean.getBoolean("napuli.legacyPosillipo")) { legacyPosillipo(mc); return; }
        if (Boolean.getBoolean("napuli.legacySeafrontVoices")) { legacySeafrontVoices(mc); return; }
        if (Boolean.getBoolean("napuli.legacyWaterfront")) { legacyWaterfront(mc); return; }
        if (LEGACY_FOOTBALL_STATS) { legacyFootballStats(mc); return; }
        if (LEGACY_GROUND_EVENTS) { legacyGroundAndEvents(mc); return; }
        if (LEGACY_HOUSES_MEMES) { legacyHousesAndMemes(mc); return; }
        if (LEGACY_CIVIC_SITES) { legacyCivicSites(mc); return; }
        if (LEGACY_LANDMARK_WALK) { legacyLandmarkWalk(mc); return; }
        if (LEGACY_MUSEUMS) { legacyMuseums(mc); return; }
        if (LEGACY_ALBERGO_PIETRARSA) { legacyAlbergoPietrarsa(mc); return; }
        if (LEGACY_COURTYARDS) { legacyCourtyards(mc); return; }
        if (LEGACY_CAPODIMONTE_SANITA) { legacyCapodimonteSanita(mc); return; }
        if (LEGACY_RESIDENCES) { legacyResidences(mc); return; }
        if (LEGACY_CERTOSA) { legacyCertosa(mc); return; }
        if (LEGACY_OVO_MUSEUM) { legacyOvoMuseum(mc); return; }
        if (LEGACY_CASTLE_CATHEDRAL) { legacyCastleCathedral(mc); return; }
        if (++tick == 90) {
            server(mc, p -> {
                var city = p.level().getServer().getLevel(NapoliMap.LEVEL);
                var theatre = NapoliTheatreQuarter.THEATRE;
                var gallery = NapoliDistrict.GALLERIA;
                check(city.getBlockState(theatre.offset(15, 15, 7)).is(Blocks.GOLD_BLOCK), "Old save acquired the theatre proscenium");
                check(city.getBlockState(theatre.offset(15, 1, 7)).is(NapoliMonumentInteriors.material("ochre_froglight")),
                        "Stage footlights are installed");
                for (int order = 0; order < 6; order++)
                    check(city.getBlockState(theatre.offset(1, 3 + 3 * order, 16)).is(Blocks.DARK_OAK_PLANKS), "Missing theatre balcony " + order);
                for (int z = 9; z <= 30; z++) {
                    var path = theatre.offset(15, 1, z);
                    var state = city.getBlockState(path);
                    check((state.isAir() || state.is(NapoliMonumentInteriors.material("red_carpet"))) && city.isEmptyBlock(path.above())
                            && city.getBlockState(path.below()).isSolidRender(), "Blocked theatre aisle: " + path);
                }
                check(city.getBlockState(theatre.offset(2, 1, 24)).is(Blocks.DARK_OAK_STAIRS), "Foyer stairs provide access to the boxes");
                check(city.getBlockState(gallery.north(3).below()).is(NapoliMonumentInteriors.material("oxidized_cut_copper")), "Old save acquired the compass rose");
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    for (int i = 0; i <= NapoliDistrict.ARM; i++) {
                        var path = gallery.relative(direction, i);
                        check(city.isEmptyBlock(path) && city.isEmptyBlock(path.above())
                                && city.getBlockState(path.below()).isSolidRender(), "Blocked gallery walkway: " + path);
                    }
                }
                check(city.getBlockState(gallery.east(12).above(20)).is(Blocks.DEEPSLATE_TILES), "Gallery roof ribs were upgraded");
                check(city.getBlockState(gallery.east(20).atY(78)).is(NapoliMonumentInteriors.material("iron_chain")),
                        "Gallery lanterns connect to the vault");
                check(city.getBlockEntity(NapoliDistrict.PUTECA.north()) instanceof net.minecraft.world.Container,
                        "Rafele’s existing shop storage survives the migration");
                city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(), "time set noon");
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(city, theatre.getX() + 15.5, 91, theatre.getZ() + 51.5, Set.of(), 180, 29, true);
            });
        }
        if (tick == 155) {
            shot(mc, "san-carlo-facciata-legacy");
            server(mc, p -> p.teleportTo(p.level(), 99.5, 65, -97.5, Set.of(), 180, -11, true));
        }
        if (tick == 220) {
            shot(mc, "san-carlo-platea-legacy");
            server(mc, p -> p.teleportTo(p.level(), 99.5, 66.8, -111.5, Set.of(), 0, -18, true));
        }
        if (tick == 285) {
            shot(mc, "san-carlo-palchi-legacy");
            server(mc, p -> p.teleportTo(p.level(), 118.5, 72, -27.5, Set.of(), 180, 22, true));
        }
        if (tick == 350) {
            shot(mc, "galleria-umberto-interno-legacy");
            server(mc, p -> p.teleportTo(p.level(), 118.5, 68, -31.5, Set.of(), 180, -54, true));
        }
        if (tick == 415) {
            shot(mc, "galleria-umberto-cupola-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: San Carlo and Galleria Umberto on an existing save");
            done = true;
        }
    }

    private static void legacyCastleCathedral(Minecraft mc) {
        if (++tick == 140) {
            server(mc, p -> {
                var city = p.level();
                var hall = NapoliCastleCathedral.BARONI;
                var chapel = NapoliCastleCathedral.PALATINA;
                var duomo = NapoliCastleCathedral.cathedralFloor();
                check(city.getBlockState(hall.offset(8, 19, 6)).is(Blocks.GLASS), "The Sala dei Baroni has a glazed oculus");
                for (int z = 14; z <= 17; z++) {
                    var tread = hall.offset(8, 18 - z, z);
                    check(city.getBlockState(tread).is(Blocks.POLISHED_ANDESITE_STAIRS) && city.isEmptyBlock(tread.above())
                            && city.isEmptyBlock(tread.above(2)), "Castel Nuovo stair is walkable at " + tread);
                }
                check(city.isEmptyBlock(hall.offset(8, 5, 13)) && city.isEmptyBlock(chapel.offset(8, 1, 0)),
                        "The courtyard connects to the Sala dei Baroni and the Palatine chapel");
                check(city.getBlockState(chapel.offset(15, 3, 5)).is(Blocks.POLISHED_GRANITE), "The Palatine altar is installed");
                check(city.getBlockState(new net.minecraft.core.BlockPos(223, 69, 25)).is(Blocks.POLISHED_BLACKSTONE_BRICKS),
                        "A Castel Nuovo tower survives the refit");
                check(city.getBlockState(duomo.offset(3, 2, 5)).is(Blocks.WATER_CAULDRON), "The cathedral has a baptismal font");
                check(city.getBlockState(duomo.offset(15, 6, 1)).is(Blocks.GOLD_BLOCK), "The cathedral sanctuary is furnished");
                check(city.getBlockState(duomo.offset(15, 3, 1)).is(Blocks.CHISELED_QUARTZ_BLOCK)
                        && city.getBlockState(duomo.offset(15, 11, 0)).is(NapoliMonumentInteriors.material("yellow_stained_glass")),
                        "The altar cross rests on its plinth in front of the stained glass apse");
                for (int x : new int[]{2, 15, 28}) for (int z = 10; z <= 32; z++) {
                    var walk = duomo.offset(x, 1, z);
                    check(city.isEmptyBlock(walk) && city.isEmptyBlock(walk.above())
                            && city.getBlockState(walk.below()).isSolidRender(), "A cathedral aisle is blocked at " + walk);
                }
                check(city.isEmptyBlock(duomo.offset(15, 1, 33)), "The cathedral main portal is accessible");
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(city, hall.getX()+8.5, hall.getY()+6.4, hall.getZ()+11.5, Set.of(), 180, -38, true);
            });
        }
        if (tick == 205) {
            shot(mc, "maschio-angioino-sala-dei-baroni-legacy");
            var chapel = NapoliCastleCathedral.PALATINA;
            server(mc, p -> p.teleportTo(p.level(), chapel.getX()+3.5, chapel.getY()+2.7, chapel.getZ()+6.5, Set.of(), -90, -4, true));
        }
        if (tick == 270) {
            shot(mc, "maschio-angioino-cappella-palatina-legacy");
            var duomo = NapoliCastleCathedral.cathedralFloor();
            server(mc, p -> p.teleportTo(p.level(), duomo.getX()+15.5, duomo.getY()+3.2, duomo.getZ()+30.5, Set.of(), 180, -9, true));
        }
        if (tick == 350) {
            shot(mc, "duomo-tre-navate-legacy");
            var duomo = NapoliCastleCathedral.cathedralFloor();
            server(mc, p -> p.teleportTo(p.level(), duomo.getX()+25.5, duomo.getY()+3.1, duomo.getZ()+12.5, Set.of(), 180, -8, true));
        }
        if (tick == 415) {
            shot(mc, "duomo-cappella-laterale-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Castel Nuovo and Duomo on an existing save");
            done = true;
        }
    }

    private static void legacyOvoMuseum(Minecraft mc) {
        if (++tick == 155) {
            server(mc, p -> {
                var city = p.level();
                var hall = NapoliOvoMuseum.COLONNE;
                var italia = NapoliOvoMuseum.ITALIA;
                var museum = NapoliOvoMuseum.museumFloor();
                for (int z = 0; z <= 5; z++) {
                    var tread = hall.offset(5, 6 - z, z);
                    check(city.getBlockState(tread).is(Blocks.POLISHED_TUFF_STAIRS) && city.isEmptyBlock(tread.above())
                            && city.isEmptyBlock(tread.above(2)), "Blocked descent in Castel dell'Ovo at " + tread);
                }
                for (int z = 6; z <= 16; z++) {
                    var passage = hall.offset(5, 1, z);
                    check(city.isEmptyBlock(passage) && city.isEmptyBlock(passage.above())
                            && city.getBlockState(passage.below()).is(Blocks.POLISHED_TUFF), "Sala delle Colonne stays dry and walkable at " + passage);
                }
                check(city.getBlockState(hall.offset(3, 2, 11)).is(Blocks.QUARTZ_PILLAR), "Sala delle Colonne has its inner colonnade");
                check(city.isEmptyBlock(hall.offset(5, 5, 11)) && city.getBlockState(hall.offset(5, 6, 11)).isSolidRender(),
                        "The new vault recess retains the courtyard floor above");
                check(city.getBlockState(italia.offset(3, 9, 1)).is(Blocks.SMOOTH_SANDSTONE), "Sala Italia has a continuous vault");
                check(city.getBlockState(italia.offset(6, 1, 3)).is(Blocks.SMOOTH_QUARTZ)
                        && city.getBlockEntity(italia.offset(5, 1, 3)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity,
                        "The previous egg display and sign remain in the castle");
                check(city.getBlockState(new net.minecraft.core.BlockPos(-4, 65, 95)).is(Blocks.POLISHED_TUFF_STAIRS),
                        "The stairs to the cannon terrace remain in place");
                check(city.getBlockState(museum.offset(41, 6, 21)).is(Blocks.CALCITE), "The Farnese sculpture is installed");
                check(city.getBlockState(museum.offset(5, 8, 14)).is(Blocks.POLISHED_BLACKSTONE), "The MANN mosaic has its frame");
                check(city.getBlockState(museum.offset(25, 15, 6)).is(Blocks.CALCITE), "The upper MANN gallery has its marble celestial globe");
                for (int z = 6; z < 24; z++) {
                    var line = museum.offset(19 + (z - 6) / 2, 8, z);
                    check(city.getBlockState(line).is(NapoliMonumentInteriors.material("waxed_cut_copper")), "Interrupted meridian at " + line);
                }
                check(city.getBlockState(museum.offset(25, 9, 24)).is(Blocks.DARK_OAK_FENCE), "The upper gallery has a rail at the atrium edge");
                check(city.getBlockState(museum.offset(17, 8, 30)).is(Blocks.QUARTZ_STAIRS), "The original stairs still reach the MANN galleries");
                check(city.isEmptyBlock(museum.offset(25, 1, 41)), "The museum entrance stays open");
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(city, hall.getX()+5.5, hall.getY()+2.7, hall.getZ()+7.5, Set.of(), 0, -4, true);
            });
        }
        if (tick == 220) {
            shot(mc, "castel-ovo-sala-delle-colonne-legacy");
            server(mc, p -> p.teleportTo(p.level(), 7.5, 66.5, 99.5, Set.of(), -90, -10, true));
        }
        if (tick == 285) {
            shot(mc, "castel-ovo-sala-italia-legacy");
            var museum = NapoliOvoMuseum.museumFloor();
            server(mc, p -> p.teleportTo(p.level(), museum.getX()+41.5, museum.getY()+3.4, museum.getZ()+31.5, Set.of(), 180, -4, true));
        }
        if (tick == 365) {
            shot(mc, "mann-galleria-farnese-legacy");
            var museum = NapoliOvoMuseum.museumFloor();
            server(mc, p -> p.teleportTo(p.level(), museum.getX()+26.5, museum.getY()+11.5, museum.getZ()+22.5, Set.of(), 180, -8, true));
        }
        if (tick == 430) {
            shot(mc, "mann-salone-meridiana-legacy");
            var museum = NapoliOvoMuseum.museumFloor();
            server(mc, p -> p.teleportTo(p.level(), museum.getX()+9.5, museum.getY()+13.0, museum.getZ()+25.5, Set.of(), 180, 31, true));
        }
        if (tick == 495) {
            shot(mc, "mann-mosaico-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Castel dell'Ovo and MANN on an existing save");
            done = true;
        }
    }
    private static void checkPlebiscitoInteriors(ServerPlayer p) {
        var city = p.level();
        var stair = new net.minecraft.core.BlockPos(42, 64, 28);
        check(city.getBlockState(stair).is(Blocks.QUARTZ_STAIRS), "The staircase to the piano nobile remains open");
        check(city.getBlockState(new net.minecraft.core.BlockPos(38, 74, 42)).is(Blocks.GOLD_BLOCK), "Throne canopy is in the palace");
        check(city.getBlockState(new net.minecraft.core.BlockPos(-78, 65, 25)).is(Blocks.LAPIS_BLOCK), "Basilica altar has lapis inlay");
        check(city.getBlockState(new net.minecraft.core.BlockPos(-75, 78, 19)).is(Blocks.CHISELED_QUARTZ_BLOCK), "Basilica colonnade has capitals");
        for (int x = -75; x <= -57; x++) {
            var aisle = new net.minecraft.core.BlockPos(x, 64, 26);
            check(city.isEmptyBlock(aisle) && city.isEmptyBlock(aisle.above()), "The basilica's central aisle stays clear at " + aisle);
        }
    }

    private static int justinSounds;
    private static net.minecraft.client.resources.sounds.SoundInstance justinSound;

    private static void legacyCoroglio(Minecraft mc) {
        var mouth = NapoliCoroglio.grotta();
        var lab = NapoliCoroglio.fablab();
        var nisida = NapoliCoroglio.nisidaPavilion();
        if (++tick == 1) server(mc, p -> {
            var city = p.level();
            city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(), "time set noon");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, mouth.getX() + 10, mouth.getY() + 11, mouth.getZ() + 13, Set.of(), 140, 24, true);
        });
        if (tick == 120) shot(mc, "napoli-coroglio-seiano-v66");
        if (tick == 125) server(mc, p -> {
            var city = p.level();
            var virgiliano = NapoliPosillipo.center("virgiliano");
            var pier = NapoliCoroglio.pierHut();
            var odeion = NapoliCoroglio.odeion();
            var checks = java.util.Map.of(
                    virgiliano.offset(0, 6, 49), Blocks.SMOOTH_SANDSTONE,
                    virgiliano.offset(-17, 0, 36), Blocks.BOOKSHELF,
                    lab.offset(3, 1, 7), Blocks.STONECUTTER,
                    lab.offset(3, 1, 4), Blocks.CRAFTING_TABLE,
                    pier.offset(2, 1, 3), Blocks.LECTERN,
                    odeion.offset(8, 0, 3), Blocks.POLISHED_DIORITE,
                    nisida.offset(10, 0, 6), Blocks.LECTERN,
                    nisida.offset(1, 0, 5), Blocks.BOOKSHELF,
                    NapoliCoroglio.chiuppino().offset(6, 0, 4), Blocks.LECTERN);
            for (var check : checks.entrySet())
                check(city.getBlockState(check.getKey()).is(check.getValue()), "Missing " + check.getValue()
                        + " at " + check.getKey() + "; present " + city.getBlockState(check.getKey()));
            for (var door : java.util.List.of(NapoliCoroglio.fablabDoor(), pier.offset(5, 0, 9),
                    odeion.offset(9, 0, 16), nisida.offset(7, 0, 0), nisida.offset(7, 0, 12),
                    NapoliCoroglio.chiuppino().offset(5, 0, 11))) {
                check(city.getBlockState(door).is(Blocks.SPRUCE_DOOR) && city.getBlockState(door.above()).is(Blocks.SPRUCE_DOOR)
                        && city.getBlockState(door.below()).isSolidRender() && city.isEmptyBlock(door.north())
                        && city.isEmptyBlock(door.south()), "Blocked building entrance " + door);
            }
            for (int z = -19; z <= 6; z++) {
                var path = mouth.south(z);
                check(city.isEmptyBlock(path) && city.isEmptyBlock(path.above())
                        && city.getBlockState(path.below()).isSolidRender(), "Blocked tunnel " + path);
            }
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.setGameMode(GameType.ADVENTURE);
            p.teleportTo(city, mouth.getX() + .5, mouth.getY(), mouth.getZ() + 5.5, Set.of(), 180, 0, true);
        });
        if (tick >= 170 && tick < 349)
            mc.options.keyUp.setDown(mc.player.getZ() > mouth.getZ() - 19);
        if (tick == 260) shot(mc, "napoli-seiano-interno-v66");
        if (tick == 349) mc.options.keyUp.setDown(false);
        if (tick == 350) {
            check(mc.player.getZ() <= mouth.getZ() - 19, "Player walks through Grotta di Seiano");
            server(mc, p -> {
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.level(), lab.getX() + 11, lab.getY() + 12, lab.getZ() - 10, Set.of(), 0, 22, true);
            });
        }
        if (tick == 440) shot(mc, "napoli-coroglio-fablab-v66");
        if (tick == 445) server(mc, p -> p.teleportTo(p.level(), lab.getX() + 12, lab.getY() + .15,
                lab.getZ() + 12, Set.of(), 155, 0, true));
        if (tick == 485) shot(mc, "napoli-coroglio-fablab-interno-v66");
        if (tick == 490) server(mc, p -> p.teleportTo(p.level(), nisida.getX() + 9,
                nisida.getY() + 13, nisida.getZ() - 10, Set.of(), 15, 27, true));
        if (tick == 590) shot(mc, "napoli-nisida-v66");
        if (tick == 595) server(mc, p -> p.teleportTo(p.level(), nisida.getX() + 7.5,
                nisida.getY() + .15, nisida.getZ() + 10, Set.of(), 175, 0, true));
        if (tick == 645) {
            shot(mc, "napoli-nisida-padiglione-v66");
            server(mc, p -> {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.setGameMode(GameType.ADVENTURE);
                var bridge = new net.minecraft.core.BlockPos(-1842, 66, 292);
                p.teleportTo(p.level(), bridge.getX() + .5, bridge.getY(), bridge.getZ() + .5,
                        Set.of(), 21, 0, true);
            });
        }
        if (tick >= 680 && tick < 895)
            mc.options.keyUp.setDown(mc.player.getZ() < 313);
        if (tick == 895) {
            mc.options.keyUp.setDown(false);
            check(mc.player.getZ() >= 313 && mc.player.getY() >= 65.5,
                    "Player crosses the Nisida causeway on foot: " + mc.player.blockPosition());
            shot(mc, "napoli-ponte-nisida-v66");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Coroglio interiors, Seiano walk, pier and Nisida on old save");
            done = true;
        }
    }


    private static int coastalVoices;
    private static net.minecraft.client.resources.sounds.SoundInstance coastalVoice;

    private static void legacyPosillipo(Minecraft mc) {
        var villa = NapoliPosillipo.center("villa_comunale");
        var square = NapoliPosillipo.center("sannazaro");
        var village = NapoliPosillipo.center("marechiaro");
        var event = NapoliStreetMemes.Scene.INDICAZIONI;
        if (++tick == 1) {
            mc.getSoundManager().addListener((sound, weighted, range) -> {
                if (sound.getIdentifier().equals(Napuli.id("vocale_indicazioni"))) {
                    coastalVoices++;
                    coastalVoice = sound;
                }
            });
            server(mc, p -> {
                var city = p.level();
                city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(), "time set 6000");
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(city, villa.getX() - 1, villa.getY() + 17, villa.getZ() + 37, Set.of(), 160, 24, true);
            });
        }
        if (tick == 155) shot(mc, "napoli-villa-comunale-v65");
        if (tick == 160) server(mc, p -> {
            var city = p.level();
            var exhibits = java.util.Map.of(
                    villa.offset(-33, 1, -11), Blocks.WATER,
                    villa.offset(15, 1, -22), Blocks.DECORATED_POT,
                    villa.offset(19, 0, 16), Blocks.DARK_OAK_PLANKS,
                    villa.offset(-4, 2, 21), Blocks.NOTE_BLOCK,
                    square.offset(-1, 1, -22), Blocks.CHISELED_QUARTZ_BLOCK,
                    village.offset(-8, 1, -11), Blocks.CHISELED_QUARTZ_BLOCK,
                    village.offset(9, 4, 5), CityBlocks.vanilla("white_bed"));
            for (var entry : exhibits.entrySet())
                check(city.getBlockState(entry.getKey()).is(entry.getValue()), "Interior missing at " + entry.getKey()
                        + " expected " + entry.getValue() + " got " + city.getBlockState(entry.getKey()));
            for (var portal : java.util.List.of(villa.offset(-23, 0, 8), villa.offset(23, 0, -12),
                    villa.offset(25, 0, 27), square.offset(-1, 0, -9), village.offset(-8, 0, 4),
                    village.offset(7, 0, 8))) {
                check(city.getBlockState(portal).is(Blocks.SPRUCE_DOOR) && city.getBlockState(portal.above()).is(Blocks.SPRUCE_DOOR)
                                && city.getBlockState(portal.below()).isSolidRender() && city.isEmptyBlock(portal.north())
                                && city.isEmptyBlock(portal.south()), "Coastal building entrance obstructed: " + portal);
            }
            int shore = (int) NapoliExpansion.coast(village.getX());
            check(city.getBlockState(new net.minecraft.core.BlockPos(village.getX(), 64, shore + 4)).is(Blocks.STONE_BRICKS),
                    "Calata Ponticello reaches the open water");
            int plateau = village.getY() - 1;
            int gradeStart = shore - (plateau - 64) * 2;
            for (int z = village.getZ() + 22; z <= shore; z++) {
                int y = plateau - Math.max(0, (z - gradeStart + 1) / 2);
                var tread = new net.minecraft.core.BlockPos(village.getX(), y, z);
                var state = city.getBlockState(tread);
                check((state.is(Blocks.POLISHED_TUFF) || state.is(Blocks.POLISHED_TUFF_STAIRS))
                        && city.isEmptyBlock(tread.above()) && city.isEmptyBlock(tread.above(2)),
                        "Obstruction at " + tread + " " + state + " / " + city.getBlockState(tread.above())
                                + " / " + city.getBlockState(tread.above(2)));
            }
            check(city.getBlockState(new net.minecraft.core.BlockPos(village.getX() - 22, 64, shore + 11))
                            .is(Blocks.POLISHED_TUFF), "Villa degli Spiriti floor is " + city.getBlockState(new net.minecraft.core.BlockPos(village.getX() - 22, 64, shore + 11)));
            for (var scene : java.util.List.of(event, NapoliStreetMemes.Scene.RICETTA, NapoliStreetMemes.Scene.BALLO,
                    NapoliStreetMemes.Scene.GIUBBETTO, NapoliStreetMemes.Scene.SALUTO)) {
                long positions = java.util.stream.IntStream.range(0, 15).filter(i -> {
                    var spot = scene.point.offset((i % 5 - 2) * 2 + i / 5 % 2, 0, i / 5 * 2 - 2);
                    city.getChunkAt(spot);
                    return NapoliResidentHomes.walkable(city, spot);
                }).count();
                check(positions >= scene.actors, "Not enough space for the cast of " + scene.id);
            }
            var pz = NapoliAtlas.find("donnanna");
            var start = NapoliLandmarkAccess.approach(city, pz);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.setGameMode(GameType.ADVENTURE);
            p.teleportTo(city, start.getX() + .5, start.getY(), start.getZ() + .5, Set.of(), 180, 0, true);
        });
        if (tick >= 180 && tick < 415 && mc.player.getX() < -1100 && mc.player.getX() > -1200) {
            var palace = NapoliAtlas.find("donnanna");
            if (mc.player.getZ() > palace.z() + 17.85) mc.options.keyUp.setDown(true);
            else if (mc.options.keyUp.isDown()) {
                mc.options.keyUp.setDown(false);
                shot(mc, "napoli-donnanna-interno-v65");
            }
        }
        if (tick == 410) {
            mc.options.keyUp.setDown(false);
            check(mc.player.getZ() <= NapoliAtlas.find("donnanna").z() + 17.85,
                    "Player walked from Via Posillipo through the Donn’Anna entrance");
            server(mc, p -> {
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.level(), village.getX() + 10, village.getY() + 19, village.getZ() + 35,
                        Set.of(), 165, 25, true);
            });
        }
        if (tick == 510) shot(mc, "napoli-marechiaro-v65");
        int shore = (int) NapoliExpansion.coast(village.getX());
        if (tick == 515) server(mc, p -> {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.setGameMode(GameType.ADVENTURE);
            p.teleportTo(p.level(), village.getX() + .5, village.getY(), village.getZ() + 21.5, Set.of(), 0, 3, true);
        });
        if (tick >= 555 && tick < 920)
            mc.options.keyUp.setDown(mc.player.getZ() < shore + 3);
        if (tick == 920) {
            mc.options.keyUp.setDown(false);
            check(mc.player.getZ() >= shore + 3 && mc.player.getY() < 66,
                    "Player walked from the village to the quay: " + mc.player.blockPosition());
            shot(mc, "napoli-calataponticello-v65");
            server(mc, p -> p.teleportTo(p.level(), event.point.getX() + .5,
                    event.point.getY(), event.point.getZ() + 4.5, Set.of(), 180, 0, true));
        }
        if (tick == 965) mc.getConnection().sendCommand("citta meme indicazioni");
        if (tick == 1100) {
            check(coastalVoices == 1 && coastalVoice != null && mc.getSoundManager().isActive(coastalVoice),
                    "The command plays one copy of the voice clip in Marechiaro");
            shot(mc, "napoli-marechiaro-vocale-v65");
        }
        if (tick == 1173) {
            check(coastalVoices == 1 && !mc.getSoundManager().isActive(coastalVoice),
                    "The new recording ends without repeating");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Posillipo interiors, palace walk and Marechiaro recording on old save");
            done = true;
        }
    }


    private static int marketVoices;
    private static net.minecraft.client.resources.sounds.SoundInstance marketVoice;

    private static void legacySeafrontVoices(Minecraft mc) {
        var event = NapoliStreetMemes.Scene.MERCATO;
        if (++tick == 1) {
            mc.getSoundManager().addListener((sound, weighted, range) -> {
                if (sound.getIdentifier().equals(Napuli.id("vocale_mercato"))) {
                    marketVoices++;
                    marketVoice = sound;
                }
            });
            server(mc, p -> {
                var city = p.level();
                var server = city.getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(city, -20, 77, 25, Set.of(), -90, 14, true);
            });
        }
        if (tick == 130) shot(mc, "napoli-plebiscito-palazzi-v64");
        if (tick == 145) server(mc, p -> {
            var city = p.level();
            for (int z : new int[] {14, 36}) {
                var portal = new net.minecraft.core.BlockPos(15, 64, z);
                check(city.getBlockState(portal.above(5)).is(Blocks.SMOOTH_QUARTZ)
                                || city.getBlockState(portal.above(5)).is(Blocks.CALCITE),
                        "Missing side palace at " + portal);
                check(NapoliResidentHomes.walkable(city, portal), "Side palace entrance blocked: " + portal);
            }
            check(city.getBlockState(new net.minecraft.core.BlockPos(-69, 101, 26)).is(Blocks.POLISHED_ANDESITE),
                    "Dome lantern has a supporting base");
            check(city.getBlockState(new net.minecraft.core.BlockPos(37, 87, 10)).is(Blocks.BRICKS),
                    "Palazzo Reale roof tiles are supported");
            for (var scene : java.util.List.of(event, NapoliStreetMemes.Scene.TEATRO,
                    NapoliStreetMemes.Scene.CANTANTE, NapoliStreetMemes.Scene.ORDINANZA)) {
                long spots = java.util.stream.IntStream.range(0, 15).filter(i ->
                        NapoliResidentHomes.walkable(city, scene.point.offset((i % 5 - 2) * 2 + i / 5 % 2,
                                0, i / 5 * 2 - 2))).count();
                check(spots >= scene.actors, "Not enough walkable positions for " + scene.id + ": " + spots);
            }
            p.teleportTo(city, -153, 73, 54, Set.of(), 150, 13, true);
        });
        if (tick == 240) shot(mc, "napoli-case-mergellina-v64");
        if (tick == 245) server(mc, p -> {
            var point = event.point;
            p.teleportTo(p.level(), point.getX() + .5, point.getY(), point.getZ() - 7, Set.of(), 0, 0, true);
        });
        if (tick == 290) mc.getConnection().sendCommand("citta meme mercato");
        if (tick == 423) {
            check(marketVoices == 1 && marketVoice != null && mc.getSoundManager().isActive(marketVoice),
                    "The market command plays one copy of the supplied recording after the setup");
            shot(mc, "napoli-vocale-mercato-v64");
        }
        if (tick == 515) {
            check(marketVoices == 1 && !mc.getSoundManager().isActive(marketVoice),
                    "The short clip ends without looping");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: side palaces, waterfront and proximity voice on old save");
            done = true;
        }
    }


    private static void legacyWaterfront(Minecraft mc) {
        if (++tick == 1) server(mc, p -> {
            var city = p.level();
            var server = city.getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, -49, 72, 62, Set.of(), 111, 8, true);
        });
        if (tick == 120) shot(mc, "napoli-via-partenope-v63");
        if (tick == 125) server(mc, p -> {
            var city = p.level();
            for (int x : new int[] {-106, -93}) {
                check(city.getBlockEntity(new net.minecraft.core.BlockPos(x + 3, 64, 44))
                        instanceof net.minecraft.world.level.block.entity.SignBlockEntity, "Waterfront hotel missing at " + x);
                for (int z = 42; z <= 49; z++) {
                    var path = new net.minecraft.core.BlockPos(x, 64, z);
                    check(city.isEmptyBlock(path) && city.isEmptyBlock(path.above()), "Hotel entrance blocked: " + path);
                }
            }
            check(city.getBlockState(new net.minecraft.core.BlockPos(-61, 65, 51)).is(Blocks.WATER),
                    "Fontana del Gigante contains water");
            for (int z = 46; z <= 67; z++) {
                var path = new net.minecraft.core.BlockPos(0, 64, z);
                check(city.isEmptyBlock(path) && city.getBlockState(path.below()).isSolidRender(),
                        "Causeway path obstructed: " + path);
            }
            p.teleportTo(city, 12, 73, 26, Set.of(), 90, 10, true);
        });
        if (tick == 240) {
            shot(mc, "napoli-plebiscito-v63");
            mc.getSoundManager().addListener((sound, event, range) -> {
                if (sound.getIdentifier().equals(Napuli.id("justin_crowd"))) {
                    justinSounds++;
                    justinSound = sound;
                }
            });
            server(mc, p -> {
                var scene = NapoliStreetMemes.Scene.JUSTIN;
                var point = scene.point;
                p.teleportTo(p.level(), point.getX() + .5, point.getY() + 1, point.getZ() - 9.5,
                        Set.of(), 0, 0, true);
                check(NapoliStreetMemes.start(p.level(), scene), "Justin event starts after map upgrade");
            });
        }
        if (tick == 285) {
            check(justinSounds == 1 && justinSound != null && mc.getSoundManager().isActive(justinSound),
                    "The client plays the revised clip");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: waterfront hotels, fountain and causeway on old save");
            done = true;
        }
    }


    private static void legacyFootballStats(Minecraft mc) {
        var point = NapoliStreetMemes.Scene.JUSTIN.point;
        if (++tick == 1) {
            mc.getSoundManager().addListener((sound, event, range) -> {
                if (sound.getIdentifier().equals(Napuli.id("justin_crowd"))) {
                    justinSounds++;
                    justinSound = sound;
                }
            });
            server(mc, p -> {
                var server = p.level().getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
                p.setGameMode(GameType.CREATIVE);
                p.teleportTo(p.level(), 0.5, 65, -27.5, Set.of(), 0, 0, true);
            });
        }
        try {
            if (tick == 105) mc.getConnection().sendCommand("calcio statistiche");
            if (tick == 135) {
                check(statsScreen(mc), "The profile command opens the football statistics GUI");
                shot(mc, "napoli-stats-carriera");
                mc.gui.screen().onClose();
                mc.getConnection().sendCommand("calcio gioca napoli");
            }
            if (tick == 255) server(mc, p -> {
                var stats = FootballPractice.stats(p);
                check(stats.live() && stats.lineup().size() == 10, "The live lineup includes the ten actual participants");
                check(stats.lineup().stream().anyMatch(row -> !row.npc() && row.name().equals(stats.profile().name())),
                        "The lineup contains the currently controlled player");
                check(stats.lineup().stream().filter(row -> row.side() == 1).count() == 5,
                        "Opponents have their own full roster");
            });
            if (tick == 258) mc.getConnection().sendCommand("calcio statistiche");
            if (tick == 296) clickStatsTab(mc, "teammates");
            if (tick == 305) shot(mc, "napoli-stats-compagni");
            if (tick == 309) clickStatsTab(mc, "opponents");
            if (tick == 318) {
                shot(mc, "napoli-stats-avversari");
                mc.gui.screen().onClose();
                mc.getConnection().sendCommand("calcio esci");
            }
            if (tick == 325) server(mc, p -> p.teleportTo(p.level(), point.getX() + .5, point.getY() + .2,
                    point.getZ() - 31.5, Set.of(), 0, 0, true));
            if (tick == 422) server(mc, p -> {
                var city = p.level();
                check(NapoliStreetMemes.start(city, NapoliStreetMemes.Scene.JUSTIN),
                        "The scene begins while the listener is out of audio range");
                p.teleportTo(city, point.getX() + .5, point.getY() + .2, point.getZ() - 26.5,
                        Set.of(), 0, 0, true);
            });
            if (tick == 465) {
                check(justinSounds == 1 && justinSound != null && mc.getSoundManager().isActive(justinSound),
                        "Approaching a crowd already in progress at 27 blocks starts the clip");
                mc.getConnection().sendCommand("citta meme justin");
                mc.getConnection().sendCommand("citta meme justin");
            }
            if (tick == 485) {
                check(justinSounds == 1 && justinSound != null && mc.getSoundManager().isActive(justinSound),
                        "The client plays the supplied clip once from the active crowd");
                shot(mc, "napoli-meme-justin-audio");
                Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: real football statistics and Justin audio");
                done = true;
            }
        } catch (Throwable e) {
            failed = true;
            Napuli.LOGGER.error("[DISTRICTTEST] FAILED", e);
        }
    }

    private static boolean statsScreen(Minecraft mc) {
        return mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("FootballStatsScreen");
    }

    private static void clickStatsTab(Minecraft mc, String tab) {
        check(statsScreen(mc), "The match statistics GUI is open");
        var label = Component.translatable("napuli.football.stats." + tab);
        var button = mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().equals(label) && b.active).findFirst().orElseThrow();
        button.onPress(null);
    }

    private static void legacyGroundAndEvents(Minecraft mc) {
        var field = new net.minecraft.core.BlockPos(FootballGround.MID_X, 78, FootballGround.MID_Z + 20);
        var grass = new net.minecraft.core.BlockPos(FootballGround.MID_X, 63, -104);
        var venue = NapoliEventCatalogue.VENUES.getFirst();
        if (++tick == 1) server(mc, p -> {
            var city = p.level();
            city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(), "time set noon");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, field.getX() + .5, field.getY(), field.getZ() + .5, Set.of(), 180, 22, true);
            city.getChunkAt(grass);
        });
        if (tick == 230) server(mc, p -> {
            var city = p.level();
            check(city.getBlockState(grass).is(Blocks.GRASS_BLOCK), "Legacy exposed land near the playing field is grass");
            check(city.getBlockState(new net.minecraft.core.BlockPos(FootballGround.MID_X, 63, FootballGround.MID_Z))
                    .is(Blocks.CALCITE), "Pitch center line is intact");
        });
        if (tick == 235) shot(mc, "napoli-campo-senza-sabbia");
        if (tick == 240) server(mc, p -> {
            var city = p.level();
            var server = city.getServer();
            var commands = server.getCommands();
            var point = venue.point();
            commands.performPrefixedCommand(server.createCommandSourceStack(), "time set " + (venue.opens() + 20));
            p.teleportTo(city, point.getX() + .5, point.getY(), point.getZ() + .5, Set.of(), 180, 0, true);
        });
        if (tick == 440) server(mc, p ->
                p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "citta eventi"));
        if (tick == 465) {
            try {
                check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,
                        "The city agenda opens a native dialog from /citta eventi");
                shot(mc, "napoli-programma-eventi");
                mc.gui.screen().onClose();
            } catch (Throwable e) {
                failed = true;
                Napuli.LOGGER.error("[DISTRICTTEST] FAILED", e);
            }
        }
        if (tick == 475) server(mc, p -> {
            var city = p.level();
            var scene = venue.scene(city.getSeed(), NapoliBroadcast.day(city));
            if (scene.donation() != null)
                p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(scene.donation()));
            int before = p.getInventory().countItem(scene.reward());
            String command = scene.quiz() == null ? "citta partecipa" : "citta partecipa " + scene.quiz().answer();
            var commands = city.getServer().getCommands();
            commands.performPrefixedCommand(p.createCommandSourceStack(), command);
            commands.performPrefixedCommand(p.createCommandSourceStack(), command);
            check(p.getInventory().countItem(scene.reward()) == before + 1,
                    "The live event command awards the active scene's souvenir once");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: legacy ground and live neighbourhood event");
        });
        if (tick == 520) done = true;
    }

    private static void legacyHousesAndMemes(Minecraft mc) {
        var home = DistrictLife.homes().stream()
                .filter(h -> Math.floorMod(h.x(), 16) <= 3 && Math.floorMod(h.z(), 16) <= 3)
                .findFirst().orElseThrow();
        var origin = new net.minecraft.core.BlockPos(home.x(), home.ground(), home.z());
        int roof = home.floors() * 5;
        var point = NapoliStreetMemes.Scene.JUSTIN.point;
        if (++tick == 1) server(mc, p -> {
            var city = p.level();
            var server = city.getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, origin.getX() - 13.5, origin.getY() + roof + 11,
                    origin.getZ() - 15.5, Set.of(), -39, 32, true);
        });
        if (tick == 200) server(mc, p -> {
            var city = p.level();
            var chunk = city.getChunkAt(origin);
            check(NapoliExpansion.revision(chunk) >= 1, "The existing map has generated this atlas lot");
            check(!city.isEmptyBlock(origin.offset(5, roof, 6)), "The test starts with the saved roof");
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin, origin.offset(7, roof, 7)))
                city.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            chunk.removeAttached(NapoliExpansion.HOUSE_SECTIONS_FIXED);
        });
        if (tick == 212) shot(mc, "napoli-casa-sezione-prima");
        if (tick == 220) server(mc, p -> {
            var city = p.level();
            var chunk = city.getChunkAt(origin);
            check(NapoliExpansion.repairHouseSections(city, chunk) > 0, "The old saved chunk receives the house repair");
            check(!city.isEmptyBlock(origin.offset(0, 2, 1)) && !city.isEmptyBlock(origin.offset(5, 4, 6))
                    && !city.isEmptyBlock(origin.offset(5, roof, 6)), "The wall, floors and roof no longer show a section cut");
        });
        if (tick == 245) shot(mc, "napoli-casa-sezione-riparata");
        if (tick == 253) server(mc, p -> p.teleportTo(p.level(), point.getX() + .5, point.getY() + 0.2,
                point.getZ() - 7.5, Set.of(), 0, 0, true));
        if (tick == 400) server(mc, p -> {
            var commands = p.level().getServer().getCommands();
            commands.performPrefixedCommand(p.createCommandSourceStack(), "citta meme justin");
            check(memeActors(p).size() >= 6, "The rumor spawns a real crowd");
            commands.performPrefixedCommand(p.createCommandSourceStack(), "citta meme justin");
            check(memeActors(p).size() <= 10, "Starting again does not duplicate the crowd");
            check(memeActors(p).stream().noneMatch(NapoliResident::shouldBeSaved), "The acting NPCs are never persisted");
        });
        if (tick == 442) shot(mc, "napoli-meme-justin-lungomare");
        if (tick == 470) server(mc, p -> p.teleportTo(p.level(), 0.5, 75, -28.5, Set.of(), 0, 0, true));
        if (tick == 525) server(mc, p -> {
            check(memeActors(p).isEmpty(), "Actors are cleared after the audience leaves");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: repaired house section and live Justin crowd");
            done = true;
        });
    }

    private static java.util.List<NapoliResident> memeActors(ServerPlayer player) {
        return player.level().getEntitiesOfClass(NapoliResident.class,
                new net.minecraft.world.phys.AABB(NapoliStreetMemes.Scene.JUSTIN.point).inflate(20), NapoliStreetMemes::actor);
    }

    private static void legacyCivicSites(Minecraft mc) {
        var station = NapoliCentraleMaradonaScienza.centrale();
        var stadium = NapoliCentraleMaradonaScienza.maradona();
        var science = NapoliCentraleMaradonaScienza.scienza();
        if (++tick == 30) server(mc, p -> p.setGameMode(GameType.CREATIVE));
        if (tick == 90) server(mc, p -> {
            var city = p.level();
            check(city.getBlockState(station.offset(38, 4, 29)).is(Blocks.QUARTZ_PILLAR), "The station canopy has support");
            check(city.getBlockState(station.offset(42, 11, 7)).is(Blocks.SMOOTH_QUARTZ), "The station has a coffered hall");
            for (int z = 2; z <= 39; z++)
                check(city.isEmptyBlock(station.offset(45, 1, z)) && city.isEmptyBlock(station.offset(45, 2, z)),
                        "The main passage to the station hall is open");
            check(city.getBlockState(stadium.offset(76, 0, 114)).is(NapoliMonumentInteriors.material("light_blue_concrete")),
                    "The track is blue and outside the football pitch");
            check(city.getBlockState(stadium.offset(76, 0, 76)).is(Blocks.CALCITE), "The center line remains in place");
            check(!city.isEmptyBlock(stadium.offset(76, 20, 136)), "The grandstand has a roof");
            check(city.isEmptyBlock(stadium.offset(76, 2, 139)), "The southern stadium tunnel remains open");
            check(city.getBlockState(science.offset(49, 1, 22)).is(Blocks.DARK_OAK_STAIRS), "The planetarium has seating");
            check(city.getBlockState(science.offset(54, 22, 37)).is(NapoliMonumentInteriors.material("light_gray_concrete")),
                    "The lower dome closes the gap between its courses");
            check(city.getBlockState(science.offset(9, 1, 13)).is(Blocks.SMOOTH_QUARTZ), "Corporea has exhibit plinths");
            for (int z = 23; z <= 35; z++)
                check(city.isEmptyBlock(science.offset(54, 1, z)) && city.isEmptyBlock(science.offset(54, 2, z)),
                        "The planetarium center aisle is open");
            p.teleportTo(city, station.getX()+45.5, station.getY()+1, station.getZ()+47.5, Set.of(), 180, -8, true);
        });
        if (tick < 90) return;
        if (tick > 3200) {
            Napuli.LOGGER.error("[DISTRICTTEST] Civic camera did not finish loading scene {} at {}", museumScene, mc.player.blockPosition());
            failed = true;
            return;
        }
        switch (museumScene) {
            case 0 -> {
                if (!museumViewReady(mc, station.offset(45, 1, 47), station.offset(38, 4, 29), station.offset(52, 4, 29))) return;
                shot(mc, "napoli-centrale-pensiline-legacy");
                server(mc, p -> p.teleportTo(p.level(), station.getX()+45.5, station.getY()+1, station.getZ()+20.5, Set.of(), 180, -14, true));
            }
            case 1 -> {
                if (!museumViewReady(mc, station.offset(45, 1, 20), station.offset(38, 4, 6), station.offset(52, 4, 6))) return;
                shot(mc, "napoli-centrale-atrio-legacy");
                server(mc, p -> {
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.teleportTo(p.level(), stadium.getX()+76.5, stadium.getY()+56, stadium.getZ()+136.5, Set.of(), 180, 49, true);
                });
            }
            case 2 -> {
                if (!museumViewReady(mc, stadium.offset(76, 56, 136), stadium.offset(25, 19, 42), stadium.offset(126, 19, 45))) return;
                shot(mc, "stadio-maradona-copertura-legacy");
                server(mc, p -> p.teleportTo(p.level(), science.getX()+39.5, science.getY()+8, science.getZ()+62.5,
                        Set.of(), 180, -5, true));
            }
            case 3 -> {
                if (!museumViewReady(mc, science.offset(39, 8, 62), science.offset(17, 8, 39), science.offset(63, 8, 39))) return;
                shot(mc, "citta-della-scienza-corporea-legacy");
                server(mc, p -> p.teleportTo(p.level(), science.getX()+54.5, science.getY()+3, science.getZ()+33.5, Set.of(), 180, 0, true));
            }
            case 4 -> {
                if (!museumViewReady(mc, science.offset(54, 3, 33), science.offset(49, 1, 22), science.offset(59, 3, 15))) return;
                shot(mc, "citta-della-scienza-planetario-legacy");
                Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Centrale, Maradona and Corporea on an existing save");
                done = true;
            }
            default -> {}
        }
        museumScene++;
        museumReadyTicks = 0;
    }

    private static void legacyLandmarkWalk(Minecraft mc) {
        boolean palace = walkStage >= 3;
        var site = NapoliAtlas.find(palace ? "donnanna" : "santelmo");
        var arrival = site.arrival();
        int endZ = palace ? site.z() + 17 : site.z() + 2;
        int endY = NapoliExpansion.height(site.x(), site.z()) + (palace ? 1 : 10);
        if (++walkTicks > 700) {
            mc.options.keyUp.setDown(false);
            Napuli.LOGGER.error("[DISTRICTTEST] Walk timed out at stage {} and {}", walkStage, mc.player.blockPosition());
            failed = true;
            return;
        }
        switch (walkStage % 3) {
            case 0 -> {
                if (walkTicks < 70) return;
                server(mc, p -> {
                    p.setGameMode(GameType.ADVENTURE);
                    var surface = NapoliLandmarkAccess.approach(p.level(), site);
                    p.teleportTo(p.level(), surface.getX()+.5, surface.getY(), surface.getZ()+.5, Set.of(), 180, 0, true);
                });
            }
            case 1 -> {
                if (Math.abs(mc.player.getX() - arrival.getX() - .5) > 1
                        || Math.abs(mc.player.getZ() - arrival.getZ() - .5) > 1 || !mc.player.onGround()) return;
                mc.options.keyUp.setDown(true);
            }
            case 2 -> {
                mc.options.keyUp.setDown(true);
                if (mc.player.getZ() > endZ + .85 || mc.player.getY() + .05 < endY) return;
                mc.options.keyUp.setDown(false);
                shot(mc, palace ? "donnanna-arrivo-a-piedi-legacy" : "santelmo-arrivo-a-piedi-legacy");
                if (palace) {
                    Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Walked from both Atlas arrivals on an existing save");
                    done = true;
                }
            }
            default -> throw new IllegalStateException();
        }
        walkStage++;
        walkTicks = 0;
    }

    private static void legacyMuseums(Minecraft mc) {
        var villa = NapoliAtlasLandmarks.floridiana();
        var fort = NapoliAtlasLandmarks.santelmo();
        var palace = NapoliAtlasLandmarks.donnanna();
        var theatre = NapoliAtlasLandmarks.mostra();
        var wings = PietrarsaMuseumCampus.WINGS;
        var campus = NapoliAlbergoPietrarsa.pietrarsa();
        var g = wings.getLast().origin();
        if (++tick == 90) server(mc, p -> {
            var city = p.level();
            var exhibits = java.util.List.of(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE,
                    Blocks.POLISHED_BLACKSTONE, NapoliMonumentInteriors.material("black_concrete"), Blocks.ANVIL, Blocks.LECTERN);
            var offsets = java.util.List.of(new net.minecraft.core.BlockPos(5, 2, 5), new net.minecraft.core.BlockPos(5, 2, 5),
                    new net.minecraft.core.BlockPos(6, 2, 5), new net.minecraft.core.BlockPos(8, 4, 1),
                    new net.minecraft.core.BlockPos(4, 2, 5), new net.minecraft.core.BlockPos(21, 2, 3));
            for (int i = 0; i < wings.size(); i++) {
                var wing = wings.get(i);
                var entrance = wing.entrance();
                check(city.isEmptyBlock(entrance) && city.isEmptyBlock(entrance.above())
                        && city.getBlockState(entrance.below()).is(Blocks.POLISHED_ANDESITE),
                        "Pietrarsa " + wing.letter() + " has a walkable entrance");
                check(city.getBlockState(wing.origin().offset(offsets.get(i))).is(exhibits.get(i)),
                        "Pietrarsa " + wing.letter() + " has its own exhibit");
            }
            check(city.getBlockState(villa.offset(3, 2, 3)).is(Blocks.DECORATED_POT), "The Floridiana has an exhibit gallery");
            check(city.getBlockState(fort.offset(13, 1, 16)).is(Blocks.POLISHED_TUFF_STAIRS), "The castle gallery is accessible from the parade ground");
            check(city.getBlockState(palace.offset(26, 2, 15)).is(Blocks.DARK_OAK_PLANKS), "Donn'Anna has an interior stage");
            check(city.getBlockState(theatre.offset(11, 6, 29)).is(Blocks.QUARTZ_PILLAR)
                    && city.getBlockState(theatre.offset(19, 1, 14)).is(Blocks.CRIMSON_STAIRS), "The Mostra has the theater portico and seating");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, campus.getX()+45.5, campus.getY()+44, campus.getZ()+8.5, Set.of(), 180, 51, true);
        });
        if (tick < 90) return;
        if (tick > 3200) {
            Napuli.LOGGER.error("[DISTRICTTEST] Museum camera did not finish loading scene {}", museumScene);
            failed = true;
            return;
        }
        switch (museumScene) {
            case 0 -> {
                if (!museumViewReady(mc, campus.offset(45, 44, 8), campus.offset(12, 3, -12), g.offset(15, 3, 1))) return;
                shot(mc, "pietrarsa-padiglioni-nord-legacy");
                server(mc, p -> p.teleportTo(p.level(), g.getX()+13.5, g.getY()+2, g.getZ()+14.5, Set.of(), 180, 0, true));
            }
            case 1 -> {
                if (!museumViewReady(mc, g.offset(13, 2, 14), g.offset(8, 3, 5), g.offset(21, 2, 3))) return;
                shot(mc, "pietrarsa-trecentotreni-legacy");
                server(mc, p -> p.teleportTo(p.level(), villa.getX()+10.5, villa.getY()+2, villa.getZ()+24.5, Set.of(), 180, 0, true));
            }
            case 2 -> {
                if (!museumViewReady(mc, villa.offset(10, 2, 24), villa.offset(2, 3, 16), villa.offset(18, 3, 16))) return;
                shot(mc, "atlante-floridiana-legacy");
                server(mc, p -> p.teleportTo(p.level(), theatre.getX()+38.5, theatre.getY()+2, theatre.getZ()+26.5, Set.of(), 180, 0, true));
            }
            case 3 -> {
                if (!museumViewReady(mc, theatre.offset(38, 2, 26), theatre.offset(19, 3, 6), theatre.offset(56, 3, 6))) return;
                shot(mc, "atlante-teatro-mediterraneo-legacy");
                Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Six Pietrarsa wings and four atlas landmarks");
                done = true;
            }
            default -> {}
        }
        museumScene++;
        museumReadyTicks = 0;
    }

    private static boolean museumViewReady(Minecraft mc, net.minecraft.core.BlockPos camera, net.minecraft.core.BlockPos... points) {
        if (mc.player.blockPosition().distManhattan(camera) > 2) {
            museumReadyTicks = 0;
            return false;
        }
        for (var point : points) {
            if (!mc.level.getChunkSource().hasChunk(point.getX() >> 4, point.getZ() >> 4)) {
                museumReadyTicks = 0;
                return false;
            }
        }
        return ++museumReadyTicks >= 40;
    }

    private static void legacyAlbergoPietrarsa(Minecraft mc) {
        var palace = NapoliAlbergoPietrarsa.albergo();
        var museum = NapoliAlbergoPietrarsa.pietrarsa();
        if (++tick == 90) server(mc, p -> {
            var city = p.level();
            for (int side : new int[]{-1, 1}) for (int step = 0; step < 3; step++) {
                var stair = palace.offset(48 + side * (8 - step), step + 1, 44);
                check(city.getBlockState(stair).is(Blocks.SMOOTH_SANDSTONE_STAIRS) && city.isEmptyBlock(stair.above()),
                        "Both stair flights reach the entry terrace");
            }
            for (int z = 38; z <= 42; z++) {
                var walk = palace.offset(48, Math.min(z - 37, 3) + 1, z);
                check(city.isEmptyBlock(walk) && city.isEmptyBlock(walk.above()), "The stairwell stays open into the atrium");
            }
            check(city.getBlockState(palace.offset(48, 0, 28)).is(Blocks.SMOOTH_STONE), "The palace atrium has the new stone floor");
            check(city.isEmptyBlock(museum.offset(43, 22, 13)), "Pietrarsa loses the low flat roof");
            check(city.getBlockState(museum.offset(43, 32, 16)).is(NapoliMonumentInteriors.material("light_gray_stained_glass")),
                    "A skylight tops the pitched roof");
            check(city.getBlockState(museum.offset(18, 2, 19)).is(Blocks.POLISHED_DEEPSLATE),
                    "The hall contains a modeled locomotive");
            check(!city.getBlockState(museum.offset(18, 5, 17)).is(Blocks.CAMPFIRE), "The old smoke source is removed");
            for (int z = 9; z <= 42; z++)
                check(city.isEmptyBlock(museum.offset(43, 1, z)), "The exhibition has a clear center aisle");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, palace.getX()+48.5, palace.getY()+7, palace.getZ()+67.5, Set.of(), 180, -4, true);
        });
        if (tick == 260) {
            shot(mc, "albergo-facciata-legacy");
            server(mc, p -> p.teleportTo(p.level(), palace.getX()+48.5, palace.getY()+2, palace.getZ()+34.5, Set.of(), 180, -6, true));
        }
        if (tick == 365) {
            shot(mc, "albergo-atrio-legacy");
            server(mc, p -> p.teleportTo(p.level(), museum.getX()+55.5, museum.getY()+12, museum.getZ()+64.5, Set.of(), 155, -7, true));
        }
        if (tick == 510) {
            shot(mc, "pietrarsa-pannello-esterno-legacy");
            server(mc, p -> p.teleportTo(p.level(), museum.getX()+40.5, museum.getY()+3.5, museum.getZ()+10.5, Set.of(), 25, -10, true));
        }
        if (tick == 615) {
            shot(mc, "pietrarsa-sala-vaporiere-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Albergo and Pietrarsa on an existing save");
            done = true;
        }
    }

    private static void legacyCourtyards(Minecraft mc) {
        var palace = NapoliCapodimonteSanita.capodimonte();
        var tower = NapoliHistoricCourtyards.campanile();
        var gate = NapoliHistoricCourtyards.capuana();
        if (++tick == 90) server(mc, p -> {
            var city = p.level();
            for (int x : new int[]{7, 34, 61}) {
                var court = palace.offset(x, 0, 2);
                check(city.isEmptyBlock(court.offset(8, 24, 5)) && city.isEmptyBlock(court.offset(8, 9, 5)),
                        "Each court is open to the sky");
                check(city.getBlockState(court.offset(8, 0, 5)).is(Blocks.STONE_BRICKS), "Each courtyard is paved");
                check(city.isEmptyBlock(court.offset(8, 1, 9)) && city.isEmptyBlock(court.offset(8, 2, 9)),
                        "The ground-floor arcade can be entered");
            }
            for (int x = 15; x <= 69; x++) {
                var walk = palace.offset(x, 1, 7);
                check(city.isEmptyBlock(walk) && city.isEmptyBlock(walk.above()), "The lateral porticoes link all three courts");
            }
            check(city.getBlockState(palace.offset(17, 11, 15)).is(Blocks.DECORATED_POT), "The adjacent porcelain gallery survives");
            check(city.getBlockState(tower.offset(6, 28, 6)).is(Blocks.BELL), "The belfry has a usable bell");
            for (int y = 1; y <= 22; y++) {
                var rung = tower.offset(3, y, 6);
                check(city.getBlockState(rung).is(Blocks.LADDER) && city.getBlockState(rung.west()).isSolidRender(),
                        "The internal ladder is supported all the way to the belfry");
            }
            check(city.isEmptyBlock(tower.offset(6, 1, 10)), "The tower has a clear doorway");
            check(city.getBlockState(gate.offset(8, 14, 4)).is(Blocks.SMOOTH_QUARTZ), "The central span has a marble arch");
            for (int z = 0; z <= 8; z++)
                check(city.isEmptyBlock(gate.offset(8, 1, z)), "The route through Porta Capuana is clear");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, palace.getX()+42.5, palace.getY()+3, palace.getZ()+10.5, Set.of(), 180, -25, true);
        });
        if (tick == 165) {
            shot(mc, "capodimonte-cortile-legacy");
            server(mc, p -> p.teleportTo(p.level(), palace.getX()+42.5, palace.getY()+49, palace.getZ()+51.5, Set.of(), 180, 48, true));
        }
        if (tick == 230) {
            shot(mc, "capodimonte-tre-cortili-legacy");
            server(mc, p -> p.teleportTo(p.level(), tower.getX()+18.5, tower.getY()+24, tower.getZ()+28.5, Set.of(), 155, -2, true));
        }
        if (tick == 305) {
            shot(mc, "sanita-campanile-legacy");
            server(mc, p -> p.teleportTo(p.level(), gate.getX()+8.5, gate.getY()+5, gate.getZ()+30.5, Set.of(), 180, -8, true));
        }
        if (tick == 385) {
            shot(mc, "porta-capuana-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Courtyards, Sanita tower and Porta Capuana on an existing save");
            done = true;
        }
    }

    private static void legacyCapodimonteSanita(Minecraft mc) {
        var palace = NapoliCapodimonteSanita.capodimonte();
        var church = NapoliCapodimonteSanita.sanita();
        if (++tick == 90) server(mc, p -> {
            var city = p.level();
            check(city.getBlockState(palace.offset(6, 2, 41)).is(NapoliMonumentInteriors.material("red_terracotta")),
                    "Capodimonte has its red facade");
            check(city.isEmptyBlock(palace.offset(42, 1, 41)) && city.isEmptyBlock(palace.offset(42, 2, 42)),
                    "The palace entrance remains open");
            check(city.isEmptyBlock(palace.offset(15, 25, 43)), "The detached legacy roof band is removed");
            check(city.getBlockState(palace.offset(17, 11, 15)).is(Blocks.DECORATED_POT),
                    "The upper gallery displays porcelain");
            check(city.getBlockState(church.offset(16, 34, 17)).is(NapoliMonumentInteriors.material("yellow_terracotta")),
                    "Sanita has a glazed-color dome");
            for (int x : new int[]{12, 20}) for (int step = 0; step < 4; step++) {
                var stair = church.offset(x, step + 1, 18 - step);
                check(city.getBlockState(stair).is(Blocks.SMOOTH_QUARTZ_STAIRS) && city.isEmptyBlock(stair.above()),
                        "Both flights reach the sanctuary");
            }
            check(city.isEmptyBlock(church.offset(16, 2, 12)), "The central passage below the sanctuary stays open");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, palace.getX()+42.5, palace.getY()+7, palace.getZ()+66.5, Set.of(), 180, -6, true);
        });
        if (tick == 165) {
            shot(mc, "napoli-capodimonte-facciata-legacy");
            server(mc, p -> p.teleportTo(p.level(), palace.getX()+21.5, palace.getY()+10, palace.getZ()+25.5, Set.of(), 180, 4, true));
        }
        if (tick == 235) {
            shot(mc, "napoli-capodimonte-porcellane-legacy");
            server(mc, p -> p.teleportTo(p.level(), church.getX()+16.5, church.getY()+8, church.getZ()+58.5, Set.of(), 180, -9, true));
        }
        if (tick == 320) {
            shot(mc, "napoli-sanita-facciata-legacy");
            server(mc, p -> p.teleportTo(p.level(), church.getX()+44.5, church.getY()+24, church.getZ()+50.5, Set.of(), 138, 7, true));
        }
        if (tick == 385) {
            shot(mc, "napoli-sanita-cupola-legacy");
            server(mc, p -> p.teleportTo(p.level(), church.getX()+16.5, church.getY()+1, church.getZ()+29.5, Set.of(), 180, -8, true));
        }
        if (tick == 455) {
            shot(mc, "napoli-sanita-presbiterio-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Capodimonte and Sanita on an existing save");
            done = true;
        }
    }

    private static void legacyResidences(Minecraft mc) {
        var home = CityLivingHomes.PUBLIC_HOMES.getFirst();
        if (++tick == 90) server(mc, p -> {
            var city = p.level();
            var door = home.entrance().north();
            check(city.getBlockState(door).getBlock() instanceof net.minecraft.world.level.block.DoorBlock, "The home keeps its working door");
            check(city.getBlockState(door.west()).is(Blocks.POLISHED_DEEPSLATE), "The front door gains a stone jamb");
            check(city.getBlockState(new net.minecraft.core.BlockPos(home.x(), 65, door.getZ())).is(Blocks.CUT_SANDSTONE),
                    "Facade glazing ends against a solid corner");
            var tile = new net.minecraft.core.BlockPos(home.x() + 3, 63, home.z() + 3);
            var finish = city.getBlockState(tile);
            check(finish.is(NapoliMonumentInteriors.material("light_blue_glazed_terracotta")), "The kitchen has a tile inset");
            city.setBlockAndUpdate(tile, Blocks.SPRUCE_PLANKS.defaultBlockState());
            CityProperty.placed(city, tile);
            try {
                NapoliResidentialFinish.historic(city);
                check(city.getBlockState(tile).is(Blocks.SPRUCE_PLANKS), "The refit preserves a tracked player floor");
            } finally {
                CityProperty.forget(city, tile);
                city.setBlockAndUpdate(tile, finish);
            }
            check(city.getBlockState(new net.minecraft.core.BlockPos(32, 70, 7)).is(Blocks.POLISHED_ANDESITE_SLAB),
                    "Palazzo Reale has a facade cornice");
            check(city.getBlockState(new net.minecraft.core.BlockPos(35, 73, 14)).is(NapoliMonumentInteriors.material("red_wool")),
                    "The throne room has a damask panel");
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(city, door.getX() + .5, 66, door.getZ() + 8.5, Set.of(), 180, 0, true);
        });
        if (tick == 175) {
            shot(mc, "napoli-case-facciata-legacy");
            server(mc, p -> p.teleportTo(p.level(), home.x() + 4.5, 64.1, home.z() + 8.5, Set.of(), 180, 17, true));
        }
        if (tick == 250) {
            shot(mc, "napoli-case-interni-legacy");
            server(mc, p -> p.teleportTo(p.level(), 38.5, 71.1, 15.5, Set.of(), 0, 6, true));
        }
        if (tick == 330) {
            shot(mc, "napoli-palazzo-reale-legacy");
            var expanded = DistrictLife.homes().getFirst();
            server(mc, p -> p.teleportTo(p.level(), expanded.x() + 10.5, expanded.ground() + 1,
                    expanded.z() + 19.5, Set.of(), 180, 10, true));
        }
        if (tick == 450) server(mc, p -> {
            var expanded = DistrictLife.homes().getFirst();
            var tile = new net.minecraft.core.BlockPos(expanded.x() + 9, expanded.ground() - 1, expanded.z() + 17);
            check(p.level().getBlockState(tile).is(Blocks.TERRACOTTA), "The expansion applies room finishes when house chunks load");
            check(p.level().getBlockState(tile.above(5)).is(Blocks.SPRUCE_PLANKS), "The next floor keeps its wood ceiling finish below");
        });
        if (tick == 490) {
            shot(mc, "napoli-case-atlas-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Residential finishes and Palazzo Reale on an existing save");
            done = true;
        }
    }

    private static void legacyCertosa(Minecraft mc) {
        if (++tick == 90) {
            var floor = NapoliCertosa.floor();
            server(mc, p -> p.teleportTo(p.level(), floor.getX()+.5, floor.getY()+1, floor.getZ()+18.5, Set.of(), 180, 0, true));
        }
        if (tick == 175) {
            server(mc, p -> {
                var city = p.level();
                var floor = NapoliCertosa.floor();
                var exhibit = NapoliCertosa.exhibit();
                check(city.getBlockState(floor.above()).is(Blocks.WATER_CAULDRON), "The cloister has its well");
                check(city.getBlockState(floor.offset(-16, 4, -5)).is(Blocks.QUARTZ_PILLAR), "The cloister has its arcades");
                check(city.isEmptyBlock(floor.offset(0, 7, -15)), "The middle of the arch opens above the approach");
                check(city.getBlockState(floor.offset(0, 8, -15)).is(Blocks.SMOOTH_QUARTZ_SLAB), "The arch has a raised crown");
                check(city.getBlockState(floor.offset(0, 11, -15)).is(NapoliMonumentInteriors.material("light_gray_stained_glass")),
                        "A framed window sits above the arch");
                check(city.getBlockState(floor.offset(16, 9, -5)).is(Blocks.CHISELED_QUARTZ_BLOCK), "The side arcade has a stone frieze");
                var wallEdit = floor.offset(0, 14, -15);
                var originalCornice = city.getBlockState(wallEdit);
                city.setBlockAndUpdate(wallEdit, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                CityProperty.placed(city, wallEdit);
                try {
                    NapoliCertosa.refitArcades(city);
                    check(city.getBlockState(wallEdit).is(Blocks.SMOOTH_SANDSTONE), "The arcade refit preserves tracked wall blocks");
                } finally {
                    CityProperty.forget(city, wallEdit);
                    city.setBlockAndUpdate(wallEdit, originalCornice);
                }
                check(city.getBlockState(floor.offset(0, 0, 7)).is(Blocks.TERRACOTTA), "The revised path reaches the well from the entrance");
                check(city.getBlockState(floor.offset(4, 0, 4)).is(Blocks.CUT_SANDSTONE), "The well apron has a clipped stone border");
                check(city.getBlockState(floor.offset(-8, 0, 7)).is(Blocks.GRASS_BLOCK), "The paving meets the planted lawn");
                check(city.getBlockState(floor.offset(-10, 1, -7)).is(Blocks.STRIPPED_OAK_LOG), "The garden trees are rooted in the lawn");
                var edited = floor.offset(7, 0, 7);
                var original = city.getBlockState(edited);
                city.setBlockAndUpdate(edited, Blocks.BRICKS.defaultBlockState());
                CityProperty.placed(city, edited);
                try {
                    NapoliCertosa.refitGarden(city);
                    check(city.getBlockState(edited).is(Blocks.BRICKS), "The garden refit preserves tracked player paving");
                } finally {
                    CityProperty.forget(city, edited);
                    city.setBlockAndUpdate(edited, original);
                }
                for (int z = 4; z <= 20; z++) {
                    var walk = floor.offset(0, 1, z);
                    check(city.isEmptyBlock(walk) && city.isEmptyBlock(walk.above()), "The Certosa approach is clear at " + walk);
                }
                check(city.getBlockState(exhibit).is(NapoliCertosa.PRESEPE), "The Cuciniello exhibit was placed in the west gallery");
                check(city.getBlockState(exhibit.offset(-1, 4, 2)).is(Blocks.TUFF_BRICKS), "The exhibit grotto roof has masonry support");
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(exhibit),
                        Direction.SOUTH, exhibit, false);
                check(city.getBlockState(exhibit).useWithoutItem(city, p, hit).consumesAction(), "The Nativity exhibit has a visitor label");
                var staff = java.util.Map.of(NapoliResident.Role.CERTOSA_GUIDE, NapoliCertosa.guidePost(),
                        NapoliResident.Role.CERTOSA_RESTORER, NapoliCertosa.restorerPost());
                for (var entry : staff.entrySet()) {
                    var found = city.getEntitiesOfClass(NapoliResident.class, new net.minecraft.world.phys.AABB(entry.getValue()).inflate(3),
                            npc -> npc.role() == entry.getKey());
                    check(found.size() == 1, "The Certosa has one resident at " + entry.getKey());
                    check(found.getFirst().mobInteract(p, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(), "The resident has a dialogue");
                }
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(city, floor.getX()+.5, floor.getY()+4, floor.getZ()+12.5, Set.of(), 180, 7, true);
            });
        }
        if (tick == 245) {
            shot(mc, "certosa-portico-rifatto-legacy");
            var floor = NapoliCertosa.floor();
            server(mc, p -> p.teleportTo(p.level(), floor.getX()+12.5, floor.getY()+1, floor.getZ()+12.5, Set.of(), 165, -8, true));
        }
        if (tick == 280) {
            shot(mc, "certosa-portico-laterale-legacy");
            var exhibit = NapoliCertosa.exhibit();
            server(mc, p -> p.teleportTo(p.level(), exhibit.getX()-3.5, exhibit.getY()+1, exhibit.getZ()+4.5, Set.of(), -136, 9, true));
        }
        if (tick == 325) {
            shot(mc, "certosa-presepe-restauratore-legacy");
            var exhibit = NapoliCertosa.exhibit();
            server(mc, p -> p.teleportTo(p.level(), exhibit.getX() + .5, exhibit.getY() + .9, exhibit.getZ() + 4.5,
                    Set.of(), 180, 9, true));
        }
        if (tick == 420) {
            shot(mc, "certosa-presepe-legacy");
            Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: Certosa structures, exhibit and staff on an existing save");
            done = true;
        }
    }
    private DistrictClientTest(){}
    private static void checkAccessories(ServerPlayer p, net.minecraft.core.BlockPos strip) {
        var city = p.level();
        var dryer = strip.east();
        var vacuum = strip.south();
        var scale = strip.west();
        var fixtures = java.util.Map.of(strip, NapoliFurniture.POWER_STRIP, dryer, NapoliFurniture.HAIR_DRYER,
                vacuum, NapoliFurniture.VACUUM, scale, NapoliFurniture.SCALE);
        var original = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        fixtures.forEach((pos, block) -> {
            original.put(pos, city.getBlockState(pos));
            city.setBlockAndUpdate(pos, block.defaultBlockState());
        });
        var lit = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
        try {
            check(!HomeFixtureBlock.powered(city, dryer), "An off power strip supplies no power");
            useFixture(p, strip);
            check(HomeFixtureBlock.powered(city, dryer), "A switched-on strip powers the nearby hair dryer");
            NapoliHygiene.soakHair(p);
            useFixture(p, dryer);
            check(city.getBlockState(dryer).getValue(lit) && !NapoliHygiene.dryHair(p), "The hair dryer dries hair");
            var litterPos = vacuum.south();
            var key = Long.toString(litterPos.asLong());
            int previous = city.getAttachedOrElse(CityOccupations.LITTER, java.util.Map.of())
                    .getOrDefault(key, new CityOccupations.Litter(litterPos, 0, 0)).units();
            CityOccupations.litter(city, litterPos, 1);
            useFixture(p, vacuum);
            check(city.getBlockState(vacuum).getValue(lit) && city.getAttachedOrElse(CityOccupations.LITTER, java.util.Map.of())
                    .getOrDefault(key, new CityOccupations.Litter(litterPos, 0, 0)).units() == previous, "The vacuum removes litter");
            useFixture(p, scale);
            check(city.getBlockState(scale).getValue(lit), "The scale turns on to show a reading");
        } finally {
            original.forEach(city::setBlockAndUpdate);
        }
    }

    private static void useFixture(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        player.level().getBlockState(pos).useWithoutItem(player.level(), player, hit);
    }

}
