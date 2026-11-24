package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import static dev.napuli.CityText.t;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Ownership and finance are changed together on the server thread, with no client-supplied prices. */
final class CityHousingFinance {
    record Account(UUID tenant,int debt,int installment,int periods,int arrears,int missed,long billed,boolean rental) {
        static final Codec<Account> CODEC=RecordCodecBuilder.create(i->i.group(
                UUIDUtil.CODEC.fieldOf("tenant").forGetter(Account::tenant),Codec.INT.fieldOf("debt").forGetter(Account::debt),
                Codec.INT.fieldOf("installment").forGetter(Account::installment),Codec.INT.fieldOf("periods").forGetter(Account::periods),
                Codec.INT.fieldOf("arrears").forGetter(Account::arrears),Codec.INT.fieldOf("missed").forGetter(Account::missed),
                Codec.LONG.fieldOf("billed").forGetter(Account::billed),Codec.BOOL.fieldOf("rental").forGetter(Account::rental)).apply(i,Account::new));
    }
    static final AttachmentType<Map<String,Account>> ACCOUNTS=AttachmentRegistry.<Map<String,Account>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Account.CODEC)).buildAndRegister(Napuli.id("housing_accounts"));
    static final UUID LANDLORD=UUID.nameUUIDFromBytes("napulicraft:landlord".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    static boolean tenant(ServerLevel city,String house,UUID id) { var account=city.getAttachedOrElse(ACCOUNTS,Map.of()).get(house);return account!=null&&account.tenant().equals(id); }
    static void ownOutright(ServerLevel city,String id,UUID owner) {
        HouseGuests.clear(city,id);
        var accounts=new HashMap<>(city.getAttachedOrElse(ACCOUNTS,Map.of()));accounts.put(id,new Account(owner,0,0,0,0,0,NapoliBroadcast.day(city),false));city.setAttached(ACCOUNTS,Map.copyOf(accounts));
    }
    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%200!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city!=null)bill(city,NapoliBroadcast.day(city));
        });
    }
    static void bill(ServerLevel city,long today) {
        var accounts=new HashMap<>(city.getAttachedOrElse(ACCOUNTS,Map.of()));
        for(var entry:List.copyOf(accounts.entrySet())) {
            var a=entry.getValue();long periods=Math.clamp((today-a.billed())/7,0,32);
            if(NapoliCivicLife.profile(city,a.tenant())!=null)continue;
            if(periods==0)continue;
            int unpaidPrincipal=Math.max(0,a.debt()-a.installment()*a.missed());
            int charges=2*(int)periods+(a.rental()?(int)periods*a.installment():Math.min(unpaidPrincipal,(int)periods*a.installment()));
            int arrears=Math.min(99999,a.arrears()+charges);
            accounts.put(entry.getKey(),new Account(a.tenant(),a.debt(),a.installment(),a.periods(),arrears,a.missed()+(int)periods,a.billed()+periods*7,a.rental()));
        }
        city.setAttached(ACCOUNTS,Map.copyOf(accounts));
        for(var player:city.players()) for(var h:NapoliMergellina.PROPERTIES) {
            var a=city.getAttachedOrElse(ACCOUNTS,Map.of()).get(h.id());
            if(a==null||!a.tenant().equals(player.getUUID())||a.arrears()==0)continue;
            if(NapoliStreet.cash(player)>=a.arrears())pay(player,h);
            else if(a.missed()>=3&&(a.rental()||a.debt()>0)) {
                remove(city,h.id(),true);player.sendSystemMessage(t("house.closed_contract",h.name()));
            } else player.sendSystemMessage(t("house.due_notice",h.name(),a.arrears(),h.id()));
        }
    }
    static void mortgageForResident(ServerLevel city,NapoliMergellina.House h,UUID resident,long day,int deposit) {
        int principal=((h.price()-deposit)*110+99)/100;
        var copy=new HashMap<>(city.getAttachedOrElse(ACCOUNTS,Map.of()));
        copy.put(h.id(),new Account(resident,principal,(principal+11)/12,12,0,0,day,false));city.setAttached(ACCOUNTS,Map.copyOf(copy));
    }
    static void settleResidents(ServerLevel city,Map<UUID,NapoliCivicLife.Citizen> people,long day) {
        var accounts=new HashMap<>(city.getAttachedOrElse(ACCOUNTS,Map.of()));
        for(var entry:List.copyOf(accounts.entrySet())) {
            var a=entry.getValue();var owner=people.get(a.tenant());if(owner==null||day-a.billed()<7)continue;
            var partner=people.values().stream().filter(p->p.id().toString().equals(owner.partner())).findFirst().orElse(null);
            int total=owner.balance()+(partner==null?0:partner.balance());
            int due=Math.min(a.debt(),a.installment()*(a.missed()+1))+2*(a.missed()+1);
            if(total>=due) {
                int share=Math.min(owner.balance(),due);people.put(owner.id(),owner.withMoney(owner.balance()-share,owner.job()));
                if(partner!=null)people.put(partner.id(),partner.withMoney(partner.balance()-(due-share),partner.job()));
                int debt=Math.max(0,a.debt()-a.installment()*(a.missed()+1));
                if(debt==0)accounts.remove(entry.getKey());else accounts.put(entry.getKey(),new Account(a.tenant(),debt,a.installment(),a.periods()-1,0,0,day,false));
            } else if(a.missed()>=2) {
                HouseGuests.clear(city,entry.getKey());
                accounts.remove(entry.getKey());
                var owners=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));owners.remove(entry.getKey());city.setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));
                for(var person:List.copyOf(people.values()))if(person.property().equals(entry.getKey()))people.put(person.id(),person.withHousehold(person.partner(),"public_0",person.balance()));
            } else accounts.put(entry.getKey(),new Account(a.tenant(),a.debt(),a.installment(),a.periods(),due,a.missed()+1,day,false));
        }
        city.setAttached(ACCOUNTS,Map.copyOf(accounts));
    }
    static int acquire(ServerPlayer p,String id,boolean rental) {
        var h=CityLivingHomes.find(id);if(h==null||p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive()
                ||Carabinieri.isJailed(p)||h.sign().distToCenterSqr(p.position())>36)return 0;
        if(NapoliProperties.owner(p.level(),h)!=null)return 0;
        var terms=CompanionHousing.terms(h,rental);
        int deposit=terms.deposit();
        if(NapoliStreet.cash(p)<deposit) {p.sendSystemMessage(t("house.deposit_needed",deposit));return 0;}
        int principal=terms.principal();
        int installment=terms.installment();
        NapoliStreet.credit(p,-deposit,rental?"rent_deposit":"mortgage_deposit",h.name());
        HouseGuests.clear(p.level(),id);
        var owners=new HashMap<>(p.level().getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));
        owners.put(id,rental?LANDLORD:p.getUUID());p.level().setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));
        var accounts=new HashMap<>(p.level().getAttachedOrElse(ACCOUNTS,Map.of()));
        accounts.put(id,new Account(p.getUUID(),principal,installment,rental?-1:12,0,0,NapoliBroadcast.day(p.level()),rental));
        p.level().setAttached(ACCOUNTS,Map.copyOf(accounts));
        p.sendSystemMessage(t("house.acquired",t(rental?"house.rental":"house.mortgage"),h.name(),deposit,installment));return 1;
    }
    static int leave(ServerPlayer p,String id) {
        if(p.level().dimension()!=NapoliMap.LEVEL)return 0;
        var a=p.level().getAttachedOrElse(ACCOUNTS,Map.of()).get(id);
        if(a==null||!a.rental()||!a.tenant().equals(p.getUUID()))return 0;
        if(a.arrears()>NapoliStreet.cash(p))return 0;
        NapoliStreet.credit(p,-a.arrears(),"housing",id);remove(p.level(),id,true);p.sendSystemMessage(t("house.rental_ended"));return 1;
    }
    static int pay(ServerPlayer p,NapoliMergellina.House h) {
        var accounts=new HashMap<>(p.level().getAttachedOrElse(ACCOUNTS,Map.of()));var a=accounts.get(h.id());
        if(a==null||!a.tenant().equals(p.getUUID())||a.arrears()==0||NapoliStreet.cash(p)<a.arrears())return 0;
        NapoliStreet.credit(p,-a.arrears(),"housing",h.name());
        int periodsPaid=Math.min(a.missed(),Math.max(0,a.periods()));
        int debt=Math.max(0,a.debt()-periodsPaid*a.installment());
        if(!a.rental()&&debt==0)accounts.put(h.id(),new Account(a.tenant(),0,0,0,0,0,a.billed(),false));
        else accounts.put(h.id(),new Account(a.tenant(),debt,a.installment(),a.rental()?-1:Math.max(0,a.periods()-periodsPaid),0,0,a.billed(),a.rental()));
        p.level().setAttached(ACCOUNTS,Map.copyOf(accounts));p.sendSystemMessage(t("house.payment_done",a.arrears(),debt));return 1;
    }
    static void inherit(ServerLevel city,UUID from,UUID to) {
        var accounts=new HashMap<>(city.getAttachedOrElse(ACCOUNTS,Map.of()));
        for(var entry:List.copyOf(accounts.entrySet()))if(entry.getValue().tenant().equals(from)) {
            HouseGuests.clear(city,entry.getKey());
            var a=entry.getValue();
            if(to==null) {
                accounts.remove(entry.getKey());
                if(a.rental()){var owners=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));owners.remove(entry.getKey());city.setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));}
            } else accounts.put(entry.getKey(),new Account(to,a.debt(),a.installment(),a.periods(),a.arrears(),a.missed(),a.billed(),a.rental()));
        }
        city.setAttached(ACCOUNTS,Map.copyOf(accounts));
    }
    static int sell(ServerPlayer p,String id) {
        var h=CityLivingHomes.find(id);if(h==null||h.sign().distToCenterSqr(p.position())>36||!p.getUUID().equals(NapoliProperties.owner(p.level(),h)))return 0;
        var a=p.level().getAttachedOrElse(ACCOUNTS,Map.of()).get(id);
        int proceeds=h.price()*80/100-(a==null?0:a.debt()+a.arrears());
        if(proceeds<0&&NapoliStreet.cash(p)<-proceeds)return 0;
        remove(p.level(),id,true);NapoliStreet.credit(p,proceeds,"house_sale",h.name());p.sendSystemMessage(t("house.sold",proceeds));return 1;
    }
    private static void remove(ServerLevel city,String id,boolean ownership) {
        HouseGuests.clear(city,id);
        var accounts=new HashMap<>(city.getAttachedOrElse(ACCOUNTS,Map.of()));accounts.remove(id);city.setAttached(ACCOUNTS,Map.copyOf(accounts));
        if(ownership){var owners=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));owners.remove(id);city.setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));}
    }
    private CityHousingFinance() {}
}
