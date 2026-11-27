package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;

/** Division calendars, resumable matches and player-owned prize receipts. */
final class CityLeague {
    static final List<String> TEAMS=FootballDivisions.Division.D.clubs.stream().map(FootballDivisions.Club::id).toList();
    static String name(String team){var club=FootballDivisions.club(team);return club==null?team:club.name();}
    static int color(String team){var club=FootballDivisions.club(team);return club==null?0xf4eeee:club.color();}
    record Member(String team,int season){static final Codec<Member> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("team").forGetter(Member::team),Codec.INT.fieldOf("season").forGetter(Member::season)).apply(i,Member::new));}
    record Participant(int side,int ticks,int goals,int passes,int recoveries){
        Participant(int side,int ticks,int goals){this(side,ticks,goals,0,0);}
        static final Codec<Participant> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("side").forGetter(Participant::side),Codec.INT.fieldOf("ticks").forGetter(Participant::ticks),Codec.INT.fieldOf("goals").forGetter(Participant::goals),Codec.INT.optionalFieldOf("passes",0).forGetter(Participant::passes),
                Codec.INT.optionalFieldOf("recoveries",0).forGetter(Participant::recoveries)).apply(i,Participant::new));
    }
    record Fixture(String id,String home,String away,long day,int homeGoals,int awayGoals,int elapsed,String state,Map<String,Participant> players){
        static final Codec<Fixture> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(Fixture::id),Codec.STRING.fieldOf("home").forGetter(Fixture::home),Codec.STRING.fieldOf("away").forGetter(Fixture::away),Codec.LONG.fieldOf("day").forGetter(Fixture::day),
                Codec.INT.fieldOf("home_goals").forGetter(Fixture::homeGoals),Codec.INT.fieldOf("away_goals").forGetter(Fixture::awayGoals),Codec.INT.fieldOf("elapsed").forGetter(Fixture::elapsed),Codec.STRING.fieldOf("state").forGetter(Fixture::state),Codec.unboundedMap(Codec.STRING,Participant.CODEC).fieldOf("players").forGetter(Fixture::players)).apply(i,Fixture::new));
    }
    record Season(int number,long opened,List<Fixture> fixtures){static final Codec<Season> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("number").forGetter(Season::number),Codec.LONG.fieldOf("opened").forGetter(Season::opened),Fixture.CODEC.listOf().fieldOf("fixtures").forGetter(Season::fixtures)).apply(i,Season::new));}
    record Standing(String team,int points,int goals,int conceded,int played){}
    static final AttachmentType<Season> SEASON=AttachmentRegistry.<Season>builder().persistent(Season.CODEC).buildAndRegister(Napuli.id("neighborhood_league"));
    static final AttachmentType<Map<String,Fixture>> ARCHIVE=AttachmentRegistry.<Map<String,Fixture>>builder().persistent(Codec.unboundedMap(Codec.STRING,Fixture.CODEC)).buildAndRegister(Napuli.id("league_archive"));
    static final AttachmentType<Member> MEMBER=AttachmentRegistry.<Member>builder().persistent(Member.CODEC).copyOnDeath().buildAndRegister(Napuli.id("league_membership"));
    static final AttachmentType<Set<String>> RECEIPTS=AttachmentRegistry.<Set<String>>builder().persistent(Codec.STRING.listOf().xmap(Set::copyOf,List::copyOf)).copyOnDeath().buildAndRegister(Napuli.id("league_prizes"));
    static Season schedule(int number,long day){
        int[][] pairs={{0,1},{2,3},{0,2},{1,3},{0,3},{1,2}};var fixtures=new ArrayList<Fixture>();
        for(int i=0;i<pairs.length;i++)fixtures.add(new Fixture(number+"/"+i,TEAMS.get(pairs[i][0]),TEAMS.get(pairs[i][1]),day+i/2*2,0,0,0,"scheduled",Map.of()));
        return withDivisions(new Season(number,day,fixtures));
    }
    private static Season withDivisions(Season season) {
        var fixtures = new ArrayList<>(season.fixtures());
        for (var division : FootballDivisions.Division.values()) {
            if (division == FootballDivisions.Division.D) continue;
            var rotation = new ArrayList<>(division.clubs);
            int count = rotation.size();
            for (int round = 0; round < count - 1; round++) {
                for (int i = 0; i < count / 2; i++) {
                    var home = rotation.get(i);
                    var away = rotation.get(count - 1 - i);
                    fixtures.add(new Fixture(season.number()+"/"+division.name()+"_"+round+"_"+i,
                            home.id(),away.id(),season.opened()+round,0,0,0,"scheduled",Map.of()));
                }
                rotation.add(1,rotation.removeLast());
            }
        }
        return new Season(season.number(),season.opened(),List.copyOf(fixtures));
    }
    static Season season(ServerLevel city){
        var s=city.getAttached(SEASON);
        if(s==null){s=schedule(1,NapoliBroadcast.day(city));city.setAttached(SEASON,s);}
        else if(s.fixtures().size()==6){s=withDivisions(s);city.setAttached(SEASON,s);}
        return s;
    }
    static Fixture fixture(ServerLevel city,String id){return season(city).fixtures().stream().filter(f->f.id().equals(id)).findFirst().orElse(null);}
    static void put(ServerLevel city,Fixture fixture){var s=season(city);city.setAttached(SEASON,new Season(s.number(),s.opened(),s.fixtures().stream().map(f->f.id().equals(fixture.id())?fixture:f).toList()));}
    static int choose(ServerPlayer p,String team){
        var target=FootballDivisions.division(team);
        if(target==null||p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive()||Carabinieri.isJailed(p)||FootballPractice.isPlaying(p))return 0;
        var s=season(p.level());var old=p.getAttached(MEMBER);
        if(!canSign(p,old,target)||old!=null&&old.season()==s.number()&&!old.team().equals(team)
                &&FootballDivisions.division(old.team())==target){
            p.sendOverlayMessage(Component.literal("Offerta non disponibile per la tua carriera."));return 0;
        }
        p.setAttached(MEMBER,new Member(team,s.number()));
        NapoliEmployment.footballSigned(p);
        CityOnboarding.event(p,CityOnboarding.JOB);
        NapoliNotice.show(p,FootballEmployment.summary(p));return 1;
    }
    private static boolean canSign(ServerPlayer p,Member current,FootballDivisions.Division target) {
        if (current == null) return target == FootballDivisions.Division.D;
        var division = FootballDivisions.division(current.team());
        if (division == target) return true;
        return division != null && target.ordinal() == division.ordinal() + 1
                && target.qualifies(FootballPractice.position(p),FootballPractice.overall(p),FootballPractice.careerMatches(p),
                FootballPractice.careerGoals(p),FootballPractice.leaguePasses(p),FootballPractice.leagueRecoveries(p));
    }
    static int side(ServerPlayer p,Fixture f){var m=p.getAttached(MEMBER);if(m==null)return -1;return m.team().equals(f.home())?0:m.team().equals(f.away())?5:-1;}
    static int start(ServerPlayer p,String id){
        if(p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive()||Carabinieri.isJailed(p))return 0;
        var f=fixture(p.level(),id);if(f==null||f.state().equals("played")||NapoliBroadcast.day(p.level())<f.day()||side(p,f)<0)return 0;
        var membership=p.getAttached(MEMBER);p.setAttached(MEMBER,new Member(membership.team(),season(p.level()).number()));
        return FootballPractice.startLeague(p,f);
    }
    static Fixture begin(ServerLevel city,Fixture f){if(f.state().equals("scheduled")){f=new Fixture(f.id(),f.home(),f.away(),f.day(),0,0,0,"playing",f.players());put(city,f);}return f;}
    static void progress(ServerLevel city,String id,int home,int away,int elapsed,Map<String,Participant> present){
        var f=fixture(city,id);if(f==null||!f.state().equals("playing"))return;var roster=new HashMap<>(f.players());
        present.forEach((player,stats)->{var old=roster.get(player);if(old==null||old.side()==stats.side())roster.put(player,new Participant(stats.side(),Math.max(old==null?0:old.ticks(),stats.ticks()),Math.max(old==null?0:old.goals(),stats.goals()),Math.max(old==null?0:old.passes(),stats.passes()),Math.max(old==null?0:old.recoveries(),stats.recoveries())));});
        put(city,new Fixture(id,f.home(),f.away(),f.day(),Math.clamp(home,0,99),Math.clamp(away,0,99),Math.max(f.elapsed(),elapsed),"playing",Map.copyOf(roster)));
    }
    static void complete(ServerLevel city,String id){
        var f=fixture(city,id);if(f==null||!f.state().equals("playing")||f.elapsed()<3000)return;
        put(city,new Fixture(id,f.home(),f.away(),f.day(),f.homeGoals(),f.awayGoals(),f.elapsed(),"played",f.players()));
        for(var p:city.players())NapoliNotice.show(p,Component.literal(name(f.home())+" "+f.homeGoals()+" – "+f.awayGoals()+" "+name(f.away())));
    }
    static List<Standing> standings(Collection<Fixture> fixtures){
        return standings(fixtures,FootballDivisions.Division.D);
    }
    private static List<Standing> standings(Collection<Fixture> fixtures, FootballDivisions.Division division){
        var rows=new ArrayList<Standing>();for(var club:division.clubs){String team=club.id();int points=0,goals=0,conceded=0,played=0;
            for(var f:fixtures)if(f.state().equals("played")&&(f.home().equals(team)||f.away().equals(team))){int a=f.home().equals(team)?f.homeGoals():f.awayGoals(),b=f.home().equals(team)?f.awayGoals():f.homeGoals();goals+=a;conceded+=b;points+=a>b?3:a==b?1:0;played++;}rows.add(new Standing(team,points,goals,conceded,played));
        }rows.sort(Comparator.comparingInt(Standing::points).reversed().thenComparing(Comparator.comparingInt((Standing r)->r.goals()-r.conceded()).reversed()).thenComparing(Comparator.comparingInt(Standing::goals).reversed()).thenComparing(Standing::team));return rows;
    }
    static int claim(ServerPlayer p){
        if(p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive())return 0;var city=p.level();var receipts=new HashSet<>(p.getAttachedOrElse(RECEIPTS,Set.of()));var fixtures=new HashMap<>(city.getAttachedOrElse(ARCHIVE,Map.of()));for(var f:season(city).fixtures())fixtures.put(f.id(),f);
        int money=0,wages=0;for(var f:fixtures.values()){
            var participant=f.players().get(p.getUUID().toString());if(!f.state().equals("played")||participant==null||participant.ticks()<600||!receipts.add(f.id()))continue;
            boolean home=participant.side()==0;int a=home?f.homeGoals():f.awayGoals(),b=home?f.awayGoals():f.homeGoals();
            var terms=FootballEmployment.terms(home?f.home():f.away());
            int wage=terms==null?0:terms.appearancePay;
            int reward=wage+(a>b?12:a==b?6:3);
            p.setAttached(RECEIPTS,Set.copyOf(receipts));NapoliStreet.credit(p,reward,"league",f.id());FootballPractice.awardLeague(p,participant.goals(),participant.passes(),participant.recoveries());money+=reward;wages+=wage;
        }
        // The receipt and wallet live in the same player save, so replaying a result cannot pay twice.
        if(money>0)NapoliNotice.show(p,Component.translatable("napuli.football.job.paid",wages,money-wages));return money;
    }
    static int next(ServerPlayer p){
        if(p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator())return 0;var city=p.level();var s=season(city);if(s.fixtures().stream().anyMatch(f->!f.state().equals("played")))return 0;
        var archive=new HashMap<>(city.getAttachedOrElse(ARCHIVE,Map.of()));s.fixtures().forEach(f->archive.put(f.id(),f));while(archive.size()>264){String oldest=archive.entrySet().stream().min(Comparator.comparingLong(e->e.getValue().day())).orElseThrow().getKey();archive.remove(oldest);}city.setAttached(ARCHIVE,Map.copyOf(archive));city.setAttached(SEASON,schedule(s.number()+1,NapoliBroadcast.day(city)));return 1;
    }
    static String champion(ServerLevel city){var s=season(city);if(s.fixtures().stream().allMatch(f->f.state().equals("played")))return name(standings(s.fixtures()).getFirst().team());var past=city.getAttachedOrElse(ARCHIVE,Map.of()).values().stream().filter(f->f.id().startsWith((s.number()-1)+"/")&&TEAMS.contains(f.home())).toList();return past.size()==6?name(standings(past).getFirst().team()):"";}
    static String fanLine(ServerPlayer p){var m=p.getAttached(MEMBER);String champion=champion(p.level());return m!=null&&!champion.isEmpty()?(name(m.team()).equals(champion)?"Complimenti al tuo quartiere: "+champion+" ha vinto il campionato!":"Vediamo se il tuo quartiere riuscirà a battere "+champion+" nella prossima stagione."):"";}
    static int menu(ServerPlayer p){
        var member=p.getAttached(MEMBER);
        return menu(p,member==null?FootballDivisions.Division.D:FootballDivisions.division(member.team()),LeaguePage.CALENDARIO);
    }
    private enum LeaguePage {
        CALENDARIO("Calendario"), CLASSIFICA("Classifica"), CONTRATTI("Contratti");
        final String label;
        LeaguePage(String label) { this.label = label; }
        String command() { return name().toLowerCase(Locale.ROOT); }
    }

    private static int menu(ServerPlayer p, FootballDivisions.Division division, LeaguePage page) {
        if (p.level().dimension() != NapoliMap.LEVEL || division == null) return 0;
        var city = p.level();
        var season = season(city);
        long today = NapoliBroadcast.day(city);
        String command = "calcio campionato " + division.name();
        var entries = new ArrayList<CompanionBoard.Entry>();
        var member = p.getAttached(MEMBER);
        var current = member == null ? null : FootballDivisions.division(member.team());
        var fixtures = season.fixtures().stream().filter(f -> FootballDivisions.division(f.home()) == division).toList();

        for (var section : LeaguePage.values())
            if (section != page) entries.add(new CompanionBoard.Entry(section.label, division.label(), command + " " + section.command()));

        switch (page) {
            case CALENDARIO -> {
                var next = fixtures.stream().filter(f -> side(p, f) >= 0 && !f.state().equals("played")).findFirst();
                next.ifPresent(f -> entries.add(new CompanionBoard.Entry("Prossima partita · " + matchup(f),
                        matchStatus(f, today), f.day() <= today ? "calcio partita " + f.id() : "")));
                if (member == null) entries.add(new CompanionBoard.Entry("Scegli il tuo club", "La carriera parte dalla Serie D.",
                        "calcio campionato D contratti"));
                for (var f : fixtures) {
                    boolean personal = side(p, f) >= 0;
                    boolean available = personal && f.day() <= today && !f.state().equals("played");
                    if (next.filter(f::equals).isPresent()) continue;
                    entries.add(new CompanionBoard.Entry(matchup(f), matchStatus(f, today),
                            available ? "calcio partita " + f.id() : ""));
                }
                if (season.fixtures().stream().allMatch(f -> f.state().equals("played")))
                    entries.add(new CompanionBoard.Entry("Nuova stagione", "Tutte le partite sono concluse.", "calcio nuova_stagione"));
            }
            case CLASSIFICA -> {
                int place = 0;
                for (var row : standings(fixtures,division)) {
                    int difference = row.goals() - row.conceded();
                    entries.add(new CompanionBoard.Entry(++place + ". " + name(row.team()) + " · " + row.points() + " pt",
                            "Partite " + row.played() + " · Gol " + row.goals() + "–" + row.conceded()
                                    + " · Diff. " + (difference > 0 ? "+" : "") + difference, ""));
                }
            }
            case CONTRATTI -> {
                if (FootballEmployment.hasContract(p))
                    entries.add(new CompanionBoard.Entry(FootballEmployment.summary(p), FootballPractice.roleLabel(p), ""));
                entries.add(new CompanionBoard.Entry("Profilo", "OVR " + FootballPractice.overall(p) + " · Partite "
                        + FootballPractice.careerMatches(p) + " · Gol " + FootballPractice.careerGoals(p)
                        + " · Passaggi " + FootballPractice.leaguePasses(p) + " · Recuperi " + FootballPractice.leagueRecoveries(p), ""));
                if (current != null && division.ordinal() == current.ordinal() + 1) {
                    var role = FootballPractice.position(p);
                    String performance = switch (role) {
                        case ATTACCANTE -> FootballPractice.careerGoals(p) + "/" + division.roleTarget(role) + " gol";
                        case CENTROCAMPISTA -> FootballPractice.leaguePasses(p) + "/" + division.roleTarget(role) + " passaggi";
                        case DIFENSORE -> FootballPractice.leagueRecoveries(p) + "/" + division.roleTarget(role) + " recuperi";
                    };
                    entries.add(new CompanionBoard.Entry("Requisiti offerta", "OVR " + FootballPractice.overall(p) + "/" + division.overall
                            + " · Partite " + FootballPractice.careerMatches(p) + "/" + division.matches + " · " + performance, ""));
                }
                if (canSign(p,member,division) && (member == null || current != division || member.season() != season.number()))
                    for (var club : division.clubs) if (member == null || !club.id().equals(member.team()))
                        entries.add(new CompanionBoard.Entry("Firma con " + club.name(),
                                FootballEmployment.terms(club.id()).appearancePay + " € a presenza ufficiale", "calcio quartiere " + club.id()));
                entries.add(new CompanionBoard.Entry("Ritira paga e premi", "Compensi delle partite concluse.", "calcio premi"));
            }
        }
        for (var tier : FootballDivisions.Division.values()) if (tier != division)
            entries.add(new CompanionBoard.Entry(tier.label(), page.label, "calcio campionato " + tier.name() + " " + page.command()));
        if (page == LeaguePage.CALENDARIO) {
            entries.add(new CompanionBoard.Entry("Basket", "Tiri liberi", "basket"));
            entries.add(new CompanionBoard.Entry("Atletica", "Sprint cronometrato", "atletica"));
        }
        return CityCompanion.show(p,"campionato/" + division.name() + "/" + page.command(),division.label() + " · Giorno " + today + " · Stagione " + season.number(),entries);
    }

    private static String matchup(Fixture fixture) {
        return name(fixture.home()) + " – " + name(fixture.away());
    }

    private static String matchStatus(Fixture f, long today) {
        String status = switch (f.state()) {
            case "played" -> "Finale " + f.homeGoals() + "–" + f.awayGoals();
            case "playing" -> "Riprendi · " + f.homeGoals() + "–" + f.awayGoals();
            default -> f.day() == today ? "Oggi" : f.day() < today ? "Da giocare" : "Tra " + (f.day() - today) + " g";
        };
        return "Giorno " + f.day() + " · " + status;
    }

    static void tick(ServerLevel city){
        var s=season(city);long day=NapoliBroadcast.day(city);
        for(var f:s.fixtures())if(f.state().equals("scheduled")&&day>f.day()+1){var random=new Random(31L*s.number()+f.id().hashCode());put(city,new Fixture(f.id(),f.home(),f.away(),f.day(),random.nextInt(4),random.nextInt(4),3000,"played",Map.of()));}
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null&&NapoliCivicLife.census(city)!=null)tick(city);}});
        var league=Commands.literal("campionato").executes(c->menu(c.getSource().getPlayerOrException()));
        for (var division : FootballDivisions.Division.values()) {
            var tab = Commands.literal(division.name()).executes(c->menu(c.getSource().getPlayerOrException(),division,LeaguePage.CALENDARIO));
            for (var page : LeaguePage.values())
                tab.then(Commands.literal(page.command()).executes(c->menu(c.getSource().getPlayerOrException(),division,page)));
            league.then(tab);
        }
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("calcio").then(league)
                .then(Commands.literal("quartiere").then(Commands.argument("nome",StringArgumentType.word()).suggests((c,b)->{for(var division:FootballDivisions.Division.values())for(var team:division.clubs)b.suggest(team.id());return b.buildFuture();}).executes(c->choose(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"nome")))))
                .then(Commands.literal("partita").then(Commands.argument("id",StringArgumentType.greedyString()).executes(c->start(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id")))))
                .then(Commands.literal("premi").executes(c->claim(c.getSource().getPlayerOrException()))).then(Commands.literal("nuova_stagione").executes(c->next(c.getSource().getPlayerOrException())))));
    }
    private CityLeague(){}
}
