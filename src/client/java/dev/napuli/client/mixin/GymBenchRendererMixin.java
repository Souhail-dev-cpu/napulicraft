package dev.napuli.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import dev.napuli.client.FootballActionClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class GymBenchRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void napuli$recline(AvatarRenderState state, PoseStack pose, float bodyYaw, float scale, CallbackInfo ci) {
        if (!FootballActionClient.bench(state.id)) return;
        pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(bodyYaw - 180)));
        pose.translate(0, .03, 1.24);
        pose.mulPose(new Matrix4f().rotationX((float) (-Math.PI / 2)));
    }
}
