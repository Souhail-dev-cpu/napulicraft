package dev.napuli;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

final class CityResidentShop {
    static final Item AIR=bottle("ampolla_aria_napoli",false);
    static final Item WATER=bottle("acqua_influencer",true);
    private static Item bottle(String name,boolean water) {
        var key=ResourceKey.create(Registries.ITEM,Napuli.id(name));
        return Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key).stacksTo(16)
                .food(new FoodProperties.Builder().nutrition(0).saturationModifier(0).alwaysEdible().build(),Consumables.defaultDrink().build())
                .usingConvertsTo(Items.GLASS_BOTTLE)) {
            @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity user) {
                var result=super.finishUsingItem(stack,level,user);
                if(user instanceof ServerPlayer p) {
                    p.addEffect(new MobEffectInstance(water?MobEffects.SPEED:MobEffects.LUCK,water?600:400,0));
                    if(water){p.addEffect(new MobEffectInstance(MobEffects.HUNGER,600,0));p.causeFoodExhaustion(4);}
                    p.sendOverlayMessage(Component.literal(water?"Acqua da bagno dell'influencer: Speed per 30 secondi… e fame!":"Hai aperto un po' d'aria di Napoli. Che fortuna!"));
                }
                return result;
            }
        });
    }
    static boolean merchant(NapoliCivicLife.Citizen c) { return c.profession()==NapoliCivicLife.Job.AIR_SELLER||c.profession()==NapoliCivicLife.Job.INFLUENCER; }
    static void offer(ServerPlayer p,NapoliCivicLife.Citizen c) {
        if(!merchant(c))return;
        boolean water=c.profession()==NapoliCivicLife.Job.INFLUENCER;int cost=shopPrice(p,c.id(),water?12:4);
        var title=Component.literal(water?"Acqua da bagno personale · influencer adulta":"Ampolle d'aria di Napoli");
        var action=new ActionButton(new CommonButtonData(Component.literal("Compra · "+cost+" €"),250),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/bottega "+c.id()))));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(title,Optional.empty(),true,false,DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(water?"Prodotto fittizio: Speed per 30 s, Fame per 30 s. Una bottiglia viene consumata.":"Un souvenir originale: Fortuna per 20 s."),260)),List.of()),List.of(action),Optional.empty(),1)));
    }
    static int buy(ServerPlayer p,UUID id) {
        if(p.level().dimension()!=NapoliMap.LEVEL||!p.isAlive()||p.isSpectator()||Carabinieri.isJailed(p))return 0;
        var c=NapoliCivicLife.profile(p.level(),id);var entity=p.level().getEntity(id);
        if(c==null||!merchant(c)||entity==null||entity.distanceToSqr(p)>36||c.age(NapoliBroadcast.day(p.level()))<18)return 0;
        if(!CityProfessions.open(c.profession(),NapoliBroadcast.time(p.level()),NapoliBroadcast.day(p.level()))) {
            NapoliNotice.show(p, Component.literal("La bottega è chiusa: ripassa durante il turno."));return 0;
        }
        if(CitySocial.bond(p.level(),id,p.getUUID()).trust()<15)return 0;
        boolean water=c.profession()==NapoliCivicLife.Job.INFLUENCER;int cost=shopPrice(p,id,water?12:4);
        if(NapoliStreet.cash(p)<cost)return 0;
        var stack=new ItemStack(water?WATER:AIR);if(!p.getInventory().add(stack))return 0;
        NapoliStreet.credit(p,-cost);
        CitySocial.purchased(p.level(),id,p.getUUID());
        var census=NapoliCivicLife.census(p.level());
        p.level().setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream()
                .map(person->person.id().equals(id)?person.withMoney(Math.min(99999,person.balance()+cost),person.job()):person).toList()));return 1;
    }
    private static int shopPrice(ServerPlayer p, UUID seller, int base) {
        return CityEconomy.price(p,seller,base);
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("bottega").then(Commands.argument("venditore",StringArgumentType.word())
                .executes(c->{try{return buy(c.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(c,"venditore")));}catch(IllegalArgumentException invalid){return 0;}}))));
    }
    private CityResidentShop() {}
}
