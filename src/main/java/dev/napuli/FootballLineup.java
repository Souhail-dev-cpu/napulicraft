package dev.napuli;

import java.util.function.IntPredicate;

final class FootballLineup {
    private FootballLineup() {}

    static int available(int role, int side, IntPredicate occupied) {
        if (side >= 0) return onSide(role, side, occupied);
        int home = onSide(role, 0, occupied);
        return home >= 0 ? home : onSide(role, 5, occupied);
    }

    private static int onSide(int role, int side, IntPredicate occupied) {
        if (!occupied.test(side + role)) return side + role;
        for (int offset : new int[]{2, 3, 1, 4, 0})
            if (!occupied.test(side + offset)) return side + offset;
        return -1;
    }
}
