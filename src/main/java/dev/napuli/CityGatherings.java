package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Items;

/** Scheduled invitations, responses and shared food are persistent and server-owned. */
final class CityGatherings {
    record Guest(String name,String status,boolean gift,boolean eaten) {
        static final Codec<Guest> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("name").forGetter(Guest::name),Codec.STRING.fieldOf("status").forGetter(Guest::status),Codec.BOOL.fieldOf("gift").forGetter(Guest::gift),Codec.BOOL.fieldOf("eaten").forGetter(Guest::eaten)).apply(i,Guest::new));
    }
    record Party(String home,UUID host,long starts,long ends,int portions,boolean closed,Map<String,Guest> guests) {
        static final Codec<Party> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("home").forGetter(Party::home),UUIDUtil.CODEC.fieldOf("host").forGetter(Party::host),
                Codec.LONG.fieldOf("starts").forGetter(Party::starts),Codec.LONG.fieldOf("ends").forGetter(Party::ends),Codec.INT.fieldOf("portions").forGetter(Party::portions),Codec.BOOL.fieldOf("closed").forGetter(Party::closed),
                Codec.unboundedMap(Codec.STRING,Guest.CODEC).fieldOf("guests").forGetter(Party::guests)).apply(i,Party::new));
        String scope(){return "dinner:"+starts;}
        Party with(int food,boolean ended,Map<String,Guest> people){return new Party(home,host,starts,ends,food,ended,Map.copyOf(people));}
    }
    static final AttachmentType<Map<String,Party>> PARTIES=AttachmentRegistry.<Map<String,Party>>builder().persistent(Codec.unboundedMap(Codec.STRING,Party.CODEC)).buildAndRegister(Napuli.id("dinner_parties"));
    private static boolean allowed(ServerPlayer p){return p.level().dimension()==NapoliMap.LEVEL&&p.isAlive()&&!p.isSpectator()&&!Carabinieri.isJailed(p);}
    static Party party(ServerLevel city,String home){return city.getAttachedOrElse(PARTIES,Map.of()).get(home);}
    static void put(ServerLevel city,Party party){var all=new HashMap<>(city.getAttachedOrElse(PARTIES,Map.of()));all.put(party.home(),party);city.setAttached(PARTIES,Map.copyOf(all));}
    static boolean accepted(Guest g){return g!=null&&(g.status().equals("accepted")||g.status().equals("arrived"));}
    static int create(ServerPlayer p,String home,int minutes){
        if(p.level().dimension()!=NapoliMap.LEVEL||!p.isAlive()||p.isSpectator()||!HouseGuests.host(p.level(),home,p.getUUID())||minutes<1||minutes>30)return 0;
        var old=party(p.level(),home);if(old!=null&&(!old.closed()||old.portions()>0)){p.sendOverlayMessage(Component.literal("Concludi la cena e recupera prima gli avanzi."));return 0;}
        long start=p.level().getGameTime()+minutes*1200L;
        if(p.level().getAttachedOrElse(PARTIES,Map.of()).values().stream().anyMatch(g->!g.closed()&&g.host().equals(p.getUUID())&&g.starts()<start+2400&&g.ends()>start))return 0;
        put(p.level(),new Party(home,p.getUUID(),start,start+2400,0,false,Map.of(p.getUUID().toString(),new Guest(p.getName().getString(),"accepted",false,false))));return 1;
    }
    static int invite(ServerPlayer p,String home,String reference){
        var party=party(p.level(),home);if(!allowed(p)||party==null||party.closed()||!party.host().equals(p.getUUID())||!HouseGuests.host(p.level(),home,p.getUUID())||p.level().getGameTime()>=party.ends())return 0;
        var player=p.level().getServer().getPlayerList().getPlayerByName(reference);UUID id;
        try{id=player==null?UUID.fromString(reference):player.getUUID();}catch(IllegalArgumentException invalid){return 0;}
        if(player==null)player=p.level().getServer().getPlayerList().getPlayer(id);
        var citizen=NapoliCivicLife.profile(p.level(),id);if(player==null&&citizen==null||id.equals(p.getUUID())||party.guests().containsKey(id.toString()))return 0;
        String status="pending",name=player!=null?player.getName().getString():citizen.name();
        if(player==null){
            long delta=party.starts()-p.level().getGameTime(),clock=NapoliBroadcast.time(p.level())+delta,day=NapoliBroadcast.day(p.level())+Math.floorDiv(clock,24000);
            boolean busy=citizen.profession()!=null&&CityProfessions.open(citizen.profession(),Math.floorMod(clock,24000),day);
            var invitee=id;boolean booked=p.level().getAttachedOrElse(PARTIES,Map.of()).values().stream().anyMatch(g->!g.closed()&&!g.home().equals(home)&&g.starts()<party.ends()&&g.ends()>party.starts()&&accepted(g.guests().get(invitee.toString())));
            status=citizen.age(day)>=12&&!busy&&!booked&&CitySimulation.life(p.level(),id).health()>=45&&!CityPublicOrder.jailed(p.level(),id)&&CitySocial.bond(p.level(),id,p.getUUID()).trust()>=30?"accepted":"declined";
        }
        var guests=new HashMap<>(party.guests());guests.put(id.toString(),new Guest(name,status,false,false));put(p.level(),party.with(party.portions(),false,guests));
        if(player!=null)player.sendSystemMessage(Component.literal(p.getName().getString()+" ti invita a cena. [Apri invito]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/cena menu "+home))));
        p.sendSystemMessage(Component.literal(name+": "+(status.equals("accepted")?"Ci sarò!":status.equals("declined")?"Mi dispiace, ho altri impegni.":"In attesa di risposta.")));return 1;
    }
    static int respond(ServerPlayer p,String home,boolean yes){
        var party=party(p.level(),home);var guest=party==null?null:party.guests().get(p.getUUID().toString());if(!allowed(p)||guest==null||party.closed()||p.level().getGameTime()>=party.ends()||party.host().equals(p.getUUID()))return 0;
        if(yes&&p.level().getAttachedOrElse(PARTIES,Map.of()).values().stream().anyMatch(g->!g.closed()&&!g.home().equals(home)&&g.starts()<party.ends()&&g.ends()>party.starts()&&accepted(g.guests().get(p.getUUID().toString()))))return 0;
        var all=new HashMap<>(party.guests());all.put(p.getUUID().toString(),new Guest(guest.name(),yes?"accepted":"declined",guest.gift(),guest.eaten()));put(p.level(),party.with(party.portions(),false,all));
        if(!yes)HouseGuests.revokeEvent(p.level(),home,party.host(),p.getUUID(),party.scope());return 1;
    }
    static int food(ServerPlayer p,String home,int amount){
        var party=party(p.level(),home);if(!allowed(p)||party==null||party.closed()||!accepted(party.guests().get(p.getUUID().toString()))||amount<1||amount>64||party.portions()+amount>256||!CityEconomy.take(p,Items.BREAD,amount))return 0;
        var people=new HashMap<>(party.guests());var g=people.get(p.getUUID().toString());people.put(p.getUUID().toString(),new Guest(g.name(),g.status(),true,g.eaten()));put(p.level(),party.with(party.portions()+amount,false,people));return 1;
    }
    static Party attending(ServerLevel city,UUID id){
        long now=city.getGameTime();for(var p:city.getAttachedOrElse(PARTIES,Map.of()).values())if(!p.closed()&&now>=p.starts()-600&&now<=p.ends()&&accepted(p.guests().get(id.toString())))return p;return null;
    }
    static String visitingHome(ServerLevel city,UUID id){var p=attending(city,id);return p==null?"":p.home();}
    static boolean departing(ServerLevel city,UUID id){long now=city.getGameTime();return city.getAttachedOrElse(PARTIES,Map.of()).values().stream().anyMatch(p->p.closed()&&now<p.ends()+1200&&p.guests().containsKey(id.toString()));}
    static boolean hasMeal(ServerLevel city,UUID id){var p=attending(city,id);return p!=null&&city.getGameTime()>=p.starts()&&!p.guests().get(id.toString()).eaten();}
    static boolean eat(ServerLevel city,UUID id){
        var p=attending(city,id);if(p==null||city.getGameTime()<p.starts()||p.portions()<=0)return false;var g=p.guests().get(id.toString());if(g.eaten())return false;
        var people=new HashMap<>(p.guests());people.put(id.toString(),new Guest(g.name(),g.status(),g.gift(),true));put(city,p.with(p.portions()-1,false,people));return true;
    }
    static int eat(ServerPlayer p,String home){var party=party(p.level(),home);var house=CityLivingHomes.find(home);if(!allowed(p)||party==null||house==null||!house.contains(p.blockPosition())||!eat(p.level(),p.getUUID()))return 0;p.getFoodData().eat(6,.6f);return 1;}
    static void close(ServerLevel city,Party p){
        if(p.closed())return;var people=new HashMap<>(p.guests());
        p.guests().forEach((id,g)->{
            HouseGuests.revokeEvent(city,p.home(),p.host(),UUID.fromString(id),p.scope());
            people.put(id,new Guest(g.name(),accepted(g)?"departed":g.status(),g.gift(),g.eaten()));
            var entity=city.getEntity(UUID.fromString(id));if(entity instanceof NapoliResident npc&&accepted(g)){
                CityNeighborhood.say(city,npc,"Grazie per la cena, a presto!",12);
                if(g.eaten())CitySocial.helped(city,npc.getUUID(),p.host(),NapoliBroadcast.day(city));
            }
        });put(city,p.with(p.portions(),true,people));
    }
    static void tick(ServerLevel city){
        long now=city.getGameTime();for(var original:List.copyOf(city.getAttachedOrElse(PARTIES,Map.of()).values())){
            if(original.closed())continue;
            if(now>=original.ends()||!HouseGuests.host(city,original.home(),original.host())){close(city,original);continue;}
            if(now<original.starts()-600)continue;var house=CityLivingHomes.find(original.home());if(house==null)continue;
            var guests=new HashMap<>(original.guests());int food=original.portions();
            for(var entry:original.guests().entrySet()){
                var g=entry.getValue();if(!accepted(g))continue;var id=UUID.fromString(entry.getKey());
                if(!id.equals(original.host()))HouseGuests.grantForEvent(city,original.home(),original.host(),id,g.name(),original.scope());
                var entity=city.getEntity(id);if(entity!=null&&house.contains(entity.blockPosition())){
                    boolean gift=g.gift();if(entity instanceof NapoliResident&&!gift){var l=CitySimulation.life(city,id);if(l.food()>2){CitySimulation.put(city,id,l.withNeeds(l.needs(),l.food()-1,l.last(),l.water(),l.energy()));food++;gift=true;}}
                    guests.put(entry.getKey(),new Guest(g.name(),"arrived",gift,g.eaten()));
                }
            }put(city,original.with(food,false,guests));
        }
    }
    static int menu(ServerPlayer p,String home){
        if(!allowed(p))return 0;
        var party=party(p.level(),home);if(party==null){if(HouseGuests.host(p.level(),home,p.getUUID()))create(p,home,5);else return 0;party=party(p.level(),home);if(party==null)return 0;}
        boolean host=party.host().equals(p.getUUID());if(!host&&!party.guests().containsKey(p.getUUID().toString()))return 0;
        var buttons=new ArrayList<ActionButton>();
        if(host&&!party.closed()){
            for(var other:p.level().getServer().getPlayerList().getPlayers())if(!party.guests().containsKey(other.getUUID().toString()))buttons.add(CitySocial.button("Invita "+other.getName().getString(),"/cena invita "+home+" "+other.getUUID()));
            for(var npc:p.level().getEntitiesOfClass(NapoliResident.class,p.getBoundingBox().inflate(32),n->NapoliCivicLife.profile(p.level(),n.getUUID())!=null))if(!party.guests().containsKey(npc.getUUID().toString()))buttons.add(CitySocial.button("Invita "+npc.getName().getString(),"/cena invita "+home+" "+npc.getUUID()));
            buttons.add(CitySocial.button("Concludi la cena","/cena annulla "+home));
        }
        if(!party.closed()){
            if(!host){buttons.add(CitySocial.button("Ci sarò","/cena si "+home));buttons.add(CitySocial.button("Non posso venire","/cena no "+home));}
            buttons.add(CitySocial.button("Porta 4 pani","/cena cibo "+home+" 4"));buttons.add(CitySocial.button("Mangia una porzione","/cena mangia "+home));
        }else if(host)buttons.add(CitySocial.button("Recupera gli avanzi","/cena avanzi "+home));
        StringBuilder text=new StringBuilder(party.closed()?"Cena conclusa":"Inizio fra "+Math.max(0,(party.starts()-p.level().getGameTime())/20)+" s");text.append(" · Porzioni: ").append(party.portions());
        party.guests().values().forEach(g->text.append("\n").append(g.name()).append(": ").append(switch(g.status()){case "pending"->"in attesa";case "accepted"->"confermato";case "declined"->"non viene";case "arrived"->"presente";default->"salutato";}));
        if(buttons.isEmpty())buttons.add(CitySocial.button("Chiudi","/cena chiudi"));p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Cena a casa"),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(Component.literal(text.toString()),270)),List.of()),buttons,Optional.empty(),1)));return 1;
    }
    static int refund(ServerPlayer p,String home){var party=party(p.level(),home);if(!allowed(p)||party==null||!party.closed()||!party.host().equals(p.getUUID())||party.portions()==0)return 0;put(p.level(),party.with(0,true,party.guests()));var food=new net.minecraft.world.item.ItemStack(Items.BREAD,party.portions());p.getInventory().add(food);if(!food.isEmpty())p.spawnAtLocation(p.level(),food);return 1;}
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%20==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)tick(city);}});
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var root=Commands.literal("cena");root.then(Commands.literal("organizza").then(Commands.argument("casa",StringArgumentType.word()).then(Commands.argument("minuti",IntegerArgumentType.integer(1,30)).executes(c->create(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"casa"),IntegerArgumentType.getInteger(c,"minuti"))))));
            root.then(Commands.literal("invita").then(Commands.argument("casa",StringArgumentType.word()).then(Commands.argument("ospite",StringArgumentType.word()).executes(c->invite(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"casa"),StringArgumentType.getString(c,"ospite"))))));
            root.then(Commands.literal("cibo").then(Commands.argument("casa",StringArgumentType.word()).then(Commands.argument("porzioni",IntegerArgumentType.integer(1,64)).executes(c->food(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"casa"),IntegerArgumentType.getInteger(c,"porzioni"))))));
            for(var action:List.of("menu","si","no","mangia","annulla","avanzi"))root.then(Commands.literal(action).then(Commands.argument("casa",StringArgumentType.word()).executes(c->{var p=c.getSource().getPlayerOrException();var home=StringArgumentType.getString(c,"casa");if(p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive()||Carabinieri.isJailed(p))return 0;
                return switch(action){case "menu"->menu(p,home);case "si","no"->respond(p,home,action.equals("si"));case "mangia"->eat(p,home);case "avanzi"->refund(p,home);default->{var party=party(p.level(),home);if(party==null||!party.host().equals(p.getUUID()))yield 0;close(p.level(),party);yield 1;}};
            })));root.then(Commands.literal("chiudi").executes(c->1));d.register(root);
        });
    }
    private CityGatherings(){}
}
