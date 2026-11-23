package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Revocable hospitality is separate from ownership, tenancy and permanent resident capacity. */
final class HouseGuests {
    record Invitation(UUID host, UUID guest, String name, String occasion) {
        Invitation(UUID host,UUID guest,String name){this(host,guest,name,"");}
        static final Codec<Invitation> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("host").forGetter(Invitation::host),
                UUIDUtil.CODEC.fieldOf("guest").forGetter(Invitation::guest),
                Codec.STRING.fieldOf("name").forGetter(Invitation::name),Codec.STRING.optionalFieldOf("occasion","").forGetter(Invitation::occasion)).apply(i, Invitation::new));
    }
    static final AttachmentType<Map<String, List<Invitation>>> INVITATIONS = AttachmentRegistry
            .<Map<String, List<Invitation>>>builder().persistent(Codec.unboundedMap(Codec.STRING, Invitation.CODEC.listOf()))
            .buildAndRegister(Napuli.id("house_guests"));

    private static NapoliMergellina.House house(String id) {
        return CityLivingHomes.find(id);
    }

    static boolean host(ServerLevel city, String house, UUID id) {
        var home = house(house);
        return home != null && (id.equals(NapoliProperties.owner(city, home)) || CityHousingFinance.tenant(city, house, id));
    }

    static boolean invited(ServerLevel city, String home, UUID id) {
        for (var invitation : city.getAttachedOrElse(INVITATIONS, Map.of()).getOrDefault(home, List.of()))
            if (invitation.guest().equals(id) && host(city, home, invitation.host())) return true;
        return false;
    }

    static boolean capacityExempt(ServerLevel city, String home, UUID id) {
        if (!invited(city, home, id)) return false;
        var citizen = NapoliCivicLife.profile(city, id);
        return !host(city, home, id) && (citizen == null || !citizen.property().equals(home));
    }

    static boolean grant(ServerLevel city, String home, UUID owner, Entity guest) {
        if (!host(city, home, owner) || !guest.isAlive() || owner.equals(guest.getUUID())
                || !(guest instanceof ServerPlayer || guest instanceof NapoliResident)) return false;
        var all = new HashMap<>(city.getAttachedOrElse(INVITATIONS, Map.of()));
        var guests = new ArrayList<>(all.getOrDefault(home, List.of()));
        guests.removeIf(g -> g.guest().equals(guest.getUUID()) || !host(city, home, g.host()));
        guests.add(new Invitation(owner, guest.getUUID(), guest.getName().getString()));
        all.put(home, List.copyOf(guests)); city.setAttached(INVITATIONS, Map.copyOf(all));
        return true;
    }

    static boolean revoke(ServerLevel city, String home, UUID owner, UUID guest) {
        if (!host(city, home, owner)) return false;
        var all = new HashMap<>(city.getAttachedOrElse(INVITATIONS, Map.of()));
        var guests = new ArrayList<>(all.getOrDefault(home, List.of()));
        if (!guests.removeIf(g -> g.guest().equals(guest))) return false;
        if (guests.isEmpty()) all.remove(home); else all.put(home, List.copyOf(guests));
        city.setAttached(INVITATIONS, Map.copyOf(all)); return true;
    }
    static boolean grantForEvent(ServerLevel city,String home,UUID owner,UUID guest,String name,String occasion){
        if(!host(city,home,owner)||owner.equals(guest))return false;
        if(invited(city,home,guest))return true;
        if(NapoliCivicLife.profile(city,guest)==null&&city.getServer().getPlayerList().getPlayer(guest)==null)return false;
        var all=new HashMap<>(city.getAttachedOrElse(INVITATIONS,Map.of()));var list=new ArrayList<>(all.getOrDefault(home,List.of()));
        list.removeIf(g->g.guest().equals(guest));list.add(new Invitation(owner,guest,name,occasion));all.put(home,List.copyOf(list));city.setAttached(INVITATIONS,Map.copyOf(all));return true;
    }
    static void revokeEvent(ServerLevel city,String home,UUID owner,UUID guest,String occasion){
        var all=new HashMap<>(city.getAttachedOrElse(INVITATIONS,Map.of()));var list=new ArrayList<>(all.getOrDefault(home,List.of()));
        if(!list.removeIf(g->g.host().equals(owner)&&g.guest().equals(guest)&&g.occasion().equals(occasion)))return;
        if(list.isEmpty())all.remove(home);else all.put(home,List.copyOf(list));city.setAttached(INVITATIONS,Map.copyOf(all));
    }

    static void clear(ServerLevel city, String home) {
        var all = city.getAttachedOrElse(INVITATIONS, Map.of());
        if (!all.containsKey(home)) return;
        var updated = new HashMap<>(all); updated.remove(home); city.setAttached(INVITATIONS, Map.copyOf(updated));
    }

    static String visitingHome(ServerLevel city, UUID citizen) {
        for (var entry : city.getAttachedOrElse(INVITATIONS, Map.of()).entrySet())
            if (capacityExempt(city, entry.getKey(), citizen)) return entry.getKey();
        return "";
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("casa")
                        .then(Commands.literal("ospiti").then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> menu(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("invita").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("ospite", StringArgumentType.word()).suggests((c, builder) -> {
                                    for (var p : c.getSource().getServer().getPlayerList().getPlayers()) builder.suggest(p.getName().getString());
                                    return builder.buildFuture();
                                }).executes(c -> invite(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "ospite"))))))
                        .then(Commands.literal("revoca").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("ospite", StringArgumentType.word()).executes(c -> remove(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "ospite"))))))
                        .then(Commands.literal("fineospiti").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                            var p = c.getSource().getPlayerOrException(); var id = StringArgumentType.getString(c, "id");
                            if (!mayManage(p, id)) return 0; clear(p.level(), id);
                            p.sendSystemMessage(Component.translatable("napuli.guest.ended")); return 1;
                        })))));
    }

    private static boolean mayManage(ServerPlayer p, String home) {
        boolean allowed = p.level().dimension() == NapoliMap.LEVEL && p.isAlive() && !p.isSpectator()
                && !Carabinieri.isJailed(p) && host(p.level(), home, p.getUUID());
        if (!allowed) p.sendSystemMessage(Component.translatable("napuli.guest.owner_only"));
        return allowed;
    }

    private static Entity target(ServerPlayer host, String reference) {
        var player = host.level().getServer().getPlayerList().getPlayerByName(reference);
        if (player != null) return player;
        try {
            var id = UUID.fromString(reference);
            var online = host.level().getServer().getPlayerList().getPlayer(id);
            return online == null ? host.level().getEntity(id) : online;
        } catch (IllegalArgumentException ignored) { return null; }
    }

    private static int invite(ServerPlayer owner, String id, String reference) {
        if (!mayManage(owner, id)) return 0;
        var guest = target(owner, reference);
        if (guest == null || !grant(owner.level(), id, owner.getUUID(), guest)) {
            owner.sendSystemMessage(Component.translatable("napuli.guest.not_found")); return 0;
        }
        owner.sendSystemMessage(Component.translatable("napuli.guest.invited", guest.getName(), house(id).address()));
        if (guest instanceof ServerPlayer player) {
            String command = player.level().dimension() == NapoliMap.LEVEL ? "/casa vai " + id : "/napoli";
            player.sendSystemMessage(Component.translatable("napuli.guest.received", owner.getName(), house(id).address())
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command))));
        }
        return 1;
    }

    private static int remove(ServerPlayer owner, String id, String reference) {
        if (!mayManage(owner, id)) return 0;
        var invitation = owner.level().getAttachedOrElse(INVITATIONS, Map.of()).getOrDefault(id, List.of()).stream()
                .filter(g -> g.name().equalsIgnoreCase(reference) || g.guest().toString().equals(reference)).findFirst().orElse(null);
        if (invitation == null || !revoke(owner.level(), id, owner.getUUID(), invitation.guest())) return 0;
        owner.sendSystemMessage(Component.translatable("napuli.guest.revoked", invitation.name())); return 1;
    }

    static int menu(ServerPlayer owner, String id) {
        if (!mayManage(owner, id)) return 0;
        var choices = new LinkedHashMap<UUID, String>();
        for (var p : owner.level().getServer().getPlayerList().getPlayers())
            if (!p.getUUID().equals(owner.getUUID())) choices.put(p.getUUID(), p.getName().getString());
        for (var npc : owner.level().getEntitiesOfClass(NapoliResident.class, owner.getBoundingBox().inflate(32), n -> n.role().civic()))
            choices.put(npc.getUUID(), npc.getName().getString());
        for (var guest : owner.level().getAttachedOrElse(INVITATIONS, Map.of()).getOrDefault(id, List.of())) choices.put(guest.guest(), guest.name());
        var buttons = new ArrayList<ActionButton>();
        for (var entry : choices.entrySet()) {
            boolean invited = invited(owner.level(), id, entry.getKey());
            buttons.add(button(Component.translatable(invited ? "napuli.guest.remove" : "napuli.guest.add", entry.getValue()),
                    "/casa " + (invited ? "revoca " : "invita ") + id + " " + entry.getKey()));
        }
        buttons.add(button(Component.translatable("napuli.guest.end"), "/casa fineospiti " + id));
        owner.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.translatable("napuli.guest.title"),
                Optional.empty(), true, false, DialogAction.CLOSE,
                List.of(new PlainMessage(Component.translatable("napuli.guest.help"), 270)), List.of()), buttons, Optional.empty(), 1)));
        return 1;
    }

    private static ActionButton button(Component label, String command) {
        return new ActionButton(new CommonButtonData(label, 250), Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }

    private HouseGuests() {}
}
