package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.server.level.ServerPlayer;
import static dev.napuli.CityText.t;

/** Quotes show authoritative totals and are single-use; execution revalidates price and ownership. */
final class CompanionHousing {
    record Terms(int deposit,int principal,int installment,int periods) { int total(){return deposit+principal;} }
    record Quote(String token,String house,String action,int charge,String snapshot,long expires) {}
    static final AttachmentType<Quote> QUOTE=AttachmentRegistry.create(Napuli.id("housing_quote"));
    static Terms terms(NapoliMergellina.House h,boolean rent){int deposit=rent?Math.max(5,h.price()/12):Math.max(1,(h.price()+4)/5);int principal=rent?0:((h.price()-deposit)*110+99)/100;return new Terms(deposit,principal,rent?Math.max(4,h.price()/18):(principal+11)/12,rent?-1:12);}
    static boolean mine(ServerPlayer p,NapoliMergellina.House h){return NapoliProperties.owns(p,h);}
    static int menu(ServerPlayer p){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));
        var entries=new ArrayList<CompanionBoard.Entry>();
        for(var h:NapoliMergellina.PROPERTIES){boolean own=mine(p,h);var owner=NapoliProperties.owner(p.level(),h);
            if(own||owner==null)entries.add(new CompanionBoard.Entry(t(own?"house.mine":"house.available",h.address()),t("house.list",h.price(),(int)Math.sqrt(h.entrance().distSqr(p.blockPosition()))),"compagno casa "+h.id()));}
        return CityCompanion.show(p,"case",t("house.title"),entries);
    }
    static int detail(ServerPlayer p,String id){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var h=CityLivingHomes.find(id);if(h==null)return CityCompanion.fail(p,t("house.invalid"));
        var entries=new ArrayList<CompanionBoard.Entry>();var a=p.level().getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()).get(id);
        entries.add(new CompanionBoard.Entry(t("house.navigate"),t("house.address",h.sign().toShortString()),"compagno casa_vai "+id));
        if(mine(p,h)){
            if(a!=null&&a.tenant().equals(p.getUUID())){
                long days=Math.max(0,a.billed()+7-NapoliBroadcast.day(p.level()));
                entries.add(new CompanionBoard.Entry(t(a.rental()?"house.rental":"house.owned"),t("house.account",a.debt(),a.arrears(),a.installment(),days,a.missed()),""));
                entries.add(new CompanionBoard.Entry(t("house.pay",a.arrears()),t("house.bill_help"),"compagno casa_preventivo paga/"+id));
                if(a.rental())entries.add(new CompanionBoard.Entry(t("house.leave"),t("house.leave_cost",a.arrears()),"compagno casa_preventivo lascia/"+id));
            }else entries.add(new CompanionBoard.Entry(t("house.owned"),t("house.no_debt"),""));
            entries.add(new CompanionBoard.Entry(t("house.keys"),t("house.keys_help"),"casa chiave "+id));
            entries.add(new CompanionBoard.Entry(t("house.guests"),t("house.guests_help"),"compagno permessi "+id));
            entries.add(new CompanionBoard.Entry(t("house.dinner"),t("house.dinner_help"),"cena menu "+id));
            if(p.getUUID().equals(NapoliProperties.owner(p.level(),h)))entries.add(new CompanionBoard.Entry(t("house.sell"),t("house.sell_help"),"compagno casa_preventivo vendi/"+id));
        }else if(NapoliProperties.owner(p.level(),h)==null&&NapoliMergellina.PROPERTIES.contains(h)){
            entries.add(new CompanionBoard.Entry(t("house.buy"),t("house.buy_cost",h.price()),"compagno casa_preventivo compra/"+id));
            for(boolean rent:new boolean[]{true,false}){var terms=terms(h,rent);entries.add(new CompanionBoard.Entry(t(rent?"house.rent":"house.mortgage"),t("house.offer",terms.deposit(),terms.installment(),terms.periods()<0?t("house.unlimited"):terms.periods(),terms.total()),"compagno casa_preventivo "+(rent?"affitta/":"mutuo/")+id));}
        }else return CityCompanion.fail(p,t("house.unavailable"));
        return CityCompanion.show(p,"casa/"+id,t("house.detail",h.address()),entries);
    }
    static String snapshot(ServerPlayer p,NapoliMergellina.House h){return h.price()+"/"+NapoliProperties.owner(p.level(),h)+"/"+p.level().getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()).get(h.id());}
    static int charge(ServerPlayer p,NapoliMergellina.House h,String action){var a=p.level().getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()).get(h.id());return switch(action){case "compra"->h.price();case "affitta"->terms(h,true).deposit();case "mutuo"->terms(h,false).deposit();case "paga","lascia"->a==null?0:a.arrears();case "vendi"->-CityHousingFinance.saleProceeds(h,a);default->Integer.MAX_VALUE;};}
    static int quote(ServerPlayer p,String reference){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var parts=reference.split("/",2);if(parts.length!=2)return 0;
        var action=parts[0];var h=CityLivingHomes.find(parts[1]);if(h==null)return CityCompanion.fail(p,t("house.invalid"));
        boolean acquisition=Set.of("compra","affitta","mutuo").contains(action);
        if(acquisition?(NapoliProperties.owner(p.level(),h)!=null||!NapoliMergellina.PROPERTIES.contains(h)):!mine(p,h))return CityCompanion.fail(p,t("house.unavailable"));
        int cost=charge(p,h,action);if(cost==Integer.MAX_VALUE)return 0;
        var q=new Quote(UUID.randomUUID().toString(),h.id(),action,cost,snapshot(p,h),p.level().getGameTime()+1200);p.setAttached(QUOTE,q);
        var entries=new ArrayList<CompanionBoard.Entry>();
        entries.add(new CompanionBoard.Entry(t("house.quote_action",t("house.action."+action)),t(cost<0?"house.quote_credit":"house.quote_total",Math.abs(cost),NapoliStreet.cash(p)),""));
        if(action.equals("affitta")||action.equals("mutuo")){var terms=terms(h,action.equals("affitta"));entries.add(new CompanionBoard.Entry(t("house.contract_terms"),t("house.offer",terms.deposit(),terms.installment(),terms.periods()<0?t("house.unlimited"):terms.periods(),terms.total()),""));entries.add(new CompanionBoard.Entry(t("house.recurring"),t("house.recurring_help"),""));}
        entries.add(new CompanionBoard.Entry(t("house.confirm"),t("house.confirm_help"),"compagno casa_conferma "+q.token()));
        entries.add(new CompanionBoard.Entry(t("house.back"),t("house.cancel_help"),"compagno casa "+h.id()));
        return CityCompanion.show(p,"preventivo/"+h.id(),t("house.quote",h.address()),entries);
    }
    static boolean valid(Quote quote,String token,long now,String snapshot){return quote!=null&&quote.token().equals(token)&&now<quote.expires()&&quote.snapshot().equals(snapshot);}
    static int confirm(ServerPlayer p,String token){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var q=p.getAttached(QUOTE);var h=q==null?null:CityLivingHomes.find(q.house());
        if(h==null||!valid(q,token,p.level().getGameTime(),snapshot(p,h)))return CityCompanion.fail(p,t("house.quote_expired"));
        if(Set.of("compra","affitta","mutuo","vendi").contains(q.action())&&h.sign().distToCenterSqr(p.position())>36)return CityCompanion.fail(p,t("house.at_sign"));
        if(q.charge()>NapoliStreet.cash(p))return CityCompanion.fail(p,t("house.need_money",q.charge()-NapoliStreet.cash(p)));
        if(q.charge()<0&&!NapoliStreet.canCredit(p,-q.charge()))return CityCompanion.fail(p,t("wallet.full",NapoliStreet.MAX_CASH));
        p.removeAttached(QUOTE);
        int result=switch(q.action()){case "compra"->NapoliProperties.buy(p,h.id());case "affitta","mutuo"->CityHousingFinance.acquire(p,h.id(),q.action().equals("affitta"));case "paga"->CityHousingFinance.pay(p,h);case "lascia"->CityHousingFinance.leave(p,h.id());case "vendi"->CityHousingFinance.sell(p,h.id());default->0;};
        if(result==0)return CityCompanion.fail(p,t("house.action_failed"));return detail(p,h.id());
    }
    static int navigate(ServerPlayer p,String id){var h=CityLivingHomes.find(id);if(h==null||!CityCompanion.inCity(p))return 0;CityCompanion.pin(p,new CityCompanion.Goal(h.address(),h.sign().south()));return 1;}
    private CompanionHousing(){}
}
