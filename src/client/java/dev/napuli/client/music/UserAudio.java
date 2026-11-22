package dev.napuli.client.music;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.lwjgl.BufferUtils;

/**
 * Opens a song file from disk (.mp3, .ogg, .wav) as a Minecraft audio stream: always MONO (so it fades with distance
 * and can follow the scooter), starting at a given second, optionally cut after N seconds with a short fade.
 */
public final class UserAudio {
	private UserAudio() {}

	public static boolean supported(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav");
	}

	public static AudioStream open(File file, double startSeconds, double maxSeconds) throws IOException {
		String n = file.getName().toLowerCase(Locale.ROOT);
		Pcm pcm;
		if (n.endsWith(".mp3")) pcm = new Mp3Pcm(file, startSeconds);
		else if (n.endsWith(".ogg")) pcm = new OggPcm(file);
		else pcm = new WavPcm(file);
		double skip = n.endsWith(".mp3") ? 0 : startSeconds; // mp3 skips whole frames itself, much faster
		return new MonoStream(pcm, skip, maxSeconds);
	}

	/** interleaved 16-bit PCM, chunk by chunk */
	interface Pcm extends AutoCloseable {
		int rate();
		int channels();
		/** @return null at the end */
		short[] next() throws IOException;
		@Override void close() throws IOException;
	}

	static final class Mp3Pcm implements Pcm {
		private final InputStream in;
		private final Bitstream bits;
		private final Decoder decoder = new Decoder();
		private short[] first;
		private int rate = 44100, channels = 2;
		private boolean ended;

		Mp3Pcm(File f, double skipSeconds) throws IOException {
			in = new BufferedInputStream(new FileInputStream(f), 1 << 16);
			bits = new Bitstream(in);
			try {
				// skip whole frames without decoding them
				double skipped = 0;
				Header h;
				while (skipped < skipSeconds * 1000 && (h = bits.readFrame()) != null) {
					skipped += h.ms_per_frame();
					bits.closeFrame();
				}
				first = next();
			} catch (Exception e) {
				throw new IOException("MP3 non leggibile: " + f.getName(), e);
			}
		}

		@Override public int rate() { return rate; }
		@Override public int channels() { return channels; }

		@Override
		public short[] next() throws IOException {
			if (first != null) { short[] f = first; first = null; return f; }
			if (ended) return null;
			try {
				for (int guard = 0; guard < 64; guard++) {
					Header h = bits.readFrame();
					if (h == null) { ended = true; return null; }
					try {
						SampleBuffer out = (SampleBuffer) decoder.decodeFrame(h, bits);
						rate = out.getSampleFrequency();
						channels = out.getChannelCount();
						short[] s = new short[out.getBufferLength()];
						System.arraycopy(out.getBuffer(), 0, s, 0, s.length);
						return s;
					} catch (Exception badFrame) {
						// a broken frame (ID3 junk, cut file): skip it
					} finally {
						bits.closeFrame();
					}
				}
				ended = true;
				return null;
			} catch (Exception e) {
				ended = true;
				return null;
			}
		}

		@Override
		public void close() throws IOException {
			try { bits.close(); } catch (Exception ignored) {}
			in.close();
		}
	}

	static final class OggPcm implements Pcm {
		private final JOrbisAudioStream ogg;
		private final int rate, channels;

		OggPcm(File f) throws IOException {
			ogg = new JOrbisAudioStream(new BufferedInputStream(new FileInputStream(f), 1 << 16));
			rate = (int) ogg.getFormat().getSampleRate();
			channels = ogg.getFormat().getChannels();
		}

		@Override public int rate() { return rate; }
		@Override public int channels() { return channels; }

		@Override
		public short[] next() throws IOException {
			ByteBuffer b = ogg.read(16384);
			if (b == null || !b.hasRemaining()) return null;
			b.order(ByteOrder.nativeOrder());
			short[] s = new short[b.remaining() / 2];
			for (int i = 0; i < s.length; i++) s[i] = b.getShort();
			return s;
		}

		@Override public void close() throws IOException { ogg.close(); }
	}

	static final class WavPcm implements Pcm {
		private final AudioInputStream in;
		private final int rate, channels;
		private final byte[] buf = new byte[16384];

		WavPcm(File f) throws IOException {
			try {
				AudioInputStream raw = AudioSystem.getAudioInputStream(f);
				AudioFormat src = raw.getFormat();
				AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, src.getChannels(),
						src.getChannels() * 2, src.getSampleRate(), false);
				in = AudioSystem.getAudioInputStream(target, raw);
				rate = (int) target.getSampleRate();
				channels = target.getChannels();
			} catch (Exception e) {
				throw new IOException("WAV non leggibile: " + f.getName(), e);
			}
		}

		@Override public int rate() { return rate; }
		@Override public int channels() { return channels; }

		@Override
		public short[] next() throws IOException {
			int n = in.read(buf);
			if (n <= 0) return null;
			short[] s = new short[n / 2];
			for (int i = 0; i < s.length; i++) s[i] = (short) ((buf[2 * i] & 0xFF) | (buf[2 * i + 1] << 8));
			return s;
		}

		@Override public void close() throws IOException { in.close(); }
	}

	/** downmix to mono, skip the start, fade in, cut and fade out at the limit */
	static final class MonoStream implements AudioStream {
		private final Pcm pcm;
		private final AudioFormat format;
		private long skipFrames;
		private final long limitFrames, fadeIn, fadeOut;
		private long produced;
		private short[] pending;
		private int pendingPos;
		private boolean done;

		MonoStream(Pcm pcm, double skipSeconds, double maxSeconds) {
			this.pcm = pcm;
			int rate = pcm.rate();
			this.format = new AudioFormat(rate, 16, 1, true, false);
			this.skipFrames = (long) (skipSeconds * rate);
			this.limitFrames = maxSeconds > 0 ? (long) (maxSeconds * rate) : Long.MAX_VALUE;
			this.fadeIn = rate / 20;                                           // 50 ms: no click
			this.fadeOut = maxSeconds > 0 ? (long) (Math.min(1.0, maxSeconds / 5) * rate) : 0;
		}

		@Override public AudioFormat getFormat() { return format; }

		@Override
		public ByteBuffer read(int expectedSize) throws IOException {
			int frames = Math.max(1024, expectedSize / 2);
			ByteBuffer out = BufferUtils.createByteBuffer(frames * 2);
			int ch = Math.max(1, pcm.channels());
			while (out.remaining() >= 2 && !done) {
				if (pending == null || pendingPos >= pending.length) {
					pending = pcm.next();
					pendingPos = 0;
					if (pending == null) { done = true; break; }
					ch = Math.max(1, pcm.channels());
				}
				int sum = 0;
				for (int c = 0; c < ch && pendingPos < pending.length; c++) sum += pending[pendingPos++];
				if (skipFrames > 0) { skipFrames--; continue; }
				if (produced >= limitFrames) { done = true; break; }
				double v = sum / (double) ch;
				if (produced < fadeIn) v *= produced / (double) fadeIn;
				if (fadeOut > 0 && limitFrames - produced < fadeOut) v *= (limitFrames - produced) / (double) fadeOut;
				out.putShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v))));
				produced++;
			}
			out.flip();
			return out;
		}

		@Override public void close() throws IOException { pcm.close(); }
	}
}
