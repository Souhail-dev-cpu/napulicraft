package dev.napuli;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

/** UUID-derived tastes survive reloads without another copy of citizen identity. */
final class CityCharacter {
    record Tastes(String food, String greeting, boolean quiet, int company) {}
    static Tastes tastes(UUID id) {
        int seed = id.hashCode();
        return new Tastes(new String[]{"pane","caffe","mele","pesce"}[Math.floorMod(seed,4)],
                new String[]{"Buongiorno, come va?", "Uè, piacere di rivederti!", "Ciao, hai un momento?", "Salve, tutto bene?"}[Math.floorMod(seed / 7,4)],
                Math.floorMod(seed / 13,3)==0, Math.floorMod(seed / 17,4));
    }
    static DistrictLife.District usualShop(NapoliCivicLife.Citizen c) {
        var local = DistrictLife.district(c); if (local != null) return local;
        return DistrictLife.districts().stream().min(Comparator.comparingDouble(d -> d.business().entrance().distSqr(NapoliCivicLife.home(c)))).orElseThrow();
    }
    static BlockPos leisure(ServerLevel city, NapoliCivicLife.Citizen c) {
        if(CityNightlife.outing(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city))||CityThieves.onProwl(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city)))return CityNightlife.destination(city,c);
        var home = NapoliCivicLife.home(c); var tastes = tastes(c.id());
        var friend=companion(city,c);if(friend!=null){var actor=city.getEntity(friend.id());if(actor!=null&&actor.blockPosition().distSqr(home)<96*96)return actor.blockPosition().east(2); }
        if (DistrictLife.resident(c)) {
            var d = usualShop(c);
            var baker = NapoliCivicLife.profile(city,DistrictLife.identity(d,0));
            if (!tastes.quiet() && baker != null && CityHabits.working(baker,NapoliBroadcast.time(city),NapoliBroadcast.day(city)))
                return d.business().approach(3);
            if(tastes.quiet()){var own=CityLivingHomes.find(c.property());return own==null?home:own.approach(4);}
            return meetingPoint(city,c,DistrictLife.square(d).arrival());
        }
        // Keep residents local; a preference never sends a Partenope resident across the map for coffee.
        return meetingPoint(city,c,CityNeighborhood.cafe(home).offset(tastes.quiet()?-3:0,0,tastes.quiet()?3:0));
    }
    static BlockPos meetingPoint(ServerLevel city,NapoliCivicLife.Citizen c,BlockPos center){
        // Stable personal spacing: a crowd occupies a square rather than one identical destination.
        int seed=Math.floorMod(c.id().hashCode(),49);var desired=center.offset((seed%7-3)*2,0,(seed/7-3)*2);
        return city.hasChunkAt(desired)&&NapoliResidentHomes.walkable(city,desired)?desired:center;
    }
    static NapoliCivicLife.Citizen companion(ServerLevel city,NapoliCivicLife.Citizen c){
        NapoliCivicLife.Citizen best=null;int score=59;long time=NapoliBroadcast.time(city),day=NapoliBroadcast.day(city);
        for(var entry:CitySimulation.life(city,c.id()).relations().entrySet())if(entry.getValue()>=score&&entry.getKey().length()==36){
            UUID id;try{id=UUID.fromString(entry.getKey());}catch(IllegalArgumentException invalid){continue;}
            if(id.equals(c.id()))continue;var other=NapoliCivicLife.profile(city,id);
            if(other!=null&&!CityHabits.working(other,time,day)&&(entry.getValue()>score||best==null||other.id().toString().compareTo(best.id().toString())>0)){best=other;score=entry.getValue();}
        }
        return best;
    }
    static String describe(ServerLevel city, NapoliCivicLife.Citizen c) {
        var taste = tastes(c.id());
        String food = DistrictShops.products().stream().filter(p -> p.id().equals(taste.food())).findFirst().orElseThrow().name();
        return "Il mio solito è " + food.toLowerCase(Locale.ITALIAN) + ". "
                + (taste.quiet() ? "Preferisco gli angoli tranquilli alle piazze affollate." : "Mi piace ritrovare le stesse facce al bar.")
                + (DistrictLife.resident(c) ? " Passo spesso dalla bottega di " + usualShop(c).name() + "." : "Il mio ritrovo è il bar del rione.");
    }
    static void snack(ServerLevel city, NapoliCivicLife.Citizen c) {
        if (!DistrictLife.resident(c) || c.balance()<2) return;
        var d=usualShop(c); var at=city.getEntity(c.id()); if (!(at instanceof NapoliResident npc) || d.business().entrance().distToCenterSqr(at.position())>144 || DistrictShops.staff(city,d)==null) return;
        var life=CitySimulation.life(city,c.id()); if(life.needs().fun()>80) return;
        var product=DistrictShops.products().stream().filter(p->p.id().equals(tastes(c.id()).food())).findFirst().orElseThrow();
        if(c.balance()<product.price()||!DistrictShops.reserve(city,d,product.id(),1))return;
        CityOccupations.money(city,c.id(),-product.price()); DistrictShops.credit(city,d,product.price());
        CitySimulation.put(city,c.id(),life.withNeeds(life.needs().change(8,0,0,15,5,0),life.food(),life.last(),life.water(),life.energy()));
        npc.setDropChance(EquipmentSlot.MAINHAND,0); npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(product.item())); npc.animate(CityGestures.Gesture.EAT,60);
    }
    static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%600!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;var census=NapoliCivicLife.census(city);if(census==null)return;for(var c:census.people()){var e=city.getEntity(c.id());if(e instanceof NapoliResident npc&&npc.activity()==NapoliHouseholds.Activity.SOCIAL)snack(city,c);}});}
    private CityCharacter() {}
}
