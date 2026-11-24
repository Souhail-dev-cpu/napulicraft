package dev.napuli;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The everyday Neapolitans of the centro storico. Each one says something and has its own small deal:
 *  - 'a nonna feeds you (once in a while, for free: "Mangia, ca si' sicco!")
 *  - 'o parcheggiatore abusivo wants 2 euro to "watch" your scooter
 *  - 'o pizzaiolo sells a margherita, 'o presepaio a cornicello
 *  - 'o scugnizzo sometimes pinches a coin, 'o tifoso just sings for the Napoli
 */
final class NapoliPeople {
	private NapoliPeople() {}

	private static final AttachmentType<Long> NONNA_GIFT = AttachmentRegistry.create(Napuli.id("nonna_gift"));
	static final int PIZZA_PRICE = 5, CORNO_PRICE = 10, PARKING = 2;

	static void talk(ServerPlayer p, NapoliResident npc) {
		String id = npc.role().id();
		int line = p.getRandom().nextInt(4);
		long now = p.level().getGameTime();
		switch (npc.role()) {
			case NONNA -> {
				if (now >= p.getAttachedOrElse(NONNA_GIFT, 0L)) {
					p.setAttached(NONNA_GIFT, now + 20 * 300);
					give(p, new ItemStack(Napuli.PIZZA));
					say(p, npc, "napuli.people.nonna.gift");
					return;
				}
			}
			case PARCHEGGIATORE -> {
				boolean bike = !p.level().getEntitiesOfClass(Motorino.class, p.getBoundingBox().inflate(8)).isEmpty();
				if (bike) {
					if (NapoliStreet.cash(p) >= PARKING) {
						NapoliStreet.credit(p, -PARKING,"parking");
						say(p, npc, "napuli.people.parcheggiatore.paid");
					} else say(p, npc, "napuli.people.parcheggiatore.broke");
					return;
				}
			}
			case PIZZAIOLO, PRESEPAIO -> {
				boolean pizza = npc.role() == NapoliResident.Role.PIZZAIOLO;
				int price = NeighbourMemory.price(p, npc, pizza ? PIZZA_PRICE : CORNO_PRICE);
				if (p.isShiftKeyDown()) {                       // sneak + click = buy
					if (NapoliStreet.cash(p) < price) { say(p, npc, "napuli.people.broke"); return; }
					NapoliStreet.credit(p, -price,"purchase");
					NeighbourMemory.remember(p, npc, 2);
					give(p, new ItemStack(pizza ? Napuli.PIZZA : Napuli.CORNICELLO));
					say(p, npc, "napuli.people." + id + ".sold");
					return;
				}
				p.sendOverlayMessage(Component.translatable("napuli.people.buy_hint", price));
			}
			case SCUGNIZZO -> {
				if (p.getRandom().nextFloat() < 0.25f && NapoliStreet.cash(p) > 0 && !p.isCreative()) {
					NapoliStreet.credit(p, -1,"service");
					say(p, npc, "napuli.people.scugnizzo.pinch");
					return;
				}
			}
			case TIFOSO -> p.level().playSound(null, npc.blockPosition(), SoundEvents.NOTE_BLOCK_TRUMPET.value(), SoundSource.NEUTRAL, 1f, 1.2f);
			default -> {}
		}
		if (NeighbourMemory.trust(p, npc) != 0) NeighbourMemory.greet(p, npc);
		else say(p, npc, "napuli.people." + id + "." + line);
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) p.spawnAtLocation(p.level(), stack);
	}

	private static void say(ServerPlayer p, NapoliResident npc, String key) {
		NpcSpeech.say(p,npc,Component.translatable(key));
		npc.getLookControl().setLookAt(p, 30, 30);
	}
}
