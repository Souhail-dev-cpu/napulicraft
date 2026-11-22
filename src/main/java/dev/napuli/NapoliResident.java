package dev.napuli;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class NapoliResident extends PathfinderMob {
    public enum Role {
        GENNARO, CIRO, ENZO, TERESA, LUCIA, MBRIANA, PARTENOPE, ANIMA, VENDOR, TAILOR, BUSKER, PORTER, PASSERBY, ADA, MICHELE, NICO, SALVATORE, ROSA, ANTONIO,
        // added later: keep new roles at the END, saved worlds store the ordinal
        CARABINIERE, CARMELA, GIUSY, SOFIA,
        // the people of the centro storico (v8)
        NONNA, PARCHEGGIATORE, TIFOSO, SCUGNIZZO, PRESEPAIO, PIZZAIOLO,
        GIORGIA_MELONI, MOTOR_DEALER, ARMORER;

        public String id() { return name().toLowerCase(Locale.ROOT); }
        /** the girls you can talk to and date */
        public boolean social() { return this == TERESA || this == LUCIA || this == CARMELA || this == GIUSY || this == SOFIA; }
        public boolean spirit() { return this == MBRIANA || this == PARTENOPE || this == ANIMA; }
        public boolean street() { return (ordinal() >= VENDOR.ordinal() && ordinal() <= ANTONIO.ordinal() && !fighter())
                || this == MOTOR_DEALER || this == ARMORER; }
        public boolean fighter() { return this == NICO || this == ANTONIO; }
        /** everyday Neapolitans with their own little interaction (NapoliPeople) */
        public boolean people() { return ordinal() >= NONNA.ordinal() && ordinal() <= PIZZAIOLO.ordinal(); }
        /** slimmer arms, girl faces and hairstyles in the model */
        public boolean female() { return social() || this == GIORGIA_MELONI || this == NONNA || this == PORTER || this == ADA || this == ROSA || this == MBRIANA || this == PARTENOPE; }
        /** how many different looks this role has (passers-by are many, they must not all look the same) */
        public int variants() { return this == PASSERBY ? 6 : this == CARABINIERE ? 2 : 1; }
        Component displayName() { return Component.translatable("napuli.resident." + id()); }
    }

    private static final EntityDataAccessor<Integer> ROLE =
            SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private int spiritLife = 160;

    public int variant() { return entityData.get(VARIANT); }

    public NapoliResident(EntityType<? extends NapoliResident> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.ATTACK_DAMAGE, 3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.05, true));
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 9, 1));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, Role.GENNARO.ordinal());
        builder.define(VARIANT, 0);
    }

    public Role role() { return Role.values()[entityData.get(ROLE)]; }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("napuli_role", role().ordinal());
        out.putInt("napuli_spirit_life", spiritLife);
        out.putInt("napuli_variant", variant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(ROLE, Math.clamp(in.getIntOr("napuli_role", 0), 0, Role.values().length - 1));
        spiritLife = in.getIntOr("napuli_spirit_life", 160);
        // old saves had no variant: give passers-by a random look instead of all the same one
        int v = in.getIntOr("napuli_variant", -1);
        entityData.set(VARIANT, v >= 0 ? v % role().variants() : random.nextInt(role().variants()));
    }

    static NapoliResident create(ServerLevel level, Role role, BlockPos pos, float yaw) {
        NapoliResident npc = Napuli.RESIDENT.create(level, EntitySpawnReason.EVENT);
        if (npc == null) throw new IllegalStateException("Cannot create " + role);
        npc.entityData.set(ROLE, role.ordinal());
        npc.entityData.set(VARIANT, level.getRandom().nextInt(role.variants()));
        npc.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        npc.setCustomName(role.displayName());
        npc.setHomeTo(pos, npc.wanderRadius());
        if (role == Role.CARABINIERE) {
            npc.setHomeTo(pos, 10);
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Napuli.BATON));
            npc.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
            var speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(0.27);
        }
        if (role.spirit()) {
            npc.setNoAi(true);
            npc.setGlowingTag(true);
        }
        return npc;
    }

    public static NapoliResident spawn(ServerLevel level, Role role, BlockPos pos, float yaw) {
        NapoliResident npc = create(level, role, pos, yaw);
        level.addFreshEntity(npc);
        return npc;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        if (role() == Role.GIORGIA_MELONI) { GiorgiaMeloni.residentTick(this); return; }
        if (role().spirit() && --spiritLife <= 0) discard();
        if (isNoAi() || isPassenger() || role().spirit() || role().fighter()) return;
        if (role() == Role.CARABINIERE && level() instanceof ServerLevel sl && Carabinieri.chasing(sl, this)) return;
        // everybody strolls around their own place (shop, bar, street corner) instead of standing like a statue
        int radius = wanderRadius();
        if (!hasHome()) setHomeTo(blockPosition(), radius); // older saves had no home: their current spot becomes it
        int every = role() == Role.PASSERBY || role() == Role.CARABINIERE ? 60 : 110;
        if ((tickCount + getId()) % every == 0 && getNavigation().isDone() && random.nextFloat() < 0.8f) {
            var next = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, Math.max(2, radius), 1);
            if (next != null && next.y >= 63 && isWithinHome(next)) getNavigation().moveTo(next.x, next.y, next.z, role() == Role.CARABINIERE ? 0.7 : 0.6);
        }
    }

    /** how far from their spot each one walks: shopkeepers stay in the shop, people in the street go further */
    int wanderRadius() {
        return switch (role()) {
            case PASSERBY -> 9;
            case CARABINIERE -> 12;
            case VENDOR, TAILOR, PORTER, PRESEPAIO, PIZZAIOLO -> 3;
            case NONNA -> 2;
            case PARCHEGGIATORE -> 5;
            case TIFOSO, SCUGNIZZO -> 10;
            case BUSKER -> 4;
            case TERESA, LUCIA, CARMELA, GIUSY, SOFIA -> 4;
            default -> 6;
        };
    }

    /** the Neapolitan spawn egg: a random everyday Neapolitan instead of always the first role */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, EntitySpawnReason reason, net.minecraft.world.entity.SpawnGroupData data) {
        var out = super.finalizeSpawn(level, difficulty, reason, data);
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.DISPENSER) {
            Role[] everyday = {Role.PASSERBY, Role.PASSERBY, Role.PASSERBY, Role.NONNA, Role.PARCHEGGIATORE, Role.TIFOSO, Role.SCUGNIZZO, Role.PIZZAIOLO, Role.PRESEPAIO};
            Role r = everyday[random.nextInt(everyday.length)];
            entityData.set(ROLE, r.ordinal());
            entityData.set(VARIANT, random.nextInt(r.variants()));
            setCustomName(r.displayName());
            setHomeTo(blockPosition(), wanderRadius());
        }
        return out;
    }

    /** move an existing person to a new place (used when the map changes) */
    public void relocate(BlockPos pos, float yaw) {
        snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        getNavigation().stop();
        setHomeTo(pos, wanderRadius());
    }

    @Override
    public void rideTick() {
        super.rideTick();
        if (getVehicle() instanceof Motorino bike) {
            float forward = bike.getYRot();
            setYRot(forward);
            setYHeadRot(forward);
            setYBodyRot(forward);
            setXRot(0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return (role().fighter() || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
                && super.hurtServer(level, source, amount);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && role() == Role.GIORGIA_MELONI) {
            if (player instanceof ServerPlayer sp) GiorgiaMeloni.open(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && role() == Role.CARABINIERE) {
            if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable(
                    Carabinieri.isWanted(sp) ? "napuli.cc.talk.wanted" : "napuli.cc.talk." + random.nextInt(4)));
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && role().people()) {
            if (player instanceof ServerPlayer sp) NapoliPeople.talk(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND || (!role().social() && !role().street()))
            return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (role().social()) NapoliDialogue.open(serverPlayer, this);
            else NapoliStreet.open(serverPlayer, this);
        }
        return InteractionResult.SUCCESS;
    }
}
