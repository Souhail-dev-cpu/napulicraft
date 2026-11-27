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
        GIORGIA_MELONI, PEPPE_FETISH, MOTOR_DEALER, ARMORER,
        BUCATO, TURISTA, PESCATORE, CUSTODE_TUNNEL, CARTOMANTE, MERCATANTE, MUSICISTA, GUARDIANO_PRESEPE,
        FAMILY_MOTHER, FAMILY_FATHER, FAMILY_CHILD, FAMILY_CHILD_TWO, CIVIC_WOMAN, CIVIC_MAN, EMPLOYMENT_CLERK, SHIFT_LEAD, SHIFT_WORKER, FOOTBALL_CLERK, FOOTBALL_COACH;

        boolean civic() { return this == CIVIC_WOMAN || this == CIVIC_MAN; }

        public String id() { return name().toLowerCase(Locale.ROOT); }
        /** the girls you can talk to and date */
        public boolean social() { return this == TERESA || this == LUCIA || this == CARMELA || this == GIUSY || this == SOFIA; }
        public boolean spirit() { return this == MBRIANA || this == PARTENOPE || this == ANIMA; }
        public boolean street() { return (ordinal() >= VENDOR.ordinal() && ordinal() <= ANTONIO.ordinal() && !fighter())
                || this == PEPPE_FETISH || this == MOTOR_DEALER || this == ARMORER; }
        public boolean fighter() { return this == NICO || this == ANTONIO; }
        /** everyday Neapolitans with their own little interaction (NapoliPeople) */
        public boolean people() { return ordinal() >= NONNA.ordinal() && ordinal() <= PIZZAIOLO.ordinal(); }
        /** slimmer arms, girl faces and hairstyles in the model */
        public boolean female() { return social() || this == GIORGIA_MELONI || this == NONNA || this == PORTER || this == ADA || this == ROSA || this == MBRIANA || this == PARTENOPE || this == BUCATO || this == CARTOMANTE || this == FAMILY_MOTHER || this == FAMILY_CHILD_TWO || this == CIVIC_WOMAN || this == EMPLOYMENT_CLERK; }
        /** how many different looks this role has (passers-by are many, they must not all look the same) */
        public int variants() { return civic() ? 12 : this == PASSERBY ? 18 : this == SHIFT_LEAD || this == SHIFT_WORKER ? 3 : this == CARABINIERE ? 2 : 1; }
        Component displayName() { return Component.translatable("napuli.resident." + id()); }
    }

    private static final EntityDataAccessor<Integer> ROLE =
            SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GESTURE = SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VITALITY = SynchedEntityData.defineId(NapoliResident.class, EntityDataSerializers.INT);
    private long gestureUntil;
    private int spiritLife = 160;

    void setCivicAge(int age) {
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(0.55 + 0.45 * Math.clamp(age, 0, NapoliCivicLife.ADULT_AGE) / NapoliCivicLife.ADULT_AGE);
    }

    public NapoliHouseholds.Activity activity() { return NapoliHouseholds.Activity.values()[entityData.get(ACTIVITY)]; }
    void setActivity(NapoliHouseholds.Activity activity) { entityData.set(ACTIVITY, activity.ordinal()); }
    public CityGestures.Gesture gesture() { return CityGestures.Gesture.values()[entityData.get(GESTURE)]; }
    public int mood() { return entityData.get(MOOD); }
    public int vitality() { return entityData.get(VITALITY); }
    long gestureUntil() { return gestureUntil; }
    void setGesture(CityGestures.Gesture gesture) { entityData.set(GESTURE,gesture.ordinal()); }
    void animate(CityGestures.Gesture gesture,int duration) { setGesture(gesture);gestureUntil=level().getGameTime()+duration; }
    void setExpression(int mood,int vitality) { entityData.set(MOOD,Math.clamp(mood,0,100));entityData.set(VITALITY,Math.clamp(vitality,0,100)); }
    private BlockPos dayPost;
    private BlockPos nightPost;
    private boolean returningHome;
    private int commuteTicks;
    private long commuteDue;

    public int variant() { return entityData.get(VARIANT); }
    void setEmploymentAppearance(Role role, int look) {
        entityData.set(ROLE, role.ordinal());
        entityData.set(VARIANT, look);
    }
    void setCivicAppearance(int variant) { entityData.set(VARIANT,Math.floorMod(variant,12)); }

    public NapoliResident(EntityType<? extends NapoliResident> type, Level level) {
        super(type, level);
        moveControl = new ResidentNavigation.LadderMoveControl(this);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.UNPASSABLE_RAIL,2);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.RAIL,2);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.29).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.STEP_HEIGHT, 1);
    }

    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new ResidentNavigation(this, level);
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
        builder.define(ACTIVITY, 0);
        builder.define(GESTURE, 0);builder.define(MOOD,70);builder.define(VITALITY,80);
    }

    public Role role() { return Role.values()[entityData.get(ROLE)]; }
    @Override public boolean shouldBeSaved() { return !PolicePatrols.managed(this)&&!PhoneServices.temporary(this)&&!CityJourneys.managed(this)&&super.shouldBeSaved(); }
    @Override public boolean saveAsPassenger(ValueOutput out) { return !PolicePatrols.managed(this)&&!PhoneServices.temporary(this)&&!CityJourneys.managed(this)&&super.saveAsPassenger(out); }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("napuli_role", role().ordinal());
        out.putInt("napuli_spirit_life", spiritLife);
        out.putInt("napuli_variant", variant());
        out.putBoolean("napuli_passers_v2", true);
        if (dayPost != null) out.store("napuli_day_post", BlockPos.CODEC, dayPost);
        out.putBoolean("napuli_return_home", returningHome);
        out.putLong("napuli_commute_due",commuteDue);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(ROLE, Math.clamp(in.getIntOr("napuli_role", 0), 0, Role.values().length - 1));
        spiritLife = in.getIntOr("napuli_spirit_life", 160);
        dayPost = in.read("napuli_day_post", BlockPos.CODEC).orElse(null);
        returningHome = in.getBooleanOr("napuli_return_home", false);
        commuteDue=in.getLongOr("napuli_commute_due",0);
        // old saves had no variant: give passers-by a random look instead of all the same one
        int v = in.getIntOr("napuli_variant", -1);
        if (role() == Role.PASSERBY && !in.getBooleanOr("napuli_passers_v2", false))
            v = Math.floorMod(getUUID().hashCode(), Role.PASSERBY.variants());
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
        npc.dayPost = pos.immutable();
        if (role == Role.CARABINIERE) {
            npc.setHomeTo(pos, 10);
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Napuli.BATON));
            npc.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
            var speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(0.32);
        }
        if (role == Role.PEPPE_FETISH) {
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Napuli.CALZINI_SPORCHI));
            npc.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
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
        CityGestures.tick(this,(ServerLevel)level());
        if (PhoneServices.tickActor(this, (ServerLevel)level())) return;
        if (role() == Role.GIORGIA_MELONI) { GiorgiaMeloni.residentTick(this); return; }
        if (role().spirit() && --spiritLife <= 0) discard();
        if ((getVehicle() instanceof CityVehicle||CityMovement.isSeat(getVehicle())) && level() instanceof ServerLevel city && role().civic()) {
            CitySimulation.tickEntity(this, city);
            return;
        }
        if (isNoAi() || isPassenger() || role().spirit() || role().fighter()) return;
        if (role() == Role.CARABINIERE && level() instanceof ServerLevel sl && Carabinieri.chasing(sl, this)) return;
        if (level() instanceof ServerLevel city) {
            if(CityPublicOrder.tickEntity(this,city))return;
            if(PolicePatrols.tick(this,city))return;
            if (role().civic() || NapoliCivicLife.profile(city,getUUID())!=null) { NapoliCivicLife.commute(this, city); return; }
            if (NapoliHouseholds.family(role())) { NapoliHouseholds.tick(this, city); return; }
            if (runResidentialSchedule(city)) return;
        }
        // everybody strolls around their own place (shop, bar, street corner) instead of standing like a statue
        int radius = wanderRadius();
        if (radius == 0) return;
        if (!hasHome()) setHomeTo(blockPosition(), radius); // older saves had no home: their current spot becomes it
        int every = role() == Role.PASSERBY || role() == Role.CARABINIERE ? 40 : 70;
        if ((tickCount + getId()) % every == 0 && getNavigation().isDone() && random.nextFloat() < 0.8f) {
            var next = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, Math.max(2, radius), 1);
            if (next != null && next.y >= 63 && isWithinHome(next)) getNavigation().moveTo(next.x, next.y, next.z, 1.0);
        }
    }

    /** A pursuit can end far outside the old home restriction. Patrol from the new position. */
    void resumePatrol() {
        if (role() != Role.CARABINIERE || isNoAi() || Carabinieri.npcJailed(getUUID())) return;
        setHomeTo(blockPosition(), wanderRadius());
        for (int i = 0; i < 8; i++) {
            var next = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, 8, 1);
            if (next != null && getNavigation().moveTo(next.x, next.y, next.z, 1.0)) return;
        }
    }

    private boolean runResidentialSchedule(ServerLevel city) {
        if (city.dimension() != NapoliMap.LEVEL || !hasResidentialSchedule()) return false;
        if (dayPost == null) dayPost = (hasHome() ? getHomePosition() : blockPosition()).immutable();
        int offset = Math.floorMod(getUUID().hashCode(), 400);
        long time = Math.floorMod(city.getOverworldClockTime(), 24000);
        boolean night = time >= 12600 + offset || time < 400 + offset;
        if (night != returningHome) {
            returningHome = night;
            nightPost = null;
            commuteTicks = 160;
            commuteDue=0;
            getNavigation().stop();
        }
        if (!night && commuteTicks == 0&&dayPost.distToCenterSqr(position())<=1.6) return false;
        if ((tickCount + getId()) % 20 == 0) {
            if (night && nightPost == null) nightPost = NapoliResidentHomes.destination(city, dayPost, offset);
            BlockPos destination = night ? nightPost : dayPost;
            if(destination!=null&&commuteDue==0)commuteDue=city.getGameTime()+(long)Math.ceil(Math.sqrt(destination.distToCenterSqr(position()))/.13);
            if(destination!=null&&!HousingCapacity.canEnter(city,destination,getUUID())) {getNavigation().stop();return true;}
            if (destination != null && destination.distToCenterSqr(position()) > 1.6) {
                if (commuteTicks > 0) {
                    getNavigation().moveTo(destination.getX() + .5, destination.getY(), destination.getZ() + .5, 0.85);
                    commuteTicks = Math.max(0, commuteTicks - 20);
                } else {
                    getNavigation().stop();
                    if (NapoliResidentHomes.walkable(city, destination)&&city.getGameTime()>=commuteDue&&city.players().stream().noneMatch(p->p.distanceToSqr(this)<96*96))
                        snapTo(destination.getX() + .5, destination.getY(), destination.getZ() + .5, getYRot(), 0);
                    else CityDomestic.move(this,CityRoutines.localGoal(city,this,destination));
                }
            } else {
                getNavigation().stop();
                commuteTicks = 0;
                commuteDue=0;
            }
        }
        // On the tick after the final path attempt, finish the dawn journey if needed.
        if (!night && commuteTicks == 0 && dayPost.distToCenterSqr(position()) > 1.6
                && NapoliResidentHomes.walkable(city, dayPost)&&city.getGameTime()>=commuteDue&&city.players().stream().noneMatch(p->p.distanceToSqr(this)<96*96)) {
            getNavigation().stop();
            snapTo(dayPost.getX() + .5, dayPost.getY(), dayPost.getZ() + .5, getYRot(), 0);
        }
        return true;
    }

    private boolean hasResidentialSchedule() {
        return switch (role()) {
            case PASSERBY, NONNA, TIFOSO, SCUGNIZZO, PARCHEGGIATORE, VENDOR, TAILOR, BUSKER,
                 PIZZAIOLO, PRESEPAIO, PESCATORE, MERCATANTE, MUSICISTA, CARTOMANTE, GUARDIANO_PRESEPE -> true;
            default -> false;
        };
    }

    /** how far from their spot each one walks: shopkeepers stay in the shop, people in the street go further */
    int wanderRadius() {
        return switch (role()) {
            case PASSERBY -> 9;
            case CARABINIERE -> 12;
            case VENDOR, TAILOR, PORTER, PRESEPAIO, PIZZAIOLO, PEPPE_FETISH -> 3;
            case NONNA -> 2;
            case BUCATO, CUSTODE_TUNNEL, MUSICISTA -> 0;
            case TURISTA, PESCATORE, CARTOMANTE, MERCATANTE, GUARDIANO_PRESEPE -> 2;
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
        dayPost = pos.immutable();
        nightPost = null;
        returningHome = false;
        commuteTicks = 0;
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
        boolean hurt=(role().fighter() || role().civic() || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
                && super.hurtServer(level, source, amount);
        if(hurt&&source.getEntity() instanceof ServerPlayer p&&NapoliCivicLife.profile(level,getUUID())!=null)CitySocial.harm(level,getUUID(),p.getUUID());
        return hurt;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && PhoneServices.interact(this, player)) return InteractionResult.SUCCESS;
        if (hand == InteractionHand.MAIN_HAND && FootballPractice.interact(this, player)) return InteractionResult.SUCCESS;
        if (hand == InteractionHand.MAIN_HAND && NapoliEmployment.interact(this, player)) return InteractionResult.SUCCESS;
        if (hand == InteractionHand.MAIN_HAND && (role().civic() || level() instanceof ServerLevel sl && NapoliCivicLife.profile(sl,getUUID())!=null)) {
            if (player instanceof ServerPlayer sp) NapoliCivicLife.describe(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && NapoliHouseholds.family(role())) {
            if (player instanceof ServerPlayer sp) NpcSpeech.say(sp,this,Component.translatable(
                    "napuli.family." + activity().name().toLowerCase(Locale.ROOT)));
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && role().spirit()) {
            if (isAlive() && player instanceof ServerPlayer sp) NapoliScenes.answerSpirit(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && role() == Role.GIORGIA_MELONI) {
            if (player instanceof ServerPlayer sp) GiorgiaMeloni.open(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && role() == Role.CARABINIERE) {
            if (player instanceof ServerPlayer sp) NpcSpeech.say(sp,this,Component.translatable(
                    Carabinieri.isWanted(sp) ? "napuli.cc.talk.wanted" : "napuli.cc.talk." + random.nextInt(4)));
            return InteractionResult.SUCCESS;
        }
        NapoliLocalJobs.Job job = NapoliLocalJobs.job(role());
        if (hand == InteractionHand.MAIN_HAND && job != null && !(role() == Role.PIZZAIOLO && player.isShiftKeyDown())) {
            if (player instanceof ServerPlayer sp) NapoliLocalJobs.open(sp, this, job);
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
