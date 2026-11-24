package dev.napuli;

import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

/** Doorbells ask the host; a copied key never bypasses server-owned permissions. */
final class CityArrivals {
    private record Wait(String home,long since,long cafeUntil){}
    private static final Map<ServerLevel,Map<UUID,Wait>> WAITING=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<String,Long>> BELLS=new WeakHashMap<>();
    static BlockPos bell(NapoliMergellina.House h){return h.entrance().east().above();}
    static NapoliMergellina.House house(String id){return CityLivingHomes.find(id);}
    static void fixtures(ServerLevel city){
        // Connect the courts to the existing Partenope street without rebuilding any house.
        for(int row=0;row<4;row++)for(int x=-536;x<=-337;x++){
            var path=new BlockPos(x,63,94+row*35);if(city.isEmptyBlock(path))city.setBlock(path,Blocks.STONE_BRICKS.defaultBlockState(),2);
        }
        for(var h:NapoliMergellina.PROPERTIES)if(city.isEmptyBlock(bell(h)))city.setBlock(bell(h),Blocks.STONE_BUTTON.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE,AttachFace.WALL).setValue(HorizontalDirectionalBlock.FACING,Direction.SOUTH),2);
        if(city.isEmptyBlock(CityEconomy.DEPOT)){
            if(city.isEmptyBlock(CityEconomy.DEPOT.below()))city.setBlock(CityEconomy.DEPOT.below(),Blocks.STONE_BRICKS.defaultBlockState(),2);
            NapoliEmploymentMap.sign(city,CityEconomy.DEPOT,"DEPOSITO PROVVISTE","/quartiere incarico","forno / mercato");
        }
    }
    static void visitorMenu(ServerPlayer p,NapoliMergellina.House h){
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(h.address()),Optional.empty(),true,false,DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal("Casa privata. Suona il campanello: chi abita qui può invitarti. Gli ospiti autorizzati possono fermarsi anche oltre i cinque posti ordinari."),270)),List.of()),
                List.of(CitySocial.button("Suona il campanello","/casa suona "+h.id())),Optional.empty(),1)));
    }
    static int ring(ServerPlayer p,String id){
        var h=house(id);if(h==null||p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive()||Carabinieri.isJailed(p)||p.blockPosition().distSqr(h.entrance())>36)return 0;
        var city=p.level();var times=BELLS.computeIfAbsent(city,k->new HashMap<>());var key=p.getUUID()+"/"+id;long now=city.getGameTime();
        if(now-times.getOrDefault(key,Long.MIN_VALUE/2)<200)return 0;times.put(key,now);
        CityEffects.sound(city,null,bell(h),SoundEvents.NOTE_BLOCK_BELL.value(),SoundSource.BLOCKS,.65f,1.3f);
        boolean answered=false;
        for(var host:city.players())if(HouseGuests.host(city,id,host.getUUID())){
            host.sendSystemMessage(Component.literal(p.getName().getString()+" ha suonato a "+h.address()+". [Invita] ")
                    .withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/casa invita "+id+" "+p.getUUID()))));answered=true;
        }
        var owner=NapoliProperties.owner(city,h);var npc=owner==null?null:city.getEntity(owner);
        if(npc instanceof NapoliResident resident&&h.contains(npc.blockPosition())){
            npc.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,p.getEyePosition());
            if(CitySocial.bond(city,owner,p.getUUID()).trust()>=65&&HouseGuests.grant(city,id,owner,p)){
                NpcSpeech.say(p,resident,Component.literal("Entra pure, sei il benvenuto!"));
            }else NpcSpeech.say(p,resident,Component.literal("Chi è? Oggi non aspettavo visite."));answered=true;
        }
        p.sendOverlayMessage(Component.literal(answered?"Il campanello ha avvisato chi abita qui.":"Nessuna risposta. Puoi ripassare più tardi."));return 1;
    }
    static String keyHome(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(!stack.is(Items.TRIPWIRE_HOOK)||data==null)return "";var tag=data.copyTag();return tag.getStringOr("napuli_home",tag.getStringOr("napuli_house",""));}
    static int key(ServerPlayer p,String id){
        var h=house(id);if(h==null||p.level().dimension()!=NapoliMap.LEVEL||!p.isAlive()||p.isSpectator()||!NapoliProperties.hasAccess(p,h))return 0;
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(keyHome(p.getInventory().getItem(i)).equals(id))return 0;
        var stack=new ItemStack(Items.TRIPWIRE_HOOK);var tag=new CompoundTag();tag.putString("napuli_home",id);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Chiave · "+h.address()));return p.getInventory().add(stack)?1:0;
    }
    static boolean waitOutside(NapoliResident npc,ServerLevel city,NapoliMergellina.House home){
        var all=WAITING.computeIfAbsent(city,k->new HashMap<>());long now=city.getGameTime();var wait=all.get(npc.getUUID());
        if(wait==null||!wait.home().equals(home.id())){wait=new Wait(home.id(),now,0);all.put(npc.getUUID(),wait);}
        if(HousingCapacity.canEnter(city,home.inside(),npc.getUUID())&&(wait.cafeUntil()<=now||HouseGuests.capacityExempt(city,home.id(),npc.getUUID()))){all.remove(npc.getUUID());return false;}
        if(wait.cafeUntil()==0&&now-wait.since()>=400){
            wait=new Wait(home.id(),wait.since(),now+1200);all.put(npc.getUUID(),wait);
            var l=CitySimulation.life(city,npc.getUUID());CitySimulation.put(city,npc.getUUID(),l.career(l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),CitySimulation.memory(l,"Casa piena: aspetto gli altri al bar.")));
            CityNeighborhood.say(city,npc,"La casa è piena. Ci vediamo al bar?",16);
        }
        npc.setActivity(wait.cafeUntil()>0?NapoliHouseholds.Activity.SOCIAL:NapoliHouseholds.Activity.WAIT);
        CityDomestic.move(npc,wait.cafeUntil()>0?CityNeighborhood.cafe(home.entrance()):home.approach(1));return true;
    }
    static void register(){
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("casa")
                .then(Commands.literal("suona").then(Commands.argument("id",StringArgumentType.word()).executes(c->ring(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id")))))
                .then(Commands.literal("chiave").then(Commands.argument("id",StringArgumentType.word()).executes(c->key(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id")))))));
        UseBlockCallback.EVENT.register((p,level,hand,hit)->{
            if(level.dimension()!=NapoliMap.LEVEL||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            for(var h:NapoliMergellina.PROPERTIES)if(hit.getBlockPos().equals(bell(h))){if(p instanceof ServerPlayer sp)ring(sp,h.id());return InteractionResult.SUCCESS;}
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((p,level,hand)->{
            var id=keyHome(p.getItemInHand(hand));if(id.isEmpty())return InteractionResult.PASS;
            if(p instanceof ServerPlayer sp){
                var h=house(id);if(h==null||level.dimension()!=NapoliMap.LEVEL||!NapoliProperties.hasAccess(sp,h)||p.blockPosition().distSqr(h.entrance())>36)return InteractionResult.FAIL;
                var door=h.entrance().north();var state=level.getBlockState(door);
                if(state.getBlock() instanceof DoorBlock block)block.setOpen(sp,level,state,door,!state.getValue(DoorBlock.OPEN));
            }return InteractionResult.SUCCESS;
        });
    }
    private CityArrivals(){}
}
