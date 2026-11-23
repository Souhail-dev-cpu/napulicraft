package dev.napuli;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TelevisionProgram(String channel,String opponent,int napoli,int away,int minute,long clock,int duration) implements CustomPacketPayload {
    public static final Type<TelevisionProgram> TYPE=new Type<>(Napuli.id("television_program"));
    static final StreamCodec<FriendlyByteBuf,TelevisionProgram> CODEC=StreamCodec.of((b,p)->{
        b.writeUtf(p.channel());b.writeUtf(p.opponent());b.writeVarInt(p.napoli());b.writeVarInt(p.away());b.writeVarInt(p.minute());b.writeLong(p.clock());b.writeVarInt(p.duration());
    },b->new TelevisionProgram(b.readUtf(32),b.readUtf(128),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readLong(),b.readVarInt()));
    @Override public Type<TelevisionProgram> type(){return TYPE;}
}
