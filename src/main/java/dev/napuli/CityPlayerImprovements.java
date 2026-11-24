package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

final class CityPlayerImprovements {
    static void register(){
        WorkEconomy.register();HomePermissions.register();CooperativeJobs.register();ScooterGarage.register();PlayerTrade.register();CityEffects.register();
        CommandRegistrationCallback.EVENT.register((d,r,e)->{
            var root=Commands.literal("compagno");
            root.then(Commands.literal("scambi").executes(c->PlayerTrade.menu(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("cooperative").executes(c->CooperativeJobs.menu(c.getSource().getPlayerOrException()))
                    .then(Commands.argument("pagina",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c->CooperativeJobs.menu(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"pagina")))));
            root.then(Commands.literal("motorini").executes(c->ScooterGarage.menu(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("economia").executes(c->PlayerEconomyGuide.menu(c.getSource().getPlayerOrException())));
            for(var action:java.util.List.of("scambio","cooperativa","motorino","permessi"))root.then(Commands.literal(action).then(Commands.argument("azione",StringArgumentType.greedyString()).executes(c->{var p=c.getSource().getPlayerOrException();var value=StringArgumentType.getString(c,"azione");return switch(action){case "scambio"->PlayerTrade.action(p,value);case "cooperativa"->CooperativeJobs.action(p,value);case "motorino"->ScooterGarage.action(p,value);default->HomePermissions.action(p,value);};})));
            d.register(root);
        });
    }
    static void parking(net.minecraft.server.level.ServerLevel city,net.minecraft.world.level.chunk.LevelChunk chunk){for(int i=0;i<ScooterGarage.PARKING.size();i++){var marker=ScooterGarage.PARKING.get(i).east(2);if(!ChunkPos.containing(marker).equals(chunk.getPos())||CityProperty.playerBuiltInChunk(city,chunk.getPos()))continue;if(city.isEmptyBlock(marker)&&city.isEmptyBlock(marker.above())){city.setBlock(marker,Blocks.OAK_FENCE.defaultBlockState(),2);NapoliEmploymentMap.sign(city,marker.above(),"P · MOTORINI","Parcheggio "+(i+1),"/compagno motorini");}}}
    private CityPlayerImprovements(){}
}
