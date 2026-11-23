package dev.napuli.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Includes entities in sections that have stopped tracking but have not finished unloading. */
@Mixin(ServerLevel.class)
public interface CityEntityManagerAccessor {
    @Accessor("entityManager") PersistentEntitySectionManager<Entity> napuli$entityManager();
}
