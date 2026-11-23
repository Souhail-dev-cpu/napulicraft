package dev.napuli;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FootballPractice {
    private static final int KICKOFF_SLOT = 4, MATCH_TICKS = 20 * 150;
    private static final String ACTOR = "napuli_practice_actor", BALL = "napuli_practice_ball";
    private static final AABB ARENA = new AABB(358, 62, 2, FootballGround.MAX_X + 6, 79, FootballGround.MAX_Z + 6);
    private static final String[] NAMES = {"Russo", "Greco", "Marino", "Esposito", "Vitale",
            "Riva", "Serra", "Costa", "Leone", "Barbieri"};
    private static final double[] DEPTH = {2, 22, 25, 25, 33};
    private static final int[] WIDTH = {0, 0, -13, 13, 0};
    private static final WeakHashMap<ServerLevel, Game> GAMES = new WeakHashMap<>();
    private static final AttachmentType<Career> CAREER = AttachmentRegistry.<Career>builder()
            .persistent(Career.CODEC).copyOnDeath().buildAndRegister(Napuli.id("football_career"));
    private static final AttachmentType<Long> RUN_STARTED = AttachmentRegistry.create(Napuli.id("football_training_run"));

    private enum Position {
        ATTACCANTE(68, 65, 34, 51, 55), CENTROCAMPISTA(54, 62, 52, 52, 66), DIFENSORE(38, 52, 70, 69, 58);

        final int shooting, speed, defense, strength, stamina;

        Position(int shooting, int speed, int defense, int strength, int stamina) {
            this.shooting = shooting;
            this.speed = speed;
            this.defense = defense;
            this.strength = strength;
            this.stamina = stamina;
        }
    }

    private static final AttachmentType<String> POSITION = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).copyOnDeath().buildAndRegister(Napuli.id("football_position"));

    private static final AttachmentType<java.util.Map<String, Integer>> ROLE_TRAINING =
            AttachmentRegistry.<java.util.Map<String, Integer>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().buildAndRegister(Napuli.id("football_role_training"));
    private static final net.minecraft.resources.Identifier MOVEMENT_BONUS = Napuli.id("football_pace");

    private enum Kick { PASS, DRIVE, CURL, CHIP }

    private FootballPractice() {}

    public record MatchStats(int passes, int shots, int interceptions, double playerKickDistance, double playerKickHeight) {}

    public static MatchStats statistics(ServerLevel level) {
        Game game = GAMES.get(level);
        return game == null ? new MatchStats(0, 0, 0, 0, 0)
                : new MatchStats(game.passes, game.shots, game.interceptions, game.playerKickDistance, game.playerKickHeight);
    }

    public static int fitness(ServerPlayer player) { return career(player).fitness(); }

    static boolean isPlaying(ServerPlayer p) { var game=GAMES.get(p.level());return game!=null&&game.controls(p); }
    static void awardLeague(ServerPlayer p,int goals) {var c=career(p);p.setAttached(CAREER,new Career(c.matches()+1,c.goals()+goals,c.fitness(),c.trainingDay()));}
    static int startLeague(ServerPlayer p,CityLeague.Fixture fixture) {
        if(!arrive(p))return 0;var city=p.level();var game=GAMES.get(city);
        if(game!=null&&!game.leagueId.equals(fixture.id())){p.sendOverlayMessage(Component.literal("Il campo è occupato da un'altra partita."));return 0;}
        fixture=CityLeague.begin(city,fixture);
        if(game==null){game=new Game(city);game.leagueId=fixture.id();game.home=fixture.homeGoals();game.away=fixture.awayGoals();game.ticks=fixture.elapsed();
            for(int i=0;i<10;i++){int color=i%5==0?0xe1a538:CityLeague.color(i<5?fixture.home():fixture.away());var shirt=game.team[i].getItemBySlot(EquipmentSlot.CHEST);shirt.set(DataComponents.DYED_COLOR,new DyedItemColor(color));}GAMES.put(city,game);}
        if(!game.controls(p)&&!game.substitute(p,CityLeague.side(p,fixture)))return 0;
        return 1;
    }

    private record Career(int matches, int goals, int fitness, long trainingDay) {
        static final Career BEGINNER = new Career(0, 0, 0, Long.MIN_VALUE);
        static final Codec<Career> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("matches").forGetter(Career::matches),
                Codec.INT.fieldOf("goals").forGetter(Career::goals),
                Codec.INT.fieldOf("fitness").forGetter(Career::fitness),
                Codec.LONG.fieldOf("training_day").forGetter(Career::trainingDay)).apply(i, Career::new));
    }

    private static final AttachmentType<GlobalPos> RETURN_POINT =
            AttachmentRegistry.create(Napuli.id("football_return_point"));

    static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(FootballMenu.TYPE, FootballMenu.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(FootballControls.TYPE, FootballControls.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, kind) -> dispatcher.register(
                Commands.literal("calcio").executes(c -> profile(c.getSource().getPlayerOrException()))
                        .then(Commands.literal("guarda").executes(c -> enter(c.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("gioca").executes(c -> enter(c.getSource().getPlayerOrException(), true))
                                .then(Commands.literal("napoli").executes(c -> enter(c.getSource().getPlayerOrException(), true, 0)))
                                .then(Commands.literal("ospiti").executes(c -> enter(c.getSource().getPlayerOrException(), true, 5))))
                        .then(Commands.literal("esci").executes(c -> exit(c.getSource().getPlayerOrException())))
                        .then(Commands.literal("torna").executes(c -> returnToEntry(c.getSource().getPlayerOrException()))).then(kickCommand("passa", Kick.PASS)).then(kickCommand("tira", Kick.DRIVE))
                        .then(kickCommand("effetto", Kick.CURL)).then(kickCommand("pallonetto", Kick.CHIP))
                        .then(Commands.literal("chiedi").executes(c -> offBall(c.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("intercetta").executes(c -> offBall(c.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("ruolo").then(positionOption("attaccante", Position.ATTACCANTE))
                                .then(positionOption("centrocampista", Position.CENTROCAMPISTA)).then(positionOption("difensore", Position.DIFENSORE)))
                        .then(Commands.literal("statistiche").executes(c -> attributes(c.getSource().getPlayerOrException())))
                        .then(Commands.literal("roulette").executes(c -> skill(c.getSource().getPlayerOrException())))
                        .then(Commands.literal("palestra").executes(c -> gym(c.getSource().getPlayerOrException())))));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (level.dimension() == NapoliMap.LEVEL && entity instanceof NapoliResident npc)
                FootballFacilities.updateStaffAppearance(npc);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GAMES.entrySet().removeIf(entry -> {
            if (entry.getKey().getServer() != server) return false;
            entry.getValue().saveLeague(entry.getKey());
            entry.getValue().removeActors();
            return true;
        }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var city = server.getLevel(NapoliMap.LEVEL);
            Game game = city == null ? null : GAMES.get(city);
            if (game != null && !game.tick(city)) GAMES.remove(city);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean inMatch = city != null && player.level() == city && game != null && GAMES.get(city) == game
                        && game.controls(player) && inside(player.position());
                updateMovement(player, inMatch ? game : null);
                if (city != null && player.level() == city) training(player, city);
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL || hand != InteractionHand.MAIN_HAND || player.isSpectator())
                return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (!(player instanceof ServerPlayer server) || !player.getMainHandItem().isEmpty()
                    || pos.getY() != 63 || !inside(Vec3.atCenterOf(pos))) return InteractionResult.PASS;
            Game game = GAMES.get(server.level());
            return game != null && game.playerKick(server, player.isShiftKeyDown() ? Kick.DRIVE : Kick.PASS)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }

    static boolean interact(NapoliResident npc, net.minecraft.world.entity.player.Player player) {
        boolean trainer = npc.entityTags().contains(FootballFacilities.TRAINER_TAG);
        if (!trainer && !npc.entityTags().contains(FootballFacilities.RECEPTION_TAG)) return false;
        if (player instanceof ServerPlayer server && server.level().dimension() == NapoliMap.LEVEL
                && !server.isSpectator() && server.distanceToSqr(npc) <= 36 && !Carabinieri.isJailed(server)) {
            if (trainer) gym(server);
            else menu(server);
        }
        return true;
    }

    private static void menu(ServerPlayer player) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, FootballMenu.TYPE)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new FootballMenu(player.getAttached(RETURN_POINT) != null));
            return;
        }
        var actions = new java.util.ArrayList<>(List.of(
                option("watch_option", "guarda"), option("play_option", "gioca napoli"),
                option("away_option", "gioca ospiti"),
                option("training_option", "palestra"), option("profile_option", ""), option("league_option", "campionato")));
        if (player.getAttached(RETURN_POINT) != null) actions.add(option("return_option", "torna"));
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(
                Component.translatable("napuli.football.menu"), Optional.empty(), true, false, DialogAction.CLOSE,
                List.of(), List.of()), actions, Optional.empty(), 2)));
    }

    private static ActionButton option(String label, String command) {
        return new ActionButton(new CommonButtonData(Component.translatable("napuli.football." + label), 132),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(command.isEmpty() ? "/calcio" : "/calcio " + command))));
    }

    private static boolean arrive(ServerPlayer player) {
        if (player.isSpectator() || Carabinieri.isJailed(player) || Carabinieri.isWanted(player)) return false;
        GlobalPos origin = GlobalPos.of(player.level().dimension(), player.blockPosition());
        if (player.level().dimension() != NapoliMap.LEVEL && NapoliMap.visit(player) == 0) return false;
        if (origin.dimension() != NapoliMap.LEVEL || !atGround(Vec3.atCenterOf(origin.pos())))
            player.setAttached(RETURN_POINT, origin);
        return true;
    }

    private static boolean atGround(Vec3 pos) {
        return pos.x >= 348 && pos.x <= FootballGround.MAX_X + 6 && pos.z >= -4 && pos.z <= FootballGround.MAX_Z + 6;
    }

    private static int returnToEntry(ServerPlayer p) {
        if (p.level().dimension() != NapoliMap.LEVEL || !atGround(p.position())
                || Carabinieri.isJailed(p) || Carabinieri.isWanted(p)) return 0;
        GlobalPos origin = p.getAttached(RETURN_POINT);
        if (origin == null) {
            say(p, "no_return");
            return 0;
        }
        ServerLevel destination = p.level().getServer().getLevel(origin.dimension());
        BlockPos pos = origin.pos();
        if (destination == null || !destination.getBlockState(pos).isAir()
                || !destination.getBlockState(pos.above()).isAir() || !destination.getBlockState(pos.below()).isSolidRender()) {
            say(p, "no_return");
            return 0;
        }
        ServerLevel city = p.level();
        if (!p.teleportTo(destination, pos.getX() + .5, pos.getY(), pos.getZ() + .5, Set.of(), p.getYRot(), p.getXRot(), true))
            return 0;
        Game game = GAMES.get(city);
        if (game != null && game.controls(p)) game.restoreNpc(city, p);
        p.removeAttached(RETURN_POINT);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kickCommand(String name, Kick shot) {
        return Commands.literal(name).executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            Game game = GAMES.get(player.level());
            if (game == null || !game.controls(player)) {
                say(player, "not_playing");
                return 0;
            }
            if (game.playerKick(player, shot)) return 1;
            say(player, "no_ball");
            return 0;
        });
    }

    private static int offBall(ServerPlayer player, boolean intercept) {
        Game game = GAMES.get(player.level());
        if (game == null || !game.controls(player) || !inside(player.position()) || Carabinieri.isJailed(player)) {
            say(player, "not_playing");
            return 0;
        }
        return intercept ? game.tryInterception(player) : game.requestPass(player);
    }

    private static int enter(ServerPlayer player, boolean play) {
        return enter(player, play, -1);
    }

    private static int enter(ServerPlayer player, boolean play, int side) {
        if (!arrive(player)) return 0;
        ServerLevel city = player.level();
        Game game = GAMES.computeIfAbsent(city, Game::new);
        if (play) {
            if(!game.leagueId.isEmpty()){var fixture=CityLeague.fixture(city,game.leagueId);side=fixture==null?-1:CityLeague.side(player,fixture);if(side<0)return 0;}
            if (!game.controls(player) && !game.substitute(player, side)) {
                say(player, "team_full");
                return 0;
            }
            say(player, "controls");
            say(player, game.slotOf(player) < 5 ? "team_home" : "team_away");
        } else {
            if (game.controls(player)) game.restoreNpc(city, player);
            teleport(player, FootballGround.STAND, 0);
            say(player, "watch");
        }
        return 1;
    }

    private static int exit(ServerPlayer player) {
        Game game = GAMES.get(player.level());
        if (game == null || !game.controls(player)) return 0;
        game.restoreNpc(player.level(), player);
        teleport(player, FootballGround.STAND, 0);
        return 1;
    }

    private static int gym(ServerPlayer player) {
        if (!arrive(player)) return 0;
        Game game = GAMES.get(player.level());
        if (game != null && game.controls(player)) game.restoreNpc(player.level(), player);
        teleport(player, FootballGround.START_RUN, 0);
        player.removeAttached(RUN_STARTED);
        say(player, "train");
        return 1;
    }

    private static void training(ServerPlayer player, ServerLevel city) {
        long now = city.getGameTime();
        Long start = player.getAttached(RUN_STARTED);
        if (player.blockPosition().distSqr(FootballGround.START_RUN) <= 1 && start == null) {
            player.setAttached(RUN_STARTED, now);
        } else if (start != null && (now - start > 160 || Math.abs(player.getX() - 354.5) > 2 || player.getY() > 65.5)) {
            player.removeAttached(RUN_STARTED);
        } else if (start != null && player.blockPosition().distSqr(FootballGround.FINISH_RUN) <= 1) {
            player.removeAttached(RUN_STARTED);
            if (now - start < 30 || player.isCreative() || player.isSpectator()) return;
            Career career = career(player);
            long day = NapoliBroadcast.day(city);
            if (day <= career.trainingDay() || fullyTrained(player)) {
                say(player, "rest");
                return;
            }
            var trained = new java.util.HashMap<>(player.getAttachedOrElse(ROLE_TRAINING, java.util.Map.of()));
            trained.merge(position(player).name(), 1, Integer::sum);
            player.setAttached(ROLE_TRAINING, java.util.Map.copyOf(trained));
            int fitness = Math.min(10, career.fitness() + 1);
            player.setAttached(CAREER, new Career(career.matches(), career.goals(), fitness, day));
            say(player, "trained", fitness);
        }
    }

    private static Career career(ServerPlayer p) { return p.getAttachedOrElse(CAREER, Career.BEGINNER); }

    private static Position position(ServerPlayer p) {
        return Position.valueOf(p.getAttachedOrElse(POSITION, Position.ATTACCANTE.name()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> positionOption(String key, Position position) {
        return Commands.literal(key).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            Game game = GAMES.get(p.level());
            if (game != null && game.controls(p)) return 0;
            p.setAttached(POSITION, position.name());
            return attributes(p);
        });
    }

    private static int attributes(ServerPlayer p) {
        Position role = position(p);
        say(p, "attributes", Component.translatable("napuli.football.position." + role.name().toLowerCase(java.util.Locale.ROOT)),
                shooting(p), pace(p), defending(p), strength(p), staminaRating(p));
        return 1;
    }

    private static int roleTraining(ServerPlayer p) {
        return p.getAttachedOrElse(ROLE_TRAINING, java.util.Map.of()).getOrDefault(position(p).name(), 0);
    }

    private static void updateMovement(ServerPlayer player, Game game) {
        var movement = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        if (game == null) {
            if (movement.hasModifier(MOVEMENT_BONUS)) movement.removeModifier(MOVEMENT_BONUS);
            return;
        }
        double bonus = FootballRatings.paceBonus(pace(player));
        var old = movement.getModifier(MOVEMENT_BONUS);
        if (old == null || old.amount() != bonus) movement.addOrUpdateTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(MOVEMENT_BONUS, bonus,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static int shooting(ServerPlayer p) {
        return FootballRatings.trained(position(p).shooting, roleTraining(p), fitness(p), position(p) == Position.ATTACCANTE);
    }

    private static int pace(ServerPlayer p) {
        return FootballRatings.trained(position(p).speed, roleTraining(p), fitness(p), position(p) == Position.CENTROCAMPISTA);
    }

    private static int defending(ServerPlayer p) {
        return FootballRatings.trained(position(p).defense, roleTraining(p), fitness(p), position(p) == Position.DIFENSORE);
    }

    private static int strength(ServerPlayer p) {
        return FootballRatings.trained(position(p).strength, roleTraining(p), fitness(p), position(p) == Position.DIFENSORE);
    }

    private static int staminaRating(ServerPlayer p) {
        return FootballRatings.trained(position(p).stamina, roleTraining(p), fitness(p), position(p) == Position.CENTROCAMPISTA);
    }

    private static boolean fullyTrained(ServerPlayer p) {
        return shooting(p) == 99 && pace(p) == 99 && defending(p) == 99 && strength(p) == 99 && staminaRating(p) == 99;
    }

    private static int capacity(ServerPlayer p) { return 60 + staminaRating(p); }

    private static int skill(ServerPlayer p) {
        Game game = GAMES.get(p.level());
        return game == null ? 0 : game.roulette(p);
    }

    private static int profile(ServerPlayer p) {
        Career c = career(p);
        say(p, "profile", c.matches(), c.goals(), c.fitness());
        return 1;
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        NapoliNotice.show(p, Component.translatable("napuli.football." + key, args));
    }

    private static void teleport(ServerPlayer player, BlockPos pos, float yaw) {
        player.teleportTo(player.level(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, Set.of(), yaw, 0, true);
    }

    private static Vec3 spot(int slot) {
        int role = slot % 5, direction = slot < 5 ? 1 : -1;
        int goal = slot < 5 ? FootballGround.MIN_X : FootballGround.MAX_X;
        return new Vec3(goal + direction * DEPTH[role] + .5, 64, FootballGround.MID_Z + direction * WIDTH[role] + .5);
    }

    private static final class Seat {
        final ServerPlayer player;
        double stamina;
        int requestUntil, nextInterception, protectedUntil, nextFoul, goals, leagueTicks;
        Vec3 previous, movement = Vec3.ZERO;

        Seat(ServerPlayer player) {
            this.player = player;
            stamina = capacity(player);
        }

        void tick() {
            Vec3 current = player.position();
            movement = previous == null ? Vec3.ZERO : flat(current.subtract(previous));
            previous = current;
            if (player.isSprinting()) stamina = Math.max(0, stamina - FootballRatings.staminaDrain(pace(player), staminaRating(player)));
            else stamina = Math.min(capacity(player), stamina + .32 + staminaRating(player) * .004);
            if (stamina <= 1) player.setSprinting(false);
        }
    }

    private static final class Game {
        final NapoliResident[] team = new NapoliResident[10];
        final Display.ItemDisplay marker;
        final Vec3[] motion = new Vec3[team.length];
        final Seat[] players = new Seat[team.length];
        String leagueId="";
        Vec3 ball = spot(KICKOFF_SLOT).add(.55, 0, 0), velocity = Vec3.ZERO;
        int ticks, unattended, home, away, owner = -1, kicker = -1, pickupDelay, held;
        int passes, shots, interceptions;
        double playerKickDistance, playerKickHeight, curve;

        Game(ServerLevel city) {
            for (int x = 352; x <= FootballGround.MAX_X + 6; x += 16)
                for (int z = 0; z <= FootballGround.MAX_Z + 6; z += 16)
                city.getChunkAt(new BlockPos(x, 64, z));
            for (var npc : city.getEntitiesOfClass(NapoliResident.class, ARENA, n -> n.entityTags().contains(ACTOR))) npc.discard();
            for (var item : city.getEntitiesOfClass(ItemEntity.class, ARENA, n -> n.entityTags().contains(BALL))) item.discard();
            for (var display : city.getEntitiesOfClass(Display.ItemDisplay.class, ARENA, n -> n.entityTags().contains(BALL))) display.discard();
            for (int i = 0; i < team.length; i++) spawn(city, i);
            marker = new Display.ItemDisplay(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("item_display")), city);
            ItemStack model = new ItemStack(Items.PAPER);
            model.set(DataComponents.ITEM_MODEL, Napuli.id("pallone"));
            marker.setItemStack(model);
            marker.setItemTransform(ItemDisplayContext.FIXED);
            marker.setPosRotInterpolationDuration(2);
            marker.setWidth(1);
            marker.setHeight(1);
            marker.setShadowRadius(.32f);
            marker.setPos(ball.x, ball.y + .44, ball.z);
            marker.addTag(BALL);
            marker.setNoGravity(true);
            marker.setPermanentlyInvulnerable(true);
            city.addFreshEntity(marker);
            setOwner(KICKOFF_SLOT);
        }

        private void spawn(ServerLevel city, int slot) {
            var npc = NapoliResident.spawn(city, NapoliResident.Role.PASSERBY, BlockPos.containing(spot(slot)), slot < 5 ? -90 : 90);
            npc.addTag(ACTOR);
            npc.setEmploymentAppearance(NapoliResident.Role.PASSERBY, (slot * 5 + 2) % 18);
            npc.setNoAi(true);
            npc.setPermanentlyInvulnerable(true);
            npc.setCustomName(Component.literal(NAMES[slot]));
            npc.setCustomNameVisible(true);
            var shirt = new ItemStack(Items.LEATHER_CHESTPLATE);
            shirt.set(DataComponents.DYED_COLOR, new DyedItemColor(slot % 5 == 0 ? 0xe1a538 : slot < 5 ? 0x319bdb : 0xf4eeee));
            npc.setItemSlot(EquipmentSlot.CHEST, shirt);
            team[slot] = npc;
            motion[slot] = Vec3.ZERO;
        }

        int slotOf(ServerPlayer p) {
            for (int slot = 0; slot < players.length; slot++)
                if (players[slot] != null && players[slot].player == p) return slot;
            return -1;
        }

        boolean controls(ServerPlayer p) { return slotOf(p) >= 0; }

        Seat seat(ServerPlayer p) { return players[slotOf(p)]; }

        boolean substitute(ServerPlayer p, int side) {
            Position role = FootballPractice.position(p);
            int preferred = switch (role) {
                case ATTACCANTE -> 4;
                case CENTROCAMPISTA -> 2;
                case DIFENSORE -> 1;
            };
            int slot = FootballLineup.available(preferred, side, index -> players[index] != null);
            if (slot < 0) return false;
            Vec3 at = position(slot);
            players[slot] = new Seat(p);
            if(!leagueId.isEmpty()){var fixture=CityLeague.fixture(p.level(),leagueId);var saved=fixture==null?null:fixture.players().get(p.getUUID().toString());if(saved!=null){players[slot].goals=saved.goals();players[slot].leagueTicks=saved.ticks();}}
            if (team[slot] != null) team[slot].discard();
            team[slot] = null;
            teleport(p, BlockPos.containing(at), slot < 5 ? -90 : 90);
            if (owner == slot) held = 0;
            return true;
        }

        void restoreNpc(ServerLevel city, ServerPlayer player) {
            int slot = slotOf(player);
            if (slot < 0) return;
            saveLeague(city);
            if (owner == slot) setOwner(-1);
            if (kicker == slot) kicker = -1;
            players[slot] = null;
            spawn(city, slot);
        }

        private Vec3 position(int index) {
            if (players[index] != null) return players[index].player.position();
            return team[index] == null ? spot(index) : team[index].position();
        }

        private Vec3 movement(int index) {
            return players[index] == null ? motion[index] : players[index].movement;
        }

        int requestPass(ServerPlayer p) {
            int slot = slotOf(p);
            if (owner < 0 || owner == slot || (owner < 5) != (slot < 5)) return 0;
            Seat request = players[slot];
            if (ticks < request.requestUntil) return 0;
            request.requestUntil = ticks + 45;
            if (players[owner] != null) say(players[owner].player, "teammate_requests", p.getDisplayName());
            return 1;
        }

        int roulette(ServerPlayer p) {
            int slot = slotOf(p);
            if (slot < 0 || owner != slot || !inside(p.position())) return 0;
            Seat seat = players[slot];
            if (seat.stamina < 20 || ticks < seat.protectedUntil) return 0;
            seat.stamina -= 20;
            seat.protectedUntil = ticks + 16;
            Vec3 forward = flat(p.getLookAngle()).normalize();
            Vec3 sidestep = forward.yRot(p.isShiftKeyDown() ? 1.12f : -1.12f).scale(.52);
            p.push(sidestep.x, .08, sidestep.z);
            say(p, "roulette");
            return 1;
        }

        int tryInterception(ServerPlayer player) {
            int slot = slotOf(player);
            Seat seat = players[slot];
            if (seat.stamina < 8 || ticks < seat.nextInterception
                    || owner >= 0 && (owner < 5) == (slot < 5) || kicker >= 0 && (kicker < 5) == (slot < 5)) return 0;
            if (owner >= 0 && players[owner] != null && !playerDuel(players[owner], slot)) return 0;
            seat.nextInterception = ticks + 16;
            seat.stamina -= 8;
            Vec3 toBall = flat(ball.subtract(player.position()));
            double range = FootballRatings.interceptionRange(defending(player));
            if (toBall.lengthSqr() > range * range || Math.abs(player.getY() - ball.y) > 1.3
                    || toBall.lengthSqr() > .16 && flat(player.getLookAngle()).normalize().dot(toBall.normalize()) < FootballRatings.minimumFacing(defending(player))) {
                say(player, "intercept_missed");
                return 0;
            }
            interceptions++;
            setOwner(slot);
            return 1;
        }

        boolean playerKick(ServerPlayer p, Kick shot) {
            int slot = slotOf(p);
            if (slot < 0 || owner != slot || !inside(p.position())) return false;
            Seat seat = players[slot];
            Vec3 facing = flat(p.getLookAngle()).normalize();
            if (facing.lengthSqr() < .01) return false;
            if (shot != Kick.PASS) {
                shots++;
                int rating = shooting(p);
                double fitnessBoost = FootballRatings.shotBoost(rating);
                facing = aimShot(slot, facing, rating);
                if (seat.stamina < 12) fitnessBoost -= .16;
                seat.stamina = Math.max(0, seat.stamina - 5);
                switch (shot) {
                    case DRIVE -> kick(facing, 1.08 + fitnessBoost);
                    case CURL -> {
                        double bend = p.getZ() > FootballGround.MID_Z ? 1 : -1;
                        kick(facing.yRot((float) (-bend * .13)), .92 + fitnessBoost);
                        curve = bend * .036;
                    }
                    case CHIP -> {
                        kick(facing, .73 + fitnessBoost * .6);
                        velocity = velocity.add(0, .43, 0);
                    }
                    default -> throw new IllegalStateException("Unexpected shot " + shot);
                }
            } else {
                Vec3 target = null;
                double best = .30;
                int first = slot < 5 ? 0 : 5;
                for (int i = first; i < first + 5; i++) {
                    if (i == slot) continue;
                    Vec3 to = position(i).subtract(ball);
                    double alignment = flat(to).normalize().dot(facing);
                    if (alignment <= best || to.lengthSqr() > 24 * 24) continue;
                    best = alignment;
                    target = position(i);
                }
                if (target == null) { passes++; kick(facing, .64); }
                else pass(target);
            }
            return true;
        }

        private Vec3 aimShot(int slot, Vec3 facing, int rating) {
            double goalX = slot < 5 ? FootballGround.MAX_X + 1 : FootballGround.MIN_X;
            double centreZ = FootballGround.MID_Z + .5;
            double keeperZ = position(slot < 5 ? 5 : 0).z;
            double targetZ = centreZ + (keeperZ >= centreZ ? -1.85 : 1.85);
            Vec3 towardsGoal = flat(new Vec3(goalX, ball.y, targetZ).subtract(ball));
            if (towardsGoal.horizontalDistanceSqr() > 33 * 33 || facing.dot(towardsGoal.normalize()) < .78)
                return facing;
            return facing.lerp(towardsGoal.normalize(), FootballRatings.aimAssist(rating)).normalize();
        }

        boolean tick(ServerLevel city) {
            for (Seat seat : players) {
                if (seat == null) continue;
                ServerPlayer p = seat.player;
                if (city.getServer().getPlayerList().getPlayer(p.getUUID()) != p || p.level() != city || !p.isAlive()
                        || !ARENA.contains(p.position()) || Carabinieri.isJailed(p) || p.isSpectator()) {
                    restoreNpc(city, p);
                } else {seat.tick();seat.leagueTicks++;}
            }
            boolean audience = false;
            for (ServerPlayer p : city.players()) if (ARENA.contains(p.position())) { audience = true; break; }
            unattended = audience ? 0 : unattended + 1;
            if (unattended > 100 || ticks >= MATCH_TICKS) {
                saveLeague(city);
                if (ticks >= MATCH_TICKS) finish(city);
                removeActors();
                return false;
            }
            if (!audience) return true;
            ticks++;
            if(ticks%200==0)saveLeague(city);
            driveTeams();
            playBall();
            if (pickupDelay > 0) pickupDelay--;
            marker.setPos(ball.x, ball.y + .44, ball.z);
            if (ticks % 20 == 0) hud(city);
            if (ticks % 5 == 0) {
                for (int slot = 0; slot < players.length; slot++) {
                    Seat seat = players[slot];
                    if (seat == null || !inside(seat.player.position())
                            || !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(seat.player, FootballControls.TYPE)) continue;
                    boolean homeSide = slot < 5;
                    int situation = owner < 0 ? (kicker >= 0 && (kicker < 5) == homeSide ? FootballControls.TEAM_PASS : FootballControls.FREE)
                            : owner == slot ? FootballControls.YOURS
                            : (owner < 5) == homeSide ? FootballControls.TEAM : FootballControls.OPPONENT;
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(seat.player,
                            new FootballControls(situation, (int) (100 * seat.stamina / capacity(seat.player)), !homeSide));
                }
            }
            return true;
        }

        private void removeActors() {
            for (var npc : team) if (npc != null) npc.discard();
            marker.discard();
        }

        private void saveLeague(ServerLevel city) {
            if(leagueId.isEmpty())return;var roster=new java.util.HashMap<String,CityLeague.Participant>();
            for(int i=0;i<players.length;i++)if(players[i]!=null){var s=players[i];roster.put(s.player.getUUID().toString(),new CityLeague.Participant(i<5?0:5,s.leagueTicks,s.goals));}
            CityLeague.progress(city,leagueId,home,away,ticks,roster);
        }

        private void driveTeams() {
            Vec3 projected = ball.add(velocity.scale(7));
            int homePress = nearest(0, projected, -1);
            int awayPress = nearest(5, projected, -1);
            int homeCover = nearest(0, projected, homePress);
            int awayCover = nearest(5, projected, awayPress);
            for (int slot = 0; slot < team.length; slot++) {
                var npc = team[slot];
                if (npc == null) continue;
                int role = slot % 5, direction = slot < 5 ? 1 : -1;
                boolean inPossession = owner >= 0 && (owner < 5) == (slot < 5);
                Vec3 base = spot(slot), pos = npc.position(), target;
                double speed = .17;
                if (slot == owner) {
                    // Carry into the available lane before looking to pass or shoot.
                    int opponent = slot < 5 ? awayPress : homePress;
                    Vec3 defender = opponent < 0 ? null : position(opponent);
                    double lane = defender != null && defender.distanceToSqr(pos) < 36 ? (pos.z < defender.z ? -4 : 4) : 0;
                    target = pos.add(direction * 6, 0, lane);
                    speed = .19;
                } else if (role == 0) {
                    double depth = (projected.x - base.x) * direction;
                    target = owner < 0 && depth > -1 && depth < 7 && Math.abs(projected.z - base.z) < 6
                            ? projected : base.add(direction * Math.clamp((ball.x - base.x) * direction * .07, 0, 4),
                            0, Math.clamp((ball.z - base.z) * .23, -4, 4));
                    speed = .21;
                } else if (!inPossession && slot == (slot < 5 ? homePress : awayPress)) {
                    target = projected;
                    speed = .30;
                } else if (!inPossession && owner >= 0 && slot == (slot < 5 ? homeCover : awayCover)) {
                    double depth = Math.max(12, (ball.x - ownGoal(slot)) * direction - 7);
                    double side = Math.clamp((FootballGround.MID_Z + .5 - ball.z) * .45, -5, 5);
                    target = new Vec3(ownGoal(slot) + direction * depth, 64, ball.z + side);
                    speed = .23;
                } else if (inPossession) {
                    target = supportLane(slot);
                    speed = .22;
                } else {
                    double advance = (ball.x - FootballGround.MID_X - .5) * .38;
                    double depth = (base.x + advance - ownGoal(slot)) * direction;
                    double width = (base.z - FootballGround.MID_Z - .5) * .72;
                    double shift = (ball.z - FootballGround.MID_Z - .5) * .35;
                    target = new Vec3(ownGoal(slot) + direction * Math.max(14, depth),
                            64, FootballGround.MID_Z + .5 + width + shift);
                }
                target = new Vec3(Math.clamp(target.x, FootballGround.MIN_X + 1.5, FootballGround.MAX_X - .5),
                        64, Math.clamp(target.z, FootballGround.MIN_Z + 1.5, FootballGround.MAX_Z - .5));
                // Sprint speeds for the CPU mirror its positional strengths.
                speed *= .86 + FootballRatings.paceBonus(npcPace(role));
                Vec3 delta = flat(target.subtract(pos));
                Vec3 desired = delta.normalize().scale(Math.min(speed, delta.horizontalDistance() * .22));
                Vec3 movement = motion[slot].lerp(desired, .24);
                motion[slot] = movement;
                npc.move(net.minecraft.world.entity.MoverType.SELF, movement);
                Vec3 facing = movement.horizontalDistanceSqr() > .0025 ? movement : flat(ball.subtract(pos));
                if (facing.lengthSqr() > .01) {
                    float angle = (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
                    float turn = Math.clamp(net.minecraft.util.Mth.wrapDegrees(angle - npc.getYRot()), -12f, 12f);
                    npc.setYRot(npc.getYRot() + turn);
                    npc.setYBodyRot(npc.getYRot());
                    npc.setYHeadRot(npc.getYRot());
                }
            }
        }

        private int nearest(int first, Vec3 target, int excluded) {
            int closest = -1;
            double distance = Double.POSITIVE_INFINITY;
            for (int i = first + 1; i < first + 5; i++) {
                if (i == excluded || team[i] == null) continue;
                double candidate = flat(position(i).subtract(target)).lengthSqr();
                if (candidate < distance) {
                    distance = candidate;
                    closest = i;
                }
            }
            return closest;
        }

        private static double ownGoal(int slot) {
            return (slot < 5 ? FootballGround.MIN_X : FootballGround.MAX_X) + .5;
        }

        private Vec3 supportLane(int slot) {
            int role = slot % 5, sign = slot < 5 ? 1 : -1;
            double depth = (ball.x - ownGoal(slot)) * sign;
            double forward = switch (role) { case 1 -> -9; case 4 -> 13; default -> 5; };
            double lane = switch (role) { case 2 -> -14; case 3 -> 14; case 1 -> 5; default -> -4; };
            double x = ownGoal(slot) + sign * Math.clamp(depth + forward, 15, 68);
            double z = FootballGround.MID_Z + .5 + lane * sign + (ball.z - FootballGround.MID_Z - .5) * .23;
            Vec3 best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int offset : new int[]{0, -5, 5}) {
                Vec3 target = new Vec3(x, 64, Math.clamp(z + offset, FootballGround.MIN_Z + 2, FootballGround.MAX_Z - 1));
                double score = -Math.abs(offset) * .15;
                for (int foe = slot < 5 ? 5 : 0; foe < (slot < 5 ? 10 : 5); foe++)
                    if (segmentDistance(position(foe), ball, target) < 3) score -= 3;
                if (target.distanceToSqr(ball) < 36) score -= 4;
                if (score > bestScore) {
                    bestScore = score;
                    best = target;
                }
            }
            return best;
        }

        private void playBall() {
            if (owner >= 0) {
                Vec3 pos = position(owner);
                int direction = owner < 5 ? 1 : -1;
                Seat carrier = players[owner];
                Vec3 travel = carrier != null ? flat(carrier.player.getLookAngle()) : motion[owner];
                if (travel.lengthSqr() < .0025) travel = new Vec3(direction, 0, 0);
                ball = new Vec3(pos.x, 64, pos.z).add(travel.normalize().scale(.62));
                held++;
                for (int i = owner < 5 ? 5 : 0; i < (owner < 5 ? 10 : 5); i++) {
                    if (held > (carrier == null ? 24 : 18) && players[i] == null && (carrier != null
                            ? playerDuel(carrier, i) : ball.distanceToSqr(position(i)) < .62)) {
                        interceptions++;
                        setOwner(i);
                        return;
                    }
                }
                if (carrier == null) {
                    if (held >= 14 && held % 4 == 2) npcKick();
                } else if (!inside(pos)) {
                    setOwner(-1);
                }
                return;
            }
            Vec3 previous = ball;
            ball = ball.add(velocity);
            if (kicker >= 0 && players[kicker] != null) {
                playerKickDistance += velocity.horizontalDistance();
                playerKickHeight = Math.max(playerKickHeight, ball.y - 64);
            }
            double vertical = velocity.y;
            if (ball.y > 64 || vertical > 0) vertical -= .036;
            if (ball.y < 64) {
                ball = new Vec3(ball.x, 64, ball.z);
                vertical = vertical < -.18 ? -vertical * .28 : 0;
            }
            velocity = flat(velocity).yRot((float) curve).scale(ball.y > 64 ? .992 : .978).add(0, vertical, 0);
            curve *= .94;
            if (!inside(ball)) {
                int edge = ball.x <= FootballGround.MIN_X + .5 ? FootballGround.MIN_X : FootballGround.MAX_X;
                double line = edge + .5;
                boolean crossed = (previous.x - line) * (ball.x - line) <= 0 && ball.x != previous.x;
                double z = crossed ? previous.z + (ball.z - previous.z) * (line - previous.x) / (ball.x - previous.x) : ball.z;
                double height = crossed ? previous.y + (ball.y - previous.y) * (line - previous.x) / (ball.x - previous.x) : ball.y;
                if (crossed && height < 66.55 && Math.abs(z - FootballGround.MID_Z - .5) < 2.65) {
                    boolean homeScored = edge == FootballGround.MAX_X;
                    if (homeScored) home++; else away++;
                    if (kicker >= 0 && players[kicker] != null && (kicker < 5) == homeScored) players[kicker].goals++;
                    ball = mid();
                    setOwner(-1);
                } else {
                    ball = new Vec3(Math.clamp(ball.x, FootballGround.MIN_X + 1, FootballGround.MAX_X),
                            64, Math.clamp(ball.z, FootballGround.MIN_Z + 1, FootballGround.MAX_Z));
                    velocity = Vec3.ZERO;
                    kicker = -1;
                }
                return;
            }
            if (pickupDelay >= 5) return;
            int best = -1;
            double closest = .90;
            for (int i = 0; i < team.length; i++) {
                if (pickupDelay > 0 && i == kicker) continue;
                Vec3 position = position(i);
                double reach = i % 5 == 0 ? 1.7 : .7;
                if (Math.min(previous.y, ball.y) > position.y + reach) continue;
                double distance = segmentDistance(position, previous, ball);
                if (distance < closest) { closest = distance; best = i; }
            }
            if (best >= 0) {
                if (kicker >= 0 && (best < 5) != (kicker < 5)) interceptions++;
                setOwner(best);
            }
        }

        private static int npcPace(int role) {
            return role == 2 || role == 3 ? 67 : role == 1 ? 54 : 60;
        }

        private boolean playerDuel(Seat carrier, int opponent) {
            if (ticks < carrier.protectedUntil) return false;
            ServerPlayer p = carrier.player;
            Vec3 delta = flat(position(opponent).subtract(p.position()));
            if (delta.lengthSqr() > 1.1 * 1.1) return false;
            Vec3 facing = flat(p.getLookAngle()).normalize();
            double angle = facing.dot(delta.normalize());
            if (angle < -.20 && ticks >= carrier.nextFoul && movement(opponent).dot(delta) < -.04) {
                carrier.protectedUntil = ticks + 38;
                carrier.nextFoul = ticks + 100;
                say(p, "foul");
                p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_FLUTE.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, .8f, 1.65f);
            }
            double movingForward = carrier.movement.dot(delta.normalize());
            Seat challenger = players[opponent];
            int chasingPace = challenger == null ? npcPace(opponent % 5) : pace(challenger.player);
            if (chasingPace < pace(p) && carrier.movement.horizontalDistanceSqr() > .04
                    && carrier.movement.normalize().dot(movement(opponent).normalize()) > .65) return false;
            int defense = challenger == null ? (opponent % 5 == 1 ? 66 : 50) : defending(challenger.player);
            double reach = FootballRatings.tackleDistance(defense, strength(p));
            return angle > .48 && delta.lengthSqr() < reach * reach
                    && movingForward > .025;
        }

        private void npcKick() {
            int sign = owner < 5 ? 1 : -1, goal = owner < 5 ? FootballGround.MAX_X + 1 : FootballGround.MIN_X;
            double distance = (goal - ball.x) * sign;
            if (distance < 15 && Math.abs(ball.z - FootballGround.MID_Z) < 13 && owner % 5 != 0) {
                Vec3 target = new Vec3(goal, 64, FootballGround.MID_Z + (ticks % 2 == 0 ? -1.6 : 2.4));
                shots++;
                kick(target.subtract(ball), .98);
                return;
            }
            Vec3 target = null;
            double best = held < 22 && owner % 5 != 0 ? 2.0 : -12.0;
            for (int i = owner < 5 ? 0 : 5; i < (owner < 5 ? 5 : 10); i++) {
                if (i == owner) continue;
                Vec3 receiver = position(i).add(movement(i).scale(7));
                Vec3 delta = receiver.subtract(ball);
                double length = delta.horizontalDistance();
                if (length < 4 || length > 29) continue;
                double score = delta.x * sign * .32 - length * .12 + Math.abs(delta.z) * .11;
                for (int foe = owner < 5 ? 5 : 0; foe < (owner < 5 ? 10 : 5); foe++)
                    if (segmentDistance(position(foe), ball, receiver) < 3.25) score -= 6;
                if (players[i] != null && ticks < players[i].requestUntil && score > -4) score += 10;
                if (score > best) { target = receiver; best = score; }
            }
            if (target != null) {
                pass(target);
                for (Seat seat : players) if (seat != null) seat.requestUntil = 0;
            }
            else if (held >= 45) kick(new Vec3(sign, 0, (FootballGround.MID_Z + .5 - ball.z) * .16), .65);
        }

        private void pass(Vec3 target) {
            passes++;
            Vec3 direction = target.subtract(ball);
            kick(direction, Math.clamp(direction.horizontalDistance() * .035 + .36, .52, 1.08));
        }

        private void kick(Vec3 direction, double speed) {
            kicker = owner;
            owner = -1;
            held = 0;
            pickupDelay = 6;
            curve = 0;
            velocity = flat(direction).normalize().scale(speed);
        }

        private void setOwner(int next) {
            owner = next;
            curve = 0;
            held = 0;
            kicker = -1;
            velocity = Vec3.ZERO;
        }

        private void hud(ServerLevel city) {
            int seconds = (MATCH_TICKS - ticks + 19) / 20;
            String clock = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
            Component score = Component.translatable("napuli.football.score", home, away, clock);
            if(!leagueId.isEmpty()){var f=CityLeague.fixture(city,leagueId);if(f!=null)score=Component.literal(CityLeague.name(f.home())+" "+home+" – "+away+" "+CityLeague.name(f.away())+" · "+clock);}
            for (ServerPlayer p : city.players()) {
                if (!ARENA.contains(p.position())) continue;
                p.sendOverlayMessage(owner >= 0 && players[owner] != null && players[owner].player == p ? score.copy().append("  ·  ")
                        .append(Component.translatable("napuli.football.possession")) : score);
            }
        }

        private void finish(ServerLevel city) {
            if(!leagueId.isEmpty()){saveLeague(city);CityLeague.complete(city,leagueId);for(var p:city.players())CityLeague.claim(p);}
            for (Seat seat : players) {
                if (seat == null) continue;
                ServerPlayer p = seat.player;
                Career c = career(p);
                if(leagueId.isEmpty())p.setAttached(CAREER, new Career(c.matches() + 1, c.goals() + seat.goals, c.fitness(), c.trainingDay()));
                teleport(p, FootballGround.STAND, 0);
            }
            for (ServerPlayer p : city.players()) if (ARENA.contains(p.position())) say(p, "final", home, away);
        }
    }

    private static Vec3 mid() { return new Vec3(FootballGround.MID_X + .5, 64, FootballGround.MID_Z + .5); }
    private static Vec3 flat(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    private static boolean inside(Vec3 pos) {
        return pos.x >= FootballGround.MIN_X + .5 && pos.x <= FootballGround.MAX_X + .5
                && pos.z >= FootballGround.MIN_Z + .5 && pos.z <= FootballGround.MAX_Z + .5;
    }

    private static double segmentDistance(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = flat(end.subtract(start)), offset = flat(point.subtract(start));
        double length = segment.lengthSqr();
        double progress = length == 0 ? 0 : Math.clamp(offset.dot(segment) / length, 0, 1);
        return offset.subtract(segment.scale(progress)).lengthSqr();
    }
}
