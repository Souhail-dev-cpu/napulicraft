package dev.napuli;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Component payload keeps navigation and activity text localized on dedicated multiplayer clients. */
public record CompanionHud(String objective,Component text) implements CustomPacketPayload {
    public static final Type<CompanionHud> TYPE=new Type<>(Napuli.id("companion_hud"));
    static final StreamCodec<RegistryFriendlyByteBuf,CompanionHud> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.objective());ComponentSerialization.STREAM_CODEC.encode(b,p.text());},b->new CompanionHud(b.readUtf(),ComponentSerialization.STREAM_CODEC.decode(b)));
    @Override public Type<CompanionHud> type(){return TYPE;}
}
