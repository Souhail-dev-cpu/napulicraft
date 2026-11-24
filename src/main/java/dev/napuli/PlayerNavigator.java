package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import static dev.napuli.CityText.t;

/** Bounded loaded-world pedestrian search over street waypoints. Never loads, edits or teleports. */
final class PlayerNavigator {
    record Search(List<BlockPos> path,boolean reached) {}
    record Instruction(Component text,BlockPos target) {}
    private static final class Route { BlockPos goal;List<BlockPos> macro=List.of(),path=List.of();int next;long planned=-100; }
    private static final Map<UUID,Route> ROUTES=new HashMap<>();
    static boolean clear(ServerLevel city,BlockPos p){if(!city.hasChunkAt(p))return false;BlockState s=city.getBlockState(p);return s.is(Blocks.LADDER)||s.is(Blocks.VINE)||s.is(BlockTags.WOODEN_DOORS)||s.getBlock() instanceof DoorBlock&&s.getValue(DoorBlock.OPEN)||s.getCollisionShape(city,p).isEmpty();}
    static boolean free(ServerLevel city,BlockPos p){if(!city.hasChunkAt(p))return false;var s=city.getBlockState(p);return (s.is(Blocks.LADDER)||s.is(Blocks.VINE)||!city.getBlockState(p.below()).getCollisionShape(city,p.below()).isEmpty())&&clear(city,p)&&clear(city,p.above());}
    static Search search(ServerLevel city,BlockPos start,BlockPos goal,int budget){
        if(!free(city,start))return new Search(List.of(),false);
        record Node(BlockPos at,int cost,double score){}
        var queue=new PriorityQueue<Node>(Comparator.comparingDouble(Node::score));var costs=new HashMap<BlockPos,Integer>();var previous=new HashMap<BlockPos,BlockPos>();
        costs.put(start,0);queue.add(new Node(start,0,heuristic(start,goal)));BlockPos best=start;double closest=heuristic(start,goal);int examined=0;
        while(!queue.isEmpty()&&examined++<budget){var current=queue.remove();if(current.cost()!=costs.get(current.at()))continue;
            double distance=heuristic(current.at(),goal);if(distance<closest){closest=distance;best=current.at();}if(current.at().equals(goal)){best=goal;break;}
            var neighbors=new ArrayList<BlockPos>();for(var d:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
                var next=current.at().relative(d).offset(0,dy,0);
                if(Math.abs(next.getX()-start.getX())>48||Math.abs(next.getZ()-start.getZ())>48||Math.abs(next.getY()-start.getY())>64)continue;
                if(dy>0&&!clear(city,current.at().above(2))||dy<0&&!clear(city,next.above(2)))continue;
                if(free(city,next))neighbors.add(next);
            }
            var state=city.getBlockState(current.at());if(state.is(Blocks.LADDER)||state.is(Blocks.VINE))for(var next:List.of(current.at().above(),current.at().below()))if(Math.abs(next.getY()-start.getY())<=64&&free(city,next))neighbors.add(next);
            for(var next:neighbors){int cost=current.cost()+10+Math.abs(next.getY()-current.at().getY())*3;if(cost>=costs.getOrDefault(next,Integer.MAX_VALUE))continue;costs.put(next,cost);previous.put(next,current.at());queue.add(new Node(next,cost,cost+10*heuristic(next,goal)));}
        }
        if(best.equals(start)&&!best.equals(goal))return new Search(List.of(),false);
        var reversed=new ArrayList<BlockPos>();for(BlockPos at=best;at!=null;at=previous.get(at))reversed.add(at);Collections.reverse(reversed);return new Search(List.copyOf(reversed),best.equals(goal));
    }
    private static double heuristic(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())+Math.abs(a.getY()-b.getY())*1.5;}
    static List<BlockPos> macro(BlockPos from,BlockPos goal){
        if(from.distSqr(goal)<96*96||NapoliAtlas.ORIGINAL.contains(from.getX(),from.getZ())&&NapoliAtlas.ORIGINAL.contains(goal.getX(),goal.getZ()))return List.of(goal);
        return CityRoutes.walk(from,goal);
    }
    static Instruction guide(ServerPlayer p,BlockPos goal){
        var city=p.level();var route=ROUTES.computeIfAbsent(p.getUUID(),id->new Route());var start=p.blockPosition();
        if(!goal.equals(route.goal)){route.goal=goal;route.macro=macro(start,goal);route.path=List.of();route.next=0;route.planned=-100;}
        while(route.next<route.path.size()&&route.path.get(route.next).distToCenterSqr(p.position())<2.3*2.3)route.next++;
        boolean blocked=route.next<route.path.size()&&!free(city,route.path.get(route.next));
        boolean deviated=route.next<route.path.size()&&route.path.get(route.next).distSqr(start)>10*10;
        if((route.next>=route.path.size()||blocked||deviated)&&city.getGameTime()-route.planned>=20){
            route.planned=city.getGameTime();BlockPos focus=goal;
            if(!route.macro.isEmpty()){
                int nearest=0;for(int i=1;i<route.macro.size();i++)if(route.macro.get(i).distSqr(start)<route.macro.get(nearest).distSqr(start))nearest=i;
                focus=route.macro.get(Math.min(route.macro.size()-1,nearest+(route.macro.get(nearest).distSqr(start)<8*8?1:0)));
            }
            BlockPos floor=start;for(int dy:new int[]{0,1,-1})if(free(city,start.offset(0,dy,0))){floor=start.offset(0,dy,0);break;}
            var path=search(city,floor,focus,4096);route.path=path.path();route.next=route.path.size()>1?1:route.path.size();
        }
        if(route.next>=route.path.size())return new Instruction(t("nav.blocked"),null);
        int next=route.next;var target=route.path.get(next);int dx=Integer.signum(target.getX()-start.getX()),dz=Integer.signum(target.getZ()-start.getZ());
        while(next+1<route.path.size()&&next-route.next<12){var a=route.path.get(next);var b=route.path.get(next+1);if(Integer.signum(b.getX()-a.getX())!=dx||Integer.signum(b.getZ()-a.getZ())!=dz)break;target=b;next++;}
        Component text=t("nav.straight",Math.max(1,(int)Math.sqrt(target.distSqr(start))));
        if(target.getY()>start.getY()+1)text=t("nav.up");else if(target.getY()<start.getY()-1)text=t("nav.down");
        else if(next+1<route.path.size()){var after=route.path.get(next+1);int cross=dx*(after.getZ()-target.getZ())-dz*(after.getX()-target.getX());if(cross!=0)text=t(cross>0?"nav.right":"nav.left",Math.max(1,(int)Math.sqrt(target.distSqr(start))));}
        return new Instruction(text,target);
    }
    static void register(){ServerLifecycleEvents.SERVER_STOPPED.register(s->ROUTES.clear());net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h,s)->ROUTES.remove(h.player.getUUID()));}
    private PlayerNavigator(){}
}
