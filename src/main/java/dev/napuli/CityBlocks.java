package dev.napuli;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** Shared, checked block references for city generators and furnishing. */
final class CityBlocks {
    private static final ConcurrentHashMap<String,Block> VANILLA=new ConcurrentHashMap<>();
    static Block vanilla(String name){return VANILLA.computeIfAbsent(name,key->{
        var id=Identifier.withDefaultNamespace(key);
        if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("Unknown city block: "+id);
        return BuiltInRegistries.BLOCK.getValue(id);
    });}
    private CityBlocks(){}
}
