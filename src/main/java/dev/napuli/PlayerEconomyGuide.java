package dev.napuli;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import static dev.napuli.CityText.t;

final class PlayerEconomyGuide {
    static int hoursFor(int cost,int wage){return (int)Math.max(0,((long)cost+Math.max(1,wage)-1)/Math.max(1,wage));}
    static int starterBudget(){var h=NapoliMergellina.PROPERTIES.stream().filter(home->home.price()>0).min(Comparator.comparingInt(NapoliMergellina.House::price)).orElseThrow();var terms=CompanionHousing.terms(h,true);return terms.deposit()+terms.installment()+2+2+2;}
    static Component price(ServerPlayer p,UUID seller,int base,Component item){int market=CityEvents.price(p.level(),base),price=CityEconomy.price(p,seller,base),adjustment=price-market;return t("economy.price",item,base,market,(adjustment>=0?"+":"")+adjustment,price);}
    static int menu(ServerPlayer p){
        var entries=new ArrayList<CompanionBoard.Entry>();var c=WorkCareer.selected(p);int wage=c==null?NapoliEmployment.Contract.CLEANER.job.wage:WorkCareer.hourly(p,c);
        entries.add(new CompanionBoard.Entry(t("economy.wallet",NapoliStreet.cash(p)),t("economy.starter",CityLifeConfig.settings.startingCash,starterBudget()),"compagno spese"));
        entries.add(new CompanionBoard.Entry(t("economy.first_pay",wage),t("economy.time",50),"compagno carriera"));
        for(var home:NapoliMergellina.PROPERTIES.stream().filter(h->h.price()>0).sorted(Comparator.comparingInt(NapoliMergellina.House::price)).limit(5).toList()){var rent=CompanionHousing.terms(home,true);int week=rent.installment()+2;entries.add(new CompanionBoard.Entry(t("house.detail",home.address()),t("economy.rent",rent.deposit(),week,hoursFor(rent.deposit(),wage),hoursFor(week,wage)),"compagno casa "+home.id()));}
        entries.add(new CompanionBoard.Entry(t("economy.tools"),t("economy.tools_help",6,30,hoursFor(30,wage)),"compagno carriera"));
        entries.add(new CompanionBoard.Entry(t("economy.prices"),t("economy.prices_help"),"negozio"));
        return CityCompanion.show(p,"economia",t("economy.title"),entries);
    }
    private PlayerEconomyGuide(){}
}
