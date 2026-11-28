package dev.napuli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/** The station's programme and its server-synchronised cue clock. */
public final class RadioTalk {
    public static final int NO_CALL = -1;
    private static final int ID_BITS = 16;
    private static final int ID_MASK = (1 << ID_BITS) - 1;

    public record Call(String caller, String district, Identifier intro, Identifier voice, Identifier reply,
                       int introTicks, int voiceTicks, int replyTicks) {
        public int length() { return introTicks + voiceTicks + replyTicks + 16; }

        public Segment segment(int elapsed) {
            if (elapsed < introTicks) return new Segment(intro, 0);
            if (elapsed < introTicks + voiceTicks) return new Segment(voice, introTicks);
            if (elapsed < introTicks + voiceTicks + replyTicks)
                return new Segment(reply, introTicks + voiceTicks);
            return null;
        }
    }

    public record Segment(Identifier sound, int startTick) {}

    public static final List<Call> CALLS = loadCalls();

    private RadioTalk() {}

    private static List<Call> loadCalls() {
        var stream = Objects.requireNonNull(RadioTalk.class.getResourceAsStream("/assets/napuli/radio/programme.tsv"),
                "Missing Radio Vicoli programme");
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<Call> calls = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split("\t");
                var voice = Napuli.id("radio_" + columns[0]);
                int voiceTicks = Integer.parseInt(columns[1]);
                var call = switch (columns[2]) {
                    case "gennaro" -> new Call("Gennaro", "Palazzo Reale", Napuli.id("radio_gennaro"), voice,
                            Napuli.id("radio_chiusura_1"), 121, voiceTicks, 105);
                    case "signora" -> new Call("La signora al telefono", "", Napuli.id("radio_signora"), voice,
                            Napuli.id("radio_chiusura_2"), 151, voiceTicks, 95);
                    case "maria" -> new Call("Maria", "Quartieri Spagnoli", Napuli.id("radio_maria"), voice,
                            Napuli.id("radio_chiusura_1"), 133, voiceTicks, 105);
                    case "listener" -> new Call("", "", Napuli.id("radio_ascoltatori"), voice,
                            Napuli.id("radio_chiusura_2"), 109, voiceTicks, 95);
                    default -> throw new IllegalStateException("Unknown Radio Vicoli caller: " + columns[2]);
                };
                calls.add(call);
            }
            if (calls.size() < 2 || calls.size() > ID_MASK + 1)
                throw new IllegalStateException("Invalid Radio Vicoli programme size: " + calls.size());
            return List.copyOf(calls);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read Radio Vicoli programme", ex);
        }
    }

    static long cue(long started, int call) { return started << ID_BITS | call; }

    public static long started(long cue) { return cue >>> ID_BITS; }

    public static int index(long cue) { return cue == NO_CALL ? -1 : (int) (cue & ID_MASK); }
}
