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
    private static int legacyLoadTicks;
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
