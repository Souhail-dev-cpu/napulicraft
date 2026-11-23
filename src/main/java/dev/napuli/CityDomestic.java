package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.BarrelBlock;

/** Arrival, not a schedule label, advances a meal. Progress and consumed supplies survive reloads. */
final class CityDomestic {
    record Dinner(String home,int stage,int progress,long served) {
        static final Codec<Dinner> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.STRING.fieldOf("home").forGetter(Dinner::home),Codec.INT.fieldOf("stage").forGetter(Dinner::stage),
                Codec.INT.fieldOf("progress").forGetter(Dinner::progress),Codec.LONG.fieldOf("served").forGetter(Dinner::served)).apply(i,Dinner::new));
        static Dinner idle(){return new Dinner("",-1,0,-1200);}
    }
    static final AttachmentType<Map<String,Dinner>> DINNERS=AttachmentRegistry.<Map<String,Dinner>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Dinner.CODEC)).buildAndRegister(Napuli.id("domestic_meals"));
    private record Lease(UUID citizen,long expires){}
    private static final Map<ServerLevel,Map<BlockPos,Lease>> STATIONS=new WeakHashMap<>();
    static Dinner dinner(ServerLevel city,UUID id){return city.getAttachedOrElse(DINNERS,Map.of()).getOrDefault(id.toString(),Dinner.idle());}
    static void put(ServerLevel city,UUID id,Dinner dinner){var all=new HashMap<>(city.getAttachedOrElse(DINNERS,Map.of()));all.put(id.toString(),dinner);city.setAttached(DINNERS,Map.copyOf(all));}
    static boolean claim(ServerLevel city,BlockPos stand,UUID id){
        var stations=STATIONS.computeIfAbsent(city,k->new HashMap<>());long now=city.getGameTime();
        stations.values().removeIf(l->l.expires()<now||city.getEntity(l.citizen())==null);
        var lease=stations.get(stand);if(lease!=null&&!lease.citizen().equals(id))return false;
        stations.put(stand,new Lease(id,now+240));return true;
    }
    static void release(ServerLevel city,UUID id){var leases=STATIONS.get(city);if(leases!=null)leases.values().removeIf(l->l.citizen().equals(id));}
    static CityLivingHomes.UsePoint point(NapoliMergellina.House h,int stage){
        return switch(stage){
            case 0->CityLivingHomes.use(h,CityLivingHomes.Furniture.FRIDGE);
            case 1->new CityLivingHomes.UsePoint(CityLivingHomes.Room.KITCHEN,CityLivingHomes.Furniture.DESK,new BlockPos(h.x()+4,64,h.z()+5),new BlockPos(h.x()+5,64,h.z()+5));
            case 2->CityLivingHomes.use(h,CityLivingHomes.Furniture.STOVE);
            case 3,4->new CityLivingHomes.UsePoint(CityLivingHomes.Room.KITCHEN,CityLivingHomes.Furniture.TABLE,new BlockPos(h.x()+4,65,h.z()+7),new BlockPos(h.x()+5,64,h.z()+7));
            default->CityLivingHomes.use(h,CityLivingHomes.Furniture.SINK);
        };
    }
    static String description(ServerLevel city,UUID id){
        int stage=dinner(city,id).stage();return stage<0?"":switch(stage){case 0->"Sta prendendo gli ingredienti dal frigo.";case 1->"Sta preparando il pranzo.";case 2->"Sta cuocendo il pasto.";case 3->"Sta apparecchiando.";case 4->"Sta mangiando a tavola.";default->"Sta lavando i piatti.";};
    }
    static void move(NapoliResident npc,BlockPos target){
        var city=(ServerLevel)npc.level();if(!city.hasChunkAt(target))return;
        if(target.distToCenterSqr(npc.position())<=2){npc.getNavigation().stop();return;}
        var path=npc.getNavigation().createPath(target,0);
        if(path!=null&&path.canReach())npc.getNavigation().moveTo(path,.85);else npc.getNavigation().stop();
    }
    private static void barrel(ServerLevel city,BlockPos pos,boolean open){
        var state=city.getBlockState(pos);if(state.hasProperty(BarrelBlock.OPEN)&&state.getValue(BarrelBlock.OPEN)!=open)city.setBlock(pos,state.setValue(BarrelBlock.OPEN,open),2);
    }
    private static void unpack(ServerLevel city,NapoliCivicLife.Citizen c,BlockPos fridge){
        if(!(city.getBlockEntity(fridge) instanceof Container container))return;
        int empty=-1;
        for(int slot=0;slot<container.getContainerSize();slot++){
            var stack=container.getItem(slot);if(stack.is(Items.WHEAT)||stack.is(NapoliKitchen.ITEMS.get("farina"))||stack.is(Items.POTATO)||stack.is(Items.CARROT)||stack.is(Items.BREAD))return;
            if(stack.isEmpty())empty=slot;
        }
        var l=CitySimulation.life(city,c.id());if(empty<0||l.food()<=0)return;
        container.setItem(empty,new ItemStack(Items.WHEAT));container.setChanged();
        CitySimulation.put(city,c.id(),l.withNeeds(l.needs(),l.food()-1,l.last(),l.water(),l.energy()));
    }
    static boolean ingredient(ServerLevel city,NapoliCivicLife.Citizen c,NapoliMergellina.House h){
        var fridge=CityLivingHomes.use(h,CityLivingHomes.Furniture.FRIDGE).block();
        if(city.getBlockEntity(fridge) instanceof Container container){
            for(int slot=0;slot<container.getContainerSize();slot++){
                var stack=container.getItem(slot);
                if(stack.is(Items.WHEAT)||stack.is(NapoliKitchen.ITEMS.get("farina"))||stack.is(Items.POTATO)||stack.is(Items.CARROT)||stack.is(Items.BREAD)){
                    container.removeItem(slot,1);container.setChanged();return true;
                }
            }
        }
        // The persistent personal pantry is finite too; materialized residents do not get free ingredients.
        var l=CitySimulation.life(city,c.id());if(l.food()<=0)return false;
        CitySimulation.put(city,c.id(),l.withNeeds(l.needs(),l.food()-1,l.last(),l.water(),l.energy()));return true;
    }
    static boolean tick(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliMergellina.House h,NapoliHouseholds.Activity wanted){
        var d=dinner(city,c.id());long now=city.getGameTime();
        boolean hungry=wanted==NapoliHouseholds.Activity.EAT||wanted==NapoliHouseholds.Activity.COOK;
        if(d.stage()<0){if(!hungry||now-d.served()<1200)return false;d=new Dinner(h.id(),0,0,d.served());}
        // A meal already being carried is finished at its original home, even if its invitation ends.
        var original=CityLivingHomes.find(d.home());if(original!=null)h=original;
        if(!d.home().equals(c.property())&&!HouseGuests.invited(city,d.home(),c.id())){put(city,c.id(),new Dinner("",-1,0,now));release(city,c.id());return false;}
        if(!HousingCapacity.canEnter(city,h.inside(),c.id())){release(city,c.id());return false;}
        var point=point(h,d.stage());
        if(!claim(city,point.stand(),c.id())){
            npc.setActivity(NapoliHouseholds.Activity.WAIT);npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOWL));
            var table=point(h,3);move(npc,claim(city,table.stand(),c.id())?table.stand():CityLivingHomes.use(h,CityLivingHomes.Furniture.SOFA).stand());
            put(city,c.id(),new Dinner(h.id(),d.stage(),0,d.served()));return true;
        }
        var activity=switch(d.stage()){case 0->NapoliHouseholds.Activity.FETCH;case 1->NapoliHouseholds.Activity.PREPARE;case 2->NapoliHouseholds.Activity.COOK;case 3->NapoliHouseholds.Activity.SET_TABLE;case 4->NapoliHouseholds.Activity.EAT;default->NapoliHouseholds.Activity.CLEAN;};
        npc.setActivity(activity);
        var item=switch(d.stage()){case 0,1->Items.WHEAT;case 2->Items.WOODEN_SHOVEL;case 3,5->Items.BOWL;default->Items.BREAD;};
        npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item));move(npc,point.stand());
        if(point.stand().distToCenterSqr(npc.position())>2){put(city,c.id(),new Dinner(h.id(),d.stage(),0,d.served()));return true;}
        npc.getLookControl().setLookAt(point.block().getX()+.5,point.block().getY()+.5,point.block().getZ()+.5);
        npc.swingForAttack(InteractionHand.MAIN_HAND);
        if(d.stage()==0){if(d.progress()==0)unpack(city,c,point.block());barrel(city,point.block(),true);}
        if(d.progress()==0){
            SoundEvent sound=switch(d.stage()){case 0->SoundEvents.BARREL_OPEN;case 1,3->SoundEvents.ITEM_FRAME_ADD_ITEM;case 2->SoundEvents.FURNACE_FIRE_CRACKLE;case 4->SoundEvents.GENERIC_EAT.value();default->SoundEvents.BUCKET_EMPTY;};
            city.playSound(null,point.block(),sound,SoundSource.NEUTRAL,.35f,1);
        }
        if(d.stage()==2||d.stage()==5)city.sendParticles(d.stage()==2?ParticleTypes.SMOKE:ParticleTypes.SPLASH,point.block().getX()+.5,point.block().getY()+1,point.block().getZ()+.5,4,.2,.15,.2,.01);
        if(d.progress()+40<120){put(city,c.id(),new Dinner(h.id(),d.stage(),d.progress()+40,d.served()));return true;}
        if(d.stage()==0){
            barrel(city,point.block(),false);
            if(!ingredient(city,c,h)){put(city,c.id(),new Dinner(h.id(),-1,0,now-600));release(city,c.id());npc.setActivity(NapoliHouseholds.Activity.SHOP);return false;}
        }
        if(d.stage()==4){
            var l=CitySimulation.life(city,c.id());CitySimulation.put(city,c.id(),l.withNeeds(l.needs().change(35,0,0,4,5,0),l.food(),l.last(),l.water(),l.energy()));
            city.sendParticles(ParticleTypes.HAPPY_VILLAGER,npc.getX(),npc.getY()+1,npc.getZ(),3,.2,.2,.2,0);
        }
        if(d.stage()==5){
            var l=CitySimulation.life(city,c.id());CitySimulation.put(city,c.id(),l.withNeeds(l.needs().change(0,0,4,0,0,0),l.food(),l.last(),l.water()+1,l.energy()+1));
            put(city,c.id(),new Dinner(h.id(),-1,0,now));npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }else put(city,c.id(),new Dinner(h.id(),d.stage()+1,0,d.served()));
        release(city,c.id());return true;
    }
    static void register(){}
    private CityDomestic(){}
}
