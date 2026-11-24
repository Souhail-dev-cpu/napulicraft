package dev.napuli;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Pure street graph: no world or chunk reads. Surface routes avoid monuments, houses and the old core. */
final class CityRoutes {
    record Node(int x,int z){BlockPos position(){return new BlockPos(x,CityStreets.walkingHeight(x,z)+1,z);}}
    private static final class Graph {static final Map<String,Node> NODES=nodes();static final Map<Node,List<Node>> EDGES=edges();static final Set<Node> MAIN=main();}
    private static Set<Node> main(){var remaining=new HashSet<>(Graph.NODES.values());Set<Node> best=Set.of();while(!remaining.isEmpty()){var component=new HashSet<Node>();var todo=new ArrayDeque<Node>();todo.add(remaining.iterator().next());while(!todo.isEmpty()){var n=todo.remove();if(!remaining.remove(n))continue;component.add(n);todo.addAll(Graph.EDGES.getOrDefault(n,List.of()));}if(component.size()>best.size())best=component;}return Set.copyOf(best);}
    private static Map<String,Node> nodes(){var all=new LinkedHashMap<String,Node>();for(int x=-2038;x<2040;x+=40)for(int z=-798;z<540;z+=40)if(clear(x,z))all.put(x+"/"+z,new Node(x,z));return Map.copyOf(all);}
    static boolean clear(int x,int z){if(!NapoliAtlas.EXPANDED.contains(x,z)||NapoliAtlas.ORIGINAL.contains(x,z)||NapoliExpansion.water(x,z))return false;for(var p:NapoliAtlas.PLACES)if(p.near(x,z,7))return false;return true;}
    private static boolean edge(Node a,Node b){for(int i=1;i<10;i++){int x=a.x()+(b.x()-a.x())*i/10,z=a.z()+(b.z()-a.z())*i/10;if(!clear(x,z))return false;}return true;}
    private static Map<Node,List<Node>> edges(){var all=new HashMap<Node,List<Node>>();for(var n:Graph.NODES.values()){var neighbors=new ArrayList<Node>();for(int[] d:new int[][]{{40,0},{-40,0},{0,40},{0,-40}}){var p=Graph.NODES.get((n.x()+d[0])+"/"+(n.z()+d[1]));if(p!=null&&edge(n,p))neighbors.add(p);}all.put(n,List.copyOf(neighbors));}return Map.copyOf(all);}
    static Node nearest(BlockPos p){return Graph.MAIN.stream().min(Comparator.comparingDouble((Node n)->(double)(n.x()-p.getX())*(n.x()-p.getX())+(double)(n.z()-p.getZ())*(n.z()-p.getZ())).thenComparingInt(Node::x).thenComparingInt(Node::z)).orElseThrow();}
    private static final Map<String,List<BlockPos>> CACHE=new LinkedHashMap<>();
    static List<BlockPos> between(BlockPos from,BlockPos to){var start=nearest(from);var end=nearest(to);String key=start.x()+"/"+start.z()+":"+end.x()+"/"+end.z();var cached=CACHE.get(key);if(cached!=null)return cached;
        record Cost(Node node,double score){}var open=new PriorityQueue<Cost>(Comparator.comparingDouble(Cost::score));var distance=new HashMap<Node,Double>();var previous=new HashMap<Node,Node>();distance.put(start,0.0);open.add(new Cost(start,0));
        while(!open.isEmpty()){var current=open.remove().node();if(current.equals(end))break;double score=distance.get(current);for(var next:Graph.EDGES.getOrDefault(current,List.of())){double g=score+40;if(g<distance.getOrDefault(next,Double.MAX_VALUE)){distance.put(next,g);previous.put(next,current);open.add(new Cost(next,g+Math.abs(next.x()-end.x())+Math.abs(next.z()-end.z())));}}}
        if(!distance.containsKey(end))return List.of();var reversed=new ArrayList<BlockPos>();for(Node at=end;at!=null;at=previous.get(at)){reversed.add(at.position());if(at.equals(start))break;}Collections.reverse(reversed);var result=List.copyOf(reversed);if(CACHE.size()>512)CACHE.clear();CACHE.put(key,result);return result;
    }
    static List<BlockPos> walk(BlockPos from,BlockPos to){if(from.distSqr(to)<24*24)return List.of(from,to);var path=between(from,to);var points=new ArrayList<BlockPos>();points.add(from);for(var h:DistrictLife.homes())if(h.contains(from)&&!h.contains(to)){points.add(h.approach(1));break;}points.addAll(path);for(var h:DistrictLife.homes())if(h.contains(to)&&!h.contains(from)){points.add(h.approach(1));break;}points.add(to);return List.copyOf(points);}
    private CityRoutes(){}
}
