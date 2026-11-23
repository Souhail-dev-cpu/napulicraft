package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;

/** Short scenes use existing residents, with a shared cooldown for nearby listeners. */
final class CityNeighborhood {
    private record Scene(String kind,UUID partner,long until,BlockPos target,UUID basket){}
    private static final Map<ServerLevel,Map<UUID,Scene>> SCENES=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,Long>> HEARD=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,Long>> NEXT=new WeakHashMap<>();
    static BlockPos cafe(BlockPos from){
        var local=CityProfessions.Category.FOOD.site().offset(8,0,14);
        return from.distSqr(local)<from.distSqr(NapoliMap.CAFE)?local:NapoliMap.CAFE;
    }
    static void say(ServerLevel city,NapoliResident npc,String text,int range){
        var heard=HEARD.computeIfAbsent(city,k->new HashMap<>());long now=city.getGameTime();
        for(var p:city.players())if(!p.isSpectator()&&p.distanceToSqr(npc)<range*range&&now>=heard.getOrDefault(p.getUUID(),0L)){
            p.sendSystemMessage(Component.literal(npc.getName().getString()+": "+text));heard.put(p.getUUID(),now+200);
        }
        city.playSound(null,npc.blockPosition(),SoundEvents.VILLAGER_AMBIENT,SoundSource.NEUTRAL,.25f,1);
    }
    static BlockPos schoolEscort(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c){
        long time=NapoliBroadcast.time(city),day=NapoliBroadcast.day(city);
        if(time<2200||time>4000||Math.floorMod(day,7)>=5)return null;
        var school=CityProfessions.Category.PUBLIC.site().offset(8,0,7);
        if(c.age(day)<12&&!c.parents().isEmpty()){
            var parent=city.getEntity(CityRelationships.carer(city,c));
            if(parent instanceof NapoliResident)return parent.distanceToSqr(npc)>16?parent.blockPosition():school;
        }else if(c.age(day)>=18){
            var census=NapoliCivicLife.census(city);if(census==null)return null;
            for(var child:census.people())if(!child.parents().isEmpty()&&CityRelationships.carer(city,child).equals(c.id())&&child.age(day)>=3&&child.age(day)<12){
                var entity=city.getEntity(child.id());if(entity==null||school.distToCenterSqr(entity.position())<36)continue;
                npc.setActivity(NapoliHouseholds.Activity.SCHOOL);
                if(entity.distanceToSqr(npc)>16){npc.getNavigation().stop();return npc.blockPosition();}
                return school;
            }
        }return null;
    }
    static boolean tick(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity wanted){
        var scenes=SCENES.computeIfAbsent(city,k->new HashMap<>());var scene=scenes.get(c.id());long now=city.getGameTime();
        if(scene!=null){
            if(now>=scene.until()||wanted==NapoliHouseholds.Activity.CARE||CityPublicOrder.jailed(city,c.id())){
                if(scene.basket()!=null){var basket=city.getEntity(scene.basket());if(basket!=null)basket.discard();}
                scenes.remove(c.id());npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);return false;
            }
            if(scene.kind().equals("bar")||scene.kind().equals("chat")){
                var partner=city.getEntity(scene.partner());if(partner!=null)npc.getLookControl().setLookAt(partner,30,30);
                npc.setItemSlot(EquipmentSlot.MAINHAND,scene.kind().equals("bar")?new ItemStack(Napuli.CAFE):ItemStack.EMPTY);npc.setActivity(NapoliHouseholds.Activity.SOCIAL);npc.getNavigation().stop();
                if(scene.until()-now<=40&&partner instanceof NapoliResident other)say(city,other,scene.kind().equals("bar")?"Eh, certe giornate non si dimenticano. Prendiamoci un altro caffè.":"Grazie del pensiero! Ci vediamo domani.",18);
            }else{
                npc.setActivity(NapoliHouseholds.Activity.SHOP);CityDomestic.move(npc,scene.target());
                if(scene.target().distToCenterSqr(npc.position())<3&&scene.basket()!=null){
                    var basket=city.getEntity(scene.basket());if(basket!=null){double progress=Math.clamp((scene.until()-now)/160.0,0,1);double drop=4*(1-Math.abs(2*progress-1));basket.snapTo(scene.target().getX()+.5,scene.target().getY()-.2-drop,scene.target().getZ()+1.5,0,0);}
                    city.sendParticles(ParticleTypes.WHITE_ASH,scene.target().getX()+.5,scene.target().getY()-1,scene.target().getZ()+1.5,3,0,.6,0,0);
                }
            }return true;
        }
        if(now<NEXT.computeIfAbsent(city,k->new HashMap<>()).getOrDefault(c.id(),0L)||wanted==NapoliHouseholds.Activity.WORK||wanted==NapoliHouseholds.Activity.CARE||wanted==NapoliHouseholds.Activity.SLEEP)return false;
        long time=NapoliBroadcast.time(city),day=NapoliBroadcast.day(city);
        if(c.age(day)>=60&&time>=9000&&time<12500&&cafe(npc.blockPosition()).distToCenterSqr(npc.position())<20*20){
            for(var other:city.getEntitiesOfClass(NapoliResident.class,npc.getBoundingBox().inflate(4))){
                var friend=NapoliCivicLife.profile(city,other.getUUID());if(friend==null||friend.id().equals(c.id())||friend.age(day)<60||scenes.containsKey(friend.id()))continue;
                scenes.put(c.id(),new Scene("bar",friend.id(),now+240,null,null));scenes.put(friend.id(),new Scene("bar",c.id(),now+240,null,null));
                NEXT.get(city).put(c.id(),now+3600);NEXT.get(city).put(friend.id(),now+3600);CitySimulation.encounter(city,c.id(),friend.id(),day);
                say(city,npc,Math.floorMod(day+c.id().hashCode(),2)==0?"Ti ricordi quando qui passava il vecchio tram?":"Ieri il Napoli ci ha fatto soffrire fino alla fine!",18);return true;
            }
        }
        if(wanted==NapoliHouseholds.Activity.SOCIAL){
            for(var other:city.getEntitiesOfClass(NapoliResident.class,npc.getBoundingBox().inflate(3))){
                var neighbor=NapoliCivicLife.profile(city,other.getUUID());if(neighbor==null||neighbor.id().equals(c.id())||scenes.containsKey(neighbor.id())||CitySimulation.activity(city,neighbor,CitySimulation.life(city,neighbor.id()),time)!=NapoliHouseholds.Activity.SOCIAL)continue;
                scenes.put(c.id(),new Scene("chat",neighbor.id(),now+240,null,null));scenes.put(neighbor.id(),new Scene("chat",c.id(),now+240,null,null));NEXT.get(city).put(c.id(),now+3600);NEXT.get(city).put(neighbor.id(),now+3600);CitySimulation.encounter(city,c.id(),neighbor.id(),day);
                say(city,npc,city.isRaining()?"Vieni sotto, che ti bagni tutto!":"Come sta la famiglia? È un po' che non ci fermiamo a parlare.",14);return true;
            }
        }
        var h=CityLivingHomes.find(c.property());
        if(h!=null&&h.style()>=0&&c.age(day)>=18&&time>=4500&&time<7000&&Math.floorMod(c.id().hashCode()+day,4)==0){
            var balcony=new BlockPos(h.x()+h.width()/2,69,h.z()+h.depth());
            if(!city.hasChunkAt(balcony)||!NapoliResidentHomes.walkable(city,balcony))return false;
            CityDomestic.move(npc,balcony);npc.setActivity(NapoliHouseholds.Activity.SHOP);
            if(balcony.distToCenterSqr(npc.position())>3)return true;
            var basket=new ItemEntity(city,balcony.getX()+.5,balcony.getY(),balcony.getZ()+1.5,new ItemStack(Items.BOWL));basket.setNoGravity(true);basket.setNeverPickUp();basket.addTag("napuli_panaro");
            scenes.put(c.id(),new Scene("panaro",null,now+160,balcony,basket.getUUID()));city.addFreshEntity(basket);NEXT.get(city).put(c.id(),now+12000);
            say(city,npc,"Aspetta, ti calo il panaro!",18);return true;
        }return false;
    }
    static void register(){
        ServerEntityEvents.ENTITY_LOAD.register((entity,city)->{if(entity.entityTags().contains("napuli_panaro")&&SCENES.getOrDefault(city,Map.of()).values().stream().noneMatch(s->entity.getUUID().equals(s.basket())))entity.discard();});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%100!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;long time=NapoliBroadcast.time(city);
            var scenes=SCENES.get(city);if(scenes!=null)scenes.entrySet().removeIf(entry->{var scene=entry.getValue();if(scene.until()>city.getGameTime()&&city.getEntity(entry.getKey())!=null)return false;if(scene.basket()!=null){var basket=city.getEntity(scene.basket());if(basket!=null)basket.discard();}return true;});
            if(time<16500&&time>900)return;
            for(var p:city.players())if(p.isSprinting())for(var npc:city.getEntitiesOfClass(NapoliResident.class,p.getBoundingBox().inflate(10)))if(npc.isSleeping()){
                var next=NEXT.computeIfAbsent(city,k->new HashMap<>());if(city.getGameTime()<next.getOrDefault(npc.getUUID(),0L))continue;
                next.put(npc.getUUID(),city.getGameTime()+2400);say(city,npc,"Facite piano, qui c'è chi dorme!",12);
                var l=CitySimulation.life(city,npc.getUUID());CitySimulation.put(city,npc.getUUID(),l.career(l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),CitySimulation.memory(l,"Ho chiesto a "+p.getName().getString()+" di non fare rumore durante la notte.")));break;
            }
        });
    }
    private CityNeighborhood(){}
}
