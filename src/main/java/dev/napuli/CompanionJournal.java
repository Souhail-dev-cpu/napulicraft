package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import static dev.napuli.CityText.t;

/** Live projection of existing saved tasks. The journal does not create parallel completion flags. */
final class CompanionJournal {
    record Activity(String id,Component title,Component detail,BlockPos target,String action) {}
    static final AttachmentType<String> SELECTED=AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath().buildAndRegister(Napuli.id("journal_selected"));
    static void register() {}
    static List<Activity> activities(ServerPlayer p){
        if(!CityCompanion.inCity(p))return List.of();var all=new ArrayList<Activity>();
        for(var project:p.level().getAttachedOrElse(CooperativeJobs.PROJECTS,Map.of()).values())if(project.members().contains(p.getUUID())&&(project.state().equals("active")||!project.contribution(p.getUUID()).claimed()))all.add(new Activity("cooperativa/"+project.id(),t("coop.kind."+project.kind()),t("coop.summary",project.progress(),project.required(),project.reward(),t("coop.state."+project.state())),project.state().equals("active")?project.position():null,"compagno cooperativa vedi/"+project.id()));
        var work=NapoliEmployment.objective(p);if(work!=null)all.add(new Activity("lavoro",t("work.shift"),CompanionWork.summary(p),work.target(),"compagno carriera"));
        var faction=NapoliFactions.objective(p);if(faction.target()!=null&&!Set.of("none","cooldown").contains(faction.key()))all.add(new Activity("missione",t("journal.faction"),Component.translatable("napuli.hud.obj."+faction.key()),faction.target(),""));
        for(var job:NapoliLocalJobs.active(p))all.add(new Activity("locale/"+job.name(),t("journal.local"),Component.translatable(job.key()+".task"),NapoliLocalJobs.journalTarget(p,job),""));
        var delivery=p.getAttached(CityEconomy.DELIVERY);
        if(delivery!=null)all.add(new Activity("consegna",t("journal.delivery",delivery.shop()),t("journal.delivery_info",Math.min(8,WorkResources.count(p,net.minecraft.world.item.Items.WHEAT)),p.level().getGameTime()<=delivery.deadline()?12:6),CityEconomy.site(delivery.shop()),"quartiere consegna "+delivery.shop()));
        for(var entry:p.level().getAttachedOrElse(CityPersonalGoals.GOALS,Map.of()).entrySet()){
            var g=entry.getValue();if(g.complete()||!g.helper().equals(p.getUUID().toString()))continue;
            UUID id=CompanionMeetups.uuid(entry.getKey());var citizen=id==null?null:NapoliCivicLife.profile(p.level(),id);if(citizen==null)continue;
            var entity=p.level().getEntity(id);var house=CityLivingHomes.find(citizen.property());var target=entity!=null?entity.blockPosition():house==null?null:house.entrance();
            all.add(new Activity("persona/"+id,t("journal.person",citizen.name()),t("journal.help."+g.kind()),target,"abitanti completa "+id));
        }
        for(var h:NapoliMergellina.PROPERTIES){var a=p.level().getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()).get(h.id());if(a!=null&&a.tenant().equals(p.getUUID())&&a.arrears()>0)all.add(new Activity("casa/"+h.id(),t("house.detail",h.address()),t("journal.bills",a.arrears()),h.entrance(),"compagno casa "+h.id()));}
        var receipts=p.getAttachedOrElse(CityLeague.RECEIPTS,Set.of());var fixtures=new HashMap<>(p.level().getAttachedOrElse(CityLeague.ARCHIVE,Map.of()));var season=p.level().getAttached(CityLeague.SEASON);
        if(season!=null)for(var f:season.fixtures())fixtures.put(f.id(),f);
        int reward=0;for(var f:fixtures.values()){var played=f.players().get(p.getUUID().toString());if(!f.state().equals("played")||played==null||played.ticks()<600||receipts.contains(f.id()))continue;int a=played.side()==0?f.homeGoals():f.awayGoals(),b=played.side()==0?f.awayGoals():f.homeGoals();reward+=a>b?12:a==b?6:3;}
        if(reward>0)all.add(new Activity("premi",t("journal.prizes"),t("journal.prize_amount",reward),new BlockPos((FootballGround.MIN_X+FootballGround.MAX_X)/2,64,(FootballGround.MIN_Z+FootballGround.MAX_Z)/2),"calcio premi"));
        return List.copyOf(all);
    }
    static Activity selected(ServerPlayer p){String id=p.getAttachedOrElse(SELECTED,"");return activities(p).stream().filter(a->a.id().equals(id)).findFirst().orElse(null);}
    static int menu(ServerPlayer p){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var entries=new ArrayList<CompanionBoard.Entry>();
        for(var a:activities(p))entries.add(new CompanionBoard.Entry(a.title(),a.detail(),"compagno diario_segui "+a.id(),a.action().isEmpty()?"":"napuli.ui.journal.action",a.action()));
        if(entries.isEmpty())entries.add(new CompanionBoard.Entry(t("journal.empty"),t("journal.empty_help"),"compagno"));
        return CityCompanion.show(p,"diario",t("journal.title"),entries);
    }
    static int track(ServerPlayer p,String id){var a=activities(p).stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(a==null)return CityCompanion.fail(p,t("journal.completed"));
        if(a.target()==null)return CityCompanion.fail(p,t("journal.unlocated"));p.setAttached(SELECTED,id);p.setAttached(CityCompanion.TRACK,"diario");return 1;}
    private CompanionJournal(){}
}
