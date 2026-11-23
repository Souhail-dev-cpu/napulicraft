package dev.napuli.client;

import net.minecraft.util.Mth;

/** A repeatable sequence of passes and changes of possession for the fictional broadcast. */
final class FootballPlay {
    record Point(float x, float y) {
        Point mix(Point to, float progress) {
            return new Point(Mth.lerp(progress, x, to.x), Mth.lerp(progress, y, to.y));
        }
    }

    record Player(int number, String name, float x, float y) {}
    record Frame(Point[] players, Point ball, int focus) {}

    private static final Player[] NAPOLI = {
            new Player(1, "Russo", .06f, .50f),
            new Player(2, "Esposito", .24f, .15f), new Player(4, "Greco", .21f, .38f),
            new Player(5, "Romano", .21f, .62f), new Player(3, "De Luca", .24f, .85f),
            new Player(8, "Marino", .42f, .31f), new Player(6, "Coppola", .39f, .55f),
            new Player(10, "Ferrara", .47f, .72f), new Player(7, "Ruggiero", .59f, .15f),
            new Player(9, "Vitale", .64f, .50f), new Player(11, "Caruso", .59f, .85f)
    };
    private static final Player[] JUVE = {
            new Player(1, "Riva", .06f, .50f),
            new Player(2, "Gallo", .24f, .15f), new Player(4, "Fontana", .21f, .38f),
            new Player(5, "Moretti", .21f, .62f), new Player(3, "Serra", .24f, .85f),
            new Player(8, "Costa", .42f, .31f), new Player(6, "Conti", .39f, .55f),
            new Player(10, "Bianchi", .47f, .72f), new Player(7, "Leone", .59f, .15f),
            new Player(9, "Barbieri", .64f, .50f), new Player(11, "Marchetti", .59f, .85f)
    };
    private static final int[] PASSING = {2, 5, 8, 9, 11, 17, 16, 19, 20, 0, 6, 7, 10, 9, 13, 15, 18, 21, 3, 6};
    private static final int PASS_TICKS = 28;

    private FootballPlay() {}

    static int playerCount() { return NAPOLI.length + JUVE.length; }
    static boolean home(int index) { return index < NAPOLI.length; }
    static Player player(int index) { return home(index) ? NAPOLI[index] : JUVE[index - NAPOLI.length]; }

    static Frame at(double tick) {
        int step = Math.floorMod((long) Math.floor(tick / PASS_TICKS), PASSING.length);
        float progress = (float) (tick / PASS_TICKS - Math.floor(tick / PASS_TICKS));
        int from = PASSING[step], to = PASSING[(step + 1) % PASSING.length];
        float travel = smooth(Mth.clamp((progress - .42f) / .58f, 0f, 1f));
        Point target = anchor(from).mix(anchor(to), travel);
        float homePossession = Mth.lerp(travel, home(from) ? 1f : -1f, home(to) ? 1f : -1f);
        Point[] positions = new Point[playerCount()];
        for (int i = 0; i < positions.length; i++) {
            var p = player(i);
            Point base = anchor(i);
            boolean home = home(i);
            if (p.number() == 1) {
                positions[i] = new Point(base.x(), Mth.lerp(.40f, .5f, Mth.clamp(target.y(), .32f, .68f)));
                continue;
            }
            float dir = home ? 1 : -1;
            float pace = (float) Math.sin(tick / 12 + i * 1.7) * .016f;
            float shift = (target.x() - .5f) * .27f + homePossession * .045f;
            float distance = Math.abs(base.x() - target.x()) + Math.abs(base.y() - target.y());
            float press = home ? Math.max(0, -homePossession) : Math.max(0, homePossession);
            float approach = press * Mth.clamp(.45f - distance, 0f, .20f);
            float run = i == from || i == to ? (float) Math.sin(progress * Math.PI) * .045f * dir : 0;
            positions[i] = new Point(Mth.clamp(base.x() + shift + run + (target.x() - base.x()) * approach, .08f, .92f),
                    Mth.clamp(base.y() + (target.y() - .5f) * .15f + pace + (target.y() - base.y()) * approach, .08f, .92f));
        }
        Point ball = positions[from].mix(positions[to], travel);
        float lead = Mth.lerp(travel, home(from) ? .032f : -.032f, home(to) ? .032f : -.032f);
        return new Frame(positions, new Point(ball.x() + lead, ball.y()), travel < .5f ? from : to);
    }

    private static Point anchor(int index) {
        Player player = player(index);
        return home(index) ? new Point(player.x(), player.y()) : new Point(1 - player.x(), 1 - player.y());
    }

    private static float smooth(float value) { return value * value * (3 - 2 * value); }
}
