package dev.napuli.client.mixin;

import dev.napuli.CityVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep standing passengers upright while vanilla still attaches them securely to the moving carriage. */
@Mixin(PlayerModel.class)
abstract class VehiclePassengerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("HEAD"))
    private void napuli$cabinPose(AvatarRenderState state,CallbackInfo ci){
        var city=Minecraft.getInstance().level;if(city==null)return;var passenger=city.getEntity(state.id);
        if(passenger!=null&&passenger.getVehicle() instanceof CityVehicle vehicle&&vehicle.standing(passenger))state.isPassenger=false;
    }
}
