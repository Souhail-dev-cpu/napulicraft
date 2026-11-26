package dev.napuli.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.napuli.client.CompanionScreen;
import dev.napuli.client.PhoneClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The default O binding otherwise gets swallowed by Minecraft's global friends shortcut. */
@Mixin(Minecraft.class)
public abstract class CompanionMenuKeyMixin {
    @Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true)
    private void napuli$openCityMenu(InputConstants.Key key, boolean controlDown, CallbackInfoReturnable<Boolean> cir) {
        var client=(Minecraft) (Object) this;
        if (CompanionScreen.handleOpenKey(client, key)||PhoneClient.handleOpenKey(client,key)) cir.setReturnValue(true);
    }
}
