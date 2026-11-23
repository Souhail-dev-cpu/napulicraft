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
public final class KitchenStationBlock extends Block {
    private record Session(GlobalPos pos, String token, long expires, List<String> choices, int puzzleStep) {}
    private static final AttachmentType<Session> SESSION = AttachmentRegistry.create(Napuli.id("kitchen_dialog"));
    public final NapoliKitchen.Kind kind;
    public KitchenStationBlock(NapoliKitchen.Kind kind, Properties properties) { super(properties); this.kind = kind; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) open(serverPlayer, pos, 0);
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
        if (kind == NapoliKitchen.Kind.PUZZLE) {
            if (player.getAttachedOrElse(KitchenProgress.PUZZLE_CLAIMED, false)) { say(player, "puzzle_done"); return; }
            if (!player.getAttachedOrElse(KitchenProgress.PUZZLE_READY, false)) { say(player, "puzzle_locked"); return; }
            body.add(new PlainMessage(Component.translatable("napuli.kitchen.puzzle_question", puzzleStep + 1), 300));
            for (String answer : List.of("farina", "impasto", "pizza")) {
                choices.add(answer);
                buttons.add(button(new ItemStack(NapoliKitchen.item(answer)).getHoverName(), token, answer));
            }
        } else {
            body.add(new PlainMessage(Component.translatable("napuli.kitchen.hint"), 300));
            var available = NapoliKitchen.RECIPES.stream().filter(r -> r.station() == kind).toList();
            int page = Math.clamp(puzzleStep, 0, Math.max(0, (available.size() - 1) / 4));
            for (NapoliKitchen.Recipe recipe : available.stream().skip(page * 4L).limit(4).toList()) {
                choices.add(recipe.id());
                Component label = new ItemStack(recipe.output()).getHoverName().copy().append(" ×" + recipe.count());
                buttons.add(button(label, token, recipe.id()));
            }
            if (page > 0) { choices.add("previous"); buttons.add(button(Component.translatable("napuli.kitchen.previous"), token, "previous")); }
            if ((page + 1) * 4 < available.size()) { choices.add("next"); buttons.add(button(Component.translatable("napuli.kitchen.next"), token, "next")); }
        }
        player.setAttached(SESSION, new Session(GlobalPos.of(player.level().dimension(), pos.immutable()), token,
                player.level().getGameTime() + 1200, List.copyOf(choices), puzzleStep));
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(getName(), Optional.empty(), true, false,
                DialogAction.CLOSE, body, List.of()), buttons, Optional.empty(), 2)));
    }

    private static ActionButton button(Component label, String token, String choice) {
        return new ActionButton(new CommonButtonData(label, 190), Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulicucina " + token + " " + choice))));
    }

    private void confirm(ServerPlayer player, BlockPos pos, NapoliKitchen.Recipe recipe, int page) {
        String token = UUID.randomUUID().toString();
        var ingredients = Component.literal("");
        quantities(recipe.ingredients()).forEach((item, count) -> ingredients.append(new ItemStack(item).getHoverName()).append(" ×" + count + "; "));
        List<net.minecraft.server.dialog.body.DialogBody> body = new ArrayList<>();
        body.add(new PlainMessage(ingredients, 300));
        if (recipe.fuel()) body.add(new PlainMessage(Component.translatable("napuli.kitchen.fuel"), 300));
        String choice = "cook_" + recipe.id();
        player.setAttached(SESSION, new Session(GlobalPos.of(player.level().dimension(), pos.immutable()), token,
                player.level().getGameTime() + 1200, List.of(choice, "back"), page));
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(new ItemStack(recipe.output()).getHoverName(), Optional.empty(),
                true, false, DialogAction.CLOSE, body, List.of()), List.of(
                    button(Component.translatable("napuli.kitchen.prepare"), token, choice),
                    button(Component.translatable("napuli.kitchen.back"), token, "back")), Optional.empty(), 1)));
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
        if (choice.equals("next") || choice.equals("previous")) {
            station.open(player, session.pos().pos(), session.puzzleStep() + (choice.equals("next") ? 1 : -1)); return 1;
        }
        if (choice.equals("back")) { station.open(player, session.pos().pos(), session.puzzleStep()); return 1; }
        boolean confirmed = choice.startsWith("cook_");
        String recipeId = confirmed ? choice.substring(5) : choice;
        NapoliKitchen.Recipe recipe = NapoliKitchen.RECIPES.stream().filter(r -> r.id().equals(recipeId) && r.station() == station.kind).findFirst().orElse(null);
        if (recipe == null) return 0;
        if (!confirmed) { station.confirm(player, session.pos().pos(), recipe, session.puzzleStep()); return 1; }
        return prepare(player, recipe) ? 1 : 0;
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
            Item fuel = inventory.stream().filter(s -> !s.isEmpty() && (s.is(Items.CHARCOAL) || s.is(Items.COAL)
                    || s.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.withDefaultNamespace("logs")))
                    || s.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.withDefaultNamespace("planks")))))
                    .map(ItemStack::getItem).findFirst().orElse(Items.COAL);
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

    private static void say(ServerPlayer player, String key, Object... args) { player.sendSystemMessage(Component.translatable("napuli.kitchen." + key, args)); }
}
