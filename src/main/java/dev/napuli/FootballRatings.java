package dev.napuli;

final class FootballRatings {
    static final int MAX = 99;

    private FootballRatings() {}

    static int trained(int base, int sessions, int fitness, boolean primary) {
        return Math.clamp(base + sessions * (primary ? 3 : 1) + fitness, 1, MAX);
    }

    static double paceBonus(int pace) { return (pace - 50) * .009; }

    static double shotBoost(int shooting) { return (shooting - 35) * .006; }

    static double aimAssist(int shooting) { return Math.clamp((shooting - 55) / 44.0, 0, 1); }

    static double interceptionRange(int defending) { return 1.45 + defending * .016; }

    static double minimumFacing(int defending) { return .64 - defending * .005; }

    static double tackleDistance(int defense, int strength) {
        return Math.clamp(.88 + (defense - strength) * .007, .36, 1.24);
    }

    static double staminaDrain(int speed, int stamina) {
        return Math.max(.22, .84 - speed * .002 - stamina * .004);
    }
}
