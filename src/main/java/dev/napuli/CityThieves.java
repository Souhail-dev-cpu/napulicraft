package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import static dev.napuli.CityText.t;

/** Physical approaches and recoverable money; identities, evidence and cooldowns survive saving. */
final class CityThieves {
    record Loot(UUID thief,UUID victim,boolean player,int amount,boolean recovered){
        static final Codec<Loot> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.STRING_CODEC.fieldOf("thief").forGetter(Loot::thief),UUIDUtil.STRING_CODEC.fieldOf("victim").forGetter(Loot::victim),Codec.BOOL.fieldOf("player").forGetter(Loot::player),Codec.intRange(1,99999).fieldOf("amount").forGetter(Loot::amount),Codec.BOOL.fieldOf("recovered").forGetter(Loot::recovered)).apply(i,Loot::new));
    }
    private record Hunt(UUID victim,long contact,long until,boolean fleeing,BlockPos escape){}
    static final AttachmentType<Set<String>> MEMBERS=AttachmentRegistry.<Set<String>>builder().persistent(Codec.STRING.listOf().xmap(Set::copyOf,List::copyOf)).buildAndRegister(Napuli.id("night_thieves"));
    static final AttachmentType<Map<String,Loot>> LOOT=AttachmentRegistry.<Map<String,Loot>>builder().persistent(Codec.unboundedMap(Codec.STRING,Loot.CODEC)).buildAndRegister(Napuli.id("stolen_money"));
    static final AttachmentType<Map<String,Long>> COOLDOWNS=AttachmentRegistry.<Map<String,Long>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)).buildAndRegister(Napuli.id("thief_cooldowns"));
    private static final AttachmentType<Hunt> HUNT=AttachmentRegistry.create(Napuli.id("thief_approach"));
    static boolean candidate(NapoliCivicLife.Citizen c,long day){return c.age(day)>=18&&c.age(day)<65&&!CityNightlife.nightWorker(c)&&(c.profession()==null||c.profession().category!=CityProfessions.Category.SECURITY&&c.profession().category!=CityProfessions.Category.HEALTH);}
    static void seed(ServerLevel city){
        var census=NapoliCivicLife.census(city);if(census==null)return;int limit=CityLifeConfig.settings.nightThieves;long day=NapoliBroadcast.day(city);
        var old=city.getAttachedOrElse(MEMBERS,Set.of());var members=new LinkedHashSet<String>();
        for(String id:old.stream().sorted().toList()){var c=NapoliCivicLife.profile(city,UUID.fromString(id));if(c!=null&&candidate(c,day)&&members.size()<limit)members.add(id);}
        for(var c:census.people().stream().filter(c->candidate(c,day)).sorted(Comparator.comparing(c->c.id().toString())).toList())if(members.size()<limit)members.add(c.id().toString());
        if(!members.equals(old))city.setAttached(MEMBERS,Set.copyOf(members));
    }
    static boolean onProwl(ServerLevel city,NapoliCivicLife.Citizen c,CitySimulation.Life life,long time){
        var n=life.needs();return CityLifeConfig.settings.nightThieves>0&&CityNightlife.night(time)&&city.getAttachedOrElse(MEMBERS,Set.of()).contains(c.id().toString())
                &&candidate(c,NapoliBroadcast.day(city))&&!CityPublicOrder.jailed(city,c.id())&&life.health()>=60&&n.sleep()>=40&&n.hunger()>=35&&n.bladder()>=30;
    }
    static boolean street(ServerLevel city,LivingEntity entity){return city.hasChunkAt(entity.blockPosition())&&CityLivingHomes.at(entity.blockPosition())==null&&!CityProperty.home(city,entity.blockPosition());}
    static boolean target(ServerLevel city,NapoliResident thief,LivingEntity victim){
        if(victim==thief||!victim.isAlive()||victim.level()!=city||victim.isSleeping()||victim.isPassenger()||!street(city,victim)||city.getGameTime()<city.getAttachedOrElse(COOLDOWNS,Map.of()).getOrDefault("victim:"+victim.getUUID(),0L))return false;
        if(city.getAttachedOrElse(LOOT,Map.of()).values().stream().anyMatch(l->l.victim().equals(victim.getUUID())))return false;
        if(victim instanceof ServerPlayer p)return !p.isCreative()&&!p.isSpectator()&&!Carabinieri.isJailed(p)&&NapoliStreet.cash(p)>=4;
        if(!(victim instanceof NapoliResident npc)||npc.role()==NapoliResident.Role.CARABINIERE||city.getAttachedOrElse(MEMBERS,Set.of()).contains(victim.getUUID().toString())||CityPublicOrder.jailed(city,victim.getUUID()))return false;
        var c=NapoliCivicLife.profile(city,victim.getUUID());return c!=null&&c.age(NapoliBroadcast.day(city))>=18&&c.balance()>=4;
    }
    static boolean noticed(NapoliResident thief,LivingEntity victim){
        if(!victim.hasLineOfSight(thief))return false;var toward=thief.position().subtract(victim.position()).multiply(1,0,1);var look=victim.getLookAngle().multiply(1,0,1);
        return toward.lengthSqr()<.01||look.normalize().dot(toward.normalize())>.6;
    }
    static boolean officerNear(ServerLevel city,NapoliResident thief){return city.getEntitiesOfClass(NapoliResident.class,thief.getBoundingBox().inflate(14),n->n!=thief&&n.role()==NapoliResident.Role.CARABINIERE&&n.isAlive()&&!n.isSleeping()&&!CityPublicOrder.jailed(city,n.getUUID())&&n.hasLineOfSight(thief)).size()>0;}
    static boolean witnessed(ServerLevel city,NapoliResident thief,LivingEntity victim){
        return witness(city,thief,victim)!=null;
    }
    private static LivingEntity witness(ServerLevel city,NapoliResident thief,LivingEntity victim){return city.getEntitiesOfClass(LivingEntity.class,thief.getBoundingBox().inflate(12),e->e!=thief&&e!=victim&&e.isAlive()&&!e.isSleeping()&&(e instanceof NapoliResident||e instanceof ServerPlayer p&&!p.isSpectator())&&e.hasLineOfSight(thief)&&e.hasLineOfSight(victim)).stream().findFirst().orElse(null);}
    private static void cooldown(ServerLevel city,UUID thief,UUID victim,long duration){var all=new HashMap<>(city.getAttachedOrElse(COOLDOWNS,Map.of()));long now=city.getGameTime();all.values().removeIf(at->at<=now);all.put("thief:"+thief,now+duration);if(victim!=null)all.put("victim:"+victim,now+6000);city.setAttached(COOLDOWNS,Map.copyOf(all));}
    static boolean steal(ServerLevel city,NapoliResident thief,LivingEntity victim){
        var c=NapoliCivicLife.profile(city,thief.getUUID());long now=city.getGameTime();
        if(c==null||!thief.isAlive()||thief.isSleeping()||thief.isPassenger()||!onProwl(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city))||!street(city,thief)||!target(city,thief,victim)
                ||thief.distanceToSqr(victim)>3.24||Math.abs(thief.getY()-victim.getY())>1.25||!Pickpocket.behind(thief,victim)||!thief.hasLineOfSight(victim)||noticed(thief,victim)||officerNear(city,thief)
                ||now<city.getAttachedOrElse(COOLDOWNS,Map.of()).getOrDefault("thief:"+c.id(),0L)||city.getAttachedOrElse(LOOT,Map.of()).values().stream().filter(l->l.thief().equals(c.id())&&!l.recovered()).count()>=16)return false;
        boolean player=victim instanceof ServerPlayer;var person=player?null:NapoliCivicLife.profile(city,victim.getUUID());int balance=player?NapoliStreet.cash((ServerPlayer)victim):person.balance();int amount=Math.min(8,Math.max(1,balance/5));
        if(player)NapoliStreet.credit((ServerPlayer)victim,-amount,"theft",c.name());else debitCitizen(city,person.id(),amount);
        var all=new HashMap<>(city.getAttachedOrElse(LOOT,Map.of()));all.put(c.id()+"/"+victim.getUUID(),new Loot(c.id(),victim.getUUID(),player,amount,false));city.setAttached(LOOT,Map.copyOf(all));cooldown(city,c.id(),victim.getUUID(),4800);
        CityNeighborhood.leave(city,c.id());thief.setAttached(HUNT,new Hunt(victim.getUUID(),0,now+600,true,null));
        var witness=witness(city,thief,victim);
        if(witness!=null||!player){CityPublicOrder.report(city,c.id(),CityPublicOrder.Offence.THEFT);if(!player)CityNeighborhood.say(city,(NapoliResident)victim,"Al ladro! Mi ha preso il denaro!",20);else if(witness instanceof NapoliResident observer)CityNeighborhood.say(city,observer,"Ehi! Un borseggiatore!",20);else if(witness instanceof ServerPlayer observer)NapoliNotice.show(observer,t("night.witness"));}
        if(player)NapoliNotice.show((ServerPlayer)victim,t("night.stolen",amount));
        return true;
    }
    private static void debitCitizen(ServerLevel city,UUID id,int amount){var census=NapoliCivicLife.census(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(id)?c.withMoney(c.balance()-amount,c.job()):c).toList()));}
    static void recover(ServerLevel city,UUID thief){var all=new HashMap<>(city.getAttachedOrElse(LOOT,Map.of()));boolean changed=false;for(var entry:all.entrySet()){var l=entry.getValue();if(l.thief().equals(thief)&&!l.recovered()){entry.setValue(new Loot(l.thief(),l.victim(),l.player(),l.amount(),true));changed=true;}}if(changed)city.setAttached(LOOT,Map.copyOf(all));refund(city);}
    static void refund(ServerLevel city){
        var before=city.getAttachedOrElse(LOOT,Map.of());if(before.isEmpty())return;var all=new HashMap<>(before);
        for(var entry:before.entrySet()){
            var l=entry.getValue();if(!l.recovered())continue;int amount=0;
            if(l.player()){
                var p=city.getServer().getPlayerList().getPlayer(l.victim());if(p!=null&&p.isAlive()){amount=Math.min(l.amount(),Math.max(0,NapoliStreet.MAX_CASH-NapoliStreet.cash(p)));if(amount>0){NapoliStreet.credit(p,amount,"recovered","");NapoliNotice.show(p,t("night.returned",amount));}}
            }else{
                var c=NapoliCivicLife.profile(city,l.victim());if(c!=null){amount=Math.min(l.amount(),Math.max(0,99999-c.balance()));if(amount>0)debitCitizen(city,c.id(),-amount);}
            }
            if(amount==l.amount())all.remove(entry.getKey());else if(amount>0)all.put(entry.getKey(),new Loot(l.thief(),l.victim(),l.player(),l.amount()-amount,true));
        }
        if(!all.equals(before))city.setAttached(LOOT,Map.copyOf(all));
    }
    static boolean interact(NapoliResident npc,Player player){
        if(!(player instanceof ServerPlayer p)||p.isSpectator()||!p.isAlive()||p.level()!=npc.level()||p.distanceToSqr(npc)>9||!npc.hasLineOfSight(p))return false;
        var city=p.level();if(city.getAttachedOrElse(LOOT,Map.of()).values().stream().noneMatch(l->l.thief().equals(npc.getUUID())&&!l.recovered()))return false;
        recover(city,npc.getUUID());CityPublicOrder.report(city,npc.getUUID(),CityPublicOrder.Offence.THEFT);cooldown(city,npc.getUUID(),null,4800);
        npc.setAttached(HUNT,new Hunt(p.getUUID(),0,city.getGameTime()+600,true,null));NpcSpeech.reply(p,npc,t("night.caught"));return true;
    }
    private static BlockPos escape(ServerLevel city,NapoliResident thief,LivingEntity from){
        Vec3 away=from==null?new Vec3(1,0,0):thief.position().subtract(from.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        for(int turn:new int[]{0,1,-1,2,-2}){double angle=Math.atan2(away.z,away.x)+turn*Math.PI/4;var at=thief.blockPosition().offset((int)Math.round(Math.cos(angle)*12),0,(int)Math.round(Math.sin(angle)*12));for(int dy:new int[]{0,1,-1}){var p=at.offset(0,dy,0);if(city.hasChunkAt(p)&&NapoliResidentHomes.walkable(city,p)&&!CityProperty.home(city,p)&&CityLivingHomes.at(p)==null)return p;}}
        return thief.blockPosition();
    }
    static boolean tick(NapoliResident thief,ServerLevel city){
        var hunt=thief.getAttached(HUNT);var c=NapoliCivicLife.profile(city,thief.getUUID());if(c==null)return false;long now=city.getGameTime();
        if(CityPublicOrder.jailed(city,c.id())){thief.removeAttached(HUNT);return false;}
        if(hunt!=null&&hunt.fleeing()){
            if(now>=hunt.until()){thief.removeAttached(HUNT);thief.getNavigation().stop();return false;}
            if((thief.tickCount+thief.getId())%10!=0)return true;
            var from=city.getEntity(hunt.victim());var goal=hunt.escape();if(goal==null||goal.distToCenterSqr(thief.position())<4)goal=escape(city,thief,from instanceof LivingEntity living?living:null);
            thief.setAttached(HUNT,new Hunt(hunt.victim(),0,hunt.until(),true,goal));CityMovement.stand(thief);if(thief.isSleeping())thief.stopSleeping();thief.setActivity(NapoliHouseholds.Activity.COMMUTE);
            var path=thief.getNavigation().createPath(goal,0);if(path!=null&&path.canReach()&&CityMovement.beforeMove(thief,goal))thief.getNavigation().moveTo(path,1.35);return true;
        }
        if(!onProwl(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city))||CityConversation.engaged(city,c.id())||city.players().stream().noneMatch(p->!p.isSpectator()&&p.distanceToSqr(thief)<64*64)){
            if(hunt!=null){thief.removeAttached(HUNT);thief.getNavigation().stop();}return false;
        }
        if(hunt==null){
            if((thief.tickCount+thief.getId())%40!=0||now<city.getAttachedOrElse(COOLDOWNS,Map.of()).getOrDefault("thief:"+c.id(),0L)||officerNear(city,thief)||!street(city,thief))return false;
            var victim=city.getEntitiesOfClass(LivingEntity.class,thief.getBoundingBox().inflate(12),v->target(city,thief,v)&&thief.hasLineOfSight(v)&&!noticed(thief,v)).stream().min(Comparator.comparingDouble(thief::distanceToSqr)).orElse(null);
            if(victim==null)return false;CityNeighborhood.leave(city,c.id());hunt=new Hunt(victim.getUUID(),0,now+300,false,null);thief.setAttached(HUNT,hunt);
        }
        if((thief.tickCount+thief.getId())%10!=0)return true;
        var entity=city.getEntity(hunt.victim());
        if(!(entity instanceof LivingEntity victim)||!target(city,thief,victim)||now>=hunt.until()||thief.distanceToSqr(victim)>16*16||!thief.hasLineOfSight(victim)||noticed(thief,victim)||officerNear(city,thief)){
            thief.removeAttached(HUNT);thief.getNavigation().stop();cooldown(city,c.id(),null,400);return false;
        }
        if(thief.distanceToSqr(victim)>3.24||!Pickpocket.behind(thief,victim)){thief.setAttached(HUNT,new Hunt(hunt.victim(),0,hunt.until(),false,null));thief.setActivity(NapoliHouseholds.Activity.COMMUTE);var back=victim.position().subtract(Vec3.directionFromRotation(0,victim.getYHeadRot()).scale(1.4));CityDomestic.move(thief,net.minecraft.core.BlockPos.containing(back));return true;}
        thief.getNavigation().stop();thief.setActivity(NapoliHouseholds.Activity.WAIT);thief.getLookControl().setLookAt(victim,30,30);thief.animate(CityGestures.Gesture.EXAMINE,20);
        if(hunt.contact()==0){thief.setAttached(HUNT,new Hunt(hunt.victim(),now,hunt.until(),false,null));if(victim instanceof ServerPlayer p)NapoliNotice.show(p,t("night.warning"));}
        else if(now-hunt.contact()>=80&&!steal(city,thief,victim)){thief.removeAttached(HUNT);cooldown(city,c.id(),null,400);}
        return true;
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%20==0){var city=server.getLevel(NapoliMap.LEVEL);if(city!=null)refund(city);}});
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(!(entity.level() instanceof ServerLevel city))return;recover(city,entity.getUUID());
            var thieves=city.getAttachedOrElse(LOOT,Map.of()).values().stream().filter(l->l.victim().equals(entity.getUUID())).map(Loot::thief).distinct().toList();for(var id:thieves)recover(city,id);
        });
    }
    private CityThieves(){}
}
