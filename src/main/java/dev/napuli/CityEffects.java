package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import static dev.napuli.CityText.t;

/** Personal cosmetic preferences never change damage, AI, traffic or another player's effects. */
public final class CityEffects {
    public static final int LIGHT=1,PARTICLES=2,FLASHES=4,QUIET=8,TEXT=16;
    public record Preferences(int flags) implements CustomPacketPayload {
        public static final Type<Preferences> TYPE=new Type<>(Napuli.id("city_effect_preferences"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Preferences> CODEC=StreamCodec.of((b,p)->b.writeByte(p.flags()),b->new Preferences(b.readUnsignedByte()));
        @Override public Type<Preferences> type(){return TYPE;}
    }
    static final AttachmentType<Integer> FLAGS=AttachmentRegistry.create(Napuli.id("effect_flags"));
    private static final Map<ServerPlayer,Map<String,Long>> CUES=new WeakHashMap<>();
    static int flags(ServerPlayer p){return p.getAttachedOrElse(FLAGS,TEXT);}
    public static int particleCount(int flags,int count,boolean flashing){if((flags&PARTICLES)!=0||flashing&&(flags&FLASHES)!=0)return 0;return (flags&LIGHT)!=0?Math.min(4,Math.max(count==0?0:1,(count+3)/4)):count;}
    static void cue(ServerPlayer p,String kind){if((flags(p)&TEXT)==0)return;var cues=CUES.computeIfAbsent(p,k->new HashMap<>());long now=p.level().getGameTime();if(now<cues.getOrDefault(kind,Long.MIN_VALUE))return;cues.put(kind,now+100);p.sendOverlayMessage(t("effects.cue."+kind));}
    public static <T extends ParticleOptions> int particles(ServerLevel city,T particle,double x,double y,double z,int count,double dx,double dy,double dz,double speed){
        int sent=0;boolean flash=particle.getType()==ParticleTypes.FLASH||particle.getType()==ParticleTypes.EXPLOSION||particle.getType()==ParticleTypes.EXPLOSION_EMITTER||particle.getType()==ParticleTypes.FIREWORK;
        for(var p:city.players()){if(p.distanceToSqr(x,y,z)>32*32)continue;int allowed=particleCount(flags(p),count,flash);if((flags(p)&PARTICLES)!=0||flash&&(flags(p)&FLASHES)!=0){cue(p,flash?"flash":particle.getType()==ParticleTypes.SMOKE||particle.getType()==ParticleTypes.CAMPFIRE_COSY_SMOKE?"smoke":"activity");continue;}if(city.sendParticles(p,particle,false,false,x,y,z,allowed,dx,dy,dz,speed))sent++;}return sent;
    }
    public static <T extends ParticleOptions> boolean particles(ServerLevel city,ServerPlayer p,T particle,boolean force,boolean important,double x,double y,double z,int count,double dx,double dy,double dz,double speed){
        boolean flash=particle.getType()==ParticleTypes.FLASH||particle.getType()==ParticleTypes.EXPLOSION||particle.getType()==ParticleTypes.EXPLOSION_EMITTER||particle.getType()==ParticleTypes.FIREWORK;
        if((flags(p)&PARTICLES)!=0||flash&&(flags(p)&FLASHES)!=0){cue(p,"activity");return false;}return city.sendParticles(p,particle,force,important,x,y,z,particleCount(flags(p),count,flash),dx,dy,dz,speed);
    }
    public static void sound(Level level,Player excluded,BlockPos pos,SoundEvent sound,SoundSource source,float volume,float pitch){sound(level,excluded,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,sound,source,volume,pitch);}
    public static void sound(Level level,Player excluded,double x,double y,double z,SoundEvent sound,SoundSource source,float volume,float pitch){
        if(!(level instanceof ServerLevel city)){level.playSound(excluded,x,y,z,sound,source,volume,pitch);return;}
        double radius=16*Math.max(1,volume);for(var p:city.players())if(p!=excluded&&p.distanceToSqr(x,y,z)<=radius*radius){
            if((flags(p)&QUIET)!=0){cue(p,sound==SoundEvents.FIREWORK_ROCKET_BLAST?"warning":source==SoundSource.HOSTILE||sound==Napuli.URLO?"danger":sound==SoundEvents.NOTE_BLOCK_BELL.value()&&source==SoundSource.BLOCKS?"bell":source==SoundSource.BLOCKS?"appliance":"ambience");continue;}
            p.connection.send(new ClientboundSoundPacket(Holder.direct(sound),source,x,y,z,volume,pitch,java.util.concurrent.ThreadLocalRandom.current().nextLong()));
        }
    }
    static void register(){PayloadTypeRegistry.serverboundPlay().register(Preferences.TYPE,Preferences.CODEC);ServerPlayNetworking.registerGlobalReceiver(Preferences.TYPE,(packet,context)->context.player().setAttached(FLAGS,packet.flags()&31));}
    private CityEffects(){}
}
