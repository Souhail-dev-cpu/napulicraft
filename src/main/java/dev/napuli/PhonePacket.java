package dev.napuli;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded phone protocol; only server snapshots contain identities, messages and call state. */
public record PhonePacket(UUID token, int model, int charge, String number, boolean airplane, boolean open,
                          List<Contact> contacts, List<Text> messages, List<Place> places, Call call,
                          String goal, BlockPos waypoint, Component instruction, Component status) implements CustomPacketPayload {
    public static final UUID NIL = new UUID(0,0);
    public record Contact(UUID id, String name, String number, boolean online) {}
    public record Text(UUID peer, String name, String body, long time, boolean outgoing) {}
    public record Place(String id, Component name, BlockPos position) {}
    public record Call(UUID id, UUID peer, String name, boolean incoming, boolean active, int seconds) {}
    public static final Type<PhonePacket> TYPE = new Type<>(Napuli.id("phone_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhonePacket> CODEC = StreamCodec.of((b,p)->{
        b.writeUUID(p.token); b.writeVarInt(p.model); b.writeVarInt(p.charge); b.writeUtf(p.number,16);
        b.writeBoolean(p.airplane); b.writeBoolean(p.open);
        count(p.contacts.size(),64); b.writeVarInt(p.contacts.size());
        for(var c:p.contacts){b.writeUUID(c.id);b.writeUtf(c.name,64);b.writeUtf(c.number,16);b.writeBoolean(c.online);}
        count(p.messages.size(),64); b.writeVarInt(p.messages.size());
        for(var m:p.messages){b.writeUUID(m.peer);b.writeUtf(m.name,64);b.writeUtf(m.body,256);b.writeLong(m.time);b.writeBoolean(m.outgoing);}
        count(p.places.size(),128); b.writeVarInt(p.places.size());
        for(var place:p.places){b.writeUtf(place.id,128);ComponentSerialization.STREAM_CODEC.encode(b,place.name);b.writeBlockPos(place.position);}
        b.writeBoolean(p.call!=null);if(p.call!=null){var c=p.call;b.writeUUID(c.id);b.writeUUID(c.peer);b.writeUtf(c.name,64);b.writeBoolean(c.incoming);b.writeBoolean(c.active);b.writeVarInt(c.seconds);}
        b.writeUtf(p.goal,128);b.writeBoolean(p.waypoint!=null);if(p.waypoint!=null)b.writeBlockPos(p.waypoint);
        ComponentSerialization.STREAM_CODEC.encode(b,p.instruction);ComponentSerialization.STREAM_CODEC.encode(b,p.status);
    },b->{
        UUID token=b.readUUID();int model=b.readVarInt(),charge=b.readVarInt();
        if(model<0||model>=PhoneModel.values().length||charge<0||charge>100)throw new IllegalArgumentException("Invalid phone model or charge");
        String number=b.readUtf(16);boolean airplane=b.readBoolean(),open=b.readBoolean();
        var contacts=new ArrayList<Contact>();for(int n=count(b.readVarInt(),64);n>0;n--)contacts.add(new Contact(b.readUUID(),b.readUtf(64),b.readUtf(16),b.readBoolean()));
        var messages=new ArrayList<Text>();for(int n=count(b.readVarInt(),64);n>0;n--)messages.add(new Text(b.readUUID(),b.readUtf(64),b.readUtf(256),b.readLong(),b.readBoolean()));
        var places=new ArrayList<Place>();for(int n=count(b.readVarInt(),128);n>0;n--)places.add(new Place(b.readUtf(128),ComponentSerialization.STREAM_CODEC.decode(b),b.readBlockPos()));
        Call call=b.readBoolean()?new Call(b.readUUID(),b.readUUID(),b.readUtf(64),b.readBoolean(),b.readBoolean(),b.readVarInt()):null;
        String goal=b.readUtf(128);BlockPos waypoint=b.readBoolean()?b.readBlockPos():null;
        return new PhonePacket(token,model,charge,number,airplane,open,List.copyOf(contacts),List.copyOf(messages),List.copyOf(places),call,goal,waypoint,ComponentSerialization.STREAM_CODEC.decode(b),ComponentSerialization.STREAM_CODEC.decode(b));
    });
    private static int count(int value,int limit){if(value<0||value>limit)throw new IllegalArgumentException("Phone collection exceeds bound");return value;}
    @Override public Type<PhonePacket> type(){return TYPE;}

    public record Action(UUID token,String operation,String target,String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(Napuli.id("phone_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeUtf(p.operation,24);b.writeUtf(p.target,128);b.writeUtf(p.text,256);},b->new Action(b.readUUID(),b.readUtf(24),b.readUtf(128),b.readUtf(256)));
        @Override public Type<Action> type(){return TYPE;}
    }
    public record Voice(UUID call,byte[] pcm) implements CustomPacketPayload {
        public static final int FRAME_BYTES=1280;
        public static final Type<Voice> TYPE=new Type<>(Napuli.id("phone_voice"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Voice> CODEC=StreamCodec.of((b,p)->{
            if(p.pcm.length!=FRAME_BYTES)throw new IllegalArgumentException("Invalid voice frame");b.writeUUID(p.call);b.writeByteArray(p.pcm);
        },b->{UUID id=b.readUUID();byte[] pcm=b.readByteArray(FRAME_BYTES);if(pcm.length!=FRAME_BYTES)throw new IllegalArgumentException("Invalid voice frame");return new Voice(id,pcm);});
        @Override public Type<Voice> type(){return TYPE;}
    }
}
