package network.wilburn.pinataparty.entity;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;

import network.wilburn.pinataparty.Config;
import network.wilburn.pinataparty.PinataParty;

public class PinataEntity extends Entity {
    public static final String TAG_ITEMS = "Items";
    public static final String TAG_BEES = "Bees";
    public static final String TAG_VARIANT = "Variant";
    public static final String TAG_HEALTH = "Health";
    public static final String TAG_MAX_HEALTH = "MaxHealth";

    /** Scoreboard tag put on bees that came out of a pinata. */
    public static final String PARTY_BEE_TAG = "pinataparty.party_bee";
    /** Persistent-data keys on party bees: game time their anger ends, and when they fly off. */
    public static final String ANGRY_UNTIL = "pinataparty.angry_until";
    public static final String LEAVE_AT = "pinataparty.leave_at";

    public static final int VARIANTS = 3;

    private static final EntityDataAccessor<Integer> DATA_HURT_TIME =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HURT_DIR =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEES =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HANGING =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Set by the client mod class; called every client tick for a pinata with bees in it, to keep
     * its buzzing sound playing. A plain field so this class never touches client-only code.
     */
    public static java.util.function.Consumer<PinataEntity> clientBuzzTicker = e -> {};

    private static final Vector3f[] CONFETTI = {
            new Vector3f(1.00f, 0.25f, 0.55f), // pink
            new Vector3f(1.00f, 0.80f, 0.10f), // yellow
            new Vector3f(0.20f, 0.85f, 0.95f), // cyan
            new Vector3f(0.55f, 0.95f, 0.30f), // lime
            new Vector3f(0.65f, 0.35f, 1.00f), // purple
            new Vector3f(1.00f, 0.50f, 0.10f), // orange
    };

    private final List<ItemStack> contents = new ArrayList<>();
    private float health = 20.0f;
    private float maxHealth = 20.0f;

    public PinataEntity(EntityType<? extends PinataEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    // --- synced data -----------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HURT_TIME, 0);
        builder.define(DATA_HURT_DIR, 1);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_BEES, 0);
        builder.define(DATA_HANGING, false);
    }

    public int getHurtTime() {
        return this.entityData.get(DATA_HURT_TIME);
    }

    public int getHurtDir() {
        return this.entityData.get(DATA_HURT_DIR);
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public int getBees() {
        return this.entityData.get(DATA_BEES);
    }

    private void setBees(int bees) {
        this.entityData.set(DATA_BEES, Math.max(0, bees));
    }

    /** Hung from a block on a string (sways and turns), as opposed to standing on something. */
    public boolean isHanging() {
        return this.entityData.get(DATA_HANGING);
    }

    public void setHanging(boolean hanging) {
        this.entityData.set(DATA_HANGING, hanging);
    }

    /** Called when a brand-new (never filled) pinata is placed. */
    public void randomizeNew() {
        this.entityData.set(DATA_VARIANT, this.random.nextInt(VARIANTS));
        int lo = Config.MIN_TOUGHNESS.get();
        int hi = Math.max(lo, Config.MAX_TOUGHNESS.get());
        this.maxHealth = lo + this.random.nextInt(hi - lo + 1);
        this.health = this.maxHealth;
    }

    // --- ticking -----------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            int hurt = getHurtTime();
            if (hurt > 0) {
                this.entityData.set(DATA_HURT_TIME, hurt - 1);
            }
        } else if (getBees() > 0) {
            // the buzzing loop is a client-side sound that follows the pinata; see PinataBuzzSound
            clientBuzzTicker.accept(this);
        }
    }

    // --- being hit ---------------------------------------------------------

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || this.isRemoved()) {
            return false;
        }
        // only a player swinging at it counts: no arrows, explosions or lava
        if (!(source.getDirectEntity() instanceof Player player)) {
            return false;
        }
        if (this.level().isClientSide()) {
            return true;
        }

        ItemStack weapon = player.getMainHandItem();
        boolean withBat = weapon.is(PinataParty.PINATA_BAT.get());
        if (withBat && !player.getAbilities().instabuild) {
            weapon.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        this.health -= Math.max(amount, 0.5f);
        this.entityData.set(DATA_HURT_TIME, 10);
        this.entityData.set(DATA_HURT_DIR, -getHurtDir());

        float pitch = 0.8f + this.random.nextFloat() * 0.4f;
        this.playSound(SoundEvents.WOOL_HIT, 1.0f, pitch);
        if (withBat) {
            this.playSound(SoundEvents.WOOD_HIT, 0.8f, pitch);
        }
        if (getBees() > 0) {
            this.playSound(SoundEvents.BEE_HURT, 0.4f + 0.05f * Math.min(getBees(), 12), 0.8f);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 c = center();
            serverLevel.sendParticles(new DustParticleOptions(CONFETTI[this.random.nextInt(CONFETTI.length)], 1.0f),
                    c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.0);
        }

        if (this.health <= 0.0f) {
            burst(player);
        }
        return true;
    }

    private Vec3 center() {
        return this.position().add(0.0, this.getBbHeight() * 0.5, 0.0);
    }

    private void burst(Player breaker) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 c = center();

        // confetti and a bang
        for (Vector3f color : CONFETTI) {
            level.sendParticles(new DustParticleOptions(color, 1.5f), c.x, c.y, c.z, 18, 0.6, 0.6, 0.6, 0.0);
        }
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.12);
        // a papery tear and a little sparkle, nothing louder than the hits
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 1.0f, 0.9f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0f, 0.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.NEUTRAL, 0.35f, 1.2f);

        // the goodies
        for (ItemStack stack : this.contents) {
            spawnItem(level, c, stack, 0.25);
        }
        this.contents.clear();

        // the bees
        int released = getBees();
        setBees(0);
        releaseBees(level, c, released, breaker, Config.ANGRY_BEES.get());

        if (released > 0) {
            Component msg = Component.translatable("message.pinataparty.bees", breaker.getDisplayName(), released);
            for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(24.0))) {
                p.displayClientMessage(msg, true);
            }
        }
        this.discard();
    }

    private void spawnItem(ServerLevel level, Vec3 c, ItemStack stack, double spread) {
        ItemEntity item = new ItemEntity(level, c.x, c.y, c.z, stack.copy());
        item.setDeltaMovement(
                this.random.nextGaussian() * spread * 0.6,
                0.2 + this.random.nextDouble() * spread,
                this.random.nextGaussian() * spread * 0.6);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private void releaseBees(ServerLevel level, Vec3 c, int count, Player breaker, boolean angry) {
        if (count <= 0) {
            return;
        }
        List<Player> targets = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0),
                p -> p.isAlive() && !p.isSpectator() && !p.getAbilities().instabuild);
        boolean breakerOnly = Config.BEE_TARGETS.get() == Config.BeeTargets.BREAKER;
        int angerTicks = Config.BEE_ANGER_SECONDS.get() * 20;
        int leaveSeconds = Config.BEES_LEAVE_AFTER_SECONDS.get();
        long now = level.getGameTime();

        for (int i = 0; i < count; i++) {
            Bee bee = EntityType.BEE.create(level);
            if (bee == null) {
                continue;
            }
            bee.moveTo(c.x + (this.random.nextDouble() - 0.5) * 0.6, c.y, c.z + (this.random.nextDouble() - 0.5) * 0.6,
                    this.random.nextFloat() * 360.0f, 0.0f);
            bee.setDeltaMovement(this.random.nextGaussian() * 0.2, 0.15, this.random.nextGaussian() * 0.2);
            bee.addTag(PARTY_BEE_TAG);
            // GameEvents keeps the bee angry (and stinging) until then, then sends it off
            bee.getPersistentData().putLong(ANGRY_UNTIL, angry ? now + angerTicks : now);
            if (leaveSeconds > 0) {
                bee.getPersistentData().putLong(LEAVE_AT, now + (angry ? angerTicks : 0) + leaveSeconds * 20L);
            }

            if (angry) {
                Player target = null;
                if (breakerOnly) {
                    if (targets.contains(breaker)) {
                        target = breaker;
                    }
                } else if (!targets.isEmpty()) {
                    target = targets.get(this.random.nextInt(targets.size()));
                }
                // always angry (red eyes, angry buzz); an angry bee with no target goes for
                // the nearest survival player by itself
                bee.setRemainingPersistentAngerTime(angerTicks);
                if (target != null) {
                    bee.setPersistentAngerTarget(target.getUUID());
                    bee.setTarget(target);
                }
            }
            level.addFreshEntity(bee);
        }
    }

    // --- filling it (right-click) ------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean client = this.level().isClientSide();

        // sneak + empty hand: take it down, filling and all
        if (held.isEmpty() && player.isSecondaryUseActive()) {
            if (!client) {
                ItemStack item = new ItemStack(PinataParty.PINATA.get());
                if (!this.contents.isEmpty() || getBees() > 0) {
                    item.set(DataComponents.CUSTOM_DATA, CustomData.of(saveFilling(this.level().registryAccess())));
                }
                if (!player.getInventory().add(item)) {
                    player.drop(item, false);
                }
                this.playSound(SoundEvents.WOOL_BREAK, 0.8f, 1.2f);
                this.discard();
            }
            return InteractionResult.sidedSuccess(client);
        }

        // empty hand: give it a squeeze and guess what's inside
        if (held.isEmpty()) {
            if (!client) {
                showFill(player);
                this.playSound(getBees() > 0 ? SoundEvents.BEEHIVE_WORK : SoundEvents.WOOL_STEP, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(client);
        }

        // these are for hitting and wearing, not stuffing
        if (held.is(PinataParty.PINATA_BAT.get()) || held.is(PinataParty.BLINDFOLD.get())
                || held.is(PinataParty.PINATA.get())) {
            return InteractionResult.PASS;
        }

        int beeRoom = Config.MAX_BEES.get() - getBees();

        // a bottled bee
        if (held.is(PinataParty.BOTTLED_BEE.get())) {
            if (beeRoom <= 0) {
                return full(player, client, "message.pinataparty.full_bees");
            }
            if (!client) {
                setBees(getBees() + 1);
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
                this.playSound(SoundEvents.BEEHIVE_ENTER, 1.0f, 1.0f);
                showFill(player);
            }
            return InteractionResult.sidedSuccess(client);
        }

        // a bee spawn egg (creative)
        if (held.is(Items.BEE_SPAWN_EGG)) {
            if (beeRoom <= 0) {
                return full(player, client, "message.pinataparty.full_bees");
            }
            if (!client) {
                setBees(getBees() + 1);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.playSound(SoundEvents.BEEHIVE_ENTER, 1.0f, 1.0f);
                showFill(player);
            }
            return InteractionResult.sidedSuccess(client);
        }

        // a bee nest or beehive picked up with Silk Touch, bees and all
        List<BeehiveBlockEntity.Occupant> occupants = held.get(DataComponents.BEES);
        if (occupants != null && !occupants.isEmpty()) {
            if (beeRoom <= 0) {
                return full(player, client, "message.pinataparty.full_bees");
            }
            if (!client) {
                int moved = Math.min(beeRoom, occupants.size());
                setBees(getBees() + moved);
                held.set(DataComponents.BEES, List.copyOf(occupants.subList(moved, occupants.size())));
                this.playSound(SoundEvents.BEEHIVE_EXIT, 1.0f, 1.0f);
                showFill(player);
            }
            return InteractionResult.sidedSuccess(client);
        }

        // anything else: in goes the whole stack
        if (this.contents.size() >= Config.MAX_STACKS.get()) {
            return full(player, client, "message.pinataparty.full_items");
        }
        if (!client) {
            this.contents.add(held.copy());
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
            this.playSound(SoundEvents.BUNDLE_INSERT, 1.0f, 0.8f + this.random.nextFloat() * 0.4f);
            showFill(player);
        }
        return InteractionResult.sidedSuccess(client);
    }

    /** Action-bar line: how full this pinata is, against the server's limits. */
    private void showFill(Player player) {
        int items = 0;
        for (ItemStack stack : this.contents) {
            items += stack.getCount();
        }
        player.displayClientMessage(Component.translatable("message.pinataparty.fill",
                this.contents.size(), Config.MAX_STACKS.get(), items, getBees(), Config.MAX_BEES.get()), true);
    }

    private InteractionResult full(Player player, boolean client, String key) {
        if (!client) {
            player.displayClientMessage(Component.translatable(key), true);
        }
        return InteractionResult.sidedSuccess(client);
    }

    // --- saving ----------------------------------------------------------

    public CompoundTag saveFilling(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag items = new ListTag();
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty()) {
                items.add(stack.save(registries));
            }
        }
        tag.put(TAG_ITEMS, items);
        tag.putInt(TAG_BEES, getBees());
        tag.putInt(TAG_VARIANT, getVariant());
        tag.putFloat(TAG_HEALTH, this.health);
        tag.putFloat(TAG_MAX_HEALTH, this.maxHealth);
        return tag;
    }

    public void loadFilling(CompoundTag tag, HolderLookup.Provider registries) {
        this.contents.clear();
        ListTag items = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, items.getCompound(i));
            if (!stack.isEmpty()) {
                this.contents.add(stack);
            }
        }
        setBees(tag.getInt(TAG_BEES));
        this.entityData.set(DATA_VARIANT, Mth.clamp(tag.getInt(TAG_VARIANT), 0, VARIANTS - 1));
        this.maxHealth = tag.contains(TAG_MAX_HEALTH) ? tag.getFloat(TAG_MAX_HEALTH) : 20.0f;
        this.health = tag.contains(TAG_HEALTH) ? tag.getFloat(TAG_HEALTH) : this.maxHealth;
        if (this.health <= 0.0f) {
            this.health = this.maxHealth;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Filling", saveFilling(this.level().registryAccess()));
        tag.putBoolean("Hanging", isHanging());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        loadFilling(tag.getCompound("Filling"), this.level().registryAccess());
        setHanging(tag.getBoolean("Hanging"));
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(PinataParty.PINATA.get());
    }
}
