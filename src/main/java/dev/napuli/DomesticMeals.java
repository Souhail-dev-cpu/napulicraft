package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class DomesticMeals {
    private enum Step {
        FRIDGE(NapoliHouseholds.Activity.FRIDGE, Items.WHEAT),
        COOK(NapoliHouseholds.Activity.COOK, Items.WOODEN_SHOVEL),
        TABLE(NapoliHouseholds.Activity.SET_TABLE, Items.MUSHROOM_STEW),
        EAT(NapoliHouseholds.Activity.EAT, Items.MUSHROOM_STEW),
        DISHES(NapoliHouseholds.Activity.DISHES, Items.BOWL);

        final NapoliHouseholds.Activity activity;
        final Item prop;

        Step(NapoliHouseholds.Activity activity, Item prop) {
            this.activity = activity;
            this.prop = prop;
        }
    }

    private record Meal(long day, int step, long performed) {
        static final Codec<Meal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Meal::day),
                Codec.INT.fieldOf("step").forGetter(Meal::step),
                Codec.LONG.fieldOf("performed").forGetter(Meal::performed)).apply(i, Meal::new));
    }

    private static final AttachmentType<Meal> MEAL = AttachmentRegistry.<Meal>builder()
            .persistent(Meal.CODEC).buildAndRegister(Napuli.id("domestic_meal"));

    private DomesticMeals() {}

    static void register() {} // Decode meals saved by the incoming branch before entity loading.

    static boolean act(ServerLevel city, NapoliResident npc, NapoliMergellina.House house, int stock) {
        var citizen=NapoliCivicLife.profile(city,npc.getUUID());
        if(citizen!=null){
            // Import the old five-step routine once; only the six-step controller runs afterwards.
            if(!city.getAttachedOrElse(CityDomestic.DINNERS,java.util.Map.of()).containsKey(npc.getUUID().toString())){
                var saved=npc.getAttached(MEAL);
                if(saved!=null&&saved.day()==NapoliBroadcast.day(city)){
                    int stage=switch(saved.step()){case 0->saved.performed()>=0?1:0;case 1->2;case 2->3;case 3->4;case 4->5;default->-1;};
                    CityDomestic.put(city,npc.getUUID(),new CityDomestic.Dinner(house.id(),stage,0,stage<0?city.getGameTime():-1200));
                    npc.removeAttached(MEAL);
                }
            }
            return CityDomestic.tick(npc,city,citizen,house,npc.activity());
        }
        long time = NapoliBroadcast.time(city);
        if (time < 9900 || time >= 12000 || !HousingCapacity.canEnter(city, house.inside(), npc.getUUID())) return false;
        long day = NapoliBroadcast.day(city);
        Meal meal = npc.getAttached(MEAL);
        if (meal == null || meal.day() != day) meal = new Meal(day, 0, -1);
        if (meal.step() < 0 || meal.step() >= Step.values().length) return false;

        Step step = Step.values()[meal.step()];
        BlockPos table = new BlockPos(house.x() + 4, 65, house.z() + 7);
        var fixture = switch (step) {
            case FRIDGE -> CityLivingHomes.use(house, CityLivingHomes.Furniture.FRIDGE);
            case COOK -> CityLivingHomes.use(house, CityLivingHomes.Furniture.STOVE);
            case DISHES -> CityLivingHomes.use(house, CityLivingHomes.Furniture.SINK);
            case TABLE, EAT -> null;
        };
        BlockPos target = fixture == null ? table.below().north() : fixture.stand();
        BlockPos focus = fixture == null ? table : fixture.block();
        if (!city.hasChunkAt(target) || !city.getBlockState(target).getCollisionShape(city, target).isEmpty()) return false;
        if (step == Step.COOK && meal.performed() < 0 && !city.getEntitiesOfClass(NapoliResident.class,
                new net.minecraft.world.phys.AABB(target).inflate(.6), other -> other != npc
                        && other.activity() == NapoliHouseholds.Activity.COOK).isEmpty()) {
            BlockPos spare = table.below().north();
            var path = npc.getNavigation().createPath(spare, 0);
            if (path != null && path.canReach()) {
                npc.setActivity(NapoliHouseholds.Activity.SET_TABLE);
                npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOWL));
                npc.getNavigation().moveTo(path, .72);
                return true;
            }
            return false;
        }
        if (npc.isSleeping()) npc.stopSleeping();
        if (target.distToCenterSqr(npc.position()) > 1.8) {
            var path = npc.getNavigation().createPath(target, 0);
            if (path != null && path.canReach()) {
                npc.setActivity(step.activity);
                npc.getNavigation().moveTo(path, .8);
                return true;
            }
            return false;
        }

        npc.setActivity(step.activity);
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(focus.getX() + .5, focus.getY() + .5, focus.getZ() + .5, 40, 40);
        long now = city.getGameTime();
        if (meal.performed() < 0) {
            if (step == Step.FRIDGE && !takeFood(city, focus, stock)) return false;
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(step.prop));
            npc.swingForAttack(InteractionHand.MAIN_HAND);
            perform(city, focus, step);
            npc.setAttached(MEAL, new Meal(day, meal.step(), now));
        } else if (now - meal.performed() >= (step == Step.COOK ? 100 : 55)) {
            if (step == Step.FRIDGE && city.getBlockState(focus).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock)
                city.setBlock(focus, city.getBlockState(focus).setValue(BlockStateProperties.OPEN, false), Block.UPDATE_CLIENTS);
            if (step == Step.DISHES) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (city.getBlockState(table).is(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)) {
                    var carpet = CityBlocks.vanilla("blue_carpet");
                    city.setBlock(table, carpet.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            npc.setAttached(MEAL, new Meal(day, meal.step() + 1, -1));
        }
        return true;
    }

    private static boolean takeFood(ServerLevel city, BlockPos focus, int stock) {
        if (!(city.getBlockEntity(focus) instanceof Container fridge)) return false;
        for (int i = 0; i < fridge.getContainerSize(); i++) {
            ItemStack stack = fridge.getItem(i);
            if (stack.is(Items.WHEAT) || stack.is(Items.CARROT) || stack.is(Items.POTATO)) {
                fridge.removeItem(i, 1);
                fridge.setChanged();
                return true;
            }
        }
        // Abstract household supplies cover meals when the physical pantry is empty.
        return stock > 0;
    }

    private static void perform(ServerLevel city, BlockPos pos, Step step) {
        var sound = switch (step) {
            case FRIDGE -> SoundEvents.BARREL_OPEN;
            case COOK -> SoundEvents.FURNACE_FIRE_CRACKLE;
            case TABLE -> SoundEvents.WOOD_PLACE;
            case EAT -> SoundEvents.GENERIC_EAT.value();
            case DISHES -> SoundEvents.GENERIC_SPLASH;
        };
        CityEffects.sound(city,null, pos, sound, SoundSource.BLOCKS, .38f, 1);
        if (step == Step.FRIDGE && city.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock)
            city.setBlock(pos, city.getBlockState(pos).setValue(BlockStateProperties.OPEN, true), Block.UPDATE_CLIENTS);
        var setting = CityBlocks.vanilla("blue_carpet");
        if (step == Step.TABLE && city.getBlockState(pos).is(setting))
            city.setBlock(pos, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (step == Step.COOK || step == Step.DISHES || step == Step.EAT) {
            var particle = step == Step.COOK ? ParticleTypes.CAMPFIRE_COSY_SMOKE
                    : step == Step.DISHES ? ParticleTypes.SPLASH : ParticleTypes.HAPPY_VILLAGER;
            CityEffects.particles(city,particle, pos.getX() + .5, pos.getY() + .7, pos.getZ() + .5, 5, .12, .10, .12, .01);
        }
    }

    static void setTable(ServerLevel city,BlockPos pos,boolean laid){
        var carpet=CityBlocks.vanilla("blue_carpet");
        if(laid&&city.getBlockState(pos).is(carpet))city.setBlock(pos,Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),Block.UPDATE_CLIENTS);
        else if(!laid&&city.getBlockState(pos).is(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE))city.setBlock(pos,carpet.defaultBlockState(),Block.UPDATE_CLIENTS);
    }
}
