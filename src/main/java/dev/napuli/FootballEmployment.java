package dev.napuli;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The signed club is the source of truth for the player's main job. */
final class FootballEmployment {
    static final String OBJECTIVE = "qol.work:football";

    enum Terms {
        D("amateur", 12), C("semi_pro", 22), B("professional", 38), A("top_flight", 60);

        final String key;
        final int appearancePay;

        Terms(String key, int appearancePay) {
            this.key = key;
            this.appearancePay = appearancePay;
        }

        Component label() {
            return Component.translatable("napuli.football.job." + key);
        }
    }

    static Terms terms(String club) {
        var division = FootballDivisions.division(club);
        return division == null ? null : Terms.valueOf(division.name());
    }

    static boolean hasContract(ServerPlayer player) {
        var member = player.getAttached(CityLeague.MEMBER);
        return member != null && terms(member.team()) != null;
    }

    static Component summary(ServerPlayer player) {
        var member = player.getAttached(CityLeague.MEMBER);
        var terms = terms(member.team());
        return Component.translatable("napuli.football.job.summary", CityLeague.name(member.team()), terms.label(), terms.appearancePay);
    }

    static NapoliFactions.Objective objective(ServerPlayer player) {
        if (player.level().dimension() != NapoliMap.LEVEL || !hasContract(player)) return null;
        var match = nextMatch(player);
        boolean ready = match != null && match.day() <= NapoliBroadcast.day(player.level()) && !FootballPractice.isPlaying(player);
        return new NapoliFactions.Objective(OBJECTIVE, ready ? FootballFacilities.reception() : null);
    }

    static Component objectiveText(ServerPlayer player) {
        var member = player.getAttached(CityLeague.MEMBER);
        if (FootballPractice.isPlaying(player))
            return Component.translatable("napuli.football.job.playing", CityLeague.name(member.team()));
        var next = nextMatch(player);
        if (next == null) return summary(player);
        String opponent = member.team().equals(next.home()) ? next.away() : next.home();
        return Component.translatable("napuli.football.job.next", CityLeague.name(member.team()), CityLeague.name(opponent), next.day());
    }

    private static CityLeague.Fixture nextMatch(ServerPlayer player) {
        return CityLeague.season(player.level()).fixtures().stream()
                .filter(f -> !f.state().equals("played") && CityLeague.side(player, f) >= 0)
                .min(java.util.Comparator.comparingLong(CityLeague.Fixture::day)).orElse(null);
    }

    private FootballEmployment() {}
}
