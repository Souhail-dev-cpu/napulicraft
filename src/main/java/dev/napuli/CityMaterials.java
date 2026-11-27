package dev.napuli;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Materials shared by house generation, conservative upgrades and player building. */
final class CityMaterials {
    static final Block OCHRE=block("intonaco_ocra",Block::new);
    static final Block CORAL=block("intonaco_corallo",Block::new);
    static final Block MAJOLICA=block("maiolica_napoletana",Block::new);
    static final Block COPPI=block("coppi_napoletani",Block::new);
    static final Block COPPI_SLAB=block("lastre_coppi",SlabBlock::new);
    static final Block COPPI_STAIRS=block("scale_coppi",p->new StairBlock(COPPI.defaultBlockState(),p));
    static final List<Block> ALL=List.of(OCHRE,CORAL,MAJOLICA,COPPI,COPPI_SLAB,COPPI_STAIRS);
    private static Block block(String name,java.util.function.Function<BlockBehaviour.Properties,Block> factory){
        var key=ResourceKey.create(Registries.BLOCK,Napuli.id(name));var itemKey=ResourceKey.create(Registries.ITEM,Napuli.id(name));
        var color=name.equals("intonaco_ocra")?MapColor.TERRACOTTA_YELLOW:name.equals("intonaco_corallo")?MapColor.TERRACOTTA_PINK:name.equals("maiolica_napoletana")?MapColor.COLOR_BLUE:MapColor.TERRACOTTA_ORANGE;
        var properties=BlockBehaviour.Properties.of().setId(key).mapColor(color).strength(1.8f,6).requiresCorrectToolForDrops().sound(SoundType.STONE);
        if(name.equals("lastre_coppi")||name.equals("scale_coppi"))properties.noOcclusion();
        var block=Registry.register(BuiltInRegistries.BLOCK,key,factory.apply(properties));
        Registry.register(BuiltInRegistries.ITEM,itemKey,new BlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));return block;
    }
    static Block facade(int seed){return switch(Math.floorMod(seed,5)){case 0,1->OCHRE;case 2,3->CORAL;default->Blocks.SMOOTH_SANDSTONE;};}
    static void register(){}
    private CityMaterials(){}
}
