package dev.napuli.client.mixin;

import dev.napuli.client.music.UserMusic;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Songs from the player's folder (namespace napuli_user) are streamed from disk instead of from a resource pack. */
@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
	@Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
	private void napuli$userSong(Identifier location, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
		if (!UserMusic.NS.equals(location.getNamespace())) return;
		cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
			try {
				return UserMusic.open(location);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}, Util.nonCriticalIoPool()));
	}
}
