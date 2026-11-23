package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Server-authoritative recipes consume ingredients atomically and return crafting containers. */
public final class KitchenStationBlock extends net.minecraft.world.level.block.BaseEntityBlock {
    private record Session(GlobalPos pos, String token, long expires, List<String> choices, int puzzleStep) {}
    private static final AttachmentType<Session> SESSION = AttachmentRegistry.create(Napuli.id("kitchen_dialog"));
    public final NapoliKitchen.Kind kind;
    public KitchenStationBlock(NapoliKitchen.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));
    }

    boolean heated() {
        return kind == NapoliKitchen.Kind.OVEN || kind == NapoliKitchen.Kind.FRY;
    }

    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
    }

    @Override public KitchenOvenEntity newBlockEntity(BlockPos pos, BlockState state) {
        return heated() ? new KitchenOvenEntity(pos, state) : null;
    }

    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
                    net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !heated()) return null;
        return createTickerHelper(type, NapoliKitchen.OVEN_ENTITY, (world, pos, block, oven) ->
                net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(server, pos, block, oven));
    }

    private void openMenu(ServerPlayer player, BlockPos pos, BlockState state) {
        var level = player.level();
        var type = heated() ? NapoliKitchen.cookingType(kind)
                : net.minecraft.world.item.crafting.RecipeType.CRAFTING;
        player.awardRecipes(level.getServer().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.value().getType() == type && recipe.id().identifier().getNamespace().equals("napuli"))
                .toList());
        if (heated()) {
            if (!(level.getBlockEntity(pos) instanceof KitchenOvenEntity)) level.setBlockEntity(new KitchenOvenEntity(pos, state));
            if (level.getBlockEntity(pos) instanceof KitchenOvenEntity oven) player.openMenu(oven);
        } else {
            var access = net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos);
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, owner) -> new KitchenMenus.Grid(id, inventory, access, kind), getName()));
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (kind == NapoliKitchen.Kind.PUZZLE) open(serverPlayer, pos, 0);
            else openMenu(serverPlayer, pos, state);
        }
        return InteractionResult.SUCCESS;
    }

    static void commands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("napulicucina").then(Commands.argument("sessione", StringArgumentType.word())
                .then(Commands.argument("ricetta", StringArgumentType.word()).executes(context -> choose(context.getSource().getPlayerOrException(),
                        StringArgumentType.getString(context, "sessione"), StringArgumentType.getString(context, "ricetta"))))));
    }

    void open(ServerPlayer player, BlockPos pos, int puzzleStep) {
        if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 36) return;
        List<String> choices = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        String token = UUID.randomUUID().toString();
        List<net.minecraft.server.dialog.body.DialogBody> body = new ArrayList<>();
        if (player.getAttachedOrElse(KitchenProgress.PUZZLE_CLAIMED, false)) { say(player, "puzzle_done"); return; }
        if (!player.getAttachedOrElse(KitchenProgress.PUZZLE_READY, false)) { say(player, "puzzle_locked"); return; }
        body.add(new PlainMessage(Component.translatable("napuli.kitchen.puzzle_question", puzzleStep + 1), 300));
        for (String answer : List.of("farina", "impasto", "pizza")) {
            choices.add(answer);
            buttons.add(button(new ItemStack(NapoliKitchen.item(answer)).getHoverName(), token, answer));
        }
        player.setAttached(SESSION, new Session(GlobalPos.of(player.level().dimension(), pos.immutable()), token,
                player.level().getGameTime() + 1200, List.copyOf(choices), puzzleStep));
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(getName(), Optional.empty(), true, false,
                DialogAction.CLOSE, body, List.of()), buttons, Optional.empty(), 2)));
    }

    private static ActionButton button(Component label, String token, String choice) {
        return new ActionButton(new CommonButtonData(label, 190), Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulicucina " + token + " " + choice))));
    }

    static int choose(ServerPlayer player, String token, String choice) {
        Session session = player.getAttached(SESSION);
        if (session == null || !session.token().equals(token) || !session.choices().contains(choice)) return 0;
        player.removeAttached(SESSION);
        if (player.level().dimension() != session.pos().dimension() || !player.isAlive() || player.isSpectator()
                || player.level().getGameTime() > session.expires() || session.pos().pos().distToCenterSqr(player.position()) > 36
                || !(player.level().getBlockState(session.pos().pos()).getBlock() instanceof KitchenStationBlock station)) return 0;
        if (station.kind == NapoliKitchen.Kind.PUZZLE) {
            if (!player.getAttachedOrElse(KitchenProgress.PUZZLE_READY, false) || player.getAttachedOrElse(KitchenProgress.PUZZLE_CLAIMED, false)) return 0;
            if (!List.of("farina", "impasto", "pizza").get(session.puzzleStep()).equals(choice)) { say(player, "puzzle_wrong"); return 0; }
            if (session.puzzleStep() < 2) { station.open(player, session.pos().pos(), session.puzzleStep() + 1); return 1; }
            player.setAttached(KitchenProgress.PUZZLE_CLAIMED, true);
            player.removeAttached(KitchenProgress.PUZZLE_READY);
            NapoliKitchen.give(player, new ItemStack(Items.EMERALD, 3));
            say(player, "puzzle_done");
            return 1;
        }
        return 0;
    }

    static Map<Item, Integer> quantities(List<Item> items) {
        Map<Item, Integer> counts = new java.util.LinkedHashMap<>();
        for (Item item : items) counts.merge(item, 1, Integer::sum);
        return counts;
    }

    /** Work on copies first: insufficient items or space never partially consumes a recipe. */
    public static boolean prepare(ServerPlayer player, NapoliKitchen.Recipe recipe) {
        if (!player.isAlive() || player.isSpectator()) return false;
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < 36; i++) inventory.add(player.getInventory().getItem(i).copy());
        Map<Item, Integer> required = quantities(recipe.ingredients());
        if (recipe.fuel()) {
            Item fuel = inventory.stream().filter(stack -> KitchenMenus.fuel(recipe.station(), stack))
                    .map(ItemStack::getItem).findFirst().orElse(null);
            if (fuel == null) { say(player, "missing"); return false; }
            required.merge(fuel, 1, Integer::sum);
        }
        List<ItemStack> remainders = new ArrayList<>();
        for (var entry : required.entrySet()) {
            int remaining = entry.getValue();
            for (ItemStack stack : inventory) {
                if (!stack.is(entry.getKey())) continue;
                int used = Math.min(remaining, stack.getCount());
                stack.shrink(used); remaining -= used;
                for (int i = 0; i < used; i++) {
                    var template = entry.getKey().getCraftingRemainder();
                    if (template == null) continue;
                    ItemStack remainder = template.create();
                    if (!remainder.isEmpty()) remainders.add(remainder);
                }
                if (remaining == 0) break;
            }
            if (remaining != 0) { say(player, "missing"); return false; }
        }
        ItemStack result = new ItemStack(recipe.output(), recipe.count());
        List<ItemStack> additions = new ArrayList<>(remainders); additions.add(result.copy());
        for (ItemStack stack : additions) if (!insert(inventory, stack)) { say(player, "full"); return false; }
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, inventory.get(i));
        player.getInventory().setChanged();
        result.onCraftedBy(player, recipe.count());
        say(player, "done", result.getHoverName(), recipe.count());
        return true;
    }

    static boolean insert(List<ItemStack> inventory, ItemStack addition) {
        for (ItemStack stack : inventory) if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, addition)) {
            int moved = Math.min(addition.getCount(), stack.getMaxStackSize() - stack.getCount());
            stack.grow(moved); addition.shrink(moved);
            if (addition.isEmpty()) return true;
        }
        for (int i = 0; i < inventory.size(); i++) if (inventory.get(i).isEmpty()) {
            int moved = Math.min(addition.getMaxStackSize(), addition.getCount());
            inventory.set(i, addition.copyWithCount(moved)); addition.shrink(moved);
            if (addition.isEmpty()) return true;
        }
        return addition.isEmpty();
    }

    private static void say(ServerPlayer player, String key, Object... args) { NapoliNotice.show(player, Component.translatable("napuli.kitchen." + key, args)); }
}
