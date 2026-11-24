package dev.napuli.client.mixin;

import dev.napuli.client.FootballActionClient;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class FootballPlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void napuli$footballPose(AvatarRenderState state, CallbackInfo callback) {
        var model = (PlayerModel) (Object) this;
        if (!FootballActionClient.pose(model, state.id, state.ageInTicks)) return;
        model.leftPants.loadPose(model.leftLeg.storePose());
        model.rightPants.loadPose(model.rightLeg.storePose());
        model.leftSleeve.loadPose(model.leftArm.storePose());
        model.rightSleeve.loadPose(model.rightArm.storePose());
        model.jacket.loadPose(model.body.storePose());
    }
}
