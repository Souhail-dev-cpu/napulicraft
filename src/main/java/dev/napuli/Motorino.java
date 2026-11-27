package dev.napuli;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 'O Motorino. Steer with the mouse, W to go. Up to THREE people on it, like in real life.
 * Boarding order determines the driver, middle seat and rear seat on both sides.
 * Sneak + right click to pick it up when empty. Riding it turns the radio on.
 */
public class Motorino extends Mob {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CITIZEN=net.minecraft.network.syncher.SynchedEntityData.defineId(Motorino.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> POLICE=net.minecraft.network.syncher.SynchedEntityData.defineId(Motorino.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    public boolean policeVehicle(){return entityData.get(POLICE);}
    void configurePolice(){entityData.set(POLICE,true);setNoAi(true);getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.2);}
    boolean residentOwned(){return entityData.get(CITIZEN);}
    void configureResident(){entityData.set(CITIZEN,true);setNoAi(true);}
    @Override public boolean shouldBeSaved(){return (!PoliceVehicles.managed(this)||PoliceVehicles.saved(this))&&!CityMobility.managed(this)&&super.shouldBeSaved();}
    @Override public boolean saveAsPassenger(net.minecraft.world.level.storage.ValueOutput out){return (!PoliceVehicles.managed(this)||PoliceVehicles.saved(this))&&!CityMobility.managed(this)&&super.saveAsPassenger(out);}
	public static final int MAX_PASSENGERS = 3;
	private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> STATION =
			net.minecraft.network.syncher.SynchedEntityData.defineId(Motorino.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> PARADE =
			net.minecraft.network.syncher.SynchedEntityData.defineId(Motorino.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
	private int paradeTicks;
	private int lastStation = 0;
	// the carabiniere who stopped the parade (boys without a helmet) and how long the stop has lasted
	private NapoliResident stoppedBy;
	private int stopTicks;

	public void startParade() {
		paradeTicks = 200;
		entityData.set(PARADE, true);
		setNoAi(true);
		setStation(Radio.PARADE);
	}

	public boolean inParade() { return entityData.get(PARADE); }
	public int station() { return this.entityData.get(STATION); }
	int savedStation() { return lastStation; }
	public void setStation(int s) {
		if (s >= 0 && s != Radio.PARADE) lastStation = s;
		this.entityData.set(STATION, s);
	}
	public boolean radioOn() { return station() != Radio.OFF; }

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(STATION, Radio.OFF);
		b.define(PARADE, false);
		b.define(CITIZEN,false);
        b.define(POLICE,false);
	}

	/** every rider wears something on the head */
	boolean ridersHelmeted() {
		for (Entity e : getPassengers())
			if (e instanceof LivingEntity l && l.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) return false;
		return true;
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("napuli_parade_ticks", paradeTicks);
		out.putInt("napuli_station", station());
		out.putInt("napuli_last_station", lastStation);
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
		super.readAdditionalSaveData(in);
		paradeTicks = in.getIntOr("napuli_parade_ticks", 0);
		entityData.set(PARADE, paradeTicks > 0);
		lastStation = in.getIntOr("napuli_last_station", 0);
		this.entityData.set(STATION, in.getIntOr("napuli_station", Radio.OFF));
	}

	@Override
	public void aiStep() {
		super.aiStep();
        if(level() instanceof ServerLevel city&&PoliceVehicles.drive(this,city))return;
		if(level() instanceof ServerLevel city&&CityMobility.drive(this,city))return;
		if (!level().isClientSide() && !inParade() && tickCount % 20 == 0 && !ScooterGarage.observe(this, getFirstPassenger() instanceof ServerPlayer p ? p : null)) return;
		if (level().isClientSide() || paradeTicks <= 0) return;
		ServerLevel sl = (ServerLevel) level();
		// three on a scooter and no helmet, right in front of a carabiniere: they get stopped
		if (stoppedBy == null && tickCount % 10 == 0 && !ridersHelmeted()) {
			for (NapoliResident cc : Carabinieri.carabinieri(sl, blockPosition(), 22)) {
				if (!cc.hasLineOfSight(this)) continue;
				stoppedBy = cc;
				setStation(Radio.OFF);  // the music stops dead
				Carabinieri.stopParade(sl, this, cc);
				break;
			}
		}
		if (stoppedBy != null) {
			stopTicks++;
			if (stopTicks % 10 == 1) stoppedBy.getNavigation().moveTo(this, 1.25);
			stoppedBy.getLookControl().setLookAt(this, 30, 30);
			for (Entity e : getPassengers()) if (e instanceof Mob m) m.getLookControl().setLookAt(stoppedBy, 30, 30);
			if (stopTicks > 200 || !stoppedBy.isAlive() || (stopTicks > 30 && stoppedBy.distanceToSqr(this) < 3.2 * 3.2)) {
				Carabinieri.jailRiders(sl, this, stoppedBy);
				paradeTicks = 0;
				discard();  // the scooter is seized
			}
			return;
		}
		if (--paradeTicks == 0) {
			// the boys are gone: the scooter takes its song with it (the sound is bound to the scooter on the clients)
			NapoliScenes.removeRiders(this);
			discard();
			return;
		}
		Vec3 direction = Vec3.directionFromRotation(0, getYRot());
		move(net.minecraft.world.entity.MoverType.SELF, direction.scale(0.19));
	}

	public Motorino(EntityType<? extends Motorino> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if(CityMobility.managed(this)||PoliceVehicles.managed(this))return InteractionResult.PASS;
		if (inParade()) return InteractionResult.PASS;
		if (player instanceof ServerPlayer p && !ScooterGarage.observe(this,p)) return InteractionResult.FAIL;
		if (player.isSecondaryUseActive()) {
			if (isVehicle()) {
				if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("napuli.motorino.occupied"));
				return InteractionResult.FAIL;
			}
			if (!this.level().isClientSide()) {
				if (!(player instanceof ServerPlayer p) || !ScooterGarage.pickup(p,this)) return InteractionResult.FAIL;
			}
			return InteractionResult.SUCCESS;
		}
		if (this.getPassengers().size() >= MAX_PASSENGERS) {
			if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("napuli.motorino.full"));
			return InteractionResult.FAIL;
		}
		if (!this.level().isClientSide()) {
			if (!player.startRiding(this)) return InteractionResult.FAIL;
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 1f, 1.6f);
			if (this.getPassengers().size() == 3)
				for (Entity e : this.getPassengers())
					if (e instanceof ServerPlayer sp) NapoliNotice.show(sp, Component.translatable("napuli.motorino.tre"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
        if(PoliceVehicles.managed(this))return getPassengers().isEmpty()&&PoliceVehicles.mayBoard(this,passenger);
		if(CityMobility.managed(this))return getPassengers().isEmpty()&&CityMobility.mayBoard(this,passenger);
		return getPassengers().size() < MAX_PASSENGERS && (!inParade() || !(passenger instanceof Player));
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if(CityMobility.managed(this)||PoliceVehicles.managed(this))return;
		if (level().isClientSide() || inParade()) return;
		if (getPassengers().size() == 1 && getControllingPassenger() instanceof ServerPlayer) setStation(lastStation);
		ScooterGarage.observe(this,getFirstPassenger() instanceof ServerPlayer p?p:null);
		announceSeats();
	}

	private void announceSeats() {
		for (int i = 0; i < getPassengers().size(); i++) {
			if (getPassengers().get(i) instanceof ServerPlayer sp)
				sp.sendOverlayMessage(Component.translatable(switch (i) {
					case 0 -> "napuli.motorino.driver";
					case 1 -> "napuli.motorino.middle";
					default -> "napuli.motorino.rear";
				}));
		}
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return !inParade() && this.getFirstPassenger() instanceof Player p ? p : null;
	}

	@Override
	protected void tickRidden(Player controller, Vec3 input) {
		super.tickRidden(controller, input);
		this.setRot(controller.getYRot(), controller.getXRot() * 0.5f);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
	}

	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		float forward = controller.zza;
		if (forward < 0) forward *= 0.3f;
		return new Vec3(0, 0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player controller) { return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED); }

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction move) {
		int i = this.getPassengers().indexOf(passenger);
		double alongSeat = 0.11 - Math.max(0, i) * 0.46;
		double yaw = Math.toRadians(this.getYRot());
		double x = this.getX() - Math.sin(yaw) * alongSeat;
		double z = this.getZ() + Math.cos(yaw) * alongSeat;
		move.accept(passenger, x, this.getY() + 0.30, z);
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if(CityMobility.managed(this)||PoliceVehicles.managed(this))return;
		if (!level().isClientSide() && !inParade()) ScooterGarage.observe(this,null);
		if (level().isClientSide() || inParade()) return;
		if (getControllingPassenger() == null) setStation(Radio.OFF);
		announceSeats();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// only /kill and the void destroy it; to take it back: sneak + right click
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurtServer(level, source, amount);
		return false;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) { return false; }

	@Override
	public boolean isPushable() { return false; }

	@Override
	public boolean removeWhenFarAway(double d) { return false; }

	public List<Entity> riders() { return this.getPassengers(); }
}
