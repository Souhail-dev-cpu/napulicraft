package dev.napuli.client;

import dev.napuli.Motorino;
import dev.napuli.RadioTalk;
import dev.napuli.client.music.RadioStations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** Follows one nearby broadcast. The server chooses the episode and its timing. */
final class RadioTalkClient {
    private static Motorino source;
    private static long cue = RadioTalk.NO_CALL;
    private static int segmentStart = -1;
    private static SpatialVoice voice;

    private RadioTalkClient() {}

    static void stop(Minecraft mc) {
        stopVoice(mc);
        source = null;
        cue = RadioTalk.NO_CALL;
        segmentStart = -1;
    }

    private static void stopVoice(Minecraft mc) {
        if (voice == null) return;
        mc.getSoundManager().stop(voice);
        voice = null;
    }

    static void tick(Minecraft mc, Motorino bike) {
        if (bike == null) {
            if (source != null) stop(mc);
            return;
        }
        long update = bike.talkCue();
        int index = RadioTalk.index(update);
        if (index < 0 || index >= RadioTalk.CALLS.size()) {
            stop(mc);
            return;
        }
        var call = RadioTalk.CALLS.get(index);
        if (bike != source || update != cue) {
            stop(mc);
            source = bike;
            cue = update;
            if (bike.hasPassenger(mc.player))
                RadioClient.showBanner(RadioStations.TALK_NAME, label(call), RadioStations.TALK_COLOR);
        }
        long elapsed = mc.level.getGameTime() - RadioTalk.started(update);
        if (elapsed < 0) return;
        var segment = call.segment((int) Math.min(Integer.MAX_VALUE, elapsed));
        int next = segment == null ? -1 : segment.startTick();
        if (next == segmentStart) return;
        stopVoice(mc);
        segmentStart = next;
        // A listener arriving halfway through a sentence joins at the next clean handoff.
        if (segment != null && elapsed - next <= 14) {
            voice = new SpatialVoice(segment.sound(), bike);
            mc.getSoundManager().play(voice);
        }
    }

    static String onAir(Motorino bike) {
        int index = RadioTalk.index(bike.talkCue());
        return index >= 0 && index < RadioTalk.CALLS.size() ? label(RadioTalk.CALLS.get(index)) : "";
    }

    private static String label(RadioTalk.Call call) {
        if (call.caller().isEmpty()) return net.minecraft.network.chat.Component.translatable("napuli.ui.radio.listener").getString();
        return call.district().isEmpty() ? call.caller() : call.caller() + " · " + call.district();
    }

    private static final class SpatialVoice extends AbstractSoundInstance implements TickableSoundInstance {
        private final Motorino bike;

        SpatialVoice(Identifier sound, Motorino bike) {
            super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.bike = bike;
            volume = 1.15f;
            attenuation = Attenuation.LINEAR;
            tick();
        }

        @Override public boolean isStopped() { return bike.isRemoved(); }

        @Override public void tick() {
            x = bike.getX();
            y = bike.getY() + .8;
            z = bike.getZ();
        }
    }
}
