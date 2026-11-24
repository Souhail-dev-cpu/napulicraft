package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;

/** Four neighborhoods, a persistent calendar, resumable matches and player-owned prize receipts. */
final class CityLeague {
    static final List<String> TEAMS=List.of("partenope","mergellina","centro","fuorigrotta");
    static String name(String team){return switch(team){case "partenope"->"Partenope";case "mergellina"->"Mergellina";case "centro"->"Centro storico";default->"Fuorigrotta";};}
    static int color(String team){return switch(team){case "partenope"->0x319bdb;case "mergellina"->0x31865b;case "centro"->0xb84b45;default->0x8258a5;};}
    record Member(String team,int season){static final Codec<Member> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("team").forGetter(Member::team),Codec.INT.fieldOf("season").forGetter(Member::season)).apply(i,Member::new));}
    record Participant(int side,int ticks,int goals){static final Codec<Participant> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("side").forGetter(Participant::side),Codec.INT.fieldOf("ticks").forGetter(Participant::ticks),Codec.INT.fieldOf("goals").forGetter(Participant::goals)).apply(i,Participant::new));}
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
        for(int i=0;i<pairs.length;i++)fixtures.add(new Fixture(number+"/"+i,TEAMS.get(pairs[i][0]),TEAMS.get(pairs[i][1]),day+i/2*2,0,0,0,"scheduled",Map.of()));return new Season(number,day,List.copyOf(fixtures));
    }
    static Season season(ServerLevel city){var s=city.getAttached(SEASON);if(s==null){s=schedule(1,NapoliBroadcast.day(city));city.setAttached(SEASON,s);}return s;}
    static Fixture fixture(ServerLevel city,String id){return season(city).fixtures().stream().filter(f->f.id().equals(id)).findFirst().orElse(null);}
    static void put(ServerLevel city,Fixture fixture){var s=season(city);city.setAttached(SEASON,new Season(s.number(),s.opened(),s.fixtures().stream().map(f->f.id().equals(fixture.id())?fixture:f).toList()));}
    static int choose(ServerPlayer p,String team){
        if(!TEAMS.contains(team)||p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive()||FootballPractice.isPlaying(p))return 0;
        var s=season(p.level());var old=p.getAttached(MEMBER);if(old!=null&&old.season()==s.number()&&!old.team().equals(team)){p.sendOverlayMessage(Component.literal("Rappresenti lo stesso quartiere per tutta la stagione."));return 0;}
        p.setAttached(MEMBER,new Member(team,s.number()));p.sendSystemMessage(Component.literal("Giochi per "+name(team)+" nella stagione "+s.number()+"."));return 1;
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
        present.forEach((player,stats)->{var old=roster.get(player);if(old==null||old.side()==stats.side())roster.put(player,new Participant(stats.side(),Math.max(old==null?0:old.ticks(),stats.ticks()),Math.max(old==null?0:old.goals(),stats.goals())));});
        put(city,new Fixture(id,f.home(),f.away(),f.day(),Math.clamp(home,0,99),Math.clamp(away,0,99),Math.max(f.elapsed(),elapsed),"playing",Map.copyOf(roster)));
    }
    static void complete(ServerLevel city,String id){
        var f=fixture(city,id);if(f==null||!f.state().equals("playing")||f.elapsed()<3000)return;
        put(city,new Fixture(id,f.home(),f.away(),f.day(),f.homeGoals(),f.awayGoals(),f.elapsed(),"played",f.players()));
        for(var p:city.players())NapoliNotice.show(p,Component.literal(name(f.home())+" "+f.homeGoals()+" – "+f.awayGoals()+" "+name(f.away())));
    }
    static List<Standing> standings(Collection<Fixture> fixtures){
        var rows=new ArrayList<Standing>();for(var team:TEAMS){int points=0,goals=0,conceded=0,played=0;
            for(var f:fixtures)if(f.state().equals("played")&&(f.home().equals(team)||f.away().equals(team))){int a=f.home().equals(team)?f.homeGoals():f.awayGoals(),b=f.home().equals(team)?f.awayGoals():f.homeGoals();goals+=a;conceded+=b;points+=a>b?3:a==b?1:0;played++;}rows.add(new Standing(team,points,goals,conceded,played));
        }rows.sort(Comparator.comparingInt(Standing::points).reversed().thenComparing(Comparator.comparingInt((Standing r)->r.goals()-r.conceded()).reversed()).thenComparing(Comparator.comparingInt(Standing::goals).reversed()).thenComparing(Standing::team));return rows;
    }
    static int claim(ServerPlayer p){
        if(p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive())return 0;var city=p.level();var receipts=new HashSet<>(p.getAttachedOrElse(RECEIPTS,Set.of()));var fixtures=new HashMap<>(city.getAttachedOrElse(ARCHIVE,Map.of()));for(var f:season(city).fixtures())fixtures.put(f.id(),f);
        int money=0;for(var f:fixtures.values()){
            var participant=f.players().get(p.getUUID().toString());if(!f.state().equals("played")||participant==null||participant.ticks()<600||!receipts.add(f.id()))continue;
            boolean home=participant.side()==0;int a=home?f.homeGoals():f.awayGoals(),b=home?f.awayGoals():f.homeGoals();int reward=a>b?12:a==b?6:3;
            p.setAttached(RECEIPTS,Set.copyOf(receipts));NapoliStreet.credit(p,reward,"league",f.id());FootballPractice.awardLeague(p,participant.goals());money+=reward;
        }
        // The receipt and wallet live in the same player save, so replaying a result cannot pay twice.
        if(money>0)p.sendSystemMessage(Component.literal("Premi del campionato: +"+money+" €."));return money;
    }
    static int next(ServerPlayer p){
        if(p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator())return 0;var city=p.level();var s=season(city);if(s.fixtures().stream().anyMatch(f->!f.state().equals("played")))return 0;
        var archive=new HashMap<>(city.getAttachedOrElse(ARCHIVE,Map.of()));s.fixtures().forEach(f->archive.put(f.id(),f));while(archive.size()>48){String oldest=archive.entrySet().stream().min(Comparator.comparingLong(e->e.getValue().day())).orElseThrow().getKey();archive.remove(oldest);}city.setAttached(ARCHIVE,Map.copyOf(archive));city.setAttached(SEASON,schedule(s.number()+1,NapoliBroadcast.day(city)));return 1;
    }
    static String champion(ServerLevel city){var s=season(city);if(s.fixtures().stream().allMatch(f->f.state().equals("played")))return name(standings(s.fixtures()).getFirst().team());var past=city.getAttachedOrElse(ARCHIVE,Map.of()).values().stream().filter(f->f.id().startsWith((s.number()-1)+"/")).toList();return past.size()==6?name(standings(past).getFirst().team()):"";}
    static String fanLine(ServerPlayer p){var m=p.getAttached(MEMBER);String champion=champion(p.level());return m!=null&&!champion.isEmpty()?(name(m.team()).equals(champion)?"Complimenti al tuo quartiere: "+champion+" ha vinto il campionato!":"Vediamo se il tuo quartiere riuscirà a battere "+champion+" nella prossima stagione."):"";}
    static int menu(ServerPlayer p){
        if(p.level().dimension()!=NapoliMap.LEVEL)return 0;var city=p.level();var s=season(city);var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();var buttons=new ArrayList<ActionButton>();
        for(var row:standings(s.fixtures()))body.add(new PlainMessage(Component.literal(name(row.team())+" · "+row.points()+" punti · "+row.goals()+"–"+row.conceded()+" gol"),270));
        for(var f:s.fixtures()){body.add(new PlainMessage(Component.literal("Giorno "+f.day()+" · "+name(f.home())+" – "+name(f.away())+" · "+(f.state().equals("played")?f.homeGoals()+"–"+f.awayGoals():f.state().equals("playing")?"in corso, riprendibile":"in programma")),270));if(!f.state().equals("played")&&side(p,f)>=0&&NapoliBroadcast.day(city)>=f.day())buttons.add(CitySocial.button("Gioca: "+name(f.home())+" – "+name(f.away()),"/calcio partita "+f.id()));}
        var member=p.getAttached(MEMBER);if(member==null||member.season()!=s.number())for(var team:TEAMS)buttons.add(CitySocial.button("Rappresenta "+name(team),"/calcio quartiere "+team));
        buttons.add(CitySocial.button("Ritira premi","/calcio premi"));if(s.fixtures().stream().allMatch(f->f.state().equals("played")))buttons.add(CitySocial.button("Nuova stagione","/calcio nuova_stagione"));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Torneo dei quartieri · stagione "+s.number()),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),buttons,Optional.empty(),1)));return 1;
    }
    static void tick(ServerLevel city){
        var s=season(city);long day=NapoliBroadcast.day(city);
        for(var f:s.fixtures())if(f.state().equals("scheduled")&&day>f.day()+1){var random=new Random(31L*s.number()+f.id().hashCode());put(city,new Fixture(f.id(),f.home(),f.away(),f.day(),random.nextInt(4),random.nextInt(4),3000,"played",Map.of()));}
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null&&NapoliCivicLife.census(city)!=null)tick(city);}});
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("calcio").then(Commands.literal("campionato").executes(c->menu(c.getSource().getPlayerOrException())))
                .then(Commands.literal("quartiere").then(Commands.argument("nome",StringArgumentType.word()).suggests((c,b)->{TEAMS.forEach(b::suggest);return b.buildFuture();}).executes(c->choose(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"nome")))))
                .then(Commands.literal("partita").then(Commands.argument("id",StringArgumentType.word()).executes(c->start(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id")))))
                .then(Commands.literal("premi").executes(c->claim(c.getSource().getPlayerOrException()))).then(Commands.literal("nuova_stagione").executes(c->next(c.getSource().getPlayerOrException())))));
    }
    private CityLeague(){}
}
