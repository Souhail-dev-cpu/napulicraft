package dev.napuli;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.phys.Vec3;

/** Metres in the vehicle's frame: x right, y up, z forward. Shared by seats and rendered furniture. */
public final class VehicleCabin {
    public record Layout(float width,float length,float height,double floor,List<Vec3> seats) {
        public Vec3 seat(int index){return seats.get(Math.floorMod(index,seats.size()));}
        public double aisleEnd(){return length/2.0-1.35;}
    }
    private static final Layout CAR=new Layout(2.15f,4.6f,1.95f,.3,List.of(
            new Vec3(-.48,-.05,.36),new Vec3(.48,-.05,.36),new Vec3(-.48,-.05,-.85),new Vec3(.48,-.05,-.85)));
    private static Layout coach(float width,float length,float height,int rows,double first){
        return new Layout(width,length,height,.46,IntStream.range(0,rows*2).mapToObj(i->new Vec3(i%2==0?-.82:.82,.08,first-(i/2)*1.1)).toList());
    }
    private static final Layout[] LAYOUTS={CAR,coach(2.6f,8.4f,3.15f,4,1.65),coach(2.6f,10.4f,3.2f,6,2.75),coach(2.75f,12f,3.35f,8,3.85)};
    public static Layout layout(int kind){return LAYOUTS[Math.clamp(kind,0,3)];}
    public static Vec3 world(Vec3 origin,float yaw,Vec3 local){
        double a=Math.toRadians(yaw),c=Math.cos(a),s=Math.sin(a);
        // Minecraft's right-hand side when looking along +Z is -X.
        return origin.add(-c*local.x-s*local.z,local.y,-s*local.x+c*local.z);
    }
    private VehicleCabin(){}
}
