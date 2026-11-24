package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import static dev.napuli.CityText.t;

/** Reserved municipal rewards, saved contributions and individual, once-only claims. */
final class CooperativeJobs {
    static final int PAGE_SIZE=100;
    record Contribution(int units,List<ItemStack> materials,boolean carrying,boolean claimed){
        static final Codec<Contribution> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("units").forGetter(Contribution::units),ItemStack.CODEC.listOf().fieldOf("materials").forGetter(Contribution::materials),Codec.BOOL.fieldOf("carrying").forGetter(Contribution::carrying),Codec.BOOL.fieldOf("claimed").forGetter(Contribution::claimed)).apply(i,Contribution::new));
        static Contribution empty(){return new Contribution(0,List.of(),false,false);}
    }
    record Project(UUID id,UUID group,UUID leader,String kind,String target,BlockPos position,int required,int reward,String state,List<UUID> members,Map<String,Contribution> contributions){
        static final Codec<Project> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("id").forGetter(Project::id),UUIDUtil.CODEC.fieldOf("group").forGetter(Project::group),UUIDUtil.CODEC.fieldOf("leader").forGetter(Project::leader),Codec.STRING.fieldOf("kind").forGetter(Project::kind),Codec.STRING.fieldOf("target").forGetter(Project::target),BlockPos.CODEC.fieldOf("position").forGetter(Project::position),Codec.INT.fieldOf("required").forGetter(Project::required),Codec.INT.fieldOf("reward").forGetter(Project::reward),Codec.STRING.fieldOf("state").forGetter(Project::state),UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Project::members),Codec.unboundedMap(Codec.STRING,Contribution.CODEC).fieldOf("contributions").forGetter(Project::contributions)).apply(i,Project::new));
        int progress(){return contributions.values().stream().mapToInt(Contribution::units).sum();}
        Contribution contribution(UUID id){return contributions.getOrDefault(id.toString(),Contribution.empty());}
        Project state(String state){return new Project(id,group,leader,kind,target,position,required,reward,state,members,contributions);}
        Project with(UUID member,Contribution c){var all=new HashMap<>(contributions);all.put(member.toString(),c);return new Project(id,group,leader,kind,target,position,required,reward,state,members,Map.copyOf(all));}
    }
    static final AttachmentType<Map<String,Project>> PROJECTS=AttachmentRegistry.<Map<String,Project>>builder().persistent(Codec.unboundedMap(Codec.STRING,Project.CODEC)).buildAndRegister(Napuli.id("cooperative_jobs"));
    static void put(ServerLevel city,Project project){var all=new HashMap<>(city.getAttachedOrElse(PROJECTS,Map.of()));all.put(project.id().toString(),project);city.setAttached(PROJECTS,Map.copyOf(all));}
    static boolean reserved(ServerLevel city,String incident){return city.getAttachedOrElse(PROJECTS,Map.of()).values().stream().anyMatch(p->p.state().equals("active")&&p.kind().equals("maintenance")&&p.target().equals(incident));}
    static boolean managed(ServerPlayer p,Project project){var group=CompanionMeetups.group(p.level(),p.getUUID());return group!=null&&group.id().equals(project.group())&&group.leader().equals(p.getUUID());}
    static void cancel(ServerLevel city,Project project){project=city.getAttachedOrElse(PROJECTS,Map.of()).get(project.id().toString());if(project==null||!project.state().equals("active"))return;put(city,project.state("cancelled"));WorkEconomy.revenue(city,project.reward());}
    static boolean visible(ServerPlayer p,Project project){return project.members().contains(p.getUUID())||managed(p,project);}
    static int menu(ServerPlayer p){return menu(p,0);}
    static int menu(ServerPlayer p,int page){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var board=board(p,page);return CityCompanion.show(p,board.page(),board.title(),board.entries());
    }
    static CompanionBoard board(ServerPlayer p,int requestedPage){
        var entries=new ArrayList<CompanionBoard.Entry>();var group=CompanionMeetups.group(p.level(),p.getUUID());
        var projects=p.level().getAttachedOrElse(PROJECTS,Map.of()).values().stream().filter(project->visible(p,project))
                .sorted(Comparator.comparingInt((Project project)->project.state().equals("active")?0:!project.contribution(p.getUUID()).claimed()&&project.members().contains(p.getUUID())?1:2).thenComparing(Project::id)).toList();
        int pages=Math.max(1,(projects.size()+PAGE_SIZE-1)/PAGE_SIZE),page=Math.clamp(requestedPage,0,pages-1),first=page*PAGE_SIZE;
        if(pages>1)entries.add(new CompanionBoard.Entry(t("screen.page",page+1,pages,projects.size()),t("coop.saved"),""));
        for(var project:projects.subList(first,Math.min(first+PAGE_SIZE,projects.size())))entries.add(new CompanionBoard.Entry(t("coop.kind."+project.kind()),t("coop.summary",project.progress(),project.required(),project.reward(),t("coop.state."+project.state())),"compagno cooperativa vedi/"+project.id()));
        if(group==null)entries.add(new CompanionBoard.Entry(t("coop.need_group"),t("coop.need_group_help"),"compagno amici"));
        else if(group.leader().equals(p.getUUID())&&p.level().getAttachedOrElse(PROJECTS,Map.of()).values().stream().noneMatch(j->j.group().equals(group.id())&&j.state().equals("active"))){
            entries.add(new CompanionBoard.Entry(t("coop.kind.supply"),t("coop.supply_help",24),"compagno cooperativa crea/supply"));
            entries.add(new CompanionBoard.Entry(t("coop.kind.courier"),t("coop.courier_help",group.members().size()*8),"compagno cooperativa crea/courier"));
            for(var issue:p.level().getAttachedOrElse(CityWorkIncidents.ISSUES,Map.of()).values())if(!issue.solved()&&!reserved(p.level(),issue.id()))entries.add(new CompanionBoard.Entry(t("coop.kind.maintenance"),t("coop.maintenance_help",issue.line(),20),"compagno cooperativa crea/maintenance/"+issue.id()));
        }
        if(page>0)entries.add(new CompanionBoard.Entry(t("previous"),t("coop.saved"),"compagno cooperative "+(page-1)));
        if(page+1<pages)entries.add(new CompanionBoard.Entry(t("next"),t("coop.saved"),"compagno cooperative "+(page+1)));
        return new CompanionBoard(page==0?"cooperativa":"cooperativa/"+page,t("coop.title"),List.copyOf(entries));
    }
    static int create(ServerPlayer p,String kind,String target){
        if(!PlayerTrade.available(p)||!CityCompanion.inCity(p))return 0;var city=p.level();var g=CompanionMeetups.group(city,p.getUUID());if(g==null||!g.leader().equals(p.getUUID())||g.members().size()<2)return CityCompanion.fail(p,t("coop.need_group"));
        var projects=city.getAttachedOrElse(PROJECTS,Map.of());if(projects.values().stream().anyMatch(j->j.group().equals(g.id())&&j.state().equals("active"))||projects.values().stream().filter(j->j.state().equals("active")).count()>=32)return CityCompanion.fail(p,t("coop.active"));
        BlockPos at;int required,reward;
        switch(kind){case "supply"->{target="forno";at=CityEconomy.site(target);required=16;reward=24;}
            case "courier"->{target="garage";at=NapoliMunicipio.GARAGE;required=g.members().size();reward=required*8;}
            case "maintenance"->{var issue=CityWorkIncidents.get(city,target);if(issue==null||reserved(city,target))return CityCompanion.fail(p,t("coop.unavailable"));at=issue.position();required=2;reward=20;}
            default->{return 0;}}
        if(WorkEconomy.fund(city)<reward)return CityCompanion.fail(p,t("coop.fund"));
        var project=new Project(UUID.randomUUID(),g.id(),p.getUUID(),kind,target,at,required,reward,"active",g.members(),Map.of());city.setAttached(WorkEconomy.FUND,WorkEconomy.fund(city)-reward);put(city,project);
        var goal=new CityCompanion.Goal("cooperative",kind.equals("courier")?CityEconomy.DEPOT:at,t("coop.kind."+kind));CompanionMeetups.put(city,new CompanionMeetups.Group(g.id(),g.leader(),goal,g.members(),Set.of(),g.revision()+1));return detail(p,project);
    }
    static int detail(ServerPlayer p,Project project){
        if(!visible(p,project))return 0;boolean isMember=project.members().contains(p.getUUID());var rows=new ArrayList<CompanionBoard.Entry>();String prefix="compagno cooperativa ";String id=project.id().toString();
        rows.add(new CompanionBoard.Entry(t("coop.kind."+project.kind()),t("coop.summary",project.progress(),project.required(),project.reward(),t("coop.state."+project.state())),""));
        for(var member:project.members()){var c=project.contribution(member);rows.add(new CompanionBoard.Entry(net.minecraft.network.chat.Component.literal(CompanionMeetups.name(p.level(),member)),t("coop.member",c.units(),project.state().equals("completed")?shares(project).getOrDefault(member,0):0,t(c.claimed()?"coop.claimed":"coop.pending")),""));}
        if(project.state().equals("active")){if(isMember&&project.kind().equals("courier"))rows.add(new CompanionBoard.Entry(t("coop.collect"),t("coop.collect_help"),prefix+"ritira/"+id));rows.add(new CompanionBoard.Entry(t("coop.navigate"),t("coop.address",project.position().toShortString()),prefix+"segui/"+id));if(isMember)rows.add(new CompanionBoard.Entry(t("coop.contribute"),t("coop.contribute_help",t("coop.kind."+project.kind())),prefix+"contribuisci/"+id));if(managed(p,project))rows.add(new CompanionBoard.Entry(t("coop.cancel"),t("coop.cancel_help"),prefix+"annulla/"+id));}
        else if(isMember&&!project.contribution(p.getUUID()).claimed())rows.add(new CompanionBoard.Entry(t(project.state().equals("completed")?"coop.claim":"coop.refund"),t(project.state().equals("completed")?"coop.share_help":"coop.refund_help"),prefix+"riscuoti/"+id));
        rows.add(new CompanionBoard.Entry(t("trade.refresh"),t("coop.saved"),prefix+"vedi/"+id));return CityCompanion.show(p,"cooperativa/"+id,t("coop.title"),rows);
    }
    static int contribute(ServerPlayer p,Project project){
        if(!project.state().equals("active")||!project.members().contains(p.getUUID())||!PlayerTrade.available(p))return 0;var group=CompanionMeetups.group(p.level(),p.getUUID());if(group==null||!group.id().equals(project.group()))return CityCompanion.fail(p,t("coop.membership"));
        if(project.position().distToCenterSqr(p.position())>64)return CityCompanion.fail(p,t("coop.at_target"));
        var old=project.contribution(p.getUUID());var materials=new ArrayList<>(old.materials());int units=0;
        if(project.kind().equals("courier")){if(!old.carrying()||old.units()!=0)return CityCompanion.fail(p,t("coop.collect_first"));units=1;}
        else{if(project.kind().equals("maintenance")&&CityWorkIncidents.get(p.level(),project.target())==null){cancel(p.level(),project);return CityCompanion.fail(p,t("coop.unavailable"));}
            var item=project.kind().equals("supply")?Items.WHEAT:Items.IRON_INGOT;int remaining=project.required()-project.progress();var inventory=PlayerInventoryPlan.copy(p);
            // One unit per click makes each participant's contribution easy to control.
            if(remaining>0)for(var stack:inventory)if(stack.is(item)){materials.add(stack.copyWithCount(1));stack.shrink(1);units=1;break;}
            if(units==0)return CityCompanion.fail(p,t("coop.material",new ItemStack(item).getHoverName()));PlayerInventoryPlan.apply(p,inventory);
        }
        var next=project.with(p.getUUID(),new Contribution(old.units()+units,List.copyOf(materials),false,false));
        if(next.progress()==next.required()){if(next.kind().equals("supply"))CityEconomy.supply(p.level(),next.target(),next.required());else if(next.kind().equals("maintenance"))CityWorkIncidents.resolve(p.level(),next.target());else CityOccupations.supply(p.level(),"parts",CityOccupations.supply(p.level(),"parts")+next.required());next=next.state("completed");}
        put(p.level(),next);return detail(p,next);
    }
    static Map<UUID,Integer> shares(Project project){
        int total=project.progress();if(total<=0)return Map.of();var result=new LinkedHashMap<UUID,Integer>();var members=project.members().stream().sorted(Comparator.comparing(UUID::toString)).toList();int assigned=0;
        for(var id:members){int amount=(int)((long)project.reward()*project.contribution(id).units()/total);result.put(id,amount);assigned+=amount;}
        var ranked=members.stream().sorted(Comparator.comparingLong((UUID id)->-((long)project.reward()*project.contribution(id).units()%total)).thenComparing(UUID::toString)).toList();for(int n=0;n<project.reward()-assigned;n++){var id=ranked.get(n);result.put(id,result.get(id)+1);}return Map.copyOf(result);
    }
    static boolean claim(ServerPlayer p,Project project){
        project=p.level().getAttachedOrElse(PROJECTS,Map.of()).get(project.id().toString());if(project==null)return false;
        if(!project.members().contains(p.getUUID())||project.state().equals("active"))return false;var own=project.contribution(p.getUUID());if(own.claimed())return false;
        if(project.state().equals("completed")){int amount=shares(project).getOrDefault(p.getUUID(),0);if((long)NapoliStreet.cash(p)+amount>99999)return false;NapoliStreet.credit(p,amount,"cooperative",project.kind());}
        else{var inventory=PlayerInventoryPlan.copy(p);for(var item:own.materials())if(!PlayerInventoryPlan.add(inventory,item))return false;PlayerInventoryPlan.apply(p,inventory);}
        put(p.level(),project.with(p.getUUID(),new Contribution(own.units(),List.of(),false,true)));return true;
    }
    static int action(ServerPlayer p,String command){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));return execute(p,command);
    }
    static int execute(ServerPlayer p,String command){
        String[] bits=command.split("/");if(bits.length<2)return 0;if(bits[0].equals("crea"))return create(p,bits[1],bits.length>2?bits[2]:"");
        var project=p.level().getAttachedOrElse(PROJECTS,Map.of()).get(bits[1]);if(project==null||!visible(p,project))return CityCompanion.fail(p,t("coop.unavailable"));
        switch(bits[0]){case "vedi"->{return detail(p,project);}case "segui"->{CityCompanion.pin(p,new CityCompanion.Goal("cooperative",project.position(),t("coop.kind."+project.kind())));return detail(p,project);}case "contribuisci"->{return contribute(p,project);}case "annulla"->{if(!PlayerTrade.available(p)||!managed(p,project))return 0;cancel(p.level(),project);}case "riscuoti"->{if(!PlayerTrade.available(p)||!claim(p,project))return CityCompanion.fail(p,t("coop.cannot_claim"));}case "ritira"->{var c=project.contribution(p.getUUID());var group=CompanionMeetups.group(p.level(),p.getUUID());if(!project.members().contains(p.getUUID())||!PlayerTrade.available(p)||group==null||!group.id().equals(project.group())||!project.state().equals("active")||!project.kind().equals("courier")||c.units()>0||c.carrying())return 0;if(p.blockPosition().distSqr(CityEconomy.DEPOT)>64){CityCompanion.pin(p,new CityCompanion.Goal("depot",CityEconomy.DEPOT,t("coop.collect")));return CityCompanion.fail(p,t("coop.at_depot"));}put(p.level(),project.with(p.getUUID(),new Contribution(0,List.of(),true,false)));CityCompanion.pin(p,new CityCompanion.Goal("cooperative",project.position(),t("coop.kind.courier")));}default->{return 0;}}
        return detail(p,p.level().getAttachedOrElse(PROJECTS,Map.of()).get(bits[1]));
    }
    static void register(){ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;var groups=city.getAttachedOrElse(CompanionMeetups.GROUPS,Map.of());for(var project:city.getAttachedOrElse(PROJECTS,Map.of()).values())if(project.state().equals("active")&&(!groups.containsKey(project.group().toString())||project.kind().equals("maintenance")&&CityWorkIncidents.get(city,project.target())==null))cancel(city,project);});}
    private CooperativeJobs(){}
}
