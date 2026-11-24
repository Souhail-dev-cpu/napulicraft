package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Items;

/** Municipal work fund and real products connect pay, upgrades, deliveries and city services. */
final class WorkEconomy {
    static final AttachmentType<Integer> FUND=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).buildAndRegister(Napuli.id("employment_fund"));
    static int fund(ServerLevel city){return city.getAttachedOrElse(FUND,50000);}
    static void revenue(ServerLevel city,int euros){if(euros>0)city.setAttached(FUND,Math.min(1000000,fund(city)+euros));}
    static boolean wage(ServerPlayer player,int euros){if(euros<=0||fund(player.level())<euros)return false;player.level().setAttached(FUND,fund(player.level())-euros);NapoliStreet.credit(player,euros,"wage");return true;}
    static void sale(ServerLevel city,DistrictLife.District shop,int price){int levy=price/4;DistrictShops.credit(city,shop,price-levy);revenue(city,levy);}
    static void delivery(ServerLevel city,NapoliEmployment.Contract job,int units){
        switch(job){
            case CARPENTER -> CityOccupations.supply(city,"planks",CityOccupations.supply(city,"planks")+units);
            case BAKER -> {var s=CityEconomy.stock(city,"forno");CityEconomy.put(city,"forno",new CityEconomy.Stock(s.grain(),s.flour(),Math.min(192,s.bread()+units),s.processed(),s.delivered()));}
            case MERCHANT -> CityEconomy.supply(city,"forno",units);
            case GARDENER -> CityOccupations.supply(city,"seeds",CityOccupations.supply(city,"seeds")+units);
            case FISHER -> {var d=DistrictLife.districts().stream().min(Comparator.comparingDouble(q->q.business().entrance().distSqr(NapoliEmployment.Contract.FISHER.task))).orElseThrow();var l=DistrictShops.ledger(city,d.id());var goods=new HashMap<>(l.goods());goods.put("pesce",Math.min(256,goods.getOrDefault("pesce",0)+units));DistrictShops.put(city,d.id(),new DistrictShops.Ledger(Map.copyOf(goods),l.till(),l.delivered()));}
            case PORTER,COURIER -> {CityOccupations.supply(city,"parts",CityOccupations.supply(city,"parts")+units);CityOccupations.supply(city,"coffee",CityOccupations.supply(city,"coffee")+units);}
            case CLEANER -> CityOccupations.supply(city,"recycled",CityOccupations.supply(city,"recycled")+units);
        }
    }
    private WorkEconomy(){}
}
