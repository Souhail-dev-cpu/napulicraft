package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** World props are views of finite, saved inventories and chores; decorative entities never drop supplies. */
final class CityTraces {
    record Home(int dishes,int laundry,boolean outside,long dryAt,long washed,long generated,long cleaned){
        static final Codec<Home> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("dishes").forGetter(Home::dishes),Codec.INT.fieldOf("laundry").forGetter(Home::laundry),Codec.BOOL.fieldOf("outside").forGetter(Home::outside),Codec.LONG.fieldOf("dry_at").forGetter(Home::dryAt),Codec.LONG.fieldOf("washed").forGetter(Home::washed),Codec.LONG.fieldOf("generated").forGetter(Home::generated),Codec.LONG.fieldOf("cleaned").forGetter(Home::cleaned)).apply(i,Home::new));
        static Home fresh(){return new Home(0,0,false,0,-24000,-1,-200);}
    }
    record Cargo(String home,int meals,long taken){static final Codec<Cargo> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("home").forGetter(Cargo::home),Codec.INT.fieldOf("meals").forGetter(Cargo::meals),Codec.LONG.fieldOf("taken").forGetter(Cargo::taken)).apply(i,Cargo::new));}
    record Issue(BlockPos position,String home,long opened,boolean fixed){static final Codec<Issue> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("position").forGetter(Issue::position),Codec.STRING.fieldOf("home").forGetter(Issue::home),Codec.LONG.fieldOf("opened").forGetter(Issue::opened),Codec.BOOL.fieldOf("fixed").forGetter(Issue::fixed)).apply(i,Issue::new));}
    private record Visual(BlockPos position,Item item,String name,boolean flat){}
    static final String PROP="napuli_city_prop";
    static final AttachmentType<Map<String,Home>> HOMES=AttachmentRegistry.<Map<String,Home>>builder().persistent(Codec.unboundedMap(Codec.STRING,Home.CODEC)).buildAndRegister(Napuli.id("home_daily_traces"));
    static final AttachmentType<Map<String,Cargo>> CARGO=AttachmentRegistry.<Map<String,Cargo>>builder().persistent(Codec.unboundedMap(Codec.STRING,Cargo.CODEC)).buildAndRegister(Napuli.id("shopping_cargo"));
    static final AttachmentType<Map<String,Integer>> PANTRIES=AttachmentRegistry.<Map<String,Integer>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.intRange(0,128))).buildAndRegister(Napuli.id("household_pantry"));
    static final AttachmentType<Map<String,Issue>> ISSUES=AttachmentRegistry.<Map<String,Issue>>builder().persistent(Codec.unboundedMap(Codec.STRING,Issue.CODEC)).buildAndRegister(Napuli.id("neighborhood_repairs"));
    static void fixtures(ServerLevel city){
        for(var category:List.of(CityProfessions.Category.FOOD,CityProfessions.Category.TRADE))for(int x=3;x<=5;x++)for(int z=2;z<=3;z++){var pos=category.site().offset(x,0,z);if(city.isEmptyBlock(pos)&&!CityProperty.playerPlaced(city,pos))city.setBlock(pos,Blocks.OAK_PLANKS.defaultBlockState(),2);}
        for(var h:CityLivingHomes.all())if(!h.id().startsWith("atlas_"))for(int i=-1;i<=1;i++){var p=h.entrance().east(i).above(4);if(city.isEmptyBlock(p)&&!CityProperty.playerPlaced(city,p))city.setBlock(p,BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("iron_chain")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,net.minecraft.core.Direction.Axis.X),2);}
    }
    static Home home(ServerLevel city,String id){return city.getAttachedOrElse(HOMES,Map.of()).getOrDefault(id,Home.fresh());}
    static void put(ServerLevel city,String id,Home h){var all=new HashMap<>(city.getAttachedOrElse(HOMES,Map.of()));all.put(id,h);city.setAttached(HOMES,Map.copyOf(all));}
    static Cargo cargo(ServerLevel city,UUID id){return city.getAttachedOrElse(CARGO,Map.of()).get(id.toString());}
    static int pantry(ServerLevel city,String id){return city.getAttachedOrElse(PANTRIES,Map.of()).getOrDefault(id,0);}
    static void pantry(ServerLevel city,String id,int amount){var all=new HashMap<>(city.getAttachedOrElse(PANTRIES,Map.of()));all.put(id,Math.clamp(amount,0,128));city.setAttached(PANTRIES,Map.copyOf(all));}
    static boolean takePantry(ServerLevel city,String id){int count=pantry(city,id);if(count<1)return false;pantry(city,id,count-1);return true;}
    static boolean shopping(ServerLevel city,NapoliCivicLife.Citizen c,int amount){
        if(amount<1||c.property().isEmpty()||cargo(city,c.id())!=null)return false;var life=CitySimulation.life(city,c.id());if(life.food()<amount)return false;
        CitySimulation.put(city,c.id(),life.withNeeds(life.needs(),life.food()-amount,life.last(),life.water(),life.energy()));var all=new HashMap<>(city.getAttachedOrElse(CARGO,Map.of()));all.put(c.id().toString(),new Cargo(c.property(),amount,city.getGameTime()));city.setAttached(CARGO,Map.copyOf(all));return true;
    }
    static boolean unload(ServerLevel city,NapoliCivicLife.Citizen c){var cargo=cargo(city,c.id());var residence=cargo==null?null:CityLivingHomes.find(cargo.home());if(residence==null||!CityJourneys.at(city,c,residence.inside(),12)||pantry(city,cargo.home())+cargo.meals()>128)return false;
        pantry(city,cargo.home(),pantry(city,cargo.home())+cargo.meals());var all=new HashMap<>(city.getAttachedOrElse(CARGO,Map.of()));all.remove(c.id().toString());city.setAttached(CARGO,Map.copyOf(all));flush(city,residence);return true;
    }
    static void flush(ServerLevel city,NapoliMergellina.House house){
        int reserved=pantry(city,house.id());if(reserved==0)return;var pos=CityLivingHomes.use(house,CityLivingHomes.Furniture.FRIDGE).block();if(!city.hasChunkAt(pos)||!(city.getBlockEntity(pos) instanceof Container fridge))return;
        int left=reserved;for(int i=0;i<fridge.getContainerSize()&&left>0;i++){var stack=fridge.getItem(i);if(stack.isEmpty()){int count=Math.min(64,left);fridge.setItem(i,new ItemStack(Items.BREAD,count));left-=count;}else if(stack.is(Items.BREAD)&&stack.getCount()<64){int count=Math.min(64-stack.getCount(),left);stack.grow(count);left-=count;}}
        if(left!=reserved){fridge.setChanged();pantry(city,house.id(),left);}
    }
    static boolean foodAvailable(ServerLevel city,NapoliCivicLife.Citizen c){if(CitySimulation.life(city,c.id()).food()>0||pantry(city,c.property())>0||cargo(city,c.id())!=null)return true;var house=CityLivingHomes.find(c.property());if(house==null)return false;var pos=CityLivingHomes.use(house,CityLivingHomes.Furniture.FRIDGE).block();if(!city.hasChunkAt(pos)||!(city.getBlockEntity(pos) instanceof Container fridge))return false;for(int i=0;i<fridge.getContainerSize();i++){var stack=fridge.getItem(i);if(stack.is(Items.BREAD)||stack.is(Items.WHEAT)||stack.is(Items.POTATO)||stack.is(Items.CARROT)||stack.is(NapoliKitchen.ITEMS.get("farina")))return true;}return false;}
    static void meal(ServerLevel city,String house){var h=home(city,house);put(city,house,new Home(Math.min(8,h.dishes()+1),h.laundry(),h.outside(),h.dryAt(),h.washed(),h.generated(),h.cleaned()));}
    static void soiled(ServerLevel city,String house){if(house.isEmpty())return;var h=home(city,house);put(city,house,new Home(h.dishes(),Math.min(12,h.laundry()+1),h.outside(),h.dryAt(),h.washed(),h.generated(),h.cleaned()));}
    static void clean(ServerLevel city,String house){var h=home(city,house);put(city,house,new Home(Math.max(0,h.dishes()-1),h.laundry(),h.outside(),h.dryAt(),h.washed(),h.generated(),city.getGameTime()));}
    static void line(ServerLevel city,String house,boolean outside){var h=home(city,house);boolean wet=!outside&&h.outside()&&city.isRaining();put(city,house,new Home(h.dishes(),outside||wet?h.laundry():0,outside,outside?city.getGameTime()+1800:wet?city.getGameTime()+2400:0,outside?city.getGameTime():h.washed(),h.generated(),h.cleaned()));}
    static boolean brokenAt(ServerLevel city,BlockPos position){var issue=city.getAttachedOrElse(ISSUES,Map.of()).get(position.asLong()+"");return issue!=null&&!issue.fixed();}
    static boolean repair(ServerLevel city,BlockPos position){var all=new HashMap<>(city.getAttachedOrElse(ISSUES,Map.of()));var issue=all.get(position.asLong()+"");if(issue==null||issue.fixed())return false;all.put(position.asLong()+"",new Issue(issue.position(),issue.home(),issue.opened(),true));city.setAttached(ISSUES,Map.copyOf(all));return true;}
    static BlockPos repairTarget(ServerLevel city){return city.getAttachedOrElse(ISSUES,Map.of()).values().stream().filter(i->!i.fixed()).min(Comparator.comparingLong(Issue::opened)).map(i->{var h=CityLivingHomes.find(i.home());return h==null?i.position().below(3):h.entrance().south();}).orElse(CityProfessions.Category.SERVICES.site().offset(8,0,7));}
    static Issue issueNear(ServerLevel city,BlockPos actor){return city.getAttachedOrElse(ISSUES,Map.of()).values().stream().filter(i->!i.fixed()&&CityLivingHomes.find(i.home())!=null&&CityLivingHomes.find(i.home()).entrance().distSqr(actor)<25).findFirst().orElse(null);}
    static boolean carry(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c){var carried=cargo(city,c.id());if(carried==null)return false;var h=CityLivingHomes.find(carried.home());if(h==null)return false;
        npc.setDropChance(EquipmentSlot.OFFHAND,0);npc.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.BUNDLE));npc.animate(CityGestures.Gesture.CARRY,50);CityMovement.stand(npc);npc.setActivity(NapoliHouseholds.Activity.COMMUTE);
        if(!HousingCapacity.canEnter(city,h.inside(),npc.getUUID())){CityArrivals.waitOutside(npc,city,h);return true;}
        var goal=CityLivingHomes.use(h,CityLivingHomes.Furniture.FRIDGE).stand();if(goal.distToCenterSqr(npc.position())>3){CityDomestic.move(npc,CityRoutines.localGoal(city,npc,goal));return true;}
        if(unload(city,c)){npc.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);CityMovement.release(city,npc.getUUID());}return true;
    }
    static boolean chores(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity wanted){
        var h=CityLivingHomes.find(c.property());if(h==null)return false;var trace=home(city,h.id());var agreement=CityRelationships.agreement(city,c);if(agreement==null||!agreement.washer().equals(c.id().toString()))return false;
        boolean collect=trace.outside()&&(city.isRaining()||city.getGameTime()>=trace.dryAt())||!trace.outside()&&trace.laundry()>0&&trace.dryAt()>0&&city.getGameTime()>=trace.dryAt();if(wanted!=NapoliHouseholds.Activity.CLEAN&&!(collect&&h.contains(npc.blockPosition())&&!CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city))))return false;
        var target=collect?h.entrance().south(2):CityLivingHomes.use(h,trace.dishes()>0?CityLivingHomes.Furniture.SINK:CityLivingHomes.Furniture.WASHER).stand();
        npc.setActivity(NapoliHouseholds.Activity.CLEAN);CityMovement.stand(npc);if(target.distToCenterSqr(npc.position())>3){CityDomestic.move(npc,CityRoutines.localGoal(city,npc,target));return true;}
        npc.animate(collect?CityGestures.Gesture.CARRY:CityGestures.Gesture.WASH,50);if(city.getGameTime()-trace.cleaned()<160)return true;
        if(collect)line(city,h.id(),false);else if(trace.dishes()>0)clean(city,h.id());else if(trace.laundry()>0&&!trace.outside()&&!city.isRaining()){line(city,h.id(),true);put(city,h.id(),new Home(trace.dishes(),trace.laundry(),true,city.getGameTime()+1800,city.getGameTime(),trace.generated(),city.getGameTime()));}
        return true;
    }
    static Map<String,Visual> visuals(ServerLevel city){var all=new HashMap<String,Visual>();
        city.getAttachedOrElse(CityOccupations.LITTER,Map.of()).forEach((id,l)->{if(l.units()>0)all.put("litter/"+id,new Visual(l.position(),Items.PAPER,"",true));});
        city.getAttachedOrElse(CityOccupations.PARCELS,Map.of()).forEach((id,p)->{if(!p.collected()){var c=NapoliCivicLife.profile(city,p.recipient());all.put("parcel/"+id,new Visual(p.position(),Items.CHEST,"Consegna per "+(c==null?"un vicino":c.name()),false));}});
        city.getAttachedOrElse(HOMES,Map.of()).forEach((id,state)->{var h=CityLivingHomes.find(id);if(h==null)return;if(state.dishes()>0)all.put("dishes/"+id,new Visual(new BlockPos(h.x()+4,h.ground()+1,h.z()+7),Items.BOWL,"",false));if(state.outside()||state.laundry()>0&&state.dryAt()>city.getGameTime())for(int i=0;i<(state.outside()?3:1);i++)all.put("laundry/"+id+"/"+i,new Visual(state.outside()?h.entrance().east(i-1).above(3):CityLivingHomes.use(h,CityLivingHomes.Furniture.WASHER).block().above(2),Items.LEATHER_CHESTPLATE,"",false));});
        for(String shop:CityEconomy.SHOPS){int loaves=Math.min(6,(CityEconomy.stock(city,shop).bread()+15)/16);for(int i=0;i<loaves;i++)all.put("shelf/"+shop+"/"+i,new Visual(CityEconomy.site(shop).offset(-5+i%3,1,-5+i/3),Items.BREAD,"",false));}
        return all;
    }
    static void sync(ServerLevel city){
        var visuals=visuals(city);var ids=new HashSet<UUID>();visuals.keySet().forEach(k->ids.add(UUID.nameUUIDFromBytes(("napuli:prop:"+k).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var removed=new ArrayList<Entity>();for(var e:city.getAllEntities())if(e!=null&&e.entityTags().contains(PROP)&&!ids.contains(e.getUUID()))removed.add(e);for(var e:removed)e.discard();
        visuals.forEach((key,view)->{var p=view.position();if(!city.hasChunkAt(p)||city.players().stream().noneMatch(player->p.distToCenterSqr(player.position())<72*72))return;var id=UUID.nameUUIDFromBytes(("napuli:prop:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));if(city.getEntity(id)!=null)return;
            if(CityJourneys.known(city,id))return;
            var display=new Display.ItemDisplay(BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("item_display")),city);display.setUUID(id);var stack=new ItemStack(view.item());if(key.startsWith("laundry/")){stack=new ItemStack(Items.LEATHER_CHESTPLATE);stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR,new net.minecraft.world.item.component.DyedItemColor(0xf3f2e9));}display.setItemStack(stack);display.setItemTransform(ItemDisplayContext.FIXED);display.setNoGravity(true);display.setPermanentlyInvulnerable(true);display.setPos(p.getX()+.5,p.getY()+(view.flat()?.05:.35),p.getZ()+.5);if(view.flat())display.setXRot(90);if(!view.name().isEmpty()){display.setCustomName(Component.literal(view.name()));display.setCustomNameVisible(true);}city.addFreshEntity(display);display.addTag(PROP);
        });
    }
    static void background(ServerLevel city){var census=NapoliCivicLife.census(city);if(census==null)return;long day=NapoliBroadcast.day(city),now=city.getGameTime();
        for(var home:census.people().stream().map(NapoliCivicLife.Citizen::property).filter(s->!s.isEmpty()).distinct().toList()){
            var h=CityLivingHomes.find(home);if(h==null)continue;var state=home(city,home);if(state.generated()<day){CityOccupations.litter(city,h.entrance().south(2),1);put(city,home,new Home(state.dishes(),state.laundry(),state.outside(),state.dryAt(),state.washed(),day,state.cleaned()));
                if(Math.floorMod(home.hashCode()+day,19)==0&&!CityProperty.playerPlaced(city,CityAtmosphere.lamp(h))){var issues=new HashMap<>(city.getAttachedOrElse(ISSUES,Map.of()));issues.putIfAbsent(CityAtmosphere.lamp(h).asLong()+"",new Issue(CityAtmosphere.lamp(h),home,day,false));city.setAttached(ISSUES,Map.copyOf(issues));}
            }flush(city,h);
        }
        for(var c:census.people()){
            if(city.getEntity(c.id())==null){if(cargo(city,c.id())!=null)unload(city,c);CityOccupations.virtualTick(city,c);}
            var h=CityLivingHomes.find(c.property());if(h==null||city.getEntity(c.id())!=null||!CityJourneys.at(city,c,h.inside(),12))continue;var state=home(city,h.id());var agreement=CityRelationships.agreement(city,c);
            if(agreement!=null&&agreement.washer().equals(c.id().toString())){if(state.outside()&&(city.isRaining()||now>=state.dryAt())||!state.outside()&&state.dryAt()>0&&now>=state.dryAt()&&state.laundry()>0)line(city,h.id(),false);else if(CityHabits.choose(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city))==NapoliHouseholds.Activity.CLEAN){if(state.dishes()>0)clean(city,h.id());else if(state.laundry()>0&&!city.isRaining())line(city,h.id(),true);}}
        }
    }
    static void register(){ServerEntityEvents.ENTITY_LOAD.register((entity,city)->{if(entity.entityTags().contains(PROP))entity.discard();});ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%100==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null){background(city);sync(city);}}});}
    private CityTraces(){}
}
