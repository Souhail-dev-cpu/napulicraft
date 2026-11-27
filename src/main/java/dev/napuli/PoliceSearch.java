package dev.napuli;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Escape needs continuous concealment, not simply crossing the officers' sight radius. */
final class PoliceSearch {
    static final int HIDE_TICKS=400;
    static int advance(int hidden,boolean visible,boolean covered){return visible||!covered?0:Math.min(HIDE_TICKS,hidden+1);}
    static boolean sees(NapoliResident officer,ServerPlayer player){return officer.isAlive()&&!officer.isSleeping()&&!Carabinieri.npcJailed(officer.getUUID())&&!CityPublicOrder.jailed(player.level(),officer.getUUID())&&officer.distanceToSqr(player)<(PoliceVehicles.riding(officer)?96*96:64*64)&&officer.hasLineOfSight(player);}
    static boolean covered(ServerPlayer player,List<NapoliResident> observers,BlockPos scene){
        var eye=player.getEyePosition();
        // Distant officers with a clear view still prevent "hiding" on an empty road.
        for(var observer:observers)if(observer.isAlive()&&!observer.isSleeping()&&observer.hasLineOfSight(player))return false;
        Vec3 from=Vec3.atBottomCenterOf(scene).add(0,1.6,0);
        if(observers.isEmpty()&&clear(player,from,eye))return false;
        if(!player.level().canSeeSky(player.blockPosition()))return true;
        // The cover must actually be close to the fugitive, rather than a far-away obstruction.
        for(int i=0;i<8;i++){double angle=i*Math.PI/4;var to=eye.add(Math.cos(angle)*4,0,Math.sin(angle)*4);if(!clear(player,eye,to))return true;}
        return false;
    }
    private static boolean clear(ServerPlayer p,Vec3 from,Vec3 to){return p.level().clip(new ClipContext(from,to,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    private PoliceSearch(){}
}
