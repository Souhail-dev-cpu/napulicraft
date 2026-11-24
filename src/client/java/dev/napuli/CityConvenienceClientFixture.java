package dev.napuli;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Only used by the disposable -Dnapuli.qoltest world, never by normal gameplay. */
public final class CityConvenienceClientFixture {
    public static volatile boolean ready,checked,effectsChecked;
    public static String house;
    private static int balance,price;
    private static String token;
    public static void prepare(ServerPlayer p){
        if(!Boolean.getBoolean("napuli.qoltest"))throw new IllegalStateException("Test fixture is disabled");
        conversations(p);
        var h=NapoliMergellina.PROPERTIES.getFirst();house=h.id();price=h.price();
        var owners=new HashMap<>(p.level().getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));owners.remove(house);p.level().setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));
        var accounts=new HashMap<>(p.level().getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()));accounts.remove(house);p.level().setAttached(CityHousingFinance.ACCOUNTS,Map.copyOf(accounts));HouseGuests.clear(p.level(),house);
        NapoliStreet.credit(p,20000-NapoliStreet.cash(p));p.setAttached(PlayerLedger.HISTORY,List.of());balance=NapoliStreet.cash(p);
        var at=h.sign().south();p.teleportTo(p.level(),at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0,true);ready=true;
    }
    public static void beforeConfirmation(ServerPlayer p){
        var q=p.getAttached(CompanionHousing.QUOTE);check(q!=null&&q.action().equals("compra"),"The server created a purchase quote");
        token=q.token();check(q.charge()==price&&NapoliStreet.cash(p)==balance,"Displaying the quote does not charge the player");
    }
    public static void afterConfirmation(ServerPlayer p){
        check(p.getUUID().equals(NapoliProperties.owner(p.level(),CityLivingHomes.find(house))),"Confirmation transferred ownership");
        check(NapoliStreet.cash(p)==balance-price,"Exactly the quoted price was charged");
        var history=p.getAttachedOrElse(PlayerLedger.HISTORY,List.of());check(history.size()==1&&history.getFirst().reason().equals("house_purchase")&&history.getFirst().amount()==-price,"The wallet history contains one accurately labeled housing charge");
        check(CompanionHousing.confirm(p,token)==0&&NapoliStreet.cash(p)==balance-price,"Replaying the confirmation cannot charge twice");checked=true;
    }
    public static void scooter(ServerPlayer p){
        if(!Boolean.getBoolean("napuli.qoltest"))throw new IllegalStateException("Test fixture is disabled");
        var bike=Napuli.MOTORINO.create(p.level(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);if(bike==null)throw new AssertionError("Scooter creation failed");bike.snapTo(p.getX()+8,p.getY(),p.getZ(),0,0);bike.setNoAi(true);p.level().addFreshEntity(bike);ScooterGarage.observe(bike,p);
    }
    public static void effects(ServerPlayer p){check(CityEffects.flags(p)==31,"Personal effect settings reached the authoritative server");effectsChecked=true;}
    private static void conversations(ServerPlayer p){
        var saved=p.getAttached(CityOnboarding.STEPS);
        var npc=NapoliResident.spawn(p.level(),NapoliResident.Role.TERESA,p.blockPosition().east(10),0);npc.setNoAi(true);
        try {
            p.setAttached(CityOnboarding.STEPS,CityOnboarding.JOB);
            NapoliDialogue.open(p,npc);
            check(p.getAttachedOrElse(CityOnboarding.STEPS,0)==CityOnboarding.JOB,"An out-of-range conversation cannot complete the introduction");
            npc.snapTo(p.getX()+2,p.getY(),p.getZ(),0,0);
            NapoliDialogue.open(p,npc);
            check(p.getAttachedOrElse(CityOnboarding.STEPS,0)==(CityOnboarding.JOB|CityOnboarding.TALK),"Talking to Teresa counts as meeting a neighbor and preserves the chosen job");
            NapoliDialogue.open(p,npc);
            check(p.getAttachedOrElse(CityOnboarding.STEPS,0)==(CityOnboarding.JOB|CityOnboarding.TALK),"Repeated conversations cannot duplicate or reset introduction progress");
            Napuli.LOGGER.info("[QOLTEST] Legacy neighbor conversation regression passed");
        } finally {npc.discard();if(saved==null)p.removeAttached(CityOnboarding.STEPS);else p.setAttached(CityOnboarding.STEPS,saved);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private CityConvenienceClientFixture(){}
}
