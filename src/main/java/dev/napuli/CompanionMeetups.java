package dev.napuli;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.*;
import static dev.napuli.CityText.t;

/** World-saved groups and invitation deadlines; accepting never teleports a player. */
final class CompanionMeetups {
    record Invitation(UUID token,UUID sender,UUID recipient,CityCompanion.Goal goal,long expires) {
        static final Codec<Invitation> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("token").forGetter(Invitation::token),UUIDUtil.CODEC.fieldOf("sender").forGetter(Invitation::sender),UUIDUtil.CODEC.fieldOf("recipient").forGetter(Invitation::recipient),CityCompanion.Goal.CODEC.fieldOf("goal").forGetter(Invitation::goal),Codec.LONG.fieldOf("expires").forGetter(Invitation::expires)).apply(i,Invitation::new));
    }
    record Group(UUID id,UUID leader,CityCompanion.Goal goal,List<UUID> members,Set<UUID> arrived,long revision) {
        static final Codec<Group> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("id").forGetter(Group::id),UUIDUtil.CODEC.fieldOf("leader").forGetter(Group::leader),CityCompanion.Goal.CODEC.fieldOf("goal").forGetter(Group::goal),UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Group::members),UUIDUtil.CODEC.listOf().xmap(Set::copyOf,List::copyOf).fieldOf("arrived").forGetter(Group::arrived),Codec.LONG.fieldOf("revision").forGetter(Group::revision)).apply(i,Group::new));
    }
    static final AttachmentType<Map<String,Invitation>> INVITES=AttachmentRegistry.<Map<String,Invitation>>builder().persistent(Codec.unboundedMap(Codec.STRING,Invitation.CODEC)).buildAndRegister(Napuli.id("meeting_invitations"));
    static final AttachmentType<Map<String,Group>> GROUPS=AttachmentRegistry.<Map<String,Group>>builder().persistent(Codec.unboundedMap(Codec.STRING,Group.CODEC)).buildAndRegister(Napuli.id("meeting_groups"));
    static final AttachmentType<String> SYNC=AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath().buildAndRegister(Napuli.id("meeting_goal_revision"));
    private static final Map<UUID,Long> LAST_INVITE=new HashMap<>();
    static boolean mayAccept(Invitation i,UUID p,long now){return i!=null&&i.recipient().equals(p)&&now<i.expires();}
    static UUID uuid(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException e){return null;}}
    static Group group(ServerLevel city,UUID p){return city.getAttachedOrElse(GROUPS,Map.of()).values().stream().filter(g->g.members().contains(p)).findFirst().orElse(null);}
    static void put(ServerLevel city,Group g){var all=new HashMap<>(city.getAttachedOrElse(GROUPS,Map.of()));all.put(g.id().toString(),g);city.setAttached(GROUPS,Map.copyOf(all));}
    static void removeInvite(ServerLevel city,UUID id){var all=new HashMap<>(city.getAttachedOrElse(INVITES,Map.of()));all.remove(id.toString());city.setAttached(INVITES,Map.copyOf(all));}
    static String name(ServerLevel city,UUID id){var p=city.getServer().getPlayerList().getPlayer(id);return p==null?id.toString().substring(0,8):p.getName().getString();}
    static int menu(ServerPlayer p){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));
        var city=p.level();var entries=new ArrayList<CompanionBoard.Entry>();long now=city.getGameTime();
        for(var i:city.getAttachedOrElse(INVITES,Map.of()).values())if(i.expires()>now){
            if(i.recipient().equals(p.getUUID()))entries.add(new CompanionBoard.Entry(t("group.invite_from",name(city,i.sender())),t("group.invite_detail",i.goal().name(),(i.expires()-now+19)/20),"compagno accetta "+i.token(),"napuli.ui.group.decline","compagno rifiuta "+i.token()));
            if(i.sender().equals(p.getUUID()))entries.add(new CompanionBoard.Entry(t("group.invite_to",name(city,i.recipient())),t("group.invite_detail",i.goal().name(),(i.expires()-now+19)/20),"compagno annulla_invito "+i.token()));
        }
        var g=group(city,p.getUUID());
        if(g!=null){
            entries.add(new CompanionBoard.Entry(t("coop.title"),t("coop.saved"),"compagno cooperative"));
            entries.add(new CompanionBoard.Entry(t("group.destination",g.goal().name()),t("group.saved"),"compagno riprendi_gruppo"));
            for(var id:g.members()){var player=city.getServer().getPlayerList().getPlayer(id);entries.add(new CompanionBoard.Entry(t("group.member",name(city,id),t(id.equals(g.leader())?"group.leader":"group.player")),t(g.arrived().contains(id)?"group.arrived":player==null?"group.offline":"group.traveling"),""));}
            if(g.leader().equals(p.getUUID()))entries.add(new CompanionBoard.Entry(t("group.redirect"),t("group.redirect_help"),"compagno gruppo_meta"));
            entries.add(new CompanionBoard.Entry(t("group.leave"),t("group.leave_help"),"compagno esci_gruppo"));
        }
        if(p.getAttached(CityCompanion.GOAL)==null&&g==null)entries.add(new CompanionBoard.Entry(t("group.choose_goal"),t("group.choose_help"),"compagno atlante"));
        else if(g==null||g.leader().equals(p.getUUID()))for(var other:city.getServer().getPlayerList().getPlayers())if(other!=p&&CityCompanion.inCity(other)&&(g==null||!g.members().contains(other.getUUID())))entries.add(new CompanionBoard.Entry(t("group.invite_player",other.getName()),t("group.share",p.getAttached(CityCompanion.GOAL)==null?g.goal().label():p.getAttached(CityCompanion.GOAL).label()),"compagno invita "+other.getUUID()));
        if(entries.isEmpty())entries.add(new CompanionBoard.Entry(t("group.empty"),t("group.empty_help"),""));
        return CityCompanion.show(p,"amici",t("group.title"),entries);
    }
    static int invite(ServerPlayer p,String id){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));
        var target=uuid(id);var other=target==null?null:p.level().getServer().getPlayerList().getPlayer(target);var goal=p.getAttached(CityCompanion.GOAL);var g=group(p.level(),p.getUUID());
        if(other==null||other==p||!CityCompanion.inCity(other))return CityCompanion.fail(p,t("group.error_online"));
        if(goal==null&&g!=null)goal=g.goal();
        if(goal==null)return CityCompanion.fail(p,t("group.choose_help"));
        if(g!=null&&(!g.leader().equals(p.getUUID())||g.members().size()>=32))return CityCompanion.fail(p,t("group.error_leader"));
        if(g!=null&&!g.goal().equals(goal))return CityCompanion.fail(p,t("group.redirect_first"));
        long now=p.level().getGameTime();if(now-LAST_INVITE.getOrDefault(p.getUUID(),-100L)<100)return CityCompanion.fail(p,t("group.cooldown"));
        var all=new HashMap<>(p.level().getAttachedOrElse(INVITES,Map.of()));all.values().removeIf(i->i.expires()<=now||i.sender().equals(p.getUUID())&&i.recipient().equals(other.getUUID()));
        if(all.values().stream().filter(i->i.recipient().equals(other.getUUID())).count()>=32)return CityCompanion.fail(p,t("group.inbox_full"));
        LAST_INVITE.put(p.getUUID(),now);var i=new Invitation(UUID.randomUUID(),p.getUUID(),other.getUUID(),goal,now+2400);all.put(i.token().toString(),i);p.level().setAttached(INVITES,Map.copyOf(all));
        NapoliNotice.show(other,t("group.invitation",p.getName(),goal.name()));NapoliNotice.show(p,t("group.sent",other.getName()));return 1;
    }
    static int accept(ServerPlayer p,String token){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var city=p.level();var i=city.getAttachedOrElse(INVITES,Map.of()).get(token);
        if(!mayAccept(i,p.getUUID(),city.getGameTime()))return CityCompanion.fail(p,t("group.invalid"));var old=group(city,i.sender());
        if(old!=null&&(!old.leader().equals(i.sender())||!old.goal().equals(i.goal())||old.members().size()>=32))return CityCompanion.fail(p,t("group.changed"));
        if(old!=null&&old.members().contains(p.getUUID())){removeInvite(city,i.token());return menu(p);}
        leave(city,p.getUUID());removeInvite(city,i.token());var members=new ArrayList<UUID>();if(old==null)members.add(i.sender());else members.addAll(old.members());members.add(p.getUUID());
        var g=new Group(old==null?UUID.randomUUID():old.id(),i.sender(),i.goal(),List.copyOf(members),old==null?Set.of():old.arrived(),old==null?1:old.revision());put(city,g);sync(p,g,true);
        var sender=city.getServer().getPlayerList().getPlayer(i.sender());if(sender!=null){if(CityCompanion.inCity(sender))sync(sender,g,false);NapoliNotice.show(sender,t("group.accepted",p.getName(),i.goal().name()));}return 1;
    }
    static void sync(ServerPlayer p,Group g,boolean force){String revision=g.id()+"/"+g.revision();if(force||!p.getAttachedOrElse(SYNC,"").equals(revision)){CityCompanion.pin(p,g.goal());p.setAttached(SYNC,revision);NapoliNotice.show(p,t("group.follow",g.goal().name()));}}
    static int decline(ServerPlayer p,String token){var i=p.level().getAttachedOrElse(INVITES,Map.of()).get(token);if(i==null||!i.recipient().equals(p.getUUID()))return CityCompanion.fail(p,t("group.invalid"));removeInvite(p.level(),i.token());return menu(p);}
    static int cancel(ServerPlayer p,String token){var i=p.level().getAttachedOrElse(INVITES,Map.of()).get(token);if(i==null||!i.sender().equals(p.getUUID()))return CityCompanion.fail(p,t("group.invalid"));removeInvite(p.level(),i.token());return menu(p);}
    static void leave(ServerLevel city,UUID player){
        var g=group(city,player);if(g==null)return;var all=new HashMap<>(city.getAttachedOrElse(GROUPS,Map.of()));var members=g.members().stream().filter(id->!id.equals(player)).toList();var arrived=new HashSet<>(g.arrived());arrived.remove(player);
        var invitations=new HashMap<>(city.getAttachedOrElse(INVITES,Map.of()));invitations.values().removeIf(i->i.sender().equals(player));city.setAttached(INVITES,Map.copyOf(invitations));
        if(members.isEmpty())all.remove(g.id().toString());else all.put(g.id().toString(),new Group(g.id(),g.leader().equals(player)?members.getFirst():g.leader(),g.goal(),members,Set.copyOf(arrived),g.revision()));city.setAttached(GROUPS,Map.copyOf(all));var p=city.getServer().getPlayerList().getPlayer(player);if(p!=null)p.removeAttached(SYNC);
    }
    static int leave(ServerPlayer p){if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));leave(p.level(),p.getUUID());NapoliNotice.show(p,t("group.left"));return menu(p);}
    static int resume(ServerPlayer p){var g=group(p.level(),p.getUUID());if(g==null)return CityCompanion.fail(p,t("group.none"));sync(p,g,true);return 1;}
    static int redirect(ServerPlayer p){
        if(!CityCompanion.inCity(p))return CityCompanion.fail(p,t("error.city"));var old=group(p.level(),p.getUUID());var goal=p.getAttached(CityCompanion.GOAL);
        if(old==null||!old.leader().equals(p.getUUID())||goal==null)return CityCompanion.fail(p,t("group.error_leader"));
        var g=new Group(old.id(),old.leader(),goal,old.members(),Set.of(),old.revision()+1);put(p.level(),g);for(var id:g.members()){var member=p.level().getServer().getPlayerList().getPlayer(id);if(member!=null&&CityCompanion.inCity(member))sync(member,g,true);}return menu(p);
    }
    static void register(){
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LAST_INVITE.clear());
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%20!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;var invitations=city.getAttachedOrElse(INVITES,Map.of());
            if(invitations.values().stream().anyMatch(i->i.expires()<=city.getGameTime())){var active=new HashMap<>(invitations);active.values().removeIf(i->i.expires()<=city.getGameTime());city.setAttached(INVITES,Map.copyOf(active));}
            for(var g:List.copyOf(city.getAttachedOrElse(GROUPS,Map.of()).values())){var arrived=new HashSet<>(g.arrived());for(var id:g.members()){var p=s.getPlayerList().getPlayer(id);if(p==null||!CityCompanion.inCity(p))continue;sync(p,g,false);
                if(!arrived.contains(id)&&g.goal().position().distToCenterSqr(p.position())<12*12){arrived.add(id);for(var member:g.members()){var other=s.getPlayerList().getPlayer(member);if(other!=null)NapoliNotice.show(other,t("group.arrival",p.getName(),g.goal().name()));}}}
                if(!arrived.equals(g.arrived()))put(city,new Group(g.id(),g.leader(),g.goal(),g.members(),Set.copyOf(arrived),g.revision()));
            }
        });
    }
    private CompanionMeetups(){}
}

