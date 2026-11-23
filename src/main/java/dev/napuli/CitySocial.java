package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;

/** Memories belong to an individual citizen and visitor, never to an NPC role. */
final class CitySocial {
    record Bond(int trust, int meetings, int purchases, int favors, int grievances, long met, long helped, long apologized) {
        static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("trust").forGetter(Bond::trust), Codec.INT.fieldOf("meetings").forGetter(Bond::meetings),
                Codec.INT.fieldOf("purchases").forGetter(Bond::purchases), Codec.INT.fieldOf("favors").forGetter(Bond::favors),
                Codec.INT.fieldOf("grievances").forGetter(Bond::grievances), Codec.LONG.fieldOf("met").forGetter(Bond::met),
                Codec.LONG.fieldOf("helped").forGetter(Bond::helped), Codec.LONG.fieldOf("apologized").forGetter(Bond::apologized)).apply(i, Bond::new));
        static Bond fresh() { return new Bond(50,0,0,0,0,-1,-1,-1); }
    }
    static final AttachmentType<Map<String,Bond>> BONDS = AttachmentRegistry.<Map<String,Bond>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Bond.CODEC)).buildAndRegister(Napuli.id("citizen_visitors"));
    private static String key(UUID citizen,UUID visitor) { return citizen+"/"+visitor; }
    static Bond bond(ServerLevel city,UUID citizen,UUID visitor) { return city.getAttachedOrElse(BONDS,Map.of()).getOrDefault(key(citizen,visitor),Bond.fresh()); }
    static void put(ServerLevel city,UUID citizen,UUID visitor,Bond bond) {
        var all=new HashMap<>(city.getAttachedOrElse(BONDS,Map.of()));all.put(key(citizen,visitor),bond);city.setAttached(BONDS,Map.copyOf(all));
    }
    static Bond meet(ServerLevel city,UUID citizen,UUID visitor,long day) {
        var b=bond(city,citizen,visitor);
        if(day>b.met()) { b=new Bond(Math.min(100,b.trust()+1),b.meetings()+1,b.purchases(),b.favors(),b.grievances(),day,b.helped(),b.apologized());put(city,citizen,visitor,b); }
        return b;
    }
    static void purchased(ServerLevel city,UUID citizen,UUID visitor) {
        var b=bond(city,citizen,visitor);put(city,citizen,visitor,new Bond(b.trust(),b.meetings(),b.purchases()+1,b.favors(),b.grievances(),b.met(),b.helped(),b.apologized()));
    }
    static void harm(ServerLevel city,UUID citizen,UUID visitor) {
        var b=bond(city,citizen,visitor);put(city,citizen,visitor,new Bond(Math.max(0,b.trust()-25),b.meetings(),b.purchases(),b.favors(),Math.min(20,b.grievances()+1),b.met(),b.helped(),b.apologized()));
    }
    static int price(ServerLevel city,UUID citizen,UUID visitor,int base) {
        var b=bond(city,citizen,visitor);
        int percent=b.trust()<30?125:b.trust()>=65&&b.meetings()>=3?90:100;
        return Math.max(1,(base*percent+99)/100);
    }
    static String greeting(Bond b,String visitor) {
        if(b.grievances()>0) return b.trust()<30?"Ricordo quello che mi hai fatto. Prima di fidarmi, devi rimediare.":"Ho accettato le tue scuse, ma la fiducia si ricostruisce con i fatti.";
        if(b.favors()>0) return "Ciao "+visitor+", non ho dimenticato il tuo aiuto con le provviste.";
        if(b.meetings()>=3&&b.purchases()>=3) return "Il mio cliente abituale! Bentornato, "+visitor+". Il solito?";
        if(b.meetings()>=3) return "Ciao "+visitor+", ormai ci incontriamo spesso nel quartiere.";
        return "Piacere di vederti. Come va la giornata?";
    }
    static NapoliCivicLife.Citizen nearby(ServerPlayer p,UUID id) {
        if(p.level().dimension()!=NapoliMap.LEVEL||!p.isAlive()||p.isSpectator()||Carabinieri.isJailed(p))return null;
        var entity=p.level().getEntity(id);
        return entity instanceof NapoliResident&&entity.isAlive()&&entity.distanceToSqr(p)<=36?NapoliCivicLife.profile(p.level(),id):null;
    }
    static void open(ServerPlayer p,NapoliCivicLife.Citizen c) {
        var b=meet(p.level(),c.id(),p.getUUID(),NapoliBroadcast.day(p.level()));
        var buttons=new ArrayList<ActionButton>();
        buttons.add(button("Porta provviste (4 grano)","/vicino aiuta "+c.id()));
        if(b.grievances()>0)buttons.add(button("Chiedi scusa","/vicino scusa "+c.id()));
        if(CityResidentShop.merchant(c))buttons.add(button("Guarda la merce","/vicino bottega "+c.id()));
        if(CityEconomy.merchant(c))buttons.add(button("Bottega e consegne","/quartiere bottega "+c.id()));
        String doing=CityDomestic.description(p.level(),c.id());
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(c.name()),Optional.empty(),true,false,DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(greeting(b,p.getName().getString())),270),
                        new PlainMessage(Component.literal("Fiducia: "+b.trust()+" · incontri in giorni diversi: "+b.meetings()+
                                (doing.isEmpty()?"":"\n"+doing)),270)),List.of()),buttons,Optional.empty(),1)));
    }
    static ActionButton button(String text,String command) { return new ActionButton(new CommonButtonData(Component.literal(text),250),Optional.of(new StaticAction(new ClickEvent.RunCommand(command)))); }
    static int act(ServerPlayer p,UUID id,String action) {
        var c=nearby(p,id);if(c==null)return 0;
        var b=bond(p.level(),id,p.getUUID());long day=NapoliBroadcast.day(p.level());
        if(action.equals("bottega")){CityResidentShop.offer(p,c);return 1;}
        if(action.equals("scusa")) {
            if(b.grievances()==0||b.apologized()>=day)return 0;
            put(p.level(),id,p.getUUID(),new Bond(Math.min(60,b.trust()+5),b.meetings(),b.purchases(),b.favors(),b.grievances(),b.met(),b.helped(),day));
            p.sendSystemMessage(Component.literal(c.name()+": Apprezzo le scuse. Dimostrami che posso fidarmi."));return 1;
        }
        if(!action.equals("aiuta")||b.helped()>=day||!CityEconomy.take(p,net.minecraft.world.item.Items.WHEAT,4))return 0;
        CityEconomy.supply(p.level(),CityEconomy.business(c),4);
        put(p.level(),id,p.getUUID(),new Bond(Math.min(100,b.trust()+8),b.meetings(),b.purchases(),b.favors()+1,Math.max(0,b.grievances()-1),b.met(),day,b.apologized()));
        var l=CitySimulation.life(p.level(),id);CitySimulation.put(p.level(),id,l.career(l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),CitySimulation.memory(l,"Aiutato con le provviste da "+p.getName().getString())));
        p.sendSystemMessage(Component.literal(c.name()+": Grazie, queste provviste ci servivano davvero."));return 1;
    }
    static void register() {
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var root=Commands.literal("vicino");
            for(var action:List.of("aiuta","scusa","bottega"))root.then(Commands.literal(action).then(Commands.argument("persona",StringArgumentType.word()).executes(c->{
                try{return act(c.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(c,"persona")),action);}catch(IllegalArgumentException invalid){return 0;}
            })));d.register(root);
        });
    }
    private CitySocial() {}
}
