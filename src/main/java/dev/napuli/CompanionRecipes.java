package dev.napuli;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import static dev.napuli.CityText.t;
final class CompanionRecipes {
    static NapoliKitchen.Recipe find(String id){return NapoliKitchen.RECIPES.stream().filter(r->r.id().equals(id)).findFirst().orElse(null);}
    static NapoliKitchen.Recipe selected(ServerPlayer p){return find(p.getAttachedOrElse(CityCompanion.RECIPE,""));}
    static Map<Item,Integer> ingredients(NapoliKitchen.Recipe r){var counts=new LinkedHashMap<Item,Integer>();for(var item:r.ingredients())counts.merge(item,1,Integer::sum);return counts;}
    static Component stationText(NapoliKitchen.Kind kind){return t("recipe.station."+kind.name().toLowerCase(Locale.ROOT));}
    static String station(NapoliKitchen.Kind kind){return stationText(kind).getString();}
    static Component summaryText(ServerPlayer p,NapoliKitchen.Recipe r){
        var result=Component.empty();boolean first=true;for(var e:ingredients(r).entrySet()){if(!first)result.append(", ");first=false;result.append(t("recipe.count",e.getKey().getDefaultInstance().getHoverName(),Math.min(e.getValue(),WorkResources.count(p,e.getKey())),e.getValue()));}return result;
    }
    static String summary(ServerPlayer p,NapoliKitchen.Recipe r){return summaryText(p,r).getString();}
    static int menu(ServerPlayer p){var entries=NapoliKitchen.RECIPES.stream().map(r->new CompanionBoard.Entry(t("recipe.output",r.output().getDefaultInstance().getHoverName(),r.count()),t("recipe.summary",stationText(r.station()),summaryText(p,r)),"compagno ricetta "+r.id(),p.getAttachedOrElse(CityCompanion.RECIPE,"").equals(r.id())?"✓":"+","compagno fissa "+r.id())).toList();return CityCompanion.show(p,"ricette",t("recipe.title"),entries);}
    static int detail(ServerPlayer p,String id){var r=find(id);if(r==null)return CityCompanion.fail(p,t("recipe.invalid"));var entries=new ArrayList<CompanionBoard.Entry>();
        entries.add(new CompanionBoard.Entry(t("recipe.pin"),t("recipe.pin_help"),"compagno fissa "+id));
        entries.add(new CompanionBoard.Entry(stationText(r.station()),t("recipe.result",r.output().getDefaultInstance().getHoverName(),r.count(),r.fuel()?t(r.station()==NapoliKitchen.Kind.FRY?"recipe.oil":"recipe.logs"):t("recipe.no_fuel")),""));
        for(var e:ingredients(r).entrySet()){int have=WorkResources.count(p,e.getKey()),missing=Math.max(0,e.getValue()-have);entries.add(new CompanionBoard.Entry(e.getKey().getDefaultInstance().getHoverName(),t("recipe.inventory",have,e.getValue(),missing==0?t("recipe.ready"):t("recipe.missing",missing)),""));}
        return CityCompanion.show(p,"ricetta/"+id,r.output().getDefaultInstance().getHoverName(),entries);
    }
    static int pin(ServerPlayer p,String id){var r=find(id);if(r==null)return CityCompanion.fail(p,t("recipe.invalid"));p.setAttached(CityCompanion.RECIPE,id);p.setAttached(CityCompanion.TRACK,"ricetta");NapoliNotice.show(p,t("recipe.pinned",r.output().getDefaultInstance().getHoverName()));return 1;}
    static Component shoppingText(ServerPlayer p){
        var r=selected(p);if(r==null)return t("recipe.choose");var missing=ingredients(r).entrySet().stream().filter(e->WorkResources.count(p,e.getKey())<e.getValue()).toList();var list=Component.empty();
        if(missing.isEmpty())list.append(t("recipe.ready"));else{boolean first=true;for(var e:missing.stream().limit(3).toList()){if(!first)list.append(", ");first=false;list.append(t("recipe.count",e.getKey().getDefaultInstance().getHoverName(),WorkResources.count(p,e.getKey()),e.getValue()));}}
        if(missing.size()>3)list.append(t("recipe.more",missing.size()-3));return t("recipe.shopping",r.output().getDefaultInstance().getHoverName(),list,stationText(r.station()));
    }
    static String shopping(ServerPlayer p){return shoppingText(p).getString();}
    private CompanionRecipes(){}
}

