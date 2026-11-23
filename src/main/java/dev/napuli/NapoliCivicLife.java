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
    static final int ADULT_AGE = 18;
    private static final int DAILY_COST = 3, CHILD_RESERVE = 24;
    private static final int HOME_X = -148, HOME_Z = 12;

    enum Job {
        PORTER(10, -222, 62), BAKER(12, -151, 45), MERCHANT(11, -186, 50),
        CLEANER(9, -166, 53), GARDENER(10, -219, 51),
        FISHER(11, -302, 69), COURIER(10, -265, 47), CARPENTER(13, -309, 54),
        PIZZA_MAKER(12, CityProfessions.Category.FOOD),
        COOK(13, CityProfessions.Category.FOOD),
        WAITER(14, CityProfessions.Category.FOOD),
        PASTRY_CHEF(15, CityProfessions.Category.FOOD),
        ICE_CREAM_MAKER(16, CityProfessions.Category.FOOD),
        BARISTA(17, CityProfessions.Category.FOOD),
        BUTCHER(18, CityProfessions.Category.FOOD),
        FISHMONGER(19, CityProfessions.Category.FOOD),
        PASTA_MAKER(20, CityProfessions.Category.FOOD),
        FRY_COOK(21, CityProfessions.Category.FOOD),
        GROCER(22, CityProfessions.Category.TRADE),
        SHOP_ASSISTANT(23, CityProfessions.Category.TRADE),
        CASHIER(24, CityProfessions.Category.TRADE),
        BOOKSELLER(12, CityProfessions.Category.TRADE),
        FLORIST(13, CityProfessions.Category.TRADE),
        NEWSAGENT(14, CityProfessions.Category.TRADE),
        ANTIQUE_DEALER(15, CityProfessions.Category.TRADE),
        AIR_SELLER(16, CityProfessions.Category.TRADE),
        SOUVENIR_SELLER(17, CityProfessions.Category.TRADE),
        MARKET_TRADER(18, CityProfessions.Category.TRADE),
        HAIRDRESSER(19, CityProfessions.Category.SERVICES),
        BARBER(20, CityProfessions.Category.SERVICES),
        MECHANIC(21, CityProfessions.Category.SERVICES),
        RECEPTIONIST(22, CityProfessions.Category.SERVICES),
        CARETAKER(23, CityProfessions.Category.SERVICES),
        LAUNDERER(24, CityProfessions.Category.SERVICES),
        TAILOR(12, CityProfessions.Category.SERVICES),
        COBBLER(13, CityProfessions.Category.SERVICES),
        BEAUTICIAN(14, CityProfessions.Category.SERVICES),
        PLUMBER(15, CityProfessions.Category.SERVICES),
        TEACHER(16, CityProfessions.Category.PUBLIC),
        LIBRARIAN(17, CityProfessions.Category.PUBLIC),
        POSTAL_CLERK(18, CityProfessions.Category.PUBLIC),
        SOCIAL_WORKER(19, CityProfessions.Category.PUBLIC),
        TOUR_GUIDE(20, CityProfessions.Category.PUBLIC),
        MUSEUM_KEEPER(21, CityProfessions.Category.PUBLIC),
        BUS_DRIVER(22, CityProfessions.Category.TRANSPORT),
        TAXI_DRIVER(23, CityProfessions.Category.TRANSPORT),
        TRAIN_DRIVER(24, CityProfessions.Category.TRANSPORT),
        TRAM_DRIVER(12, CityProfessions.Category.TRANSPORT),
        SAILOR(13, CityProfessions.Category.TRANSPORT),
        DOCK_WORKER(14, CityProfessions.Category.TRANSPORT),
        POSTMAN(15, CityProfessions.Category.TRANSPORT),
        DELIVERY_RIDER(16, CityProfessions.Category.TRANSPORT),
        NIGHT_DRIVER(17, CityProfessions.Category.TRANSPORT),
        INFLUENCER(18, CityProfessions.Category.MEDIA),
        JOURNALIST(19, CityProfessions.Category.MEDIA),
        MUSICIAN(20, CityProfessions.Category.MEDIA),
        TATTOO_ARTIST(21, CityProfessions.Category.MEDIA),
        PHOTOGRAPHER(22, CityProfessions.Category.MEDIA),
        STREET_ARTIST(23, CityProfessions.Category.MEDIA),
        ACTOR(24, CityProfessions.Category.MEDIA),
        RADIO_HOST(12, CityProfessions.Category.MEDIA),
        BRICKLAYER(13, CityProfessions.Category.BUILDING),
        ELECTRICIAN(14, CityProfessions.Category.BUILDING),
        PAINTER(15, CityProfessions.Category.BUILDING),
        WORKER(16, CityProfessions.Category.BUILDING),
        ARCHITECT(17, CityProfessions.Category.BUILDING),
        ENGINEER(18, CityProfessions.Category.BUILDING),
        CLERK(19, CityProfessions.Category.OFFICE),
        ACCOUNTANT(20, CityProfessions.Category.OFFICE),
        LAWYER(21, CityProfessions.Category.OFFICE),
        SECRETARY(22, CityProfessions.Category.OFFICE),
        MANAGER(23, CityProfessions.Category.OFFICE),
        PROGRAMMER(24, CityProfessions.Category.OFFICE),
        ESTATE_AGENT(12, CityProfessions.Category.OFFICE),
        DOCTOR(13, CityProfessions.Category.HEALTH),
        NURSE(14, CityProfessions.Category.HEALTH),
        PHARMACIST(15, CityProfessions.Category.HEALTH),
        DENTIST(16, CityProfessions.Category.HEALTH),
        PHYSIOTHERAPIST(17, CityProfessions.Category.HEALTH),
        NIGHT_DOCTOR(18, CityProfessions.Category.HEALTH),
        NIGHT_NURSE(19, CityProfessions.Category.HEALTH),
        CARABINIERE(20, CityProfessions.Category.SECURITY),
        TRAFFIC_OFFICER(21, CityProfessions.Category.SECURITY),
        GUARD(22, CityProfessions.Category.SECURITY),
        FIREFIGHTER(23, CityProfessions.Category.SECURITY);
        final CityProfessions.Category category;
        final int wage;
        final BlockPos site;

        Job(int wage, int x, int z) {
            this.category = switch(name()) {case "BAKER","FISHER" -> CityProfessions.Category.FOOD;case "MERCHANT" -> CityProfessions.Category.TRADE;case "PORTER","COURIER" -> CityProfessions.Category.TRANSPORT;case "CARPENTER" -> CityProfessions.Category.BUILDING;default -> CityProfessions.Category.SERVICES;};
            this.wage = wage;
            this.site = new BlockPos(x, 64, z);
        }

        Job(int wage, CityProfessions.Category category) { this.wage=wage; this.category=category; this.site=category.site().offset(8,0,7); }

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

        int age(long day) { return (int) Math.clamp((day - birth) / CityLifeConfig.settings.daysPerYear, 0, 999); }
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
            CityCalendar.sync(city);
            advance(city, NapoliBroadcast.day(city));
            CitySimulation.update(city);
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

    static void expand(ServerLevel city) {
        Census census = census(city);
        if (census == null || census.people().stream().anyMatch(c -> c.id().equals(identity("Lucia De Luca")))) return;
        var people = new ArrayList<>(census.people());
        people.add(founder("Lucia De Luca", true, census.day(), Job.COURIER));
        people.add(founder("Salvatore Russo", false, census.day(), Job.FISHER));
        people.add(founder("Teresa Marino", true, census.day(), Job.CARPENTER));
        people.add(founder("Antonio De Rosa", false, census.day(), Job.GARDENER));
        city.setAttached(CENSUS, new Census(census.day(), List.copyOf(people)));
    }

    static void populate(ServerLevel city) {
        seed(city); expand(city);
        var before=census(city); var people=new ArrayList<>(before.people());
        String[] women={"Maria","Anna","Concetta","Francesca","Imma","Nunzia","Chiara","Paola"};
        String[] men={"Vincenzo","Pasquale","Luigi","Raffaele","Domenico","Giuseppe","Michele","Mario"};
        String[] surnames={"Esposito","Romano","Russo","Marino","Coppola","De Luca","Ferrara","Greco","Vitale","Caruso","Rizzo","Sorrentino","Amato","Gallo"};
        long day=before.day();
        for(int n=0;people.size()<CityLifeConfig.settings.population;n++) {
            UUID id=identity("population:"+n);if(people.stream().anyMatch(c->c.id().equals(id)))continue;
            int member=n%4,group=n/4; boolean woman=member==0||member==3;
            boolean generations=group%7==2;
            int age=generations?(member<2?70+member*3:35+(member-2)*5):member<2?26+group%20:member==2?6:12;
            String name=(woman?women:men)[(group+member)%8]+" "+surnames[group%surnames.length];
            String partner=member<2?identity("population:"+(member==0?n+1:n-1)).toString():"";
            String parents=member>=2?identity("population:"+(n-member)).toString():"";
            int job=age>=ADULT_AGE&&age<CityLifeConfig.settings.retirementAge?Math.floorMod(group*3+member+8,Job.values().length):-1;
            if(group==0&&member==0)job=Job.INFLUENCER.ordinal();
            if(group==0&&member==1)job=Job.AIR_SELLER.ordinal();
            people.add(new Citizen(id,name,woman,day-(long)age*CityLifeConfig.settings.daysPerYear,member<2?8:0,job,partner,"public_"+group%28,parents,false));
            if(member>=2)CitySimulation.parentage(city,id,identity("population:"+(n-member)),identity("population:"+(n-member+1)));
        }
        city.setAttached(CENSUS,new Census(day,List.copyOf(people)));
    }

    private static Citizen founder(String name, boolean woman, long day, Job job) {
        return new Citizen(identity(name), name, woman, day - 22L * CityLifeConfig.settings.daysPerYear, 8, job.ordinal(), "", "", "", false);
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
            payWorkers(city, people, day);
            CityHousingFinance.settleResidents(city,people,day);
            formHouseholds(city, people, day);
            CitySimulation.inherit(city, people, day);
            CitySimulation.relationships(city,people,day);
        }
        for(var child:List.copyOf(people.values())) if(!child.parents().isEmpty()) {
            var parent=people.get(UUID.fromString(child.parents()));
            if(parent!=null && child.age(last)<ADULT_AGE) people.put(child.id(),child.withHousehold(child.partner(),parent.property(),child.balance()));
        }
        city.setAttached(CENSUS, new Census(last, List.copyOf(people.values())));
    }

    private static void payWorkers(ServerLevel city, Map<UUID, Citizen> people, long day) {
        var filled = new int[Job.values().length];
        for (Citizen c : people.values()) if (c.profession() != null) filled[c.job()]++;
        for (var entry : people.entrySet()) {
            Citizen c = entry.getValue();
            if (c.age(day) < ADULT_AGE) continue;
            int index = c.job();
            if (index < 0 && c.age(day)<CityLifeConfig.settings.retirementAge) {
                for (Job job : Job.values()) if (filled[job.ordinal()] < 3 && CityProfessions.eligible(job,c,CitySimulation.life(city,c.id()),day)) {
                    index = job.ordinal();
                    filled[index]++;
                    break;
                }
            }
            int pay = index < 0 || c.age(day) >= CityLifeConfig.settings.retirementAge ? 0 : Job.values()[index].wage;
            Citizen next = CitySimulation.daily(city,c.withMoney(c.balance(), index),day,pay);
            if(c.age(day)>=CityLifeConfig.settings.retirementAge) next=next.withMoney(Math.min(99999,next.balance()+8),-1);
            entry.setValue(next);
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
                if (second.woman() || !second.partner().isEmpty() || second.age(day) < ADULT_AGE || Math.abs(second.age(day)-first.age(day))>20
                        || related(city,first, second, people)) continue;
                if(CitySimulation.life(city,first.id()).relations().getOrDefault(second.id().toString(),0)<60
                        ||CitySimulation.life(city,second.id()).relations().getOrDefault(first.id().toString(),0)<60)continue;
                String address=!first.property().isEmpty()&&!first.property().startsWith("public_")?first.property():second.property();
                people.put(first.id(), first.withHousehold(second.id().toString(), address, first.balance()));
                people.put(second.id(), second.withHousehold(first.id().toString(), address, second.balance()));
                break;
            }
        }
        for (Citizen candidate : List.copyOf(people.values())) {
            Citizen a = people.get(candidate.id());
            if (!a.woman() || a.partner().isEmpty()) continue;
            Citizen b = people.get(UUID.fromString(a.partner()));
            if (b == null) continue;
            if (a.property().isEmpty() || a.property().startsWith("public_")) {
                for (var house : NapoliMergellina.PROPERTIES) {
                    int cost=house.price();
                    boolean mortgage=a.balance()+b.balance()<cost && a.age(day)>=25;
                    if(mortgage)cost=(cost+4)/5;
                    if (a.balance() + b.balance() < cost
                            || !NapoliProperties.claimForResident(city, house, a.id())) continue;
                    if(mortgage)CityHousingFinance.mortgageForResident(city,house,a.id(),day,cost);
                    int firstShare = Math.min(a.balance(), (cost + 1) / 2);
                    firstShare = Math.max(firstShare, cost - b.balance());
                    a = a.withHousehold(a.partner(), house.id(), a.balance() - firstShare);
                    b = b.withHousehold(b.partner(), house.id(), b.balance() - (cost - firstShare));
                    people.put(a.id(), a);
                    people.put(b.id(), b);
                    break;
                }
            }
            if (a.property().isEmpty() || a.property().startsWith("public_") || a.balance() + b.balance() < CHILD_RESERVE) continue;
            String family = a.id().toString();
            long children=people.values().stream().filter(child -> child.parents().equals(family)).count();
            String familyHome=a.property();
            var familyHouse=CityLivingHomes.find(familyHome);
            long occupants=familyHouse==null?people.values().stream().filter(person->person.property().equals(familyHome)).count()
                    :HousingCapacity.reservedOccupants(city,familyHouse,people.values());
            if(children>=2 || occupants>=HousingCapacity.MAX_OCCUPANTS || people.size()>=CityLifeConfig.settings.maximumPopulation || a.age(day)>=50
                    || day-CitySimulation.life(city,a.id()).lastBirth()<7 || CitySimulation.life(city,a.id()).food()<2) continue;
            int contribution = Math.min(a.balance(), CHILD_RESERVE / 2);
            contribution = Math.max(contribution, CHILD_RESERVE - b.balance());
            people.put(a.id(), a.withMoney(a.balance() - contribution, a.job()));
            people.put(b.id(), b.withMoney(b.balance() - (CHILD_RESERVE - contribution), b.job()));
            UUID childId = identity(family + ":child:" + children);
            boolean daughter = (childId.hashCode() & 1) == 0;
            String surname = b.name().substring(b.name().lastIndexOf(' ') + 1);
            CitySimulation.birth(city,a.id(),day);
            CitySimulation.parentage(city,childId,a.id(),b.id());
            people.put(childId, new Citizen(childId, (daughter ? "Lucia " : "Antonio ") + surname, daughter,
                    day, 0, -1, "", a.property(), family, false));
        }
    }

    private static boolean related(ServerLevel city,Citizen a, Citizen b, Map<UUID, Citizen> people) {
        var aa=CitySimulation.ancestors(city,a,people,4);var ba=CitySimulation.ancestors(city,b,people,4);
        if(aa.contains(b.id().toString())||ba.contains(a.id().toString())||aa.stream().anyMatch(ba::contains))return true;
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

    static BlockPos home(Citizen citizen) {
        var registered=CityLivingHomes.find(citizen.property());
        if(registered!=null)return registered.inside();
        for (var house : NapoliMergellina.PROPERTIES) if (house.id().equals(citizen.property())) return house.inside();
        int index = Math.floorMod(citizen.id().hashCode(), 3) - 1;
        if (citizen.profession() != null && citizen.profession().site.getX() < -247)
            return new BlockPos(-273 + index, 64, 17);
        return new BlockPos(HOME_X + index, 64, HOME_Z);
    }

    private static void materializeNearby(ServerLevel city) {
        Census before = census(city);
        var updated = new ArrayList<>(before.people());
        boolean changed = false;
        for (int i = 0; i < updated.size(); i++) {
            Citizen c = updated.get(i);
            if (c.spawned()) continue;
            BlockPos desired = CitySimulation.location(city,c);
            var targetHome=CityLivingHomes.all().stream().filter(h->h.contains(desired)).findFirst().orElse(null);
            BlockPos spawn = targetHome!=null&&!HousingCapacity.canEnter(city,desired,c.id())?targetHome.entrance().south():desired;
            if (!NapoliResidentHomes.walkable(city, spawn)
                    || !HousingCapacity.canEnter(city,spawn,c.id())
                    || city.players().stream().noneMatch(p -> spawn.distToCenterSqr(p.position()) < 64 * 64)) continue;
            if (city.getEntity(c.id()) == null) {
                var npc = NapoliResident.create(city, c.profession()==Job.CARABINIERE ? NapoliResident.Role.CARABINIERE : c.woman() ? NapoliResident.Role.CIVIC_WOMAN : NapoliResident.Role.CIVIC_MAN, spawn, 0);
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

    static void commute(NapoliResident npc, ServerLevel city) { CitySimulation.tickEntity(npc, city); }

    static void describe(ServerPlayer player, NapoliResident npc) {
        Citizen c = profile(player.level(), npc.getUUID());
        if (c == null) return;
        CitySimulation.describe(player,c);
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
