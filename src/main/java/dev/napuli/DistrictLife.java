package dev.napuli;

import com.mojang.serialization.Codec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

/** Real census households and nearby workplaces for the expanded city, with the existing population cap. */
final class DistrictLife {
    record District(String id,String name,NapoliAtlas.Place landmark,List<NapoliMergellina.House> homes,NapoliMergellina.House business){}
    private static final class Layout {static final List<District> ALL=layout();}
    static List<District> districts(){return Layout.ALL;}
    static List<NapoliMergellina.House> homes(){return districts().stream().flatMap(d->d.homes().stream()).toList();}
    static final AttachmentType<Set<String>> SETTLED=AttachmentRegistry.<Set<String>>builder().persistent(Codec.STRING.listOf().xmap(Set::copyOf,List::copyOf)).buildAndRegister(Napuli.id("district_households_settled"));
    static final AttachmentType<Set<String>> FITTED=AttachmentRegistry.<Set<String>>builder().persistent(Codec.STRING.listOf().xmap(Set::copyOf,List::copyOf)).buildAndRegister(Napuli.id("district_fitted_chunks"));
    private static List<District> layout(){
        var anchors=new LinkedHashMap<String,NapoliAtlas.Place>();for(var p:NapoliAtlas.PLACES)if(!p.original())anchors.putIfAbsent(p.district(),p);
        var used=new HashSet<String>();var result=new ArrayList<District>();
        for(var anchor:anchors.values()){
            var candidates=new ArrayList<BlockPos>();for(int x=-2040;x<2030;x+=40)for(int z=-800;z<520;z+=40){int hx=Math.floorDiv(x,40)*40+9,hz=Math.floorDiv(z,40)*40+9;if(NapoliExpansion.houseAllowed(hx,hz)&&!used.contains(hx+"/"+hz))candidates.add(new BlockPos(hx,64,hz));}
            candidates.sort(Comparator.comparingDouble(p->p.distSqr(anchor.arrival())));if(candidates.size()<3)throw new IllegalStateException("No housing sites for "+anchor.id());
            var houses=new ArrayList<NapoliMergellina.House>();NapoliMergellina.House business=null;
            for(int i=0;i<3;i++){var p=candidates.get(i);used.add(p.getX()+"/"+p.getZ());var h=new NapoliMergellina.House((i==2?"atlas_shop_":"atlas_home_")+anchor.id()+"_"+i,(i==2?"Bottega di ":"Casa "+(i+1)+" · ")+anchor.district(),p.getX(),p.getZ(),0,Blocks.SMOOTH_SANDSTONE,22,24,3+Math.floorMod(Math.floorDiv(p.getX()-9,40)*31+Math.floorDiv(p.getZ()-9,40),3),1000+i);if(i==2)business=h;else houses.add(h);}
            result.add(new District(anchor.id(),anchor.district(),anchor,List.copyOf(houses),business));
        }return List.copyOf(result);
    }
    static boolean resident(NapoliCivicLife.Citizen c){return c!=null&&c.property().startsWith("atlas_home_");}
    static District district(NapoliCivicLife.Citizen c){return resident(c)?districts().stream().filter(d->d.homes().stream().anyMatch(h->h.id().equals(c.property()))).findFirst().orElse(null):null;}
    static District byId(String id){return districts().stream().filter(d->d.id().equals(id)).findFirst().orElse(null);}
    static int floor(NapoliMergellina.House h){return h.id().startsWith("atlas_")?NapoliExpansion.height(h.x()+11,h.z()+24)+1:64;}
    static UUID identity(District d,int slot){return UUID.nameUUIDFromBytes(("napuli:district:"+d.id()+":"+slot).getBytes(StandardCharsets.UTF_8));}
    static BlockPos work(NapoliCivicLife.Citizen c){var d=district(c);if(d==null)return null;var h=d.business();boolean service=c.profession()==NapoliCivicLife.Job.TEACHER||c.profession()==NapoliCivicLife.Job.DOCTOR;return new BlockPos(h.x()+(service?16:9),h.ground(),h.z()+(service?10:17));}
    static BlockPos shop(NapoliCivicLife.Citizen c){var d=district(c);return d==null?null:d.business().entrance().south(2);}
    static District provider(ServerLevel city,NapoliCivicLife.Citizen c,boolean school){var census=NapoliCivicLife.census(city);if(census==null)return null;return census.people().stream().filter(p->resident(p)&&p.profession()==(school?NapoliCivicLife.Job.TEACHER:NapoliCivicLife.Job.DOCTOR)).min(Comparator.comparingDouble(p->work(p).distSqr(NapoliCivicLife.home(c)))).map(DistrictLife::district).orElse(null);}
    static BlockPos service(ServerLevel city,NapoliCivicLife.Citizen c,boolean school){var d=provider(city,c,school);return d==null?CityServices.place(school?"school":"doctor"):new BlockPos(d.business().x()+14,d.business().ground(),d.business().z()+14);}
    static NapoliAtlas.Place square(District d){return d.landmark().id().equals("nisida")?NapoliAtlas.find("scienza"):d.landmark();}
    static BlockPos destination(ServerLevel city,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity action){return switch(action){case WORK->CityOccupations.kind(c).equals("courier")||CityOccupations.kind(c).equals("cleaner")?CityOccupations.target(city,c):work(c);case SHOP->shop(c);case SCHOOL,CARE->service(city,c,action==NapoliHouseholds.Activity.SCHOOL);case SOCIAL,PLAY,REST->square(district(c)).arrival();default->NapoliCivicLife.home(c);};}
    static int seed(ServerLevel city){
        var census=NapoliCivicLife.census(city);if(census==null)return 0;var people=new ArrayList<>(census.people());var settled=new HashSet<>(city.getAttachedOrElse(SETTLED,Set.of()));var owners=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));int added=0,target=0;long day=NapoliBroadcast.day(city);
        for(int slot=0;slot<4;slot++)for(var d:districts()){
            if(target++>=CityLifeConfig.settings.districtPopulation)continue;var uuid=identity(d,slot);if(settled.contains(uuid.toString())||people.stream().anyMatch(c->c.id().equals(uuid)))continue;
            if(people.size()>=CityLifeConfig.settings.maximumPopulation)continue;var h=d.homes().getFirst();if(CityProperty.playerBuiltInChunk(city,net.minecraft.world.level.ChunkPos.containing(h.inside())))continue;
            int age=slot==0?34:slot==1?32:slot==2?12:8,rate=CityLifeConfig.settings.daysPerYear;boolean woman=(districts().indexOf(d)+slot)%2==0;
            int job=slot==0?NapoliCivicLife.Job.BAKER.ordinal():slot==1?switch(districts().indexOf(d)%4){case 0->NapoliCivicLife.Job.TEACHER.ordinal();case 1->NapoliCivicLife.Job.DOCTOR.ordinal();case 2->NapoliCivicLife.Job.CLEANER.ordinal();default->NapoliCivicLife.Job.COURIER.ordinal();}:-1;
            String[] first=woman?new String[]{"Anna","Carmela","Rosa","Lucia","Teresa","Giulia"}:new String[]{"Antonio","Ciro","Gennaro","Salvatore","Marco","Luigi"};String[] last={"Esposito","Russo","Romano","Ferrara","De Luca","Marino","Greco","Rizzo"};int hash=Math.floorMod(uuid.hashCode(),10000);
            var c=new NapoliCivicLife.Citizen(uuid,first[hash%first.length]+" "+last[(hash/7)%last.length],woman,day-(long)age*rate*(slot<2?1:2),slot<2?64:0,job,"",h.id(),slot<2?"":identity(d,0).toString(),false,slot<2?0:rate*2);people.add(c);settled.add(uuid.toString());if(slot==0)owners.putIfAbsent(h.id(),uuid);
            var life=CitySimulation.fresh(uuid);CitySimulation.put(city,uuid,life.withNeeds(new CitySimulation.Needs(90,95,95,80,85,90),8,city.getGameTime(),0,0));CityJourneys.put(city,uuid,CityJourneys.plan(h.inside(),h.inside(),city.getGameTime(),95));added++;
        }
        if(added>0){for(var d:districts()){UUID a=identity(d,0),b=identity(d,1);if(people.stream().anyMatch(c->c.id().equals(a))&&people.stream().anyMatch(c->c.id().equals(b)))for(int i=0;i<people.size();i++){var c=people.get(i);if(c.id().equals(a)||c.id().equals(b))people.set(i,c.withHousehold(c.id().equals(a)?b.toString():a.toString(),c.property(),c.balance()));}}
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people)));city.setAttached(SETTLED,Set.copyOf(settled));city.setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));}
        return added;
    }
    static boolean tick(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity action){
        if(!resident(c))return false;if(CityNetwork.commute(npc,city,destination(city,c,action)))return true;
        if(action==NapoliHouseholds.Activity.SHOP){var goal=shop(c);move(npc,city,goal);if(goal.distToCenterSqr(npc.position())<16)DistrictShops.shopResident(city,c);return true;}
        if(action==NapoliHouseholds.Activity.SCHOOL||action==NapoliHouseholds.Activity.CARE){if(provider(city,c,action==NapoliHouseholds.Activity.SCHOOL)==null)return false;var goal=service(city,c,action==NapoliHouseholds.Activity.SCHOOL);move(npc,city,goal);if(goal.distToCenterSqr(npc.position())<9){npc.getNavigation().stop();npc.setActivity(action==NapoliHouseholds.Activity.SCHOOL?NapoliHouseholds.Activity.STUDY:NapoliHouseholds.Activity.WAIT);npc.animate(action==NapoliHouseholds.Activity.SCHOOL?CityGestures.Gesture.WRITE:CityGestures.Gesture.SAD,50);if(action!=NapoliHouseholds.Activity.SCHOOL||c.age(NapoliBroadcast.day(city))<18)visit(city,c,40);}return true;}
        if(action==NapoliHouseholds.Activity.WORK){if(c.profession()==NapoliCivicLife.Job.BAKER)return CityOccupations.tick(npc,city,c);if(c.profession()==NapoliCivicLife.Job.CLEANER||c.profession()==NapoliCivicLife.Job.COURIER)return CityOccupations.tick(npc,city,c);var goal=work(c);move(npc,city,goal);if(goal.distToCenterSqr(npc.position())<9){npc.getNavigation().stop();npc.setActivity(action);npc.animate(c.profession()==NapoliCivicLife.Job.TEACHER?CityGestures.Gesture.TEACH:c.profession()==NapoliCivicLife.Job.DOCTOR?CityGestures.Gesture.EXAMINE:CityGestures.Gesture.WRITE,50);}return true;}
        if(action==NapoliHouseholds.Activity.SOCIAL||action==NapoliHouseholds.Activity.PLAY){var meeting=CityRelationships.socialDestination(city,c);var visiting=CitySimulation.visitingHouse(city,c);move(npc,city,visiting!=null?visiting.inside():meeting!=null?meeting:square(district(c)).arrival());return true;}
        return false;
    }
    private static void move(NapoliResident npc,ServerLevel city,BlockPos goal){npc.setActivity(NapoliHouseholds.Activity.COMMUTE);if(npc.isSleeping())npc.stopSleeping();CityMovement.stand(npc);CityDomestic.move(npc,localGoal(city,npc,goal));}
    static BlockPos localGoal(ServerLevel city,NapoliResident npc,BlockPos goal){
        for(var h:homes())if(h.contains(npc.blockPosition())&&!h.contains(goal))return h.entrance().south();
        var direct=npc.getNavigation().createPath(goal,0);if(goal.distToCenterSqr(npc.position())<40*40&&direct!=null&&direct.canReach())return goal;
        // Follow the generated street grid and approach the destination's doorway from the frontage.
        var house=homes().stream().filter(h->h.contains(goal)).findFirst().orElse(null);if(house!=null&&house.entrance().distToCenterSqr(npc.position())<32*32)return house.entrance().south();
        var street=CityRoutes.between(npc.blockPosition(),goal);
        if(!street.isEmpty()){var first=street.getFirst();var next=street.size()>1?street.get(1):first;double dx=next.getX()-first.getX(),dz=next.getZ()-first.getZ(),length=Math.max(1,Math.hypot(dx,dz));double along=((npc.getX()-first.getX())*dx+(npc.getZ()-first.getZ())*dz)/length;double cross=Math.abs((npc.getX()-first.getX())*dz-(npc.getZ()-first.getZ())*dx)/length;var at=along>0&&cross<5||first.distToCenterSqr(npc.position())<6*6?next:first;if(city.hasChunkAt(at)){var path=npc.getNavigation().createPath(at,0);if(path!=null&&path.canReach())return at;}}
        double angle=Math.atan2(goal.getZ()-npc.getZ(),goal.getX()-npc.getX());BlockPos best=npc.blockPosition();double score=best.distSqr(goal);
        for(int turn:new int[]{0,1,-1,2,-2}){double a=angle+turn*Math.PI/4;int x=npc.getBlockX()+(int)Math.round(Math.cos(a)*12),z=npc.getBlockZ()+(int)Math.round(Math.sin(a)*12);var p=new BlockPos(x,NapoliExpansion.height(x,z)+1,z);if(!city.hasChunkAt(p)||p.distSqr(goal)>=score||!NapoliResidentHomes.walkable(city,p))continue;var path=npc.getNavigation().createPath(p,0);if(path!=null&&path.canReach()){best=p;score=p.distSqr(goal);}}return best;
    }
    private static void visit(ServerLevel city,NapoliCivicLife.Citizen c,int increment){
        boolean school=c.age(NapoliBroadcast.day(city))<18;var kind=school?"school":"doctor";var target=service(city,c,school);if(!CityJourneys.at(city,c,target,12))return;
        var staff=NapoliCivicLife.census(city).people().stream().filter(p->resident(p)&&p.profession()==(school?NapoliCivicLife.Job.TEACHER:NapoliCivicLife.Job.DOCTOR)&&work(p).distSqr(target)<24*24&&CityHabits.working(p,NapoliBroadcast.time(city),NapoliBroadcast.day(city))&&CityJourneys.at(city,p,work(p),12)&&CitySimulation.life(city,p.id()).health()>=45).findFirst().orElse(null);if(staff==null)return;
        var v=CityServices.book(city,c,kind);if(v.state().equals("completed")||city.getGameTime()-v.last()<increment)return;int progress=v.progress()+increment;boolean complete=progress>=(school?1200:160);if(complete&&!school&&CityOccupations.supply(city,"medicine")==0)return;
        CityServices.put(city,c.id(),new CityServices.Visit(kind,v.day(),v.booked(),v.due(),complete?"completed":"waiting",progress,staff.id().toString(),city.getGameTime()));
        if(complete){var l=CitySimulation.life(city,c.id());if(school)CitySimulation.put(city,c.id(),l.career(l.health(),l.reputation(),l.experience()+1,l.rank(),l.absences(),CitySimulation.memory(l,"Lezione nel quartiere: "+provider(city,c,true).name())));else{CityOccupations.supply(city,"medicine",CityOccupations.supply(city,"medicine")-1);CitySimulation.put(city,c.id(),l.career(Math.min(100,l.health()+25),l.reputation(),l.experience(),l.rank(),l.absences(),CitySimulation.memory(l,"Visita da "+staff.name())));if(c.balance()>=2){CityOccupations.money(city,c.id(),-2);CityOccupations.money(city,staff.id(),2);}}}
    }
    static void fit(ServerLevel city,net.minecraft.world.level.chunk.LevelChunk chunk){
        String key=chunk.getPos().x()+"/"+chunk.getPos().z();if(city.getAttachedOrElse(FITTED,Set.of()).contains(key)||NapoliExpansion.revision(chunk)<1||CityProperty.playerBuiltInChunk(city,chunk.getPos()))return;
        for(var h:homes())if(h.x()>>4<=chunk.getPos().x()&&(h.x()+21)>>4>=chunk.getPos().x()&&h.z()>>4<=chunk.getPos().z()&&(h.z()+23)>>4>=chunk.getPos().z()){
            for(var point:CityLivingHomes.points(h)){var p=point.block();if(!net.minecraft.world.level.ChunkPos.containing(p).equals(chunk.getPos()))continue;Block b=switch(point.furniture()){case FRIDGE->Blocks.BARREL;case STOVE->Blocks.SMOKER;case SINK->Blocks.WATER_CAULDRON;case SHOWER->Blocks.WATER_CAULDRON;case TOILET->Blocks.CAULDRON;case SOFA->Blocks.OAK_STAIRS;case TV->net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("black_concrete"));case DESK,TABLE->Blocks.CRAFTING_TABLE;case WASHER->Blocks.IRON_BLOCK;case WARDROBE->Blocks.BARREL;case BED->null;};if(b!=null)city.setBlock(p,b.defaultBlockState(),2);else{var bed=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_bed")).defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);city.setBlock(p,bed.setValue(BedBlock.PART,BedPart.FOOT),2);if(net.minecraft.world.level.ChunkPos.containing(p.north()).equals(chunk.getPos()))city.setBlock(p.north(),bed.setValue(BedBlock.PART,BedPart.HEAD),2);if(net.minecraft.world.level.ChunkPos.containing(p.east()).equals(chunk.getPos())){city.setBlock(p.east(),bed.setValue(BedBlock.PART,BedPart.FOOT),2);city.setBlock(p.east().north(),bed.setValue(BedBlock.PART,BedPart.HEAD),2);}}}
        }
        for(var h:homes())for(int stage:new int[]{1,3}){var p=CityDomestic.point(h,stage).block();if(net.minecraft.world.level.ChunkPos.containing(p).equals(chunk.getPos())){city.setBlock(stage==3?p.below():p,(stage==3?Blocks.OAK_FENCE:Blocks.CRAFTING_TABLE).defaultBlockState(),2);if(stage==3)city.setBlock(p,Blocks.OAK_PRESSURE_PLATE.defaultBlockState(),2);}}
        for(var h:homes()){var lamp=CityAtmosphere.lamp(h);if(net.minecraft.world.level.ChunkPos.containing(lamp).equals(chunk.getPos())&&city.isEmptyBlock(lamp))city.setBlock(lamp,Blocks.REDSTONE_LAMP.defaultBlockState(),2);}
        DistrictShops.fit(city,chunk);var all=new HashSet<>(city.getAttachedOrElse(FITTED,Set.of()));all.add(key);city.setAttached(FITTED,Set.copyOf(all));
    }
    private static void retireDecorations(ServerLevel city){for(var place:NapoliAtlas.PLACES)if(!place.original())for(int slot=0;slot<2;slot++){var id=UUID.nameUUIDFromBytes(("napuli:atlas-walker:"+place.id()+"/"+slot).getBytes(StandardCharsets.UTF_8));var entity=city.getEntity(id);if(entity instanceof NapoliResident&&NapoliCivicLife.profile(city,id)==null)entity.discard();}}
    static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%100!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;seed(city);retireDecorations(city);var census=NapoliCivicLife.census(city);if(census==null)return;for(var c:census.people())if(resident(c)&&city.getEntity(c.id())==null){var a=CitySimulation.activity(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city));if(a==NapoliHouseholds.Activity.CARE||a==NapoliHouseholds.Activity.SCHOOL&&c.age(NapoliBroadcast.day(city))<18)visit(city,c,100);}for(var p:city.players())for(int x=(p.getBlockX()>>4)-2;x<=(p.getBlockX()>>4)+2;x++)for(int z=(p.getBlockZ()>>4)-2;z<=(p.getBlockZ()>>4)+2;z++){var chunk=city.getChunkSource().getChunkNow(x,z);if(chunk!=null)fit(city,chunk);}});}
    private DistrictLife(){}
}
