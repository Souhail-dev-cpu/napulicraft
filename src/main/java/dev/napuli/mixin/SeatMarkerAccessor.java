package dev.napuli.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ArmorStand.class)
public interface SeatMarkerAccessor {
    @Invoker("setMarker") void napuli$setMarker(boolean marker);
}
