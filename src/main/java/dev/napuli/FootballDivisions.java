package dev.napuli;

import java.util.List;

final class FootballDivisions {
    record Club(String id, String name, int color) {}

    enum Division {
        D(0, 0, 0, 0, 0, List.of(new Club("partenope", "Partenope", 0x319bdb),
                new Club("mergellina", "Mergellina", 0x31865b), new Club("centro", "Centro storico", 0xb84b45),
                new Club("fuorigrotta", "Fuorigrotta", 0x8258a5))),
        C(59, 4, 2, 16, 6, List.of(new Club("sorrento", "Sorrento", 0xbc342e),
                new Club("avellino", "Avellino", 0x26794c), new Club("benevento", "Benevento", 0xf2a728),
                new Club("casertana", "Casertana", 0x2b50a4))),
        B(66, 9, 5, 36, 15, List.of(new Club("bari", "Bari", 0xdf383d), new Club("palermo", "Palermo", 0xe68bab),
                new Club("spezia", "Spezia", 0xe4e4e4), new Club("venezia", "Venezia", 0xd87c32))),
        A(74, 16, 9, 64, 28, List.of(new Club("napoli", "Napoli", 0x319bdb), new Club("juventus", "Juventus", 0xe4e4e4),
                new Club("inter", "Inter", 0x294ab9), new Club("milan", "Milan", 0xba2529),
                new Club("roma", "Roma", 0x9d223b), new Club("lazio", "Lazio", 0x87b9df)));

        final int overall, matches, goals, passes, recoveries;
        final List<Club> clubs;

        Division(int overall, int matches, int goals, int passes, int recoveries, List<Club> clubs) {
            this.overall = overall;
            this.matches = matches;
            this.goals = goals;
            this.passes = passes;
            this.recoveries = recoveries;
            this.clubs = clubs;
        }

        String label() { return "Serie " + name(); }

        int roleTarget(FootballPractice.Position role) {
            return switch (role) {
                case ATTACCANTE -> goals;
                case CENTROCAMPISTA -> passes;
                case DIFENSORE -> recoveries;
            };
        }

        boolean qualifies(FootballPractice.Position role, int overall, int matches, int goals, int passes, int recoveries) {
            int performance = switch (role) {
                case ATTACCANTE -> goals;
                case CENTROCAMPISTA -> passes;
                case DIFENSORE -> recoveries;
            };
            return overall >= this.overall && matches >= this.matches && performance >= roleTarget(role);
        }
    }

    static Division division(String club) {
        for (var division : Division.values())
            for (var entry : division.clubs)
                if (entry.id.equals(club)) return division;
        return null;
    }

    static Club club(String id) {
        var division = division(id);
        if (division == null) return null;
        return division.clubs.stream().filter(club -> club.id.equals(id)).findFirst().orElseThrow();
    }

    private FootballDivisions() {}
}
