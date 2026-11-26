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
                          String goal, BlockPos waypoint, Component instruction, Component status, Personal personal) implements CustomPacketPayload {
    public static final UUID NIL = new UUID(0,0);
    public record Contact(UUID id, String name, String number, boolean online, boolean saved) {
        public Contact(UUID id,String name,String number,boolean online){this(id,name,number,online,true);}
    }
    public record Text(UUID peer, String name, String body, long time, boolean outgoing, boolean read) {
        public Text(UUID peer,String name,String body,long time,boolean outgoing){this(peer,name,body,time,outgoing,true);}
    }
    public record Place(String id, Component name, BlockPos position) {}
    public record Call(UUID id, UUID peer, String name, boolean incoming, boolean active, int seconds) {}
    public record Offer(UUID token, UUID sender, String name, String number, int seconds) {}
    public record Recent(UUID peer, String name, String number, long time, boolean incoming, String outcome, int seconds) {}
    public record Note(UUID id, String body, long time) {}
    public record Personal(List<Contact> nearby,List<Offer> offers,List<Recent> history,List<Note> notes) {
        public static final Personal EMPTY=new Personal(List.of(),List.of(),List.of(),List.of());
    }
    public PhonePacket(UUID token,int model,int charge,String number,boolean airplane,boolean open,List<Contact> contacts,List<Text> messages,List<Place> places,Call call,String goal,BlockPos waypoint,Component instruction,Component status){
        this(token,model,charge,number,airplane,open,contacts,messages,places,call,goal,waypoint,instruction,status,Personal.EMPTY);
    }
    public static final Type<PhonePacket> TYPE = new Type<>(Napuli.id("phone_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhonePacket> CODEC = StreamCodec.of((b,p)->{
        b.writeUUID(p.token); b.writeVarInt(p.model); b.writeVarInt(p.charge); b.writeUtf(p.number,16);
        b.writeBoolean(p.airplane); b.writeBoolean(p.open);
        count(p.contacts.size(),128); b.writeVarInt(p.contacts.size());
        for(var c:p.contacts){b.writeUUID(c.id);b.writeUtf(c.name,64);b.writeUtf(c.number,16);b.writeBoolean(c.online);b.writeBoolean(c.saved);}
        count(p.messages.size(),64); b.writeVarInt(p.messages.size());
        for(var m:p.messages){b.writeUUID(m.peer);b.writeUtf(m.name,64);b.writeUtf(m.body,256);b.writeLong(m.time);b.writeBoolean(m.outgoing);b.writeBoolean(m.read);}
        count(p.places.size(),128); b.writeVarInt(p.places.size());
        for(var place:p.places){b.writeUtf(place.id,128);ComponentSerialization.STREAM_CODEC.encode(b,place.name);b.writeBlockPos(place.position);}
        b.writeBoolean(p.call!=null);if(p.call!=null){var c=p.call;b.writeUUID(c.id);b.writeUUID(c.peer);b.writeUtf(c.name,64);b.writeBoolean(c.incoming);b.writeBoolean(c.active);b.writeVarInt(c.seconds);}
        b.writeUtf(p.goal,128);b.writeBoolean(p.waypoint!=null);if(p.waypoint!=null)b.writeBlockPos(p.waypoint);
        ComponentSerialization.STREAM_CODEC.encode(b,p.instruction);ComponentSerialization.STREAM_CODEC.encode(b,p.status);
        var extra=p.personal;count(extra.nearby.size(),32);b.writeVarInt(extra.nearby.size());
        for(var c:extra.nearby){b.writeUUID(c.id);b.writeUtf(c.name,64);b.writeBoolean(c.online);}
        count(extra.offers.size(),8);b.writeVarInt(extra.offers.size());
        for(var o:extra.offers){b.writeUUID(o.token);b.writeUUID(o.sender);b.writeUtf(o.name,64);b.writeUtf(o.number,16);b.writeVarInt(o.seconds);}
        count(extra.history.size(),32);b.writeVarInt(extra.history.size());
        for(var h:extra.history){b.writeUUID(h.peer);b.writeUtf(h.name,64);b.writeUtf(h.number,16);b.writeLong(h.time);b.writeBoolean(h.incoming);b.writeUtf(h.outcome,16);b.writeVarInt(h.seconds);}
        count(extra.notes.size(),16);b.writeVarInt(extra.notes.size());
        for(var n:extra.notes){b.writeUUID(n.id);b.writeUtf(n.body,256);b.writeLong(n.time);}
    },b->{
        UUID token=b.readUUID();int model=b.readVarInt(),charge=b.readVarInt();
        if(model<0||model>=PhoneModel.values().length||charge<0||charge>100)throw new IllegalArgumentException("Invalid phone model or charge");
        String number=b.readUtf(16);boolean airplane=b.readBoolean(),open=b.readBoolean();
        var contacts=new ArrayList<Contact>();for(int n=count(b.readVarInt(),128);n>0;n--)contacts.add(new Contact(b.readUUID(),b.readUtf(64),b.readUtf(16),b.readBoolean(),b.readBoolean()));
        var messages=new ArrayList<Text>();for(int n=count(b.readVarInt(),64);n>0;n--)messages.add(new Text(b.readUUID(),b.readUtf(64),b.readUtf(256),b.readLong(),b.readBoolean(),b.readBoolean()));
        var places=new ArrayList<Place>();for(int n=count(b.readVarInt(),128);n>0;n--)places.add(new Place(b.readUtf(128),ComponentSerialization.STREAM_CODEC.decode(b),b.readBlockPos()));
        Call call=b.readBoolean()?new Call(b.readUUID(),b.readUUID(),b.readUtf(64),b.readBoolean(),b.readBoolean(),b.readVarInt()):null;
        String goal=b.readUtf(128);BlockPos waypoint=b.readBoolean()?b.readBlockPos():null;
        Component instruction=ComponentSerialization.STREAM_CODEC.decode(b),status=ComponentSerialization.STREAM_CODEC.decode(b);
        var nearby=new ArrayList<Contact>();for(int n=count(b.readVarInt(),32);n>0;n--)nearby.add(new Contact(b.readUUID(),b.readUtf(64),"",b.readBoolean(),false));
        var offers=new ArrayList<Offer>();for(int n=count(b.readVarInt(),8);n>0;n--)offers.add(new Offer(b.readUUID(),b.readUUID(),b.readUtf(64),b.readUtf(16),b.readVarInt()));
        var history=new ArrayList<Recent>();for(int n=count(b.readVarInt(),32);n>0;n--)history.add(new Recent(b.readUUID(),b.readUtf(64),b.readUtf(16),b.readLong(),b.readBoolean(),b.readUtf(16),b.readVarInt()));
        var notes=new ArrayList<Note>();for(int n=count(b.readVarInt(),16);n>0;n--)notes.add(new Note(b.readUUID(),b.readUtf(256),b.readLong()));
        return new PhonePacket(token,model,charge,number,airplane,open,List.copyOf(contacts),List.copyOf(messages),List.copyOf(places),call,goal,waypoint,instruction,status,new Personal(List.copyOf(nearby),List.copyOf(offers),List.copyOf(history),List.copyOf(notes)));
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
