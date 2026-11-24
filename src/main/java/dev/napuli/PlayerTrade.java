package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import static dev.napuli.CityText.t;

/** Offers remain in the original inventories until both confirm the same revision. */
final class PlayerTrade {
    static final class Offer {final Map<Integer,ItemStack> items=new TreeMap<>();int money;}
    static final class Session {
        final UUID id=UUID.randomUUID(),first,second;final Offer a=new Offer(),b=new Offer();final long expires;
        final Set<UUID> confirmed=new HashSet<>();long revision;boolean accepted,closed;
        Session(ServerPlayer a,ServerPlayer b){first=a.getUUID();second=b.getUUID();expires=a.level().getGameTime()+2400;}
        Offer offer(UUID id){return first.equals(id)?a:b;}
        UUID other(UUID id){return first.equals(id)?second:first;}
        void changed(){revision++;confirmed.clear();}
    }
    private static final Map<MinecraftServer,Map<UUID,Session>> LIVE=new WeakHashMap<>();
    static Map<UUID,Session> sessions(MinecraftServer s){return LIVE.computeIfAbsent(s,k->new HashMap<>());}
    static Session current(ServerPlayer p){return sessions(p.level().getServer()).values().stream().filter(s->s.first.equals(p.getUUID())||s.second.equals(p.getUUID())).findFirst().orElse(null);}
    static boolean available(ServerPlayer p){return p!=null&&p.isAlive()&&!p.isSpectator()&&!Carabinieri.isJailed(p)&&p.containerMenu==p.inventoryMenu;}
    static boolean valid(Session s,ServerPlayer a,ServerPlayer b){return !s.closed&&available(a)&&available(b)&&a.level()==b.level()&&a.distanceToSqr(b)<=36&&a.level().getGameTime()<s.expires;}
    static void end(ServerPlayer p,Session s){s.closed=true;sessions(p.level().getServer()).remove(s.id);for(var id:List.of(s.first,s.second)){var other=p.level().getServer().getPlayerList().getPlayer(id);if(other!=null)other.sendSystemMessage(t("trade.ended"));}}
    static int menu(ServerPlayer p){
        var s=current(p);if(s!=null)return view(p,s);
        var entries=new ArrayList<CompanionBoard.Entry>();for(var other:p.level().players())if(other!=p&&available(other)&&p.distanceToSqr(other)<=36&&current(other)==null)entries.add(new CompanionBoard.Entry(other.getName(),t("trade.invite_help"),"compagno scambio invita/"+other.getUUID()));
        if(entries.isEmpty())entries.add(new CompanionBoard.Entry(t("trade.nearby"),t("trade.nearby_help"),""));
        return CityCompanion.show(p,"scambi",t("trade.title"),entries);
    }
    static int view(ServerPlayer p,Session s){
        var a=p.level().getServer().getPlayerList().getPlayer(s.first);var b=p.level().getServer().getPlayerList().getPlayer(s.second);
        if(!valid(s,a,b)){end(p,s);return CityCompanion.fail(p,t("trade.distance"));}
        var entries=new ArrayList<CompanionBoard.Entry>();
        for(var owner:List.of(a,b)){var offer=s.offer(owner.getUUID());entries.add(new CompanionBoard.Entry(t("trade.offer",owner.getName(),offer.money),t(s.confirmed.contains(owner.getUUID())?"trade.confirmed":"trade.waiting",s.revision),""));for(var item:offer.items.values())entries.add(new CompanionBoard.Entry(item.getHoverName(),t("trade.quantity",item.getCount()),""));}
        if(!s.accepted&&p.getUUID().equals(s.second))entries.add(new CompanionBoard.Entry(t("trade.accept"),t("trade.invite_help"),"compagno scambio accetta/"+s.id));
        if(s.accepted){entries.add(new CompanionBoard.Entry(t("trade.edit"),t("trade.reset"),"compagno scambio oggetti/"+s.id));entries.add(new CompanionBoard.Entry(t("trade.money",s.offer(p.getUUID()).money),t("trade.reset"),"compagno scambio denaro/"+s.id));entries.add(new CompanionBoard.Entry(t("trade.confirm",s.revision),t("trade.confirm_help"),"compagno scambio conferma/"+s.id+"/"+s.revision));}
        entries.add(new CompanionBoard.Entry(t("trade.refresh"),t("trade.reset"),"compagno scambio vedi/"+s.id));entries.add(new CompanionBoard.Entry(t("trade.cancel"),t("trade.no_escrow"),"compagno scambio annulla/"+s.id));
        return CityCompanion.show(p,"scambio/"+s.id,t("trade.title"),entries);
    }
    static void refresh(ServerPlayer p,Session s){for(var id:List.of(s.first,s.second)){var other=p.level().getServer().getPlayerList().getPlayer(id);if(other!=null)view(other,s);}}
    static int action(ServerPlayer p,String command){
        String[] parts=command.split("/");if(parts.length<2)return 0;
        if(parts[0].equals("invita")){var id=CompanionMeetups.uuid(parts[1]);var other=id==null?null:p.level().getServer().getPlayerList().getPlayer(id);if(other==p||current(p)!=null||other==null||current(other)!=null)return CityCompanion.fail(p,t("trade.busy"));var s=new Session(p,other);if(!valid(s,p,other))return CityCompanion.fail(p,t("trade.distance"));sessions(p.level().getServer()).put(s.id,s);refresh(p,s);return 1;}
        var s=current(p);if(s==null||!s.id.toString().equals(parts[1]))return CityCompanion.fail(p,t("trade.expired"));
        var other=p.level().getServer().getPlayerList().getPlayer(s.other(p.getUUID()));if(!valid(s,p,other)){end(p,s);return CityCompanion.fail(p,t("trade.distance"));}
        try{
            switch(parts[0]){
                case "annulla"->{end(p,s);return menu(p);}
                case "accetta"->{if(!s.second.equals(p.getUUID()))return 0;s.accepted=true;s.changed();}
                case "vedi"->{return view(p,s);}
                default->{if(!s.accepted)return 0;var offer=s.offer(p.getUUID());switch(parts[0]){
                    case "oggetti"->{var entries=new ArrayList<CompanionBoard.Entry>();for(int slot=0;slot<36;slot++){var item=p.getInventory().getItem(slot);if(item.isEmpty()&&!offer.items.containsKey(slot))continue;entries.add(new CompanionBoard.Entry(item.isEmpty()?t("trade.empty_slot"):item.getHoverName(),t("trade.slot",slot+1,item.getCount(),offer.items.containsKey(slot)?offer.items.get(slot).getCount():0),"compagno scambio scegli/"+s.id+"/"+slot));}entries.add(new CompanionBoard.Entry(t("trade.back"),t("trade.reset"),"compagno scambio vedi/"+s.id));return CityCompanion.show(p,"scambio_oggetti",t("trade.edit"),entries);}
                    case "scegli"->{int slot=Integer.parseInt(parts[2]);if(slot<0||slot>=36)return 0;var item=p.getInventory().getItem(slot);var entries=new ArrayList<CompanionBoard.Entry>();for(int count:new TreeSet<>(List.of(0,1,Math.max(1,item.getCount()/2),item.getCount())))entries.add(new CompanionBoard.Entry(t("trade.quantity",count),t("trade.reset"),"compagno scambio quantita/"+s.id+"/"+slot+"/"+count));return CityCompanion.show(p,"scambio_quantita",item.isEmpty()?t("trade.empty_slot"):item.getHoverName(),entries);}
                    case "quantita"->{int slot=Integer.parseInt(parts[2]),count=Integer.parseInt(parts[3]);if(slot<0||slot>=36||count<0)return 0;var stack=p.getInventory().getItem(slot);if(count>stack.getCount())return 0;if(count==0)offer.items.remove(slot);else offer.items.put(slot,stack.copyWithCount(count));s.changed();}
                    case "denaro"->{var entries=new ArrayList<CompanionBoard.Entry>();for(int delta:new int[]{-100,-10,-1,0,1,10,100}){int amount=Math.clamp((long)offer.money+delta,0,NapoliStreet.cash(p));if(delta==0)amount=0;entries.add(new CompanionBoard.Entry(t("trade.money",amount),t("trade.reset"),"compagno scambio euro/"+s.id+"/"+amount));}return CityCompanion.show(p,"scambio_denaro",t("trade.money",offer.money),entries);}
                    case "euro"->{int amount=Integer.parseInt(parts[2]);if(amount<0||amount>NapoliStreet.cash(p))return 0;offer.money=amount;s.changed();}
                    case "conferma"->{long revision=Long.parseLong(parts[2]);if(revision!=s.revision)return CityCompanion.fail(p,t("trade.changed"));s.confirmed.add(p.getUUID());if(s.confirmed.size()==2){var a=p.level().getServer().getPlayerList().getPlayer(s.first);var b=p.level().getServer().getPlayerList().getPlayer(s.second);if(!settle(s,a,b)){s.changed();refresh(p,s);return CityCompanion.fail(p,t("trade.cannot_complete"));}sessions(p.level().getServer()).remove(s.id);a.sendSystemMessage(t("trade.done"));b.sendSystemMessage(t("trade.done"));return menu(p);}}
                    default->{return 0;}
                }}
            }
        }catch(IndexOutOfBoundsException|NumberFormatException ignored){return 0;}
        refresh(p,s);return 1;
    }
    static List<ItemStack> plan(ServerPlayer p,Offer own,Offer incoming){
        var slots=PlayerInventoryPlan.copy(p);for(var entry:own.items.entrySet()){int index=entry.getKey();if(index<0||index>=36)return null;var present=slots.get(index);var promised=entry.getValue();if(promised.isEmpty()||present.getCount()<promised.getCount()||!ItemStack.isSameItemSameComponents(present,promised))return null;present.shrink(promised.getCount());}
        for(var addition:incoming.items.values())if(!PlayerInventoryPlan.add(slots,addition))return null;return slots;
    }
    static boolean settle(Session s,ServerPlayer a,ServerPlayer b){
        if(!s.accepted||!s.confirmed.containsAll(List.of(s.first,s.second))||!valid(s,a,b)||!a.getUUID().equals(s.first)||!b.getUUID().equals(s.second)||s.a.money<0||s.b.money<0||NapoliStreet.cash(a)<s.a.money||NapoliStreet.cash(b)<s.b.money)return false;
        long cashA=(long)NapoliStreet.cash(a)-s.a.money+s.b.money,cashB=(long)NapoliStreet.cash(b)-s.b.money+s.a.money;if(cashA>99999||cashB>99999)return false;
        var nextA=plan(a,s.a,s.b);var nextB=plan(b,s.b,s.a);if(nextA==null||nextB==null||!ScooterGarage.tradable(a,s.a.items.values())||!ScooterGarage.tradable(b,s.b.items.values()))return false;
        s.closed=true;PlayerInventoryPlan.apply(a,nextA);PlayerInventoryPlan.apply(b,nextB);ScooterGarage.transfer(a,b,s.a.items.values());ScooterGarage.transfer(b,a,s.b.items.values());NapoliStreet.credit(a,s.b.money-s.a.money,"trade",b.getName().getString());NapoliStreet.credit(b,s.a.money-s.b.money,"trade",a.getName().getString());return true;
    }
    static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%20!=0)return;for(var s:List.copyOf(sessions(server).values())){var a=server.getPlayerList().getPlayer(s.first);var b=server.getPlayerList().getPlayer(s.second);if(!valid(s,a,b)){sessions(server).remove(s.id);if(a!=null)a.sendSystemMessage(t("trade.ended"));if(b!=null)b.sendSystemMessage(t("trade.ended"));}}});}
    private PlayerTrade(){}
}
