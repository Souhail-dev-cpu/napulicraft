package dev.napuli;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class NapoliHouseholds {
    public enum Activity { REST, COOK, MATCH, PLAY, WASH, EAT, SLEEP, WORK, SCHOOL, STUDY, SHOP, SOCIAL, CELEBRATE, CARE, FRIDGE, SET_TABLE, DISHES, WAIT_TRANSIT, COMMUTE }
    private static final List<BlockPos> HOUSES = List.of(new BlockPos(-173, 64, 12), new BlockPos(-201, 64, 28));
    private static final List<NapoliResident.Role> ROLES = List.of(NapoliResident.Role.FAMILY_MOTHER,
            NapoliResident.Role.FAMILY_FATHER, NapoliResident.Role.FAMILY_CHILD, NapoliResident.Role.FAMILY_CHILD_TWO);
    private NapoliHouseholds() {}

    static void build(ServerLevel level) {
        for (BlockPos house : HOUSES) {
            BlockPos stove = house.offset(8, 0, 3);
            put(level, stove, Blocks.SMOKER);
            put(level, house.offset(7, 0, 2), Blocks.CRAFTING_TABLE);
            put(level, house.offset(6, 0, 2), Blocks.BARREL);
            put(level, house.offset(2, 0, 8), Blocks.SPRUCE_PLANKS);
            for (int dx = -1; dx <= 1; dx++) put(level, tv(house).east(dx), block("black_concrete"));
            put(level, house.offset(2, 0, 5), Blocks.SPRUCE_STAIRS);
            put(level, house.offset(3, 0, 5), Blocks.SPRUCE_STAIRS);
            put(level, house.offset(6, 0, 6), Blocks.OAK_FENCE);
            put(level, house.offset(6, 1, 6), block("blue_carpet"));
            put(level, house.offset(8, 0, 9), Blocks.QUARTZ_BLOCK);
            level.setBlock(house.offset(8, 1, 9), Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(BlockStateProperties.LEVEL_CAULDRON, 3), Block.UPDATE_CLIENTS);
            put(level, house.offset(3, 0, 3), Blocks.BOOKSHELF);
            put(level, house.offset(7, 2, 8), Blocks.LANTERN);
            for (int i = 0; i < ROLES.size(); i++) {
                var npc = NapoliResident.spawn(level, ROLES.get(i), house.offset(4 + i, 0, 9), 180);
                npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    static boolean family(NapoliResident.Role role) { return ROLES.contains(role); }

    static void tick(NapoliResident npc, ServerLevel level) {
        BlockPos post = npc.getHomePosition();
        if (post == null) return;
        int member = ROLES.indexOf(npc.role());
        BlockPos house = post.offset(-4 - member, 0, -9);
        if(!HousingCapacity.canEnter(level,house.offset(4,0,9),npc.getUUID())) {npc.getNavigation().stop();return;}
        long time = Math.floorMod(level.getOverworldClockTime(), 24000);
        boolean sportsChannel = member == 1 && NapoliBroadcast.channel(level, tv(house)) == NapoliBroadcast.Channel.SPORT;
        Activity routine = activity(member, time);
        Activity activity = sportsChannel && NapoliBroadcast.live(level) ? Activity.MATCH
                : !sportsChannel && routine == Activity.MATCH ? Activity.REST : routine;
        if (npc.activity() != activity) {
            npc.setActivity(activity);
            var item = switch (activity) {
                case COOK -> Items.WOODEN_SHOVEL;
                case PLAY -> Items.SLIME_BALL;
                case EAT -> Items.BREAD;
                case WASH -> Items.GLASS_BOTTLE;
                default -> Items.AIR;
            };
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        }
        if ((npc.tickCount + npc.getId()) % 20 != 0) return;
        BlockPos bed = house.offset(new int[]{3, 4, 6, 7}[member], 4, 4);
        if (activity != Activity.SLEEP && npc.isSleeping()) npc.stopSleeping();
        if (activity == Activity.SLEEP) {
            npc.getNavigation().stop();
            if (!npc.isSleeping() && level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                npc.startSleeping(bed);
            return;
        }
        BlockPos spot = switch (activity) {
            case COOK -> house.offset(7, 0, 3);
            case MATCH -> house.offset(2, 0, 6);
            case PLAY -> house.offset(4 + member % 2, 0, 6 + (int) (level.getGameTime() / 80 % 3));
            case WASH -> house.offset(7, 0, 9);
            case EAT -> house.offset(5 + member % 2, 0, 5 + member / 2 * 2);
            default -> post;
        };
        if (spot.distToCenterSqr(npc.position()) > 1.25) {
            var path = npc.getNavigation().createPath(spot, 0);
            if (path != null && path.canReach()) npc.getNavigation().moveTo(path, 0.75);
            else if (NapoliResidentHomes.walkable(level, spot))
                npc.snapTo(spot.getX() + .5, spot.getY(), spot.getZ() + .5, npc.getYRot(), 0);
        } else {
            npc.getNavigation().stop();
            BlockPos focus = switch (activity) {
                case COOK -> house.offset(8, 1, 3);
                case MATCH -> tv(house);
                case WASH -> house.offset(8, 1, 9);
                case EAT -> house.offset(6, 1, 6);
                default -> spot.south();
            };
            npc.getLookControl().setLookAt(focus.getX() + .5, focus.getY() + .5, focus.getZ() + .5, 35, 35);
            if (activity == Activity.COOK || activity == Activity.WASH) {
                npc.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                level.sendParticles(activity == Activity.COOK ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SPLASH,
                        focus.getX() + .5, focus.getY() + .5, focus.getZ() + .5, 2, .15, .12, .15, .015);
            }
            if (activity == Activity.PLAY && npc.getRandom().nextInt(3) == 0) npc.getJumpControl().jump();
        }
        if (member == 1) paintTelevision(level, tv(house));
    }

    static BlockPos findTelevision(BlockPos clicked) {
        for (BlockPos house : HOUSES) {
            BlockPos center = tv(house);
            if (clicked.getY() == center.getY() && clicked.getZ() == center.getZ()
                    && Math.abs(clicked.getX() - center.getX()) <= 1) return center;
        }
        for(var house:CityLivingHomes.all()) {
            var center=CityLivingHomes.use(house,CityLivingHomes.Furniture.TV).block();
            if(clicked.getY()==center.getY()&&clicked.getZ()==center.getZ()&&Math.abs(clicked.getX()-center.getX())<=1)return center;
        }
        return null;
    }

    static void paintTelevision(ServerLevel level, BlockPos screen) {
        var channel = NapoliBroadcast.channel(level, screen);
        boolean highlights = NapoliBroadcast.time(level) >= 7000 && NapoliBroadcast.time(level) < 11600;
        boolean football = channel == NapoliBroadcast.Channel.SPORT && (NapoliBroadcast.live(level) || highlights);
        String base = switch (channel) {
            case OFF -> "black_concrete";
            case SPORT -> football ? "green_concrete" : "blue_concrete";
            case NEWS -> "red_concrete";
            case WEATHER -> level.isRaining() ? "gray_concrete" : "light_blue_concrete";
        };
        for (int dx = -1; dx <= 1; dx++) {
            BlockPos pixel = screen.east(dx);
            String color = football && dx == (int) (level.getGameTime() / 40 % 3) - 1 ? "white_concrete" : base;
            Block next = block(color);
            if (!level.getBlockState(pixel).is(next)) put(level, pixel, next);
        }
    }

    static Activity activity(int member, long time) {
        if (time >= 16000 || time < 1000) return Activity.SLEEP;
        if (time >= 1000 + member * 450 && time < 1350 + member * 450) return Activity.WASH;
        if (time >= 10500 && time < 11600) return Activity.EAT;
        return switch (member) {
            case 0 -> Activity.COOK;
            case 1 -> time >= 7000 ? Activity.MATCH : Activity.REST;
            default -> Activity.PLAY;
        };
    }

    private static BlockPos tv(BlockPos house) { return house.offset(2, 1, 8); }
    private static Block block(String name) { return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name)); }
    private static void put(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
