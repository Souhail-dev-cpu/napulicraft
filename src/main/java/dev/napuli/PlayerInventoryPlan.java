package dev.napuli;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Computes complete inventory changes before touching either participant. */
final class PlayerInventoryPlan {
    static List<ItemStack> copy(ServerPlayer p){var result=new ArrayList<ItemStack>(36);for(int i=0;i<36;i++)result.add(p.getInventory().getItem(i).copy());return result;}
    static boolean add(List<ItemStack> slots,ItemStack addition){
        var rest=addition.copy();
        for(var stack:slots)if(!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,rest)){int n=Math.min(rest.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(n);rest.shrink(n);if(rest.isEmpty())return true;}
        for(int i=0;i<slots.size();i++)if(slots.get(i).isEmpty()){int n=Math.min(rest.getCount(),rest.getMaxStackSize());slots.set(i,rest.copyWithCount(n));rest.shrink(n);if(rest.isEmpty())return true;}
        return rest.isEmpty();
    }
    static void apply(ServerPlayer p,List<ItemStack> slots){for(int i=0;i<36;i++)p.getInventory().setItem(i,slots.get(i));p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
    private PlayerInventoryPlan(){}
}
