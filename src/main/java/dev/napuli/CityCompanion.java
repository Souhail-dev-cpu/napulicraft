package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import static dev.napuli.CityText.t;

/** Persistent choices with localized, server-authoritative views of the city's existing systems. */
final class CityCompanion {
    record Goal(String name,BlockPos position,Component label){
        Goal(String name,BlockPos position){this(name,position,Component.literal(name));}
        static final Codec<Goal> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("name").forGetter(Goal::name),BlockPos.CODEC.fieldOf("position").forGetter(Goal::position),ComponentSerialization.CODEC.optionalFieldOf("label").forGetter(g->Optional.of(g.label()))).apply(i,(n,p,l)->new Goal(n,p,l.orElse(Component.literal(n)))));
    }
    record Destination(String id,Component name,Component detail,BlockPos position){}
    static final AttachmentType<String> TRACK=AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath().buildAndRegister(Napuli.id("companion_tracker"));
    static final AttachmentType<Goal> GOAL=AttachmentRegistry.<Goal>builder().persistent(Goal.CODEC).copyOnDeath().buildAndRegister(Napuli.id("companion_goal"));
    static final AttachmentType<List<String>> FAVORITES=AttachmentRegistry.<List<String>>builder().persistent(Codec.STRING.listOf()).copyOnDeath().buildAndRegister(Napuli.id("companion_favorites"));
    static final AttachmentType<String> RECIPE=AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath().buildAndRegister(Napuli.id("companion_recipe"));
    private static final List<String> TRACKERS=List.of("auto","lavoro","missione","guida","meta","ricetta","diario","nessuno");
    static int fail(ServerPlayer p,String message){return fail(p,Component.literal(message));}
    static int fail(ServerPlayer p,Component message){NapoliNotice.show(p,message);return 0;}
    static boolean inCity(ServerPlayer p){return p.level().dimension()==NapoliMap.LEVEL;}
    static int show(ServerPlayer p,String page,String title,List<CompanionBoard.Entry> entries){return show(p,page,Component.literal(title),entries);}
    static int show(ServerPlayer p,String page,Component title,List<CompanionBoard.Entry> entries){
        if(ServerPlayNetworking.canSend(p,CompanionBoard.TYPE))ServerPlayNetworking.send(p,new CompanionBoard(page,title,List.copyOf(entries)));
        else{
            var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();var buttons=new ArrayList<ActionButton>();
            for(var e:entries){body.add(new PlainMessage(e.title().copy().append("\n").append(e.detail()),300));
                if(!e.command().isEmpty())buttons.add(CitySocial.button(e.title().getString(),"/"+e.command()));}
            p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(title,Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),buttons,Optional.empty(),1)));
        }return 1;
    }
    static int home(ServerPlayer p){
        String[] keys={"atlas","tracker","work","housing","wallet","travel","recipes","shop","group","journal","ledger","help"};
        String[] commands={"compagno atlante","compagno obiettivi","compagno carriera","compagno case","portafoglio","compagno trasporti","compagno ricette","negozio","compagno amici","compagno diario","compagno spese","compagno aiuto"};
        var entries=new ArrayList<CompanionBoard.Entry>();
        for(int i=0;i<keys.length;i++)entries.add(new CompanionBoard.Entry(t("menu."+keys[i]),t("menu."+keys[i]+"_help"),commands[i]));
        for(var item:List.of("coop","trade","scooter","economy")){String command=switch(item){case "coop"->"cooperative";case "trade"->"scambi";case "scooter"->"motorini";default->"economia";};entries.add(new CompanionBoard.Entry(t("menu."+item),t("menu."+item+"_help"),"compagno "+command));}
        return show(p,"menu",t("menu.title"),entries);
    }
    static List<Destination> destinations(ServerPlayer p){
        var all=new ArrayList<Destination>();
        for(var place:NapoliAtlas.PLACES)all.add(new Destination("luogo/"+place.id(),Component.literal(place.name()),t("atlas.place",place.district()),place.arrival()));
        for(var stop:CityNetwork.stops()){
            String lines=CityNetwork.lines().stream().filter(l->l.stops().contains(stop.id())).map(CityNetwork.Line::id).reduce((a,b)->a+", "+b).orElse("");
            if(!lines.isEmpty())all.add(new Destination("fermata/"+stop.id(),Component.literal(stop.name()),t("atlas.stop",lines),stop.entrance()));
        }
        for(var line:CityNetwork.lines())if(line.mode()==CityNetwork.Mode.METRO)for(var id:line.stops()){var stop=CityNetwork.stop(id);all.add(new Destination("banchina/"+line.id()+"/"+id,t("atlas.platform",line.id(),stop.name()),t("atlas.platform_help"),stop.boarding(line)));}
        for(var d:DistrictLife.districts()){
            Component status=t(!inCity(p)?"atlas.status_city":DistrictShops.staff(p.level(),d)==null?"atlas.closed":"atlas.open");
            all.add(new Destination("bottega/"+d.id(),t("atlas.shop",d.name()),t("atlas.shop_help",status),d.business().entrance()));
        }
        var city=p.level().getServer().getLevel(NapoliMap.LEVEL);
        if(city!=null)for(var h:NapoliMergellina.PROPERTIES)if(p.getUUID().equals(NapoliProperties.owner(city,h))||CityHousingFinance.tenant(city,h.id(),p.getUUID()))
            all.add(new Destination("casa/"+h.id(),t("house.detail",h.address()),t("atlas.home"),h.entrance()));
        var c=WorkCareer.selected(p);all.add(new Destination("lavoro",c==null?t("atlas.office"):t("work.career_name",c.nameLabel()),t("atlas.work"),c==null?NapoliEmployment.CLERK_POST:c.clock));
        return List.copyOf(all);
    }
    static int atlas(ServerPlayer p,boolean only){
        var favorites=p.getAttachedOrElse(FAVORITES,List.of());
        var entries=destinations(p).stream().filter(d->!only||favorites.contains(d.id()))
            .sorted(Comparator.comparing((Destination d)->!favorites.contains(d.id())).thenComparingDouble(d->d.position().distSqr(p.blockPosition())))
            .map(d->new CompanionBoard.Entry(d.name(),t("atlas.distance",d.detail(),(int)Math.sqrt(d.position().distSqr(p.blockPosition()))),"compagno luogo "+d.id(),favorites.contains(d.id())?"★":"☆","compagno preferito "+d.id())).toList();
        return show(p,only?"preferiti":"atlante",t(only?"atlas.favorites":"atlas.title"),entries);
    }
    static int favorite(ServerPlayer p,String id){
        if(destinations(p).stream().noneMatch(d->d.id().equals(id)))return fail(p,t("atlas.unavailable"));var saved=new ArrayList<>(p.getAttachedOrElse(FAVORITES,List.of()));
        if(!saved.remove(id)){if(saved.size()>=32)return fail(p,t("atlas.full"));saved.add(id);}p.setAttached(FAVORITES,List.copyOf(saved));return atlas(p,false);
    }
    static int location(ServerPlayer p,String id){
        var d=destinations(p).stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(d==null)return fail(p,t("atlas.unavailable"));var near=CityNetwork.nearest(d.position());var entries=new ArrayList<CompanionBoard.Entry>();
        entries.add(new CompanionBoard.Entry(t("atlas.walk",d.name()),t("atlas.address",d.detail(),d.position().toShortString()),"compagno segui "+id));
        entries.add(new CompanionBoard.Entry(t("atlas.near_stop",near.name()),t("atlas.stop_help"),"compagno segui fermata/"+near.id()));
        if(id.startsWith("fermata/"))for(var line:CityNetwork.lines())if(line.mode()==CityNetwork.Mode.METRO&&line.stops().contains(near.id()))
            entries.add(new CompanionBoard.Entry(t("atlas.platform",line.id(),near.name()),t("atlas.platform_help"),"compagno segui banchina/"+line.id()+"/"+near.id()));
        if(inCity(p)){
            var from=CityNetwork.nearest(p.blockPosition());var route=CityNetwork.itinerary(p.level(),from.id(),near.id());Component text;
            if(from.id().equals(near.id()))text=t("atlas.same_stop");else if(route.isEmpty())text=t("atlas.no_route");else text=Component.literal(route.stream().map(l->l.line()+" → "+CityNetwork.stop(l.to()).name()).reduce((a,b)->a+"; "+b).orElse(""));
            entries.add(new CompanionBoard.Entry(t("atlas.transit"),t("atlas.from",from.name(),text),"compagno corse "+from.id()));
        }
        if(id.startsWith("bottega/"))entries.add(new CompanionBoard.Entry(t("menu.shop"),t("menu.shop_help"),"negozio apri "+id.substring(8)));
        return show(p,"luogo/"+id,d.name(),entries);
    }
    static void pin(ServerPlayer p,Goal goal){p.setAttached(GOAL,goal);p.setAttached(TRACK,"meta");p.removeAttached(NapoliAtlasGuide.TARGET);p.removeAttached(CityNetwork.WAYPOINT);}
    static int follow(ServerPlayer p,String id){if(!inCity(p))return fail(p,t("error.city"));var d=destinations(p).stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(d==null)return fail(p,t("atlas.unavailable"));pin(p,new Goal(d.name().getString(),d.position(),d.name()));NapoliNotice.show(p,t("nav.follow",d.name()));return 1;}
    static int trackers(ServerPlayer p){var selected=p.getAttachedOrElse(TRACK,"auto");var entries=new ArrayList<CompanionBoard.Entry>();for(var id:TRACKERS)entries.add(new CompanionBoard.Entry(t("tracker.option",selected.equals(id)?"✓":"○",t("tracker."+id)),t("tracker.saved"),"compagno obiettivo "+id));return show(p,"obiettivi",t("tracker.title"),entries);}
    static int select(ServerPlayer p,String mode){
        if(!TRACKERS.contains(mode))return fail(p,t("tracker.invalid"));
        if(mode.equals("meta")&&p.getAttached(GOAL)==null&&p.getAttached(NapoliAtlasGuide.TARGET)==null&&p.getAttached(CityNetwork.WAYPOINT)==null)return fail(p,t("group.choose_help"));
        if(mode.equals("ricetta")&&CompanionRecipes.selected(p)==null)return fail(p,t("recipe.choose"));
        if(mode.equals("diario")&&CompanionJournal.selected(p)==null)return CompanionJournal.menu(p);
        if(mode.equals("guida"))p.setAttached(CityOnboarding.ACTIVE,true);p.setAttached(TRACK,mode);NapoliNotice.show(p,t("tracker.updated"));return 1;
    }
    static Goal goal(ServerPlayer p){var goal=p.getAttached(GOAL);if(goal!=null)return goal;var place=NapoliAtlas.find(p.getAttachedOrElse(NapoliAtlasGuide.TARGET,""));if(place!=null)return new Goal(place.name(),place.arrival());var stop=CityNetwork.stop(p.getAttachedOrElse(CityNetwork.WAYPOINT,""));return stop==null?null:new Goal(stop.name(),stop.entrance());}
    static NapoliFactions.Objective navigation(ServerPlayer p){var goal=goal(p);return goal==null?new NapoliFactions.Objective("none",null):new NapoliFactions.Objective("qol:"+goal.name(),goal.position());}
    static NapoliFactions.Objective objective(ServerPlayer p,NapoliFactions.Objective automatic){
        if(!inCity(p))return automatic;var none=new NapoliFactions.Objective("none",null);
        return switch(p.getAttachedOrElse(TRACK,"auto")){
            case "nessuno"->none;
            case "lavoro"->{if(CompanionWork.finished(p))yield new NapoliFactions.Objective("qol.work:finished",null);var obj=NapoliEmployment.objective(p);yield obj==null?new NapoliFactions.Objective("napuli.work.visit",NapoliEmployment.CLERK_POST):obj;}
            case "missione"->Optional.ofNullable(NapoliMap.undergroundObjective(p)).orElseGet(()->Optional.ofNullable(NapoliLocalJobs.objective(p)).orElseGet(()->NapoliFactions.objective(p)));
            case "guida"->Optional.ofNullable(CityOnboarding.objective(p)).orElse(none);
            case "ricetta"->new NapoliFactions.Objective("qol:recipe",null);
            case "diario"->{var a=CompanionJournal.selected(p);yield new NapoliFactions.Objective("qol:journal",a==null?null:a.target());}
            case "meta"->navigation(p);
            default->{var ticket=p.getAttached(CityNetwork.TICKET);if(ticket==null||ticket.legs().isEmpty())yield automatic;var leg=ticket.legs().getFirst();var stop=CityNetwork.stop(leg.from());var line=CityNetwork.line(leg.line());yield new NapoliFactions.Objective("qol:journey",ticket.state().equals("riding")||stop==null||line==null?null:stop.boarding(line));}
        };
    }
    static Component label(ServerPlayer p,NapoliFactions.Objective obj){
        String mode=p.getAttachedOrElse(TRACK,"auto");
        if(mode.equals("lavoro"))return CompanionWork.summary(p);
        if(mode.equals("ricetta"))return CompanionRecipes.shoppingText(p);
        if(mode.equals("diario")){var a=CompanionJournal.selected(p);return a==null?t("journal.completed"):t("journal.tracking",a.title(),a.detail());}
        if(mode.equals("meta")){var goal=goal(p);return goal==null?t("nav.finished"):goal.label();}
        if(obj.key().equals("qol:journey"))return CompanionTravel.journeyText(p);
        String key=obj.key();return Component.translatable(key.startsWith("napuli.")?key:"napuli.hud.obj."+key,NapoliFactions.cooldownSeconds(p));
    }
    static String workProgress(ServerPlayer p){return CompanionWork.summary(p).getString();}
    static int help(ServerPlayer p){var entries=new ArrayList<CompanionBoard.Entry>();String[] topics={"intro","citizens","shops","transport","cooking","panels"};String[] commands={"inizio","","negozio","compagno trasporti","compagno ricette",""};for(int i=0;i<topics.length;i++)entries.add(new CompanionBoard.Entry(t("help."+topics[i]),t("help."+topics[i]+"_detail"),commands[i]));entries.add(new CompanionBoard.Entry(t("help.hints",t(p.getAttachedOrElse(CompanionHints.ENABLED,true)?"yes":"no")),t("help.hints_detail"),"compagno consigli"));return show(p,"aiuto",t("help.title"),entries);}
    static void register(){
        CityPlayerImprovements.register();
        PayloadTypeRegistry.clientboundPlay().register(CompanionBoard.TYPE,CompanionBoard.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompanionHud.TYPE,CompanionHud.CODEC);
        PlayerLedger.register();PlayerNavigator.register();CompanionJournal.register();CompanionMeetups.register();CompanionHints.register();CompanionTravel.register();
        CommandRegistrationCallback.EVENT.register((d,r,e)->{
            var root=Commands.literal("compagno").executes(c->home(c.getSource().getPlayerOrException()));
            for(String page:List.of("atlante","preferiti","obiettivi","ricette","trasporti","amici","aiuto","carriera","case","diario","spese","esci_gruppo","riprendi_gruppo","gruppo_meta","banco_carriera","consigli"))root.then(Commands.literal(page).executes(c->{var p=c.getSource().getPlayerOrException();return switch(page){
                case "atlante","preferiti"->atlas(p,page.equals("preferiti"));case "obiettivi"->trackers(p);case "ricette"->CompanionRecipes.menu(p);case "trasporti"->CompanionTravel.nearest(p);case "amici"->CompanionMeetups.menu(p);case "carriera"->CompanionWork.menu(p);case "case"->CompanionHousing.menu(p);case "diario"->CompanionJournal.menu(p);case "spese"->PlayerLedger.menu(p);case "esci_gruppo"->CompanionMeetups.leave(p);case "riprendi_gruppo"->CompanionMeetups.resume(p);case "gruppo_meta"->CompanionMeetups.redirect(p);case "banco_carriera"->CompanionWork.station(p);case "consigli"->{p.setAttached(CompanionHints.ENABLED,!p.getAttachedOrElse(CompanionHints.ENABLED,true));yield help(p);}default->help(p);
            };}));
            for(String action:List.of("luogo","segui","preferito","obiettivo","ricetta","fissa","corse","invita","accetta","rifiuta","annulla_invito","casa","casa_preventivo","casa_conferma","casa_vai","diario_segui"))root.then(Commands.literal(action).then(Commands.argument("id",StringArgumentType.greedyString()).executes(c->{var p=c.getSource().getPlayerOrException();String id=StringArgumentType.getString(c,"id");return switch(action){
                case "luogo"->location(p,id);case "segui"->follow(p,id);case "preferito"->favorite(p,id);case "obiettivo"->select(p,id);case "ricetta"->CompanionRecipes.detail(p,id);case "fissa"->CompanionRecipes.pin(p,id);case "corse"->CompanionTravel.board(p,id);case "invita"->CompanionMeetups.invite(p,id);case "accetta"->CompanionMeetups.accept(p,id);case "annulla_invito"->CompanionMeetups.cancel(p,id);case "casa"->CompanionHousing.detail(p,id);case "casa_preventivo"->CompanionHousing.quote(p,id);case "casa_conferma"->CompanionHousing.confirm(p,id);case "casa_vai"->CompanionHousing.navigate(p,id);case "diario_segui"->CompanionJournal.track(p,id);default->CompanionMeetups.decline(p,id);
            };})));
            d.register(root);d.register(Commands.literal("citta").then(Commands.literal("menu").executes(c->home(c.getSource().getPlayerOrException()))));
        });
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%20!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;for(var p:city.players()){var goal=p.getAttached(GOAL);if(goal!=null&&goal.position().distToCenterSqr(p.position())<3*3){p.removeAttached(GOAL);if(p.getAttachedOrElse(TRACK,"auto").equals("meta"))p.setAttached(TRACK,"nessuno");NapoliNotice.show(p,t("nav.arrived",goal.label()));}}});
    }
    private CityCompanion(){}
}

