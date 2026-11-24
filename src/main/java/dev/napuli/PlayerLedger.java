package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.server.level.ServerPlayer;
import static dev.napuli.CityText.t;

/** The wallet records its actual clamped delta exactly once, alongside the resulting balance. */
final class PlayerLedger {
    record Transaction(long day, int time, int amount, int balance, String reason, String detail) {
        static final Codec<Transaction> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Transaction::day), Codec.INT.fieldOf("time").forGetter(Transaction::time),
                Codec.INT.fieldOf("amount").forGetter(Transaction::amount), Codec.INT.fieldOf("balance").forGetter(Transaction::balance),
                Codec.STRING.fieldOf("reason").forGetter(Transaction::reason), Codec.STRING.fieldOf("detail").forGetter(Transaction::detail)).apply(i,Transaction::new));
    }
    static final AttachmentType<List<Transaction>> HISTORY = AttachmentRegistry.<List<Transaction>>builder()
            .persistent(Transaction.CODEC.listOf()).copyOnDeath().buildAndRegister(Napuli.id("wallet_history"));
    static void register() {}
    static void record(ServerPlayer p, int delta, int balance, String reason, String detail) {
        if(delta == 0) return;
        var history = new ArrayList<Transaction>();
        history.add(new Transaction(NapoliBroadcast.day(p.level()), (int)NapoliBroadcast.time(p.level()), delta, balance, reason, detail));
        history.addAll(p.getAttachedOrElse(HISTORY,List.of()).stream().limit(99).toList());
        p.setAttached(HISTORY,List.copyOf(history));
    }
    static int menu(ServerPlayer p) {
        var entries = new ArrayList<CompanionBoard.Entry>();
        entries.add(new CompanionBoard.Entry(t("ledger.balance",NapoliStreet.cash(p)),t("ledger.explained"),""));
        var history=p.getAttachedOrElse(HISTORY,List.of());
        long income=history.stream().filter(x->x.amount()>0).mapToLong(Transaction::amount).sum();
        long spent=-history.stream().filter(x->x.amount()<0).mapToLong(Transaction::amount).sum();
        entries.add(new CompanionBoard.Entry(t("ledger.totals",income,spent),t("ledger.limit",history.size()),""));
        for(var row:history) entries.add(new CompanionBoard.Entry(t("ledger.amount",t("money."+row.reason()),(row.amount()>0?"+":"")+row.amount()),
                t("ledger.line",row.day()+1,Hud.clock(row.time()),row.balance(),row.detail()),""));
        return CityCompanion.show(p,"spese",t("ledger.title"),entries);
    }
    private PlayerLedger() {}
}
