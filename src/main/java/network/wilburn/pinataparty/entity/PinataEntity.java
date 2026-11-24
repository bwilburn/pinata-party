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

    public static final int VARIANTS = 3;

    private static final EntityDataAccessor<Integer> DATA_HURT_TIME =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HURT_DIR =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);

    private static final Vector3f[] CONFETTI = {
            new Vector3f(1.00f, 0.25f, 0.55f), // pink
            new Vector3f(1.00f, 0.80f, 0.10f), // yellow
            new Vector3f(0.20f, 0.85f, 0.95f), // cyan
            new Vector3f(0.55f, 0.95f, 0.30f), // lime
            new Vector3f(0.65f, 0.35f, 1.00f), // purple
            new Vector3f(1.00f, 0.50f, 0.10f), // orange
    };

    private final List<ItemStack> contents = new ArrayList<>();
    private int bees;
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
            // muffled buzzing now and then when there are bees inside
            if (this.bees > 0 && this.random.nextInt(80) == 0) {
                this.playSound(SoundEvents.BEEHIVE_WORK, 0.6f, 0.9f + this.random.nextFloat() * 0.2f);
            }
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
        if (this.bees > 0) {
            this.playSound(SoundEvents.BEEHIVE_WORK, 1.0f, 1.3f);
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
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 30, 0.2, 0.2, 0.2, 0.2);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.5f, 1.0f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1.0f, 1.0f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 1.0f, 1.0f);

        // the goodies
        List<ItemStack> drops = new ArrayList<>(this.contents);
        if (drops.isEmpty() && this.bees == 0) {
            // an empty pinata still gives a little something
            drops.add(new ItemStack(Items.COOKIE, 3 + this.random.nextInt(4)));
        }
        for (ItemStack stack : drops) {
            spawnItem(level, c, stack, 0.25);
        }
        this.contents.clear();

        // the bees
        int released = this.bees;
        this.bees = 0;
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

        for (int i = 0; i < count; i++) {
            Bee bee = EntityType.BEE.create(level);
            if (bee == null) {
                continue;
            }
            bee.moveTo(c.x + (this.random.nextDouble() - 0.5) * 0.6, c.y, c.z + (this.random.nextDouble() - 0.5) * 0.6,
                    this.random.nextFloat() * 360.0f, 0.0f);
            bee.setDeltaMovement(this.random.nextGaussian() * 0.2, 0.15, this.random.nextGaussian() * 0.2);
            bee.addTag(PARTY_BEE_TAG);

            if (angry) {
                Player target = null;
                if (breakerOnly) {
                    if (targets.contains(breaker)) {
                        target = breaker;
                    }
                } else if (!targets.isEmpty()) {
                    target = targets.get(this.random.nextInt(targets.size()));
                }
                if (target != null) {
                    bee.setPersistentAngerTarget(target.getUUID());
                    bee.setRemainingPersistentAngerTime(angerTicks);
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
                if (!this.contents.isEmpty() || this.bees > 0) {
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
                player.displayClientMessage(Component.translatable("message.pinataparty.peek",
                        this.contents.size(), this.bees), true);
                this.playSound(this.bees > 0 ? SoundEvents.BEEHIVE_WORK : SoundEvents.WOOL_STEP, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(client);
        }

        // these are for hitting and wearing, not stuffing
        if (held.is(PinataParty.PINATA_BAT.get()) || held.is(PinataParty.BLINDFOLD.get())
                || held.is(PinataParty.PINATA.get())) {
            return InteractionResult.PASS;
        }

        int beeRoom = Config.MAX_BEES.get() - this.bees;

        // a bottled bee
        if (held.is(PinataParty.BOTTLED_BEE.get())) {
            if (beeRoom <= 0) {
                return full(player, client, "message.pinataparty.full_bees");
            }
            if (!client) {
                this.bees++;
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
                this.playSound(SoundEvents.BEEHIVE_ENTER, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(client);
        }

        // a bee spawn egg (creative)
        if (held.is(Items.BEE_SPAWN_EGG)) {
            if (beeRoom <= 0) {
                return full(player, client, "message.pinataparty.full_bees");
            }
            if (!client) {
                this.bees++;
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.playSound(SoundEvents.BEEHIVE_ENTER, 1.0f, 1.0f);
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
                this.bees += moved;
                held.set(DataComponents.BEES, List.copyOf(occupants.subList(moved, occupants.size())));
                this.playSound(SoundEvents.BEEHIVE_EXIT, 1.0f, 1.0f);
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
        }
        return InteractionResult.sidedSuccess(client);
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
        tag.putInt(TAG_BEES, this.bees);
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
        this.bees = tag.getInt(TAG_BEES);
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        loadFilling(tag.getCompound("Filling"), this.level().registryAccess());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(PinataParty.PINATA.get());
    }
}
