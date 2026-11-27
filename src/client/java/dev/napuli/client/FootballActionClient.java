package dev.napuli.client;

import dev.napuli.FootballAction;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.model.HumanoidModel;

public final class FootballActionClient {
    private record Playing(FootballAction.Move move, boolean left, int start) {}
    private static final Map<Integer, Playing> ACTIVE = new HashMap<>();
    private static int tick;
    private static net.minecraft.client.multiplayer.ClientLevel level;

    private FootballActionClient() {}

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FootballAction.TYPE, (packet, context) -> {
            var moves = FootballAction.Move.values();
            if (packet.move() >= 0 && packet.move() < moves.length) {
                syncLevel(context.client().level);
                if (moves[packet.move()] == FootballAction.Move.BENCH_STOP) ACTIVE.remove(packet.actor());
                else ACTIVE.put(packet.actor(), new Playing(moves[packet.move()], packet.left(), tick));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            syncLevel(client.level);
            tick++;
            ACTIVE.values().removeIf(pose -> tick - pose.start >= pose.move.ticks);
        });
    }

    private static void syncLevel(net.minecraft.client.multiplayer.ClientLevel current) {
        if (current == level) return;
        level = current;
        ACTIVE.clear();
    }

    public static boolean bench(int id) {
        var pose = ACTIVE.get(id);
        return pose != null && (pose.move == FootballAction.Move.BENCH_IDLE || pose.move == FootballAction.Move.BENCH_PRESS);
    }

    public static boolean active(int id) { return ACTIVE.containsKey(id); }

    public static boolean pose(HumanoidModel<?> model, int id, float age) {
        var active = ACTIVE.get(id);
        if (active == null) return false;
        float phase = Math.clamp((tick - active.start + age % 1) / active.move.ticks, 0, 1);
        float energy = (float) Math.sin(Math.PI * phase);
        float side = active.left ? -1 : 1;
        if (bench(id)) {
            float extension = active.move == FootballAction.Move.BENCH_PRESS ? energy : 0;
            model.body.xRot = 0;
            model.head.xRot = 0;
            model.head.yRot = 0;
            model.leftLeg.xRot = .08f;
            model.rightLeg.xRot = .08f;
            model.leftArm.xRot = -1.12f - extension * .48f;
            model.rightArm.xRot = model.leftArm.xRot;
            model.leftArm.zRot = -.38f + extension * .18f;
            model.rightArm.zRot = -model.leftArm.zRot;
            return true;
        }
        if (active.move == FootballAction.Move.CHEST_PRESS || active.move == FootballAction.Move.SHOULDER_PRESS) {
            boolean overhead = active.move == FootballAction.Move.SHOULDER_PRESS;
            model.leftArm.xRot = overhead ? -1.45f - energy * 1.55f : -.65f - energy * .92f;
            model.rightArm.xRot = model.leftArm.xRot;
            model.leftArm.zRot = -.54f + energy * .28f;
            model.rightArm.zRot = -model.leftArm.zRot;
            return true;
        }
        if (active.move == FootballAction.Move.ROULETTE || active.move == FootballAction.Move.FEINT
                || active.move == FootballAction.Move.SIDE_STEP) {
            float turn = active.move == FootballAction.Move.ROULETTE ? side * phase * (float) (Math.PI * 2) : side * energy * .55f;
            model.body.yRot += turn;
            model.head.yRot += turn;
            model.leftArm.yRot += turn;
            model.rightArm.yRot += turn;
            model.leftLeg.yRot += turn;
            model.rightLeg.yRot += turn;
            model.body.zRot += side * energy * .20f;
            model.leftArm.zRot -= energy * .45f;
            model.rightArm.zRot += energy * .45f;
            model.leftLeg.xRot -= energy * .46f;
            model.rightLeg.xRot += energy * .65f;
            return true;
        }

        float sweep = phase < .3f ? phase / .3f : 1 - (phase - .3f) / .7f;
        float strike = phase < .3f ? -sweep * .38f : energy * switch (active.move) {
            case PASS -> .82f;
            case DRIVE -> 1.32f;
            case CURL -> 1.07f;
            case CHIP -> .98f;
            default -> 0f;
        };
        model.rightLeg.xRot = -strike;
        model.leftLeg.xRot = energy * .20f;
        model.rightLeg.yRot += energy * (active.move == FootballAction.Move.CURL ? -.34f : -.12f);
        model.body.xRot += energy * (active.move == FootballAction.Move.CHIP ? -.18f : .14f);
        model.leftArm.zRot -= energy * .36f;
        model.rightArm.xRot += energy * .48f;
        return true;
    }
}
