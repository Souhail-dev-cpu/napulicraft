package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

final class BakeryStock {
    private record Stock(int flour, int bread) {
        static final Codec<Stock> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("flour").forGetter(Stock::flour),
                Codec.INT.fieldOf("bread").forGetter(Stock::bread)).apply(i, Stock::new));
    }

    private static final AttachmentType<Stock> STOCK = AttachmentRegistry.<Stock>builder()
            .persistent(Stock.CODEC).buildAndRegister(Napuli.id("bakery_stock"));
    private static final Stock INITIAL = new Stock(8, 12);

    private BakeryStock() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 400 != 0) return;
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city == null || city.players().isEmpty()) return;
            long hour = NapoliBroadcast.time(city);
            if (hour >= 1000 && hour < 11000) bake(city);
        });
    }

    static void deliver(ServerLevel city) {
        Stock stock = city.getAttachedOrElse(STOCK, INITIAL);
        city.setAttached(STOCK, new Stock(Math.min(24, stock.flour() + 2), stock.bread()));
    }

    static boolean needsDelivery(ServerLevel city) {
        return city.getAttachedOrElse(STOCK, INITIAL).flour() < 4;
    }

    static boolean reserveBread(ServerLevel city, int amount) {
        Stock stock = city.getAttachedOrElse(STOCK, INITIAL);
        if (stock.bread() < amount) return false;
        city.setAttached(STOCK, new Stock(stock.flour(), stock.bread() - amount));
        return true;
    }

    private static void bake(ServerLevel city) {
        Stock stock = city.getAttachedOrElse(STOCK, INITIAL);
        if (stock.flour() < 1 || stock.bread() > 22) return;
        city.setAttached(STOCK, new Stock(stock.flour() - 1, stock.bread() + 2));
    }
}
