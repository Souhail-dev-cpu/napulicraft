package dev.napuli;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** Concrete gardening, carpentry and table service share the saved occupation state machine. */
final class CityTradeWork {
    static BlockPos target(ServerLevel city,NapoliCivicLife.Citizen c,String kind){
        var task=CityOccupations.task(city,c.id());
        if(kind.equals("waiter")&&task!=null&&!task.recipient().isEmpty()){
            var guest=city.getEntity(UUID.fromString(task.recipient()));if(guest!=null)return guest.blockPosition();
        }
        if(kind.equals("gardener")){
            for(int i=0;i<6;i++){var at=new BlockPos(-225+i*2,64,56);if(city.hasChunkAt(at)&&city.isEmptyBlock(at)&&city.getBlockState(at.below()).is(Blocks.GRASS_BLOCK))return at;}
            return new BlockPos(-225,64,56);
        }
        return kind.equals("carpenter")?NapoliEmployment.Contract.CARPENTER.task:CityProfessions.Category.FOOD.site().offset(10,0,7);
    }
    static boolean act(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,String kind){
        if(!Set.of("waiter","gardener","carpenter").contains(kind))return false;
        var task=CityOccupations.normalize(city,c,kind);var goal=target(city,c,kind);long now=city.getGameTime();
        if(goal.distToCenterSqr(npc.position())>9){npc.setActivity(NapoliHouseholds.Activity.COMMUTE);CityDomestic.move(npc,CityRoutines.localGoal(city,npc,goal));return true;}
        npc.setActivity(NapoliHouseholds.Activity.WORK);npc.getNavigation().stop();
        npc.setDropChance(EquipmentSlot.MAINHAND,0);npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(kind.equals("gardener")?Items.BONE_MEAL:kind.equals("carpenter")?Items.IRON_AXE:Items.BREAD));
        npc.animate(kind.equals("carpenter")?CityGestures.Gesture.CHOP:kind.equals("waiter")?CityGestures.Gesture.PACK:CityGestures.Gesture.SWEEP,45);
        if(now-task.last()<200)return true;
        if(kind.equals("waiter")){
            if(task.cargo()>0){
                var guest=NapoliCivicLife.profile(city,UUID.fromString(task.recipient()));var actor=city.getEntity(UUID.fromString(task.recipient()));
                if(guest!=null&&actor instanceof NapoliResident diner&&actor.distanceToSqr(npc)<16&&guest.balance()>=2){
                    CityOccupations.money(city,guest.id(),-2);CityOccupations.money(city,c.id(),2);var l=CitySimulation.life(city,guest.id());CitySimulation.put(city,guest.id(),l.withNeeds(l.needs().change(15,0,0,10,5,0),l.food(),l.last(),l.water(),l.energy()));
                    diner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BREAD));diner.setDropChance(EquipmentSlot.MAINHAND,0);diner.animate(CityGestures.Gesture.EAT,60);CityOccupations.output(city,c.id());
                }else returnBread(city);
                CityOccupations.put(city,c.id(),new CityOccupations.Task(kind,0,0,0,"",now));
            }else{
                var guest=city.getEntitiesOfClass(NapoliResident.class,npc.getBoundingBox().inflate(18),n->n!=npc&&n.activity()==NapoliHouseholds.Activity.SOCIAL).stream().map(n->NapoliCivicLife.profile(city,n.getUUID())).filter(Objects::nonNull).filter(p->p.balance()>=2&&CitySimulation.life(city,p.id()).needs().hunger()<85).findFirst().orElse(null);
                if(guest!=null&&CityEconomy.reserveBread(city,"forno",1))CityOccupations.put(city,c.id(),new CityOccupations.Task(kind,1,0,1,guest.id().toString(),now));
            }
        }else if(kind.equals("carpenter")){
            int wood=CityOccupations.supply(city,"timber");if(wood<1||CityOccupations.supply(city,"planks")>252)return true;
            CityOccupations.supply(city,"timber",wood-1);CityOccupations.supply(city,"planks",CityOccupations.supply(city,"planks")+4);CityOccupations.output(city,c.id());
            CityOccupations.put(city,c.id(),new CityOccupations.Task(kind,0,0,0,"",now));
        }else if(city.isEmptyBlock(goal)&&city.getBlockState(goal.below()).is(Blocks.GRASS_BLOCK)&&!CityProperty.playerPlaced(city,goal)&&CityOccupations.supply(city,"seeds")>0){
            CityOccupations.supply(city,"seeds",CityOccupations.supply(city,"seeds")-1);city.setBlock(goal,Blocks.POPPY.defaultBlockState(),Block.UPDATE_CLIENTS);CityOccupations.output(city,c.id());
            CityOccupations.put(city,c.id(),new CityOccupations.Task(kind,0,0,0,"",now));
        }
        return true;
    }
    static void virtualCarpenter(ServerLevel city,NapoliCivicLife.Citizen c){var t=CityOccupations.normalize(city,c,"carpenter");long now=city.getGameTime();if(now-t.last()<200||!CityJourneys.at(city,c,NapoliEmployment.Contract.CARPENTER.task,4)||CityOccupations.supply(city,"timber")<1||CityOccupations.supply(city,"planks")>252)return;CityOccupations.supply(city,"timber",CityOccupations.supply(city,"timber")-1);CityOccupations.supply(city,"planks",CityOccupations.supply(city,"planks")+4);CityOccupations.output(city,c.id());CityOccupations.put(city,c.id(),new CityOccupations.Task("carpenter",0,0,0,"",now));}
    static void returnBread(ServerLevel city){var stock=CityEconomy.stock(city,"forno");CityEconomy.put(city,"forno",new CityEconomy.Stock(stock.grain(),stock.flour(),Math.min(192,stock.bread()+1),stock.processed(),stock.delivered()));}
    private CityTradeWork(){}
}
