package dev.napuli;

import static dev.napuli.CityText.t;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;

/** Server-owned SIM accounts and private mail. A stolen handset never transfers its owner's conversations. */
public final class Phones {
    public static final Map<PhoneModel,Item> ITEMS=new EnumMap<>(PhoneModel.class);
    public static Item CHARGER;
    static final int MAIL_LIMIT=64, RING_TICKS=600;
    record Subscriber(String name,String number){
        static final Codec<Subscriber> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("name").forGetter(Subscriber::name),Codec.STRING.fieldOf("number").forGetter(Subscriber::number)).apply(i,Subscriber::new));
    }
    record Mail(UUID peer,String name,String body,long time,boolean outgoing,boolean read){
        Mail(UUID peer,String name,String body,long time,boolean outgoing){this(peer,name,body,time,outgoing,true);}
        static final Codec<Mail> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("peer").forGetter(Mail::peer),Codec.STRING.fieldOf("name").forGetter(Mail::name),Codec.STRING.fieldOf("body").forGetter(Mail::body),Codec.LONG.fieldOf("time").forGetter(Mail::time),Codec.BOOL.fieldOf("outgoing").forGetter(Mail::outgoing),Codec.BOOL.optionalFieldOf("read",true).forGetter(Mail::read)).apply(i,Mail::new));
    }
    record Session(UUID token,PhoneModel model,boolean watching){}
    record Call(UUID id,UUID caller,UUID recipient,long started,boolean active){UUID other(UUID owner){return owner.equals(caller)?recipient:caller;}}
    record Rate(long window,int count){}
    static final AttachmentType<Map<String,Subscriber>> BOOK=AttachmentRegistry.<Map<String,Subscriber>>builder().persistent(Codec.unboundedMap(Codec.STRING,Subscriber.CODEC)).buildAndRegister(Napuli.id("phone_directory"));
    static final AttachmentType<Map<String,List<Mail>>> MAIL=AttachmentRegistry.<Map<String,List<Mail>>>builder().persistent(Codec.unboundedMap(Codec.STRING,Mail.CODEC.listOf())).buildAndRegister(Napuli.id("phone_mail"));
    static final AttachmentType<Boolean> AIRPLANE=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id("phone_airplane"));
    static final AttachmentType<Session> SESSION=AttachmentRegistry.create(Napuli.id("phone_session"));
    private static final Map<UUID,Call> CALLS=new HashMap<>();
    private static final Map<UUID,Rate> REQUESTS=new HashMap<>(), VOICE=new HashMap<>();

    public static PhoneModel model(ItemStack stack){for(var entry:ITEMS.entrySet())if(stack.is(entry.getValue()))return entry.getKey();return null;}
    public static int charge(ItemStack stack){var model=model(stack);return model==null?0:Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getIntOr("phone_charge",model.battery),0,model.battery);}
    static void charge(ItemStack stack,int value){var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.putInt("phone_charge",Math.clamp(value,0,model(stack).battery));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));}
    static ItemStack find(ServerPlayer p,PhoneModel preferred){
        ItemStack fallback=ItemStack.EMPTY;
        var held=p.getMainHandItem();if(model(held)!=null&&(model(held)==preferred||preferred==null&&charge(held)>0))return held;
        for(int slot=0;slot<36;slot++){var stack=p.getInventory().getItem(slot);if(model(stack)!=null){if(model(stack)==preferred)return stack;if(fallback.isEmpty()||charge(fallback)==0&&charge(stack)>0)fallback=stack;}}
        var off=p.getOffhandItem();if(model(off)!=null&&(fallback.isEmpty()||model(off)==preferred||preferred==null&&charge(fallback)==0&&charge(off)>0))return off;
        return fallback;
    }
    static boolean available(ServerPlayer p){return p!=null&&p.isAlive()&&!p.isSpectator()&&!p.getAttachedOrElse(AIRPLANE,false)&&charge(find(p,p.getAttached(SESSION)==null?null:p.getAttached(SESSION).model))>0;}
    static Map<String,Subscriber> book(MinecraftServer server){return server.overworld().getAttachedOrElse(BOOK,Map.of());}
    static Subscriber account(ServerPlayer p){
        var directory=new HashMap<>(book(p.level().getServer()));String key=p.getUUID().toString();var old=directory.get(key);
        if(old==null||!old.number.startsWith("+39")||PhoneNumbers.normalize(old.number).isEmpty()){var used=new HashSet<String>();directory.values().forEach(s->used.add(s.number));old=new Subscriber(p.getGameProfile().name(),PhoneNumbers.allocate(used));}
        var current=new Subscriber(p.getGameProfile().name(),old.number);if(!current.equals(directory.get(key))){directory.put(key,current);p.level().getServer().overworld().setAttached(BOOK,Map.copyOf(directory));}return current;
    }
    private static void migrateNumbers(MinecraftServer server){
        var directory=new HashMap<>(book(server));var used=new HashSet<String>();directory.values().forEach(s->used.add(s.number));
        directory.replaceAll((id,old)->{if(old.number.startsWith("+39")&&!PhoneNumbers.normalize(old.number).isEmpty())return old;String number=PhoneNumbers.allocate(used);used.add(number);return new Subscriber(old.name,number);});
        server.overworld().setAttached(BOOK,Map.copyOf(directory));
    }
    static List<Mail> mail(ServerPlayer p){return p.level().getServer().overworld().getAttachedOrElse(MAIL,Map.of()).getOrDefault(p.getUUID().toString(),List.of());}
    private static void append(MinecraftServer server,UUID owner,Mail entry){
        var all=new HashMap<>(server.overworld().getAttachedOrElse(MAIL,Map.of()));var entries=new ArrayList<>(all.getOrDefault(owner.toString(),List.of()));
        entries.add(entry);while(entries.size()>MAIL_LIMIT)entries.removeFirst();all.put(owner.toString(),List.copyOf(entries));server.overworld().setAttached(MAIL,Map.copyOf(all));
    }
    static void open(ServerPlayer p,PhoneModel preferred){
        var stack=find(p,preferred);if(stack.isEmpty()||!p.isAlive()||p.isSpectator())return;
        var session=new Session(UUID.randomUUID(),model(stack),true);p.setAttached(SESSION,session);account(p);send(p,true,Component.empty());
    }
    private static boolean limited(Map<UUID,Rate> limits,ServerPlayer p,int max){
        long now=p.level().getServer().overworld().getGameTime()/20;var before=limits.get(p.getUUID());
        int count=before!=null&&before.window==now?before.count+1:1;limits.put(p.getUUID(),new Rate(now,count));return count>max;
    }
    static UUID resolve(ServerPlayer p,String target){
        try{var id=UUID.fromString(target);var call=CALLS.get(p.getUUID());return book(p.level().getServer()).containsKey(id.toString())&&(PhoneBook.known(p,id)||call!=null&&call.other(p.getUUID()).equals(id))?id:null;}catch(IllegalArgumentException ignored){}
        String number=PhoneNumbers.normalize(target);
        return book(p.level().getServer()).entrySet().stream().filter(e->e.getValue().number.equals(number)||PhoneBook.saved(p).contains(UUID.fromString(e.getKey()))&&e.getValue().name.equalsIgnoreCase(target)).map(e->UUID.fromString(e.getKey())).findFirst().orElse(null);
    }
    static void action(ServerPlayer p,PhonePacket.Action request){
        if(limited(REQUESTS,p,12))return;
        if(request.operation().equals("open")){open(p,null);return;}
        var session=p.getAttached(SESSION);if(session==null||!session.token.equals(request.token()))return;
        if(request.operation().equals("close")){p.setAttached(SESSION,new Session(session.token,session.model,false));return;}
        var stack=find(p,session.model);
        if(stack.isEmpty()||!p.isAlive()||p.isSpectator()){p.removeAttached(SESSION);end(p,t("phone.unavailable"));return;}
        if(request.operation().equals("refresh")){p.setAttached(SESSION,new Session(session.token,session.model,true));send(p,false,Component.empty());return;}
        if(request.operation().equals("charge")){recharge(p);send(p,false,Component.empty());return;}
        if(request.operation().equals("airplane")){p.setAttached(AIRPLANE,!p.getAttachedOrElse(AIRPLANE,false));if(p.getAttachedOrElse(AIRPLANE,false))end(p,t("phone.airplane"));send(p,false,Component.empty());return;}
        if(request.operation().equals("end")){end(p,t("phone.call_ended"));return;}
        switch(request.operation()){
            case "read" -> {read(p,request.target(),false);return;}
            case "delete_thread" -> {read(p,request.target(),true);send(p,false,t("phone.thread_deleted"));return;}
            case "add_contact" -> {PhoneBook.add(p,request.target());return;}
            case "remove_contact" -> {PhoneBook.remove(p,request.target());return;}
            case "save_note" -> {PhoneBook.note(p,request.target(),request.text());return;}
            case "delete_note" -> {PhoneBook.deleteNote(p,request.target());return;}
            default -> {}
        }
        if(!available(p)){send(p,false,t("phone.unavailable"));return;}
        switch(request.operation()){
            case "message" -> message(p,resolve(p,request.target()),request.text());
            case "call" -> dial(p,resolve(p,request.target()));
            case "answer" -> answer(p,request.target());
            case "share" -> {try{PhoneBook.share(p,UUID.fromString(request.target()));}catch(IllegalArgumentException ignored){}}
            case "accept_contact" -> PhoneBook.accept(p,request.target(),true);
            case "decline_contact" -> PhoneBook.accept(p,request.target(),false);
            case "share_position" -> message(p,resolve(p,request.target()),t("phone.position_message",p.blockPosition().toShortString(),p.level().dimension().identifier().toString()).getString());
            case "navigate" -> {
                if(CityCompanion.follow(p,request.target())==1)send(p,false,t("phone.route_started"));
            }
            case "stop_gps" -> {p.removeAttached(CityCompanion.GOAL);p.setAttached(CityCompanion.TRACK,"nessuno");send(p,false,t("phone.route_stopped"));}
            default -> {}
        }
    }
    static String clean(String text){return text.codePoints().filter(c->!Character.isISOControl(c)&&c!=0x00a7).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString().strip();}
    private static void read(ServerPlayer p,String target,boolean remove){
        var all=new HashMap<>(p.level().getServer().overworld().getAttachedOrElse(MAIL,Map.of()));
        var entries=mail(p).stream().filter(m->!remove||!m.peer.toString().equals(target)).map(m->!m.outgoing&&m.peer.toString().equals(target)?new Mail(m.peer,m.name,m.body,m.time,false,true):m).toList();
        all.put(p.getUUID().toString(),entries);
        if(!remove){var peerMail=all.get(target);if(peerMail!=null)all.put(target,peerMail.stream().map(m->m.outgoing&&m.peer.equals(p.getUUID())?new Mail(m.peer,m.name,m.body,m.time,true,true):m).toList());}
        p.level().getServer().overworld().setAttached(MAIL,Map.copyOf(all));send(p,false,Component.empty());
        if(!remove){try{var peer=p.level().getServer().getPlayerList().getPlayer(UUID.fromString(target));if(peer!=null)send(peer,false,Component.empty());}catch(IllegalArgumentException ignored){}}
    }
    static boolean message(ServerPlayer p,UUID target,String text){
        if(!available(p)){feedback(p,t("phone.unavailable"));return false;}
        if(target==null||target.equals(p.getUUID())||!book(p.level().getServer()).containsKey(target.toString())){feedback(p,t("phone.unknown_number"));return false;}
        String clean=clean(text);
        if(clean.isBlank()||clean.length()>256){feedback(p,t("phone.invalid_text"));return false;}
        var server=p.level().getServer();var self=account(p);var peer=book(server).get(target.toString());long now=server.overworld().getGameTime();
        append(server,p.getUUID(),new Mail(target,peer.name,clean,now,true,false));append(server,target,new Mail(p.getUUID(),self.name,clean,now,false,false));
        var session=p.getAttached(SESSION);var handset=find(p,session==null?null:session.model);charge(handset,charge(handset)-1);send(p,false,t("phone.message_sent"));
        var receiver=server.getPlayerList().getPlayer(target);if(available(receiver)){NapoliNotice.show(receiver,t("phone.new_message",self.name));send(receiver,false,t("phone.new_message",self.name));}
        return true;
    }
    static boolean dial(ServerPlayer p,UUID target){
        var server=p.level().getServer();var receiver=target==null?null:server.getPlayerList().getPlayer(target);
        if(target==null){send(p,false,t("phone.unknown_number"));return false;}
        if(target==null||target.equals(p.getUUID())||!available(p)||!available(receiver)||CALLS.containsKey(p.getUUID())||CALLS.containsKey(target)){send(p,false,t("phone.busy"));return false;}
        account(p);account(receiver);var call=new Call(UUID.randomUUID(),p.getUUID(),target,server.overworld().getGameTime(),false);
        CALLS.put(p.getUUID(),call);CALLS.put(target,call);send(p,false,Component.empty());send(receiver,false,t("phone.incoming",p.getGameProfile().name()));
        NapoliNotice.show(receiver,t("phone.incoming",p.getGameProfile().name()));return true;
    }
    static boolean answer(ServerPlayer p,String id){
        var call=CALLS.get(p.getUUID());if(call==null||call.active||!call.recipient.equals(p.getUUID())||!call.id.toString().equals(id)||!available(p))return false;
        var other=p.level().getServer().getPlayerList().getPlayer(call.caller);if(!available(other)){end(p,t("phone.call_ended"));return false;}
        var active=new Call(call.id,call.caller,call.recipient,p.level().getServer().overworld().getGameTime(),true);CALLS.put(call.caller,active);CALLS.put(call.recipient,active);
        send(p,false,Component.empty());send(other,false,Component.empty());return true;
    }
    static void end(ServerPlayer p,Component reason){
        var call=CALLS.get(p.getUUID());end(p,reason,call!=null&&call.active?"answered":call!=null&&call.caller.equals(p.getUUID())?"cancelled":"declined");
    }
    private static void end(ServerPlayer p,Component reason,String outcome){
        var call=CALLS.remove(p.getUUID());if(call==null)return;CALLS.remove(call.other(p.getUUID()));VOICE.remove(call.caller);VOICE.remove(call.recipient);
        PhoneBook.record(p.level().getServer(),call,outcome);
        var other=p.level().getServer().getPlayerList().getPlayer(call.other(p.getUUID()));send(p,false,reason);if(other!=null)send(other,false,reason);
    }
    static ServerPlayer voiceTarget(ServerPlayer p,PhonePacket.Voice frame){
        var call=CALLS.get(p.getUUID());if(call==null||!call.active||!call.id.equals(frame.call())||frame.pcm().length!=PhonePacket.Voice.FRAME_BYTES||!available(p)||limited(VOICE,p,30))return null;
        var target=p.level().getServer().getPlayerList().getPlayer(call.other(p.getUUID()));return available(target)?target:null;
    }
    static boolean recharge(ServerPlayer p){
        var session=p.getAttached(SESSION);var stack=find(p,session==null?null:session.model);if(stack.isEmpty())return false;int before=charge(stack),capacity=model(stack).battery;
        if(before>=capacity)return false;
        boolean charger=p.getOffhandItem().is(CHARGER);for(int i=0;i<36&&!charger;i++)charger=p.getInventory().getItem(i).is(CHARGER);
        if(!charger){NapoliNotice.show(p,t("phone.need_charger"));return false;}
        if(!p.isCreative()){
            int slot=-1;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(Items.REDSTONE)){slot=i;break;}
            if(slot<0){NapoliNotice.show(p,t("phone.need_redstone"));return false;}p.getInventory().getItem(slot).shrink(1);
        }
        charge(stack,Math.min(capacity,before+2000));NapoliNotice.show(p,t("phone.charged"));return true;
    }
    static PhonePacket snapshot(ServerPlayer p,boolean open,Component status){
        var stack=find(p,p.getAttached(SESSION)==null?null:p.getAttached(SESSION).model);var model=model(stack);if(model==null)return null;
        var session=p.getAttached(SESSION);if(session==null){session=new Session(UUID.randomUUID(),model,false);p.setAttached(SESSION,session);}
        var self=account(p);var server=p.level().getServer();
        var saved=PhoneBook.saved(p);var known=new HashSet<>(saved);mail(p).forEach(m->known.add(m.peer));PhoneBook.history(p).forEach(h->known.add(h.peer()));var currentCall=CALLS.get(p.getUUID());if(currentCall!=null)known.add(currentCall.other(p.getUUID()));
        var contacts=book(server).entrySet().stream().filter(e->known.contains(UUID.fromString(e.getKey()))).map(e->{var id=UUID.fromString(e.getKey());var player=server.getPlayerList().getPlayer(id);return new PhonePacket.Contact(id,e.getValue().name,e.getValue().number,available(player),saved.contains(id));})
                .sorted(Comparator.comparing(PhonePacket.Contact::saved,Comparator.reverseOrder()).thenComparing(PhonePacket.Contact::name,String.CASE_INSENSITIVE_ORDER)).limit(128).toList();
        var messages=mail(p).stream().map(m->new PhonePacket.Text(m.peer,m.name,m.body,m.time,m.outgoing,m.read)).toList();
        var places=CityCompanion.destinations(p).stream().filter(d->!d.id().startsWith("banchina/")).limit(128).map(d->new PhonePacket.Place(d.id(),d.name(),d.position())).toList();
        var goal=CityCompanion.inCity(p)?CityCompanion.goal(p):null;
        var instruction=goal==null?null:PlayerNavigator.guide(p,goal.position());
        var call=CALLS.get(p.getUUID());PhonePacket.Call display=null;
        if(call!=null){var other=book(server).get(call.other(p.getUUID()).toString());display=new PhonePacket.Call(call.id,call.other(p.getUUID()),other==null?"?":other.name,call.recipient.equals(p.getUUID()),call.active,(int)Math.max(0,(server.overworld().getGameTime()-call.started)/20));}
        return new PhonePacket(session.token,model.ordinal(),(int)Math.ceil(charge(stack)*100./model.battery),self.number,p.getAttachedOrElse(AIRPLANE,false),open,contacts,messages,places,display,goal==null?"":goal.label().getString(),instruction==null?null:instruction.target(),instruction==null?Component.empty():instruction.text(),status,PhoneBook.snapshot(p));
    }
    static void feedback(ServerPlayer p,Component status){var session=p.getAttached(SESSION);if(session==null||!session.watching)NapoliNotice.show(p,status);send(p,false,status);}
    private static void refreshAll(MinecraftServer server){for(var p:server.getPlayerList().getPlayers())if(p.getAttached(SESSION)!=null)send(p,false,Component.empty());}
    private static void send(ServerPlayer p,boolean open,Component status){
        if(p==null||!ServerPlayNetworking.canSend(p,PhonePacket.TYPE))return;var state=snapshot(p,open,status);if(state!=null)ServerPlayNetworking.send(p,state);
    }
    static void tick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        PhoneBook.expire(server);
        for(var p:server.getPlayerList().getPlayers()){
            var call=CALLS.get(p.getUUID());var session=p.getAttached(SESSION);var stack=find(p,session==null?null:session.model);
            if(call!=null&&(!available(p)||!available(server.getPlayerList().getPlayer(call.other(p.getUUID())))))end(p,t("phone.call_ended"));
            else if(call!=null&&!call.active&&now-call.started>=RING_TICKS)end(p,t("phone.call_missed"),"missed");
            if(!stack.isEmpty()&&now%1200==0&&!p.isCreative())charge(stack,charge(stack)-(call!=null?80:session!=null&&session.watching?30:1));
            if(session!=null&&(session.watching||call!=null))send(p,false,Component.empty());
        }
    }
    static void register(){
        PhoneBook.register();
        for(var m:PhoneModel.values()){
            var key=ResourceKey.create(Registries.ITEM,Napuli.id(m.id));
            var item=Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key).stacksTo(1).component(DataComponents.LORE,new ItemLore(List.of(Component.literal(m.os),Component.translatable("napuli.ui.phone.item_help"))))) {
                @Override public InteractionResult use(Level level,Player player,InteractionHand hand){if(player instanceof ServerPlayer p)open(p,m);return InteractionResult.SUCCESS;}
                @Override public boolean isBarVisible(ItemStack stack){return charge(stack)<m.battery;}
                @Override public int getBarWidth(ItemStack stack){return Math.round(13f*charge(stack)/m.battery);}
                @Override public int getBarColor(ItemStack stack){return charge(stack)<m.battery/5?0xe76362:0x54cda0;}
            });ITEMS.put(m,item);
        }
        var key=ResourceKey.create(Registries.ITEM,Napuli.id("caricatore_usb_c"));
        CHARGER=Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key).stacksTo(1)){
            @Override public InteractionResult use(Level level,Player player,InteractionHand hand){if(player instanceof ServerPlayer p)recharge(p);return InteractionResult.SUCCESS;}
        });
        PayloadTypeRegistry.clientboundPlay().register(PhonePacket.TYPE,PhonePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhonePacket.Action.TYPE,PhonePacket.Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PhonePacket.Voice.TYPE,PhonePacket.Voice.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhonePacket.Voice.TYPE,PhonePacket.Voice.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PhonePacket.Action.TYPE,(r,c)->action(c.player(),r));
        ServerPlayNetworking.registerGlobalReceiver(PhonePacket.Voice.TYPE,(r,c)->{var peer=voiceTarget(c.player(),r);if(peer!=null&&ServerPlayNetworking.canSend(peer,PhonePacket.Voice.TYPE))ServerPlayNetworking.send(peer,r);});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%20==0)tick(s);});
        ServerPlayConnectionEvents.JOIN.register((h,sender,s)->account(h.player));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{end(h.player,t("phone.call_ended"));h.player.removeAttached(SESSION);REQUESTS.remove(h.player.getUUID());VOICE.remove(h.player.getUUID());PhoneBook.disconnect(h.player.getUUID());});
        ServerLifecycleEvents.SERVER_STARTED.register(Phones::migrateNumbers);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{CALLS.clear();REQUESTS.clear();VOICE.clear();PhoneBook.clear();});
        CommandRegistrationCallback.EVENT.register((d,r,e)->{
            var root=Commands.literal("telefono").executes(c->{open(c.getSource().getPlayerOrException(),null);return 1;});
            root.then(Commands.literal("mio").executes(c->{var p=c.getSource().getPlayerOrException();p.sendSystemMessage(t("phone.my_number",PhoneNumbers.format(account(p).number)));return 1;}));
            root.then(Commands.literal("scambia").then(Commands.argument("giocatore",EntityArgument.player()).executes(c->PhoneBook.share(c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"giocatore").getUUID())?1:0)));
            root.then(Commands.literal("accetta").then(Commands.argument("giocatore",EntityArgument.player()).executes(c->PhoneBook.accept(c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"giocatore").getUUID().toString(),true)?1:0)));
            root.then(Commands.literal("numero").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.argument("giocatore",EntityArgument.player()).then(Commands.argument("numero",StringArgumentType.greedyString()).executes(c->{
                var p=EntityArgument.getPlayer(c,"giocatore");String number=PhoneNumbers.normalize(StringArgumentType.getString(c,"numero"));
                if(number.isEmpty()){c.getSource().sendFailure(t("phone.invalid_number"));return 0;}
                var directory=new HashMap<>(book(c.getSource().getServer()));
                if(directory.entrySet().stream().anyMatch(entry->!entry.getKey().equals(p.getUUID().toString())&&entry.getValue().number.equals(number))){c.getSource().sendFailure(t("phone.number_taken"));return 0;}
                directory.put(p.getUUID().toString(),new Subscriber(p.getGameProfile().name(),number));c.getSource().getServer().overworld().setAttached(BOOK,Map.copyOf(directory));
                c.getSource().sendSuccess(()->t("phone.number_changed",p.getGameProfile().name(),PhoneNumbers.format(number)),true);feedback(p,t("phone.my_number",PhoneNumbers.format(number)));refreshAll(c.getSource().getServer());return 1;
            }))));
            d.register(root);
        });
    }
    private Phones(){}
}
