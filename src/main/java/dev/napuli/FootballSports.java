package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Open practice events at the Fuorigrotta sports ground. */
final class FootballSports {
    private static final Vec3 HOOP = new Vec3(457.5, 67.05, 30.5);
    private static final Vec3 FREE_THROW = new Vec3(452.5, 64, 30.5);
    private static final String BALL_TAG = "napuli_basket_shot";
    private static final int ATTEMPTS = 5;
    private static final Map<UUID, Basket> BASKET = new HashMap<>();
    private static final Map<UUID, Long> SPRINT = new HashMap<>();
    private static final AttachmentType<Integer> BEST_BASKET = record("basket_record");
    private static final AttachmentType<Integer> BEST_SPRINT = record("sprint_record");

    private static final class Basket {
        int attempts, made, airborne;
        Vec3 velocity;
        Display.ItemDisplay ball;
    }

    private static AttachmentType<Integer> record(String name) {
        return AttachmentRegistry.<Integer>builder().persistent(Codec.INT).copyOnDeath().buildAndRegister(Napuli.id(name));
    }

    static void build(ServerLevel city) {
        for (int x=445;x<=462;x++) for (int z=23;z<=37;z++) {
            boolean line=x==445||x==462||z==23||z==37||x==452&&Math.abs(z-30)<=3;
            put(city,new BlockPos(x,63,z),line?Blocks.CALCITE:block("orange_terracotta"));
        }
        for (int y=64;y<=68;y++) put(city,new BlockPos(459,y,30),Blocks.IRON_BLOCK);
        for (int z=29;z<=31;z++) put(city,new BlockPos(458,68,z),block("white_stained_glass"));
        for (var edge : new BlockPos[]{new BlockPos(457,66,29),new BlockPos(457,66,31),new BlockPos(458,66,30)})
            put(city,edge,block("orange_concrete"));
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
    }

    private static void put(ServerLevel city,BlockPos pos,Block block) {
        if(!CityProperty.playerPlaced(city,pos)&&city.getBlockEntity(pos)==null)
            city.setBlock(pos,block.defaultBlockState(),Block.UPDATE_CLIENTS);
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((d,c,e)->{
            d.register(Commands.literal("basket").executes(ctx->beginBasket(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("tira").executes(ctx->shoot(ctx.getSource().getPlayerOrException()))));
            d.register(Commands.literal("atletica").executes(ctx->beginSprint(ctx.getSource().getPlayerOrException())));
        });
        ServerTickEvents.END_SERVER_TICK.register(server->server.getPlayerList().getPlayers().forEach(FootballSports::tick));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clear(handler.getPlayer()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{
            if(entity.entityTags().contains(BALL_TAG)&&BASKET.values().stream().noneMatch(s->s.ball==entity))entity.discard();
        });
    }

    private static boolean available(ServerPlayer p) {
        return p.level().dimension()==NapoliMap.LEVEL&&p.isAlive()&&!p.isPassenger()&&!p.isSpectator()
                &&!Carabinieri.isJailed(p)&&!FootballPractice.isPlaying(p);
    }

    private static int beginBasket(ServerPlayer p) {
        if(!available(p))return 0;
        clear(p);
        p.teleportTo(p.level(),FREE_THROW.x,FREE_THROW.y,FREE_THROW.z,java.util.Set.of(),-90,-30,true);
        BASKET.put(p.getUUID(),new Basket());
        note(p,"Tiri liberi · 5 tentativi · Mira sopra il ferro e usa /basket tira · Record "+p.getAttachedOrElse(BEST_BASKET,0)+"/5");
        return 1;
    }

    private static int shoot(ServerPlayer p) {
        var session=BASKET.get(p.getUUID());
        if(!available(p)||session==null||session.ball!=null||p.position().distanceToSqr(FREE_THROW)>1.4)return 0;
        var ball=new Display.ItemDisplay(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("item_display")),p.level());
        var stack=new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL,Napuli.id("basket_ball"));
        ball.setItemStack(stack);
        ball.setItemTransform(ItemDisplayContext.FIXED);
        ball.setWidth(1);ball.setHeight(1);
        ball.setPos(p.getEyePosition());
        ball.setNoGravity(true);ball.addTag(BALL_TAG);
        session.ball=ball;
        if(!p.level().addFreshEntity(ball)){session.ball=null;return 0;}
        session.attempts++;
        session.airborne=0;
        session.velocity=p.getLookAngle().scale(.82);
        return 1;
    }

    private static int beginSprint(ServerPlayer p) {
        if(!available(p))return 0;
        clear(p);
        BlockPos start=FootballGround.START_RUN;
        p.teleportTo(p.level(),start.getX()+.5,start.getY(),start.getZ()+.5,java.util.Set.of(),0,0,true);
        SPRINT.put(p.getUUID(),-1L);
        note(p,"Atletica · Corri fino alla linea bianca in fondo. Il tempo parte quando lasci la linea.");
        return 1;
    }

    private static void tick(ServerPlayer p) {
        if(!available(p)){clear(p);return;}
        var basket=BASKET.get(p.getUUID());
        if(basket!=null){
            if(p.position().distanceToSqr(FREE_THROW)>36){clear(p);return;}
            if(basket.ball!=null)flight(p,basket);
        }
        Long start=SPRINT.get(p.getUUID());
        if(start==null)return;
        double progress=p.getZ()-(FootballGround.START_RUN.getZ()+.5);
        if(Math.abs(p.getX()-(FootballGround.START_RUN.getX()+.5))>1.25||Math.abs(p.getY()-64)>1.8||progress< -1||progress>18){
            clear(p);return;
        }
        if(start<0){
            if(progress>.4)SPRINT.put(p.getUUID(),p.level().getGameTime());
            return;
        }
        long elapsed=p.level().getGameTime()-start;
        if(elapsed>200){clear(p);return;}
        if(p.getZ()>=FootballGround.FINISH_RUN.getZ()+.5){
            SPRINT.remove(p.getUUID());
            int best=p.getAttachedOrElse(BEST_SPRINT,Integer.MAX_VALUE);
            if(elapsed<best)p.setAttached(BEST_SPRINT,(int)elapsed);
            note(p,String.format(java.util.Locale.ITALY,"Atletica · %.2f s · Record %.2f s",elapsed/20d,Math.min(best,elapsed)/20d));
        }
    }

    private static void flight(ServerPlayer p,Basket s) {
        Vec3 previous=s.ball.position();
        Vec3 next=previous.add(s.velocity);
        s.ball.setPos(next);
        s.velocity=s.velocity.add(0,-.071,0);
        if(previous.y>=HOOP.y&&next.y<HOOP.y){
            double fraction=(previous.y-HOOP.y)/(previous.y-next.y);
            if(previous.lerp(next,fraction).subtract(HOOP).horizontalDistanceSqr()<.42*.42){finishShot(p,s,true);return;}
        }
        if(++s.airborne>=50||next.y<64||next.x>459.5)finishShot(p,s,false);
    }

    private static void finishShot(ServerPlayer p,Basket s,boolean made) {
        s.ball.discard();s.ball=null;
        if(made)s.made++;
        if(s.attempts<ATTEMPTS){
            note(p,(made?"Canestro":"Fuori")+" · "+s.made+"/"+s.attempts+" · /basket tira");
            return;
        }
        BASKET.remove(p.getUUID());
        int best=Math.max(s.made,p.getAttachedOrElse(BEST_BASKET,0));
        p.setAttached(BEST_BASKET,best);
        note(p,"Tiri liberi · "+s.made+"/5 · Record "+best+"/5");
    }

    private static void clear(ServerPlayer p) {
        var session=BASKET.remove(p.getUUID());
        if(session!=null&&session.ball!=null)session.ball.discard();
        SPRINT.remove(p.getUUID());
    }

    private static void note(ServerPlayer p,String message) {
        NapoliNotice.show(p,Component.literal(message));
    }

    private FootballSports() {}
}
