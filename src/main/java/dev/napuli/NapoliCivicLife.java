package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class NapoliCivicLife {
    static final int ADULT_AGE = 14;
    private static final int DAILY_COST = 3, CHILD_RESERVE = 24;
    private static final int HOME_X = -148, HOME_Z = 12;

    enum Job {
        PORTER(10, -222, 62), BAKER(12, -151, 45), MERCHANT(11, -186, 50),
        CLEANER(9, -166, 53), GARDENER(10, -219, 51);
        final int wage;
        final BlockPos site;

        Job(int wage, int x, int z) {
            this.wage = wage;
            this.site = new BlockPos(x, 64, z);
        }

        String key() { return "napuli.civic.job." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    record Citizen(UUID id, String name, boolean woman, long birth, int balance, int job,
                   String partner, String property, String parents, boolean spawned) {
        private static final Codec<Citizen> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Citizen::id),
                Codec.STRING.fieldOf("name").forGetter(Citizen::name),
                Codec.BOOL.fieldOf("woman").forGetter(Citizen::woman),
                Codec.LONG.fieldOf("birth").forGetter(Citizen::birth),
                Codec.INT.fieldOf("balance").forGetter(Citizen::balance),
                Codec.INT.fieldOf("job").forGetter(Citizen::job),
                Codec.STRING.optionalFieldOf("partner", "").forGetter(Citizen::partner),
                Codec.STRING.optionalFieldOf("property", "").forGetter(Citizen::property),
                Codec.STRING.optionalFieldOf("parents", "").forGetter(Citizen::parents),
                Codec.BOOL.optionalFieldOf("spawned", false).forGetter(Citizen::spawned)
        ).apply(i, Citizen::new));

        int age(long day) { return (int) Math.clamp(day - birth, 0, 999); }
        Job profession() { return job < 0 || job >= Job.values().length ? null : Job.values()[job]; }
        Citizen withMoney(int money, int newJob) {
            return new Citizen(id, name, woman, birth, money, newJob, partner, property, parents, spawned);
        }
        Citizen withHousehold(String companion, String home, int money) {
            return new Citizen(id, name, woman, birth, money, job, companion, home, parents, spawned);
        }
        Citizen materialized() {
            return new Citizen(id, name, woman, birth, balance, job, partner, property, parents, true);
        }
    }

    record Census(long day, List<Citizen> people) {
        static final Codec<Census> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Census::day),
                Citizen.CODEC.listOf().fieldOf("people").forGetter(Census::people)
        ).apply(i, Census::new));
    }

    static final AttachmentType<Census> CENSUS = AttachmentRegistry.<Census>builder()
            .persistent(Census.CODEC).buildAndRegister(Napuli.id("civic_census"));

    private NapoliCivicLife() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city == null || city.getAttached(CENSUS) == null) return;
            advance(city, NapoliBroadcast.day(city));
            if (!city.players().isEmpty()) materializeNearby(city);
        });
    }

    static void seed(ServerLevel city) {
        if (city.getAttached(CENSUS) != null) return;
        long day = NapoliBroadcast.day(city);
        city.setAttached(CENSUS, new Census(day, List.of(
                founder("Assunta Esposito", true, day, Job.BAKER),
                founder("Ciro Romano", false, day, Job.PORTER),
                founder("Rosa Sorrentino", true, day, Job.MERCHANT),
                founder("Gennaro Rizzo", false, day, Job.CLEANER))));
    }

    private static Citizen founder(String name, boolean woman, long day, Job job) {
        return new Citizen(identity(name), name, woman, day - 22, 8, job.ordinal(), "", "", "", false);
    }

    private static UUID identity(String key) {
        return UUID.nameUUIDFromBytes(("napulicraft:civic:" + key).getBytes(StandardCharsets.UTF_8));
    }

    static Census census(ServerLevel city) { return city.getAttached(CENSUS); }

    static void advance(ServerLevel city, long today) {
        Census before = census(city);
        if (before == null || today <= before.day()) return;
        // Process a bounded batch so jumping the clock cannot block the server.
        long last = Math.min(today, before.day() + 32);
        var people = new LinkedHashMap<UUID, Citizen>();
        for (Citizen citizen : before.people()) people.put(citizen.id(), citizen);
        for (long day = before.day() + 1; day <= last; day++) {
            payWorkers(people, day);
            formHouseholds(city, people, day);
        }
        city.setAttached(CENSUS, new Census(last, List.copyOf(people.values())));
    }

    private static void payWorkers(Map<UUID, Citizen> people, long day) {
        var filled = new int[Job.values().length];
        for (Citizen c : people.values()) if (c.profession() != null) filled[c.job()]++;
        for (var entry : people.entrySet()) {
            Citizen c = entry.getValue();
            if (c.age(day) < ADULT_AGE) continue;
            int index = c.job();
            if (index < 0) {
                for (Job job : Job.values()) if (filled[job.ordinal()] < 3) {
                    index = job.ordinal();
                    filled[index]++;
                    break;
                }
            }
            int pay = index < 0 ? 0 : Job.values()[index].wage;
            entry.setValue(c.withMoney(Math.clamp(c.balance() + pay - DAILY_COST, 0, 99999), index));
        }
    }

    private static void formHouseholds(ServerLevel city, Map<UUID, Citizen> people, long day) {
        var available = List.copyOf(people.values());
        for (Citizen candidate : available) {
            Citizen first = people.get(candidate.id());
            if (!first.woman() || first.age(day) < ADULT_AGE || !first.partner().isEmpty()
                    || day < first.birth() + ADULT_AGE + 1) continue;
            for (Citizen other : available) {
                Citizen second = people.get(other.id());
                if (second.woman() || !second.partner().isEmpty() || second.age(day) < ADULT_AGE
                        || related(first, second, people)) continue;
                people.put(first.id(), first.withHousehold(second.id().toString(), "", first.balance()));
                people.put(second.id(), second.withHousehold(first.id().toString(), "", second.balance()));
                break;
            }
        }
        for (Citizen candidate : List.copyOf(people.values())) {
            Citizen a = people.get(candidate.id());
            if (!a.woman() || a.partner().isEmpty()) continue;
            Citizen b = people.get(UUID.fromString(a.partner()));
            if (b == null) continue;
            if (a.property().isEmpty()) {
                for (var house : NapoliMergellina.PROPERTIES) {
                    if (a.balance() + b.balance() < house.price()
                            || !NapoliProperties.claimForResident(city, house, a.id())) continue;
                    int firstShare = Math.min(a.balance(), (house.price() + 1) / 2);
                    firstShare = Math.max(firstShare, house.price() - b.balance());
                    a = a.withHousehold(a.partner(), house.id(), a.balance() - firstShare);
                    b = b.withHousehold(b.partner(), house.id(), b.balance() - (house.price() - firstShare));
                    people.put(a.id(), a);
                    people.put(b.id(), b);
                    break;
                }
            }
            if (a.property().isEmpty() || a.balance() + b.balance() < CHILD_RESERVE) continue;
            String family = a.id().toString();
            if (people.values().stream().anyMatch(child -> child.parents().equals(family))) continue;
            int contribution = Math.min(a.balance(), CHILD_RESERVE / 2);
            contribution = Math.max(contribution, CHILD_RESERVE - b.balance());
            people.put(a.id(), a.withMoney(a.balance() - contribution, a.job()));
            people.put(b.id(), b.withMoney(b.balance() - (CHILD_RESERVE - contribution), b.job()));
            UUID childId = identity(family + ":child");
            boolean daughter = (childId.hashCode() & 1) == 0;
            String surname = b.name().substring(b.name().lastIndexOf(' ') + 1);
            people.put(childId, new Citizen(childId, (daughter ? "Lucia " : "Antonio ") + surname, daughter,
                    day, 0, -1, "", a.property(), family, false));
        }
    }

    private static boolean related(Citizen a, Citizen b, Map<UUID, Citizen> people) {
        return (!a.parents().isEmpty() && a.parents().equals(b.parents()))
                || parentOf(a, b, people) || parentOf(b, a, people);
    }

    private static boolean parentOf(Citizen adult, Citizen child, Map<UUID, Citizen> people) {
        if (child.parents().isEmpty()) return false;
        Citizen mother = people.get(UUID.fromString(child.parents()));
        return mother != null && (mother.id().equals(adult.id()) || mother.partner().equals(adult.id().toString()));
    }

    static Citizen profile(ServerLevel city, UUID id) {
        Census data = census(city);
        if (data == null) return null;
        return data.people().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    private static BlockPos home(Citizen citizen) {
        for (var house : NapoliMergellina.PROPERTIES) if (house.id().equals(citizen.property())) return house.inside();
        return new BlockPos(HOME_X + Math.floorMod(citizen.id().hashCode(), 3) - 1, 64, HOME_Z);
    }

    private static void materializeNearby(ServerLevel city) {
        Census before = census(city);
        var updated = new ArrayList<>(before.people());
        boolean changed = false;
        for (int i = 0; i < updated.size(); i++) {
            Citizen c = updated.get(i);
            if (c.spawned()) continue;
            BlockPos spawn = home(c);
            if (!NapoliResidentHomes.walkable(city, spawn)
                    || city.players().stream().noneMatch(p -> spawn.distToCenterSqr(p.position()) < 64 * 64)) continue;
            if (city.getEntity(c.id()) == null) {
                var npc = NapoliResident.create(city, c.woman() ? NapoliResident.Role.CIVIC_WOMAN : NapoliResident.Role.CIVIC_MAN, spawn, 0);
                npc.setUUID(c.id());
                npc.setCustomName(Component.literal(c.name()));
                npc.setCivicAge(c.age(before.day()));
                if (!city.addFreshEntity(npc)) continue;
            }
            updated.set(i, c.materialized());
            changed = true;
        }
        if (changed) city.setAttached(CENSUS, new Census(before.day(), List.copyOf(updated)));
    }

    static void commute(NapoliResident npc, ServerLevel city) {
        if ((npc.tickCount + npc.getId()) % 40 != 0) return;
        Citizen c = profile(city, npc.getUUID());
        if (c == null) return;
        int age = c.age(Math.max(census(city).day(), NapoliBroadcast.day(city)));
        npc.setCivicAge(age);
        int time = (int) NapoliBroadcast.time(city);
        BlockPos home = home(c);
        boolean working = c.profession() != null && age >= ADULT_AGE && time >= 2500 && time < 10500;
        boolean playing = age < ADULT_AGE && time >= 4200 && time < 12500;
        var workItem = working ? switch (c.profession()) {
            case PORTER, MERCHANT -> net.minecraft.world.item.Items.PAPER;
            case BAKER -> net.minecraft.world.item.Items.BREAD;
            case CLEANER -> net.minecraft.world.item.Items.BRUSH;
            case GARDENER -> net.minecraft.world.item.Items.WOODEN_HOE;
        } : net.minecraft.world.item.Items.AIR;
        if (npc.getMainHandItem().getItem() != workItem)
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(workItem));
        BlockPos target = working ? c.profession().site : playing ? home.south(2) : home;
        if (playing && !NapoliResidentHomes.walkable(city, target)) target = home;
        if (target.distToCenterSqr(npc.position()) <= 2) {
            npc.getNavigation().stop();
            if (playing && npc.getRandom().nextBoolean()) npc.getJumpControl().jump();
            if (working) npc.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
        if (!city.hasChunkAt(target)) return;
        var path = npc.getNavigation().createPath(target, 0);
        if (path != null && path.canReach()) npc.getNavigation().moveTo(path, .85);
        else if (NapoliResidentHomes.walkable(city, target))
            npc.snapTo(target.getX() + .5, target.getY(), target.getZ() + .5, npc.getYRot(), 0);
    }

    static void describe(ServerPlayer player, NapoliResident npc) {
        Citizen c = profile(player.level(), npc.getUUID());
        if (c == null) return;
        Job job = c.profession();
        player.sendSystemMessage(Component.translatable("napuli.civic.profile", c.name(), c.age(Math.max(census(player.level()).day(), NapoliBroadcast.day(player.level()))),
                Component.translatable(job == null ? "napuli.civic.no_job" : job.key()), job == null ? 0 : job.wage, c.balance()));
        if (!c.partner().isEmpty()) {
            Citizen partner = profile(player.level(), UUID.fromString(c.partner()));
            if (partner != null) player.sendSystemMessage(Component.translatable("napuli.civic.partner", partner.name()));
        }
        if (!c.parents().isEmpty()) {
            Citizen parent = profile(player.level(), UUID.fromString(c.parents()));
            if (parent != null) player.sendSystemMessage(Component.translatable("napuli.civic.parent", parent.name()));
        }
        for (var house : NapoliMergellina.PROPERTIES) if (house.id().equals(c.property()))
            player.sendSystemMessage(Component.translatable("napuli.civic.house", house.name()));
    }
}
