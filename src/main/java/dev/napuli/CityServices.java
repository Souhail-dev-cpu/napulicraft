package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** A service has an appointment, a physical waiting place and an available member of staff. */
final class CityServices {
    record Visit(String kind,long day,long booked,long due,String state,int progress,String provider,long last) {
        static final Codec<Visit> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Visit::kind),Codec.LONG.fieldOf("day").forGetter(Visit::day),Codec.LONG.fieldOf("booked").forGetter(Visit::booked),Codec.LONG.fieldOf("due").forGetter(Visit::due),Codec.STRING.fieldOf("state").forGetter(Visit::state),Codec.INT.fieldOf("progress").forGetter(Visit::progress),Codec.STRING.fieldOf("provider").forGetter(Visit::provider),Codec.LONG.fieldOf("last").forGetter(Visit::last)).apply(i,Visit::new));
    }
    static final AttachmentType<Map<String,Visit>> VISITS=AttachmentRegistry.<Map<String,Visit>>builder().persistent(Codec.unboundedMap(Codec.STRING,Visit.CODEC)).buildAndRegister(Napuli.id("civic_service_visits"));
    static Visit visit(ServerLevel city,UUID id){return city.getAttachedOrElse(VISITS,Map.of()).get(id.toString());}
    static void put(ServerLevel city,UUID id,Visit visit){var all=new HashMap<>(city.getAttachedOrElse(VISITS,Map.of()));all.put(id.toString(),visit);city.setAttached(VISITS,Map.copyOf(all));}
    static BlockPos place(String kind){return (kind.equals("doctor")?CityProfessions.Category.HEALTH:CityProfessions.Category.PUBLIC).site().offset(8,0,7);}
    static String schoolClass(int age){return age<6?"scuola dell'infanzia":age<11?"scuola primaria":age<14?"scuola media":"scuola superiore";}
    static boolean open(String kind,long clock,long day){return kind.equals("doctor")||Math.floorMod(day,7)<5&&clock>=2200&&clock<10500;}
    static boolean provider(NapoliCivicLife.Citizen c,String kind){if(c.profession()==null)return false;return switch(kind){case "doctor"->CityOccupations.kind(c).equals("doctor");case "school"->c.profession()==NapoliCivicLife.Job.TEACHER;default->c.profession()==NapoliCivicLife.Job.SOCIAL_WORKER||c.profession()==NapoliCivicLife.Job.POSTAL_CLERK||c.profession()==NapoliCivicLife.Job.LIBRARIAN;};}
    static NapoliCivicLife.Citizen staff(ServerLevel city,String kind){var census=NapoliCivicLife.census(city);if(census==null||!open(kind,NapoliBroadcast.time(city),NapoliBroadcast.day(city)))return null;
        return census.people().stream().filter(c->provider(c,kind)&&CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city))&&CitySimulation.life(city,c.id()).health()>=45&&!CityPublicOrder.jailed(city,c.id())&&CityJourneys.at(city,c,place(kind),14)).findFirst().orElse(null);
    }
    static Visit book(ServerLevel city,NapoliCivicLife.Citizen c,String kind){
        long day=NapoliBroadcast.day(city),now=city.getGameTime();var old=visit(city,c.id());if(old!=null&&old.day()==day&&old.kind().equals(kind))return old;
        long due=now;if(!kind.equals("doctor")&&NapoliBroadcast.time(city)<2500)due+=2500-NapoliBroadcast.time(city);
        var result=new Visit(kind,day,now,due,"booked",0,"",now);put(city,c.id(),result);return result;
    }
    static boolean needsVisit(ServerLevel city,NapoliCivicLife.Citizen c){var v=visit(city,c.id());return v!=null&&v.kind().equals("doctor")&&!v.state().equals("completed")&&city.getGameTime()>=v.due()-1200;}
    static boolean work(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c){if(c.profession()!=NapoliCivicLife.Job.TEACHER&&c.profession()!=NapoliCivicLife.Job.SOCIAL_WORKER&&c.profession()!=NapoliCivicLife.Job.POSTAL_CLERK)return false;
        var goal=place("school");if(goal.distToCenterSqr(npc.position())>9){npc.setActivity(NapoliHouseholds.Activity.COMMUTE);CityDomestic.move(npc,CityRoutines.localGoal(city,npc,goal));}else{npc.setActivity(NapoliHouseholds.Activity.WORK);npc.getNavigation().stop();npc.animate(c.profession()==NapoliCivicLife.Job.TEACHER?CityGestures.Gesture.TEACH:CityGestures.Gesture.WRITE,50);}return true;}
    static void fixtures(ServerLevel city){
        var hospital=CityProfessions.Category.HEALTH.site();var bed=hospital.offset(10,0,4);if(city.isEmptyBlock(bed)&&city.isEmptyBlock(bed.north())&&!CityProperty.playerPlaced(city,bed)&&!CityProperty.playerPlaced(city,bed.north())){
            var state=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_bed")).defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,net.minecraft.core.Direction.NORTH);
            city.setBlock(bed,state.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT),2);city.setBlock(bed.north(),state.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),2);
        }
        for(int i=0;i<4;i++){var desk=CityProfessions.Category.PUBLIC.site().offset(3+i*3,0,4);if(city.isEmptyBlock(desk)&&!CityProperty.playerPlaced(city,desk))city.setBlock(desk,Blocks.OAK_SLAB.defaultBlockState(),2);}
    }
    static BlockPos waiting(NapoliCivicLife.Citizen c,String kind){return place(kind).offset(Math.floorMod(c.id().hashCode(),3)*2-2,0,2);}
    static boolean tick(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity wanted){
        String kind=wanted==NapoliHouseholds.Activity.CARE?"doctor":wanted==NapoliHouseholds.Activity.SCHOOL&&c.age(NapoliBroadcast.day(city))<18?"school":"";if(kind.isEmpty())return false;
        var v=book(city,c,kind);if(v.state().equals("completed")&&kind.equals("doctor"))return false;var goal=waiting(c,kind);CityMovement.stand(npc);if(npc.isSleeping())npc.stopSleeping();
        npc.setActivity(wanted);if(goal.distToCenterSqr(npc.position())>3){CityDomestic.move(npc,CityRoutines.localGoal(city,npc,goal));return true;}
        npc.getNavigation().stop();if(v.state().equals("booked")){v=new Visit(kind,v.day(),v.booked(),v.due(),"waiting",0,"",city.getGameTime());put(city,c.id(),v);}
        if(staff(city,kind)==null){npc.setActivity(NapoliHouseholds.Activity.WAIT);return true;}
        if(kind.equals("school")){npc.setActivity(NapoliHouseholds.Activity.STUDY);npc.animate(CityGestures.Gesture.WRITE,50);npc.setDropChance(EquipmentSlot.MAINHAND,0);npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WRITABLE_BOOK));advanceSchool(city,c,v);}
        else{npc.setActivity(NapoliHouseholds.Activity.WAIT);npc.animate(CityGestures.Gesture.SAD,40);}
        return true;
    }
    static void advanceSchool(ServerLevel city,NapoliCivicLife.Citizen c,Visit v){
        if(v.state().equals("completed")||city.getGameTime()-v.last()<40||staff(city,"school")==null||!CityJourneys.at(city,c,place("school"),12))return;
        int progress=v.progress()+40;boolean complete=progress>=1200;put(city,c.id(),new Visit(v.kind(),v.day(),v.booked(),v.due(),complete?"completed":"waiting",progress,"",city.getGameTime()));
        if(complete){var l=CitySimulation.life(city,c.id());CitySimulation.put(city,c.id(),l.career(l.health(),l.reputation(),l.experience()+1,l.rank(),l.absences(),CitySimulation.memory(l,"Lezione frequentata: "+schoolClass(c.age(NapoliBroadcast.day(city))))));}
    }
    static NapoliCivicLife.Citizen patient(ServerLevel city,UUID doctor){return city.getAttachedOrElse(VISITS,Map.of()).entrySet().stream().filter(e->e.getValue().kind().equals("doctor")&&e.getValue().state().equals("waiting")&&(e.getValue().provider().isEmpty()||e.getValue().provider().equals(doctor.toString())))
            .sorted(Comparator.comparingLong(e->e.getValue().booked())).map(e->NapoliCivicLife.profile(city,UUID.fromString(e.getKey()))).filter(Objects::nonNull).filter(c->CityJourneys.at(city,c,place("doctor"),12)).findFirst().orElse(null);}
    static boolean examine(ServerLevel city,NapoliCivicLife.Citizen doctor,NapoliCivicLife.Citizen patient){
        var v=visit(city,patient.id());if(v==null||!v.state().equals("waiting")||v.kind().equals("school")||!v.provider().isEmpty()&&!v.provider().equals(doctor.id().toString())||CityOccupations.supply(city,"medicine")==0)return false;
        if(!CityJourneys.at(city,doctor,place("doctor"),14)||!CityJourneys.at(city,patient,place("doctor"),12)||!CityHabits.working(doctor,NapoliBroadcast.time(city),NapoliBroadcast.day(city)))return false;
        if(city.getGameTime()-v.last()<40)return false;int progress=v.progress()+40;
        if(progress<160){put(city,patient.id(),new Visit(v.kind(),v.day(),v.booked(),v.due(),"waiting",progress,doctor.id().toString(),city.getGameTime()));return false;}
        CityOccupations.supply(city,"medicine",CityOccupations.supply(city,"medicine")-1);put(city,patient.id(),new Visit(v.kind(),v.day(),v.booked(),v.due(),"completed",progress,doctor.id().toString(),city.getGameTime()));
        var l=CitySimulation.life(city,patient.id());CitySimulation.put(city,patient.id(),l.career(Math.min(100,l.health()+25),l.reputation(),l.experience(),l.rank(),l.absences(),CitySimulation.memory(l,"Visita medica con "+doctor.name())));
        if(patient.balance()>=2){CityOccupations.money(city,patient.id(),-2);CityOccupations.money(city,doctor.id(),2);}return true;
    }
    static boolean municipalityReady(ServerLevel city,NapoliCivicLife.Citizen c){var v=visit(city,c.id());if(v==null||!v.kind().equals("municipality"))v=book(city,c,"municipality");return open("municipality",NapoliBroadcast.time(city),NapoliBroadcast.day(city))&&staff(city,"municipality")!=null&&CityJourneys.at(city,c,place("municipality"),8);}
    static void completeMunicipality(ServerLevel city,NapoliCivicLife.Citizen c){var v=visit(city,c.id());var worker=staff(city,"municipality");if(v!=null&&v.kind().equals("municipality")&&worker!=null)put(city,c.id(),new Visit(v.kind(),v.day(),v.booked(),v.due(),"completed",160,worker.id().toString(),city.getGameTime()));}
    static int menu(ServerPlayer p){if(p.level().dimension()!=NapoliMap.LEVEL)return 0;var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();for(var entry:Map.of("doctor","Ambulatorio","school","Scuola","municipality","Municipio").entrySet()){
            boolean open=open(entry.getKey(),NapoliBroadcast.time(p.level()),NapoliBroadcast.day(p.level()));body.add(new PlainMessage(Component.literal(entry.getValue()+": "+(!open?"chiuso":staff(p.level(),entry.getKey())==null?"personale momentaneamente assente":"servizio disponibile")),270));}
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Servizi del quartiere"),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),List.of(CitySocial.button("Fermate e prossimi passaggi","/servizi trasporti")),Optional.empty(),1)));return 1;}
    static void tick(ServerLevel city){
        var census=NapoliCivicLife.census(city);if(census==null)return;for(var c:census.people()){
            if(CitySimulation.life(city,c.id()).health()<65){var previous=visit(city,c.id());if(previous==null||previous.day()!=NapoliBroadcast.day(city))book(city,c,"doctor");}
            if(city.getEntity(c.id())!=null)continue;var v=visit(city,c.id());if(v==null||v.state().equals("completed"))continue;
            if(v.kind().equals("doctor")&&CityJourneys.at(city,c,place("doctor"),12)){if(v.state().equals("booked")){v=new Visit(v.kind(),v.day(),v.booked(),v.due(),"waiting",v.progress(),v.provider(),city.getGameTime());put(city,c.id(),v);}var doctor=staff(city,"doctor");if(doctor!=null)examine(city,doctor,c);}
            else if(v.kind().equals("school"))advanceSchool(city,c,v);
        }
    }
    static void register(){ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%40==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)tick(city);}});CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("servizi").executes(c->menu(c.getSource().getPlayerOrException()))));}
    private CityServices(){}
}
