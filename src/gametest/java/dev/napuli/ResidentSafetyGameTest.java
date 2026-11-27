package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class ResidentSafetyGameTest {
    @GameTest public void allHumanRolesCanActuallyBeHurt(GameTestHelper h){
        for(var role:NapoliResident.Role.values()){var npc=NapoliResident.spawn(h.getLevel(),role,h.absolutePos(new BlockPos(2,3,2)),0);npc.setNoAi(true);
            try{boolean hit=npc.hurtServer(h.getLevel(),h.getLevel().damageSources().generic(),3);h.assertTrue(role.spirit()?!hit:hit&&npc.getHealth()==17,"Human roles take real damage: "+role);}finally{npc.discard();}
        }h.succeed();
    }
    @GameTest public void aKilledCitizenKeepsTheirIdentityEstateAndFamilyUntilTomorrow(GameTestHelper h){
        isolated(h,city->{var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.absolutePos(new BlockPos(2,3,2)),0);npc.setNoAi(true);var c=MobilityGameTest.person(city,npc.getUUID(),30);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(Pickpocket.day(npc),List.of(c)));city.setAttached(NapoliProperties.OWNERS,Map.of(c.property(),c.id()));
            try{h.assertTrue(npc.hurtServer(city,city.damageSources().generic(),1000)&&!npc.isAlive(),"The citizen can be killed");var pending=city.getAttached(CityResidentSafety.RETURNS).get(c.id().toString());h.assertTrue(pending.day()==Pickpocket.day(npc)+1&&NapoliCivicLife.profile(city,c.id())!=null,"The same citizen is scheduled for the next game day");var people=new LinkedHashMap<UUID,NapoliCivicLife.Citizen>();people.put(c.id(),c);CitySimulation.inherit(city,people,Pickpocket.day(npc));h.assertTrue(people.containsKey(c.id())&&city.getAttached(NapoliProperties.OWNERS).get(c.property()).equals(c.id()),"A recoverable death does not transfer the house or delete its owner");h.assertTrue(!CityJourneys.at(city,c,NapoliCivicLife.home(c),20),"The absent resident cannot use furniture or work");CityResidentSafety.tick(city);h.assertTrue(CityResidentSafety.absent(city,c.id()),"The citizen does not return on the death day");}
            finally{npc.discard();}
        });h.succeed();
    }
    @GameTest public void namedResidentsReturnWithTheSameAppearanceAndDepletedBag(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,players->{var at=h.absolutePos(new BlockPos(4,3,4));floor(city,at);players.getFirst().setPos(at.getX()+4,at.getY(),at.getZ());var npc=NapoliResident.spawn(city,NapoliResident.Role.PESCATORE,at,0);npc.setNoAi(true);npc.setCustomName(Component.literal("Pescatore riconoscibile"));npc.setEmploymentAppearance(NapoliResident.Role.PESCATORE,0);npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));npc.setDropChance(EquipmentSlot.MAINHAND,1);UUID id=npc.getUUID();var bag=Pickpocket.inventory(npc);var first=bag.stream().limit(9).filter(s->!s.get().isEmpty()).findFirst().orElseThrow();var less=first.get().copy();less.shrink(1);first.set(less);int remaining=bag.stream().limit(9).mapToInt(s->s.get().getCount()).sum();long clock=city.getOverworldClockTime();
            try{npc.hurtServer(city,city.damageSources().generic(),1000);h.assertTrue(city.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,npc.getBoundingBox().inflate(3)).isEmpty(),"A returning NPC does not duplicate its equipment as death loot");npc.discard();CityResidentSafety.tick(city);h.assertTrue(city.getEntity(id)==null,"There is no same-day replacement");setClock(city,(Math.floorDiv(clock,24000)+1)*24000+1000);CityResidentSafety.tick(city);var restored=city.getEntity(id);h.assertTrue(restored instanceof NapoliResident r&&r.isAlive()&&r.getHealth()==20&&r.role()==NapoliResident.Role.PESCATORE&&r.getName().getString().equals("Pescatore riconoscibile"),"The same named person returns alive after the day changes");h.assertTrue(Pickpocket.inventory((NapoliResident)restored).stream().limit(9).mapToInt(s->s.get().getCount()).sum()==remaining,"Returning does not replenish the stolen contents");CityResidentSafety.tick(city);h.assertTrue(city.getEntity(id)==restored&&!CityResidentSafety.absent(city,id),"A repeated tick cannot create a second replacement");}
            finally{var restored=city.getEntity(id);if(restored!=null)restored.discard();npc.discard();setClock(city,clock);}
        }));h.succeed();
    }
    @GameTest public void recoveryAndComplaintDeadlinesSurviveSaving(GameTestHelper h){
        var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());var data=new net.minecraft.nbt.CompoundTag();data.putString("name","Ciro");var pending=new CityResidentSafety.Return(8,new BlockPos(12,64,5),data,true,false);var saved=CityResidentSafety.Return.CODEC.parse(ops,CityResidentSafety.Return.CODEC.encodeStart(ops,pending).getOrThrow()).getOrThrow();h.assertTrue(saved.day()==8&&saved.at().equals(pending.at())&&saved.actor().getStringOr("name","").equals("Ciro"),"Recovery day, position and actor data survive a restart");var call=new CityResidentSafety.Complaint(UUID.randomUUID(),new BlockPos(9,64,10),60,600,false);h.assertTrue(CityResidentSafety.Complaint.CODEC.parse(ops,CityResidentSafety.Complaint.CODEC.encodeStart(ops,call).getOrThrow()).getOrThrow().equals(call),"The caller remembers the attacker and the actual last observed location");h.succeed();
    }
    @GameTest public void distantReturnsDoNotLoadTheMap(GameTestHelper h){
        isolated(h,city->{var id=UUID.randomUUID();var far=new BlockPos(15000000,70,15000000);h.assertTrue(!city.hasChunkAt(far),"Fixture starts outside loaded chunks");city.setAttached(CityResidentSafety.RETURNS,Map.of(id.toString(),new CityResidentSafety.Return(-1,far,new net.minecraft.nbt.CompoundTag(),false,false)));CityResidentSafety.tick(city);h.assertTrue(!city.hasChunkAt(far)&&CityResidentSafety.absent(city,id),"An unobserved return waits for a visitor without loading a remote chunk");});h.succeed();
    }
    private static final List<AttachmentType<?>> TYPES=List.of(NapoliCivicLife.CENSUS,CitySimulation.LIFE,CityResidentSafety.RETURNS,CityResidentSafety.CALLS,NapoliProperties.OWNERS,CityJourneys.JOURNEYS,CityMobility.RIDES,CityNetwork.NPC);
    private static void isolated(GameTestHelper h,java.util.function.Consumer<ServerLevel> body){var city=h.getLevel();var saved=new HashMap<AttachmentType<?>,Object>();for(var t:TYPES)saved.put(t,city.removeAttached(t));try{body.accept(city);}finally{for(var t:TYPES)restore(city,t,saved.get(t));}}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void restore(ServerLevel city,AttachmentType type,Object value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
    private static void floor(ServerLevel city,BlockPos at){for(var p:BlockPos.betweenClosed(at.offset(-5,-1,-5),at.offset(5,3,5)))city.setBlock(p,p.getY()==at.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);}
    private static void setClock(ServerLevel city,long value){city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set "+value);}
}
