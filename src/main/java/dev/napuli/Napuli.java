package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Napuli implements ModInitializer {
	public static final String MOD_ID = "napuli";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

	// ------------------------------------------------------------ sounds
	// one sound event per situation (filled by the songs pack); urlo = the giant's scream; risata = vanilla witch giggle
	public static final SoundEvent URLO = sound("urlo");
	public static final SoundEvent RISATA = sound("risata");
	public static final SoundEvent JUSTIN_CROWD = sound("justin_crowd");
	static {
		for (Music.Situation s : Music.Situation.values()) s.sound = sound(s.id());
	}

	private static SoundEvent sound(String name) {
		Identifier id = id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	// ------------------------------------------------------------ entities
	public static final EntityType<Munaciello> MUNACIELLO = entity("munaciello",
			EntityType.Builder.<Munaciello>of(Munaciello::new, MobCategory.MONSTER).sized(0.5f, 1.4f).eyeHeight(1.2f).clientTrackingRange(10));
	public static final EntityType<MunacielloGigante> GIGANTE = entity("munaciello_gigante",
			EntityType.Builder.<MunacielloGigante>of(MunacielloGigante::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(16));
	public static final EntityType<Motorino> MOTORINO = entity("motorino",
			EntityType.Builder.<Motorino>of(Motorino::new, MobCategory.MISC).sized(1.18f, 1.65f).eyeHeight(1f).clientTrackingRange(10));
	public static final EntityType<CityVehicle> CITY_VEHICLE = entity("city_vehicle",
			EntityType.Builder.<CityVehicle>of(CityVehicle::new,MobCategory.MISC).sized(2.15f,1.95f).clientTrackingRange(14));
	public static final EntityType<Ciabatta> CIABATTA = entity("ciabatta",
			EntityType.Builder.<Ciabatta>of(Ciabatta::new, MobCategory.MISC).noLootTable().sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10));

	public static final EntityType<NapoliResident> RESIDENT = entity("residente",
			EntityType.Builder.<NapoliResident>of(NapoliResident::new, MobCategory.MISC)
					.noLootTable().sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10));

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> b) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
	}

	// ------------------------------------------------------------ items
	public static Item CORNICELLO, PIZZA, CAFE, CIABATTA_ITEM, MOTORINO_ITEM, KNUCKLES, KNIFE, PISTOL, BATON, BOMBA_CARTA, CASCO, CALZINI_SPORCHI, FORMAGGIO_PIEDE, MUNACIELLO_EGG, GIGANTE_EGG, NAPULETANO_EGG;

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	private static Item melee(String name, double damage, double speed) {
		return item(name, Item::new, new Item.Properties().stacksTo(1)
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(id(name + "_damage"), damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED, new AttributeModifier(id(name + "_speed"), speed - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
						.build()));
	}

	/** How angry the Munaciello is with a player (each one you kill +1; at 3 the giant comes). */
	public static final AttachmentType<Integer> RAGGIA = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT).copyOnDeath().buildAndRegister(id("raggia"));

	@Override
	public void onInitialize() {
        CityPerformance.start();
		CORNICELLO = item("cornicello", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
				.attributes(ItemAttributeModifiers.builder().add(Attributes.LUCK,
						new AttributeModifier(id("cornicello_luck"), 2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND).build()));
		// Pizza Margherita: fills you up like nothing else
		PIZZA = item("pizza", Item::new, new Item.Properties().food(new FoodProperties.Builder().nutrition(10).saturationModifier(0.9f).build()));
		// Tazzulella 'e cafè: a shot of energy
		CAFE = item("cafe", Item::new, new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
				.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build(),
						Consumables.defaultDrink().consumeSeconds(0.8f).onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
								new MobEffectInstance(MobEffects.SPEED, 20 * 90, 0), new MobEffectInstance(MobEffects.HASTE, 20 * 90, 1)))).build()));
		CIABATTA_ITEM = item("ciabatta", CiabattaItem::new, new Item.Properties().stacksTo(16));
		CALZINI_SPORCHI = item("calzini_sporchi", CalziniSporchiItem::new, new Item.Properties().stacksTo(1)
				.component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
						net.minecraft.network.chat.Component.translatable("napuli.socks.hint"),
						net.minecraft.network.chat.Component.translatable("napuli.socks.effects")))));
		FORMAGGIO_PIEDE = item("formaggio_piede", FormaggioPiedeItem::new, new Item.Properties().stacksTo(16)
				.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.4f).alwaysEdible().build())
				.component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
						net.minecraft.network.chat.Component.translatable("napuli.cheese.flavour"),
						net.minecraft.network.chat.Component.translatable("napuli.cheese.bonuses"),
						net.minecraft.network.chat.Component.translatable("napuli.cheese.penalties")))));
		MOTORINO_ITEM = item("motorino", MotorinoItem::new, new Item.Properties().stacksTo(1));
		KNUCKLES = melee("tirapugni", 3, 2.5);
		KNIFE = melee("coltello", 4, 2.1);
		BOMBA_CARTA = item("bomba_carta", BombaCartaItem::new, new Item.Properties().stacksTo(8));
		BATON = melee("manganello", 5, 1.5);
		PISTOL = item("pistola", PistolaItem::new, new Item.Properties().durability(24));
		// 'O casco: the scooter helmet. Without it, the carabinieri stop you (and the boys on the scooter too)
		CASCO = item("casco", Item::new, new Item.Properties().stacksTo(1).durability(120)
				.attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR,
						new AttributeModifier(id("casco_armor"), 2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD).build())
				.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.HEAD)
						.setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_TURTLE).setAsset(net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, id("casco"))).build()));
		CityLifeConfig.load();
		NapoliKitchen.register();
		CityMaterials.register();
		Radio.register();
		Hud.register();
		NapoliNotice.register();
		CityCompanion.register();
		Pickpocket.register();
		Phones.register();
		ProximityChat.register();
		NpcSpeech.register();
        CityVerification.register();
		CityRailway.register();
		CityStreets.register();
        NapoliHomeDecor.register();
        CityArchitecture.register();
        NapoliFootballLink.register();
		MUNACIELLO_EGG = item("munaciello_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(MUNACIELLO));
		GIGANTE_EGG = item("munaciello_gigante_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(GIGANTE));
		NAPULETANO_EGG = item("napuletano_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(RESIDENT));
		// the mod's own page in the creative inventory (before, the items were only reachable with /give)
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("napulicraft"), net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
				.title(net.minecraft.network.chat.Component.translatable("itemGroup.napuli.napulicraft"))
				.icon(() -> new net.minecraft.world.item.ItemStack(CORNICELLO))
				.displayItems((params, out) -> {
					for (Item i : new Item[] {CORNICELLO, PIZZA, CAFE, CIABATTA_ITEM, CALZINI_SPORCHI, FORMAGGIO_PIEDE, MOTORINO_ITEM, CASCO, KNUCKLES, KNIFE, BATON, PISTOL, BOMBA_CARTA,
							NAPULETANO_EGG, MUNACIELLO_EGG, GIGANTE_EGG}) out.accept(i);
					NapoliKitchen.ITEMS.values().forEach(out::accept);
					Phones.ITEMS.values().forEach(out::accept);
					out.accept(Phones.CHARGER);
					out.accept(NapoliFurniture.WORKTOP);
					CityMaterials.ALL.forEach(out::accept);
					for (var machine : PublicGym.Machine.values()) out.accept(machine.block);
					out.accept(NapoliFurniture.SINK);
					out.accept(NapoliFurniture.LAMP);
					out.accept(NapoliFurniture.COFFEE_TABLE);
					out.accept(NapoliFurniture.KITCHEN_SET);
					out.accept(NapoliFurniture.BATH_SET);
					out.accept(NapoliFurniture.LAUNDRY_BASKET);
					out.accept(NapoliFurniture.COMPUTER);
					out.accept(NapoliFurniture.ENTRY_TRAY);
					out.accept(NapoliFurniture.POLTRONA);
					out.accept(NapoliFurniture.DIVANO);
					out.accept(NapoliFurniture.SEDIA_IMBOTTITA);
					out.accept(NapoliFurniture.SEDIA_SCRIVANIA);
					out.accept(NapoliFurniture.LAVASTOVIGLIE);
					out.accept(NapoliFurniture.LAVATRICE);
					out.accept(NapoliFurniture.ASCIUGATRICE);
					out.accept(NapoliFurniture.MICROONDE);
					out.accept(NapoliFurniture.TOSTAPANE);
					out.accept(NapoliFurniture.BOLLITORE);
					out.accept(NapoliFurniture.FRIGORIFERO);
					out.accept(NapoliFurniture.CAPPA);
					out.accept(NapoliFurniture.BIDET);
					out.accept(NapoliFurniture.WC);
					out.accept(NapoliFurniture.VASCA);
					out.accept(NapoliFurniture.SPECCHIO);
					out.accept(NapoliFurniture.TENDA);
					out.accept(NapoliFurniture.TERMOSIFONE);
					out.accept(NapoliFurniture.ASSE_STIRO);
					out.accept(NapoliFurniture.KIT_BAGNO);
					out.accept(NapoliFurniture.KIT_PULIZIA);
					out.accept(NapoliFurniture.KIT_TAVOLA);
					out.accept(NapoliFurniture.KIT_SCRIVANIA);
					out.accept(NapoliFurniture.RACCOLTA);
					out.accept(NapoliFurniture.APPENDIABITI);
					out.accept(NapoliFurniture.PLAID);
					out.accept(NapoliFurniture.OMBRELLONE);
					out.accept(NapoliFurniture.BARBECUE);
                    out.accept(NapoliFurniture.HAIR_DRYER);
                    out.accept(NapoliFurniture.SCALE);
                    out.accept(NapoliFurniture.VACUUM);
                    out.accept(NapoliFurniture.POWER_STRIP);
					out.accept(NapoliFurniture.TOAST);
					out.accept(NapoliFurniture.HOT_WATER);
					out.accept(NapoliFurniture.CLEAN_DISH);
					out.accept(NapoliFurniture.DIRTY_DISH);
					out.accept(NapoliFurniture.CLEAN_CLOTH);
					out.accept(NapoliFurniture.DIRTY_CLOTH);
					out.accept(NapoliFurniture.WET_CLOTH);
					out.accept(CityResidentShop.AIR);out.accept(CityResidentShop.WATER);
				}).build());

		FabricDefaultAttributeRegistry.register(MUNACIELLO, Munaciello.attributes());
		FabricDefaultAttributeRegistry.register(GIGANTE, MunacielloGigante.attributes());
		FabricDefaultAttributeRegistry.register(MOTORINO, Motorino.attributes());
		FabricDefaultAttributeRegistry.register(CITY_VEHICLE, CityVehicle.attributes());
		CityVehicle.register();
		ServerTickEvents.END_SERVER_TICK.register(Spawner::tick);
		ServerTickEvents.END_SERVER_TICK.register(Situazioni::tick);
		Situazioni.register();
		NapoliMap.registerSpawn();
		NapoliExpansion.register();
		DistrictLife.register();
		DistrictShops.register();
		CityNetwork.register();
		CityMobility.register();
        PoliceVehicles.register();
        VehicleTheft.register();
        CityResidentSafety.register();
		NapoliAtlasGuide.register();
		NapoliStreet.register();
		NapoliLocalJobs.register();
		// Detect illegal use of beds before the property access handler rejects the interaction.
		CityProperty.register();
		NapoliProperties.register();
		HouseGuests.register();
		NapoliBroadcast.register();
		FootballPractice.register();
		CityLeague.register();
		FootballSports.register();
		CityHeists.register();
		NapoliCivicLife.register();
		CitySimulation.register();
        CityHomeUse.register();
		CityGestures.register();
		CityHabits.register();
		CityJourneys.register();
		CityOccupations.register();
		CityDialogue.register();
		CityRelationships.register();
		CityServices.register();
		CityTransit.register();
		CityTraces.register();
		CityDomestic.register();
		DomesticMeals.register();
		CityRoutines.register();
		CityMovement.register();
		CitySocial.register();
		CityPersonalGoals.register();
		CityGatherings.register();
		CityReputation.register();
		CityEconomy.register();
		CityArrivals.register();
		CityAtmosphere.register();
		CityNeighborhood.register();
		CityNightlife.register();
		CityCalendar.register();
		NapoliEmployment.register();
		NapoliShower.register();
		NapoliFurniture.register();
		NapoliCertosa.register();
		PublicGym.register();
		NapoliHygiene.register();
		CityLivingHomes.register();
		CityEvents.register();
		NeighbourScenes.register();
		NeighbourAmbience.register();
		BakeryStock.register();
		CityPublicOrder.register();
		CityResidentShop.register();
		CityHousingFinance.register();
		FabricDefaultAttributeRegistry.register(RESIDENT, NapoliResident.attributes());
		ServerTickEvents.END_SERVER_TICK.register(NapoliScenes::tick);
		ServerTickEvents.END_SERVER_TICK.register(Carabinieri::tick);
		Carabinieri.register();
		ServerTickEvents.END_SERVER_TICK.register(GiorgiaMeloni::tick);
		ServerTickEvents.END_SERVER_TICK.register(FormaggioPiedeItem::tick);
		HousingCapacity.register();
		CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> {
			NapuliCommand.register(d);
			NapoliDialogue.register(d);
			NapoliStreet.commands(d);
			GiorgiaMeloni.register(d);
		});
		CityAccessibility.register();
        CityWorkIncidents.register();
        WorkResources.register();
        WorkCareer.register();
        CityOnboarding.register();
        DistrictCharacter.register();
        CityCharacter.register();
        CityPerformance.register();
        LOGGER.info("Napulicraft: jamme ja!");
	}
}
