package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

final class BakeryStock {
    record Stock(int flour, int bread) {
        static final Codec<Stock> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("flour").forGetter(Stock::flour),
                Codec.INT.fieldOf("bread").forGetter(Stock::bread)).apply(i, Stock::new));
    }

    static final AttachmentType<Stock> STOCK = AttachmentRegistry.<Stock>builder()
            .persistent(Stock.CODEC).buildAndRegister(Napuli.id("bakery_stock"));

    private BakeryStock() {}

    static void register() {} // Keep the legacy attachment registered; production belongs to CityEconomy.

    static void migrate(ServerLevel city) {
        var old=city.removeAttached(STOCK);if(old==null)return;
        var s=city.getAttachedOrElse(CityEconomy.STOCKS,java.util.Map.of()).getOrDefault("forno",CityEconomy.Stock.initial());
        CityEconomy.put(city,"forno",new CityEconomy.Stock(s.grain(),s.flour()+Math.max(0,old.flour())*2,s.bread()+Math.max(0,old.bread()),s.processed(),s.delivered()));
    }

    static void deliver(ServerLevel city) {
        CityEconomy.supplyFlour(city,4);
    }

    static boolean needsDelivery(ServerLevel city) {
        var s=CityEconomy.stock(city,"forno");return s.grain()+s.flour()<8;
    }

    static boolean reserveBread(ServerLevel city, int amount) {
        return CityEconomy.reserveBread(city,"forno",amount);
    }
}
