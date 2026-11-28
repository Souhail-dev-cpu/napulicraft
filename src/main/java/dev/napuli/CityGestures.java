package dev.napuli;

import net.minecraft.server.level.ServerLevel;

/** Small server-selected gestures; clients animate the same pose at their own frame rate. */
public final class CityGestures {
    public enum Gesture { NONE, CHOP, STIR, CARRY, EAT, WASH, YAWN, WAVE, SAD, HAPPY, SWEEP, EXAMINE, WRITE, TEACH, PACK, PHONE_CALL }
    private CityGestures() {}
    static void register() {}
    static Gesture action(NapoliHouseholds.Activity activity) {
        return switch(activity) {
            case PREPARE -> Gesture.CHOP;
            case COOK -> Gesture.STIR;
            case SET_TABLE, FETCH, FRIDGE -> Gesture.CARRY;
            case EAT -> Gesture.EAT;
            case CLEAN, DISHES, WASH -> Gesture.WASH;
            case STUDY -> Gesture.WRITE;
            case CELEBRATE -> Gesture.HAPPY;
            default -> Gesture.NONE;
        };
    }
    static void tick(NapoliResident npc,ServerLevel city) {
        if(npc.tickCount%10!=0||npc.role().fighter()||npc.role().spirit()||npc.entityTags().contains("napuli_practice_actor"))return;
        var citizen=NapoliCivicLife.profile(city,npc.getUUID());
        var life=citizen==null?null:CitySimulation.life(city,citizen.id());
        int mood=life==null?70:life.needs().mood(),energy=life==null?80:life.needs().sleep();
        npc.setExpression(mood,energy);
        if(npc.gestureUntil()>city.getGameTime())return;
        if (citizen != null && citizen.profession() == NapoliCivicLife.Job.CARPENTER
                && npc.getMainHandItem().is(net.minecraft.world.item.Items.IRON_AXE))
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        Gesture gesture=npc.isSleeping()?Gesture.NONE:action(npc.activity());
        if(gesture==Gesture.NONE&&!npc.getOffhandItem().isEmpty())gesture=Gesture.CARRY;
        if(gesture==Gesture.NONE&&!npc.getNavigation().isInProgress()&&!npc.isPassenger()) {
            long moment=Math.floorMod(city.getGameTime()+Math.floorMod(npc.getUUID().hashCode(),600),600);
            if(energy<30&&moment<55)gesture=Gesture.YAWN;
            else if(mood<30)gesture=Gesture.SAD;
            else if(npc.activity()==NapoliHouseholds.Activity.SOCIAL&&moment<45&&city.players().stream().anyMatch(p->p.distanceToSqr(npc)<36&&npc.hasLineOfSight(p)))gesture=Gesture.WAVE;
        }
        npc.setGesture(gesture);
    }
    static double pace(NapoliResident npc) {
        return (.95+.20*npc.vitality()/100.0)*(npc.level().isRaining()?.88:1);
    }
}
