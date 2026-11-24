package dev.napuli;

import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class FootballRatingsGameTest {
    @GameTest public void eliteRatingsChangeTheOutcomeOnThePitch(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var game = new FootballPractice.Game(h.getLevel());
        try {
            h.assertTrue(game.substitute(player, 0), "A real player takes the striker slot");
            var seat = game.seat(player);
            int slot = game.slotOf(player);
            var opponent = game.team[6];
            for (var npc : game.team) if (npc != null) npc.setPos(390, 64, 12);

            train(player, "DIFENSORE", 0);
            FootballPractice.updateMovement(player, game);
            double baseline = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            approach(game, player, slot, opponent);
            game.playBall();
            h.assertTrue(game.owner == 6, "A close challenge wins against the untrained striker");

            train(player, "DIFENSORE", 65);
            FootballPractice.updateMovement(player, game);
            h.assertTrue(FootballPractice.pace(player) == 99 && player.getAttributeValue(Attributes.MOVEMENT_SPEED) > baseline,
                    "99 pace raises the player's live movement attribute");
            approach(game, player, slot, opponent);
            game.playBall();
            h.assertTrue(game.owner == slot, "The slower defender cannot steal from the 99-pace runner while chasing");
            opponent.setPos(400.55, 64, 33.5);
            game.motion[6] = new Vec3(-.21, 0, 0);
            game.playBall();
            h.assertTrue(game.owner == 6, "A defender already in front can still challenge a fast player");

            opponent.setPos(390, 64, 12);
            train(player, "ATTACCANTE", 0);
            double normalShot = shoot(game, player, slot);
            h.assertTrue(game.home == 0, "The unassisted wide shot misses");
            train(player, "ATTACCANTE", 65);
            h.assertTrue(FootballPractice.shooting(player) == 99, "The striker reaches 99 shooting");
            double eliteShot = shoot(game, player, slot);
            h.assertTrue(eliteShot > normalShot && game.home == 1,
                    "The same input at 99 shooting gives a harder shot and scores");

            train(player, "DIFENSORE", 0);
            prepareInterception(game, seat);
            h.assertTrue(game.tryInterception(player) == 0, "Base defense cannot reach a ball 2.8 blocks away");
            train(player, "DIFENSORE", 65);
            prepareInterception(game, seat);
            h.assertTrue(FootballPractice.defending(player) == 99 && game.tryInterception(player) == 1 && game.owner == slot,
                    "99 defense wins possession in the same interception");
            var choices = Map.of(FootballAction.Move.ROULETTE, 20, FootballAction.Move.FEINT, 10, FootballAction.Move.SIDE_STEP, 14);
            for (var entry : choices.entrySet()) {
                seat.protectedUntil = 0;
                seat.stamina = 100;
                player.setDeltaMovement(Vec3.ZERO);
                h.assertTrue(game.skill(player, entry.getKey()) == 1 && seat.stamina == 100 - entry.getValue(),
                        "Each skill works through the match and spends its own stamina: " + entry.getKey());
                h.assertTrue(game.owner == slot && seat.dribbleEnds - seat.dribbleStarted == entry.getKey().ticks
                                && player.getDeltaMovement().horizontalDistanceSqr() > .06,
                        "The skill moves the carrier and keeps control for the animation interval");
                h.assertTrue(game.skill(player, entry.getKey()) == 0, "A skill cannot be stacked during the same animation");
            }
        } finally {
            FootballPractice.updateMovement(player, null);
            game.removeActors();
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    @GameTest public void roleAndTrainingXpRemainSpecificToTheCareer(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            h.assertTrue(FootballPractice.selectRole(player, "ATTACCANTE") && !FootballPractice.selectRole(player, "DIFENSORE"),
                    "The first choice defines the career role");
            int base = FootballPractice.shooting(player);
            int pace = FootballPractice.pace(player);
            h.assertTrue(FootballPractice.overall(player) == 55, "Overall is the rounded mean of the five ratings");
            h.assertTrue(FootballGym.earnXp(player, "shooting") == 40 && FootballPractice.shooting(player) == base,
                    "A completed primary drill awards XP before increasing the rating");
            FootballGym.earnXp(player, "shooting");
            FootballGym.earnXp(player, "shooting");
            h.assertTrue(FootballPractice.shooting(player) == base + 1 && FootballGym.progressXp(player, "shooting") == 20
                            && FootballPractice.pace(player) == pace,
                    "Three primary sessions earn one point and carry 20 XP forward for that rating only");
            player.setAttached(FootballPractice.ROLE_TRAINING, Map.of("ATTACCANTE", 100));
            h.assertTrue(FootballPractice.overall(player) == 99 && FootballGym.earnXp(player, "shooting") == 0,
                    "Individual ratings and overall stop at 99");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    private static void train(ServerPlayer player, String position, int sessions) {
        player.setAttached(FootballPractice.POSITION, position);
        player.setAttached(FootballPractice.ROLE_TRAINING, Map.of(position, sessions));
    }

    private static void approach(FootballPractice.Game game, ServerPlayer player, int slot, NapoliResident opponent) {
        player.setPos(400, 64, 33.5);
        player.setYRot(-90);
        game.seat(player).movement = new Vec3(.26, 0, 0);
        opponent.setPos(400.70, 64, 33.5);
        game.motion[6] = new Vec3(.21, 0, 0);
        game.setOwner(slot);
        game.held = 20;
    }

    private static double shoot(FootballPractice.Game game, ServerPlayer player, int slot) {
        player.setPos(421, 64, 33.5);
        player.setYRot(-61);
        game.setOwner(slot);
        game.playBall();
        if (!game.playerKick(player, FootballPractice.Kick.DRIVE)) throw new IllegalStateException("Shot input was refused");
        double initial = game.velocity.horizontalDistance();
        for (int step = 0; step < 26; step++) game.playBall();
        return initial;
    }

    private static void prepareInterception(FootballPractice.Game game, FootballPractice.Seat seat) {
        seat.player.setPos(400, 64, 33.5);
        seat.player.setYRot(-90);
        seat.nextInterception = 0;
        game.setOwner(-1);
        game.kicker = -1;
        game.ball = new Vec3(402.8, 64, 33.5);
    }
}
