package network.wilburn.pinataparty.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;

import network.wilburn.pinataparty.Config;
import network.wilburn.pinataparty.PartyFireworks;
import network.wilburn.pinataparty.PinataParty;
import network.wilburn.pinataparty.item.BottledCritterItem;

public class PinataEntity extends Entity {
    public static final String TAG_ITEMS = "Items";
    public static final String TAG_CRITTERS = "Critters";
    /** Old saves (1.0.x) kept only a bee count. */
    public static final String TAG_BEES_OLD = "Bees";
    public static final String TAG_VARIANT = "Variant";
    public static final String TAG_HEALTH = "Health";
    public static final String TAG_MAX_HEALTH = "MaxHealth";

    public static final int VARIANTS = 3;

    private static final EntityDataAccessor<Integer> DATA_HURT_TIME =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HURT_DIR =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(PinataEntity.class, EntityDataSerializers.INT);
    /** Bees only, for the buzzing sound on clients. */
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
    /** Each critter's saved data (see CritterData). */
    private final List<CompoundTag> critters = new ArrayList<>();
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

    /** Bees inside (synced to clients). */
    public int getBees() {
        return this.entityData.get(DATA_BEES);
    }

    /** All critters inside (server side). */
    public int getCritterCount() {
        return this.critters.size();
    }

    public List<CompoundTag> getCritters() {
        return List.copyOf(this.critters);
    }

    private void critterChanged() {
        int bees = 0;
        for (CompoundTag c : this.critters) {
            if (CritterData.isBee(c)) {
                bees++;
            }
        }
        this.entityData.set(DATA_BEES, bees);
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

        // confetti and a papery pop, nothing louder than the hits
        for (Vector3f color : CONFETTI) {
            level.sendParticles(new DustParticleOptions(color, 1.5f), c.x, c.y, c.z, 18, 0.6, 0.6, 0.6, 0.0);
        }
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.12);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 1.0f, 0.9f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0f, 0.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.NEUTRAL, 0.35f, 1.2f);

        // the goodies: rockets go up (up to the limit), everything else flies out
        int maxRockets = Config.MAX_FIREWORKS.get();
        int rocketsLeft = maxRockets;
        int launch = 0;
        for (ItemStack stack : this.contents) {
            if (stack.is(Items.FIREWORK_ROCKET) && rocketsLeft > 0) {
                int n = Math.min(stack.getCount(), rocketsLeft);
                for (int i = 0; i < n; i++) {
                    // spread the launches over about a second
                    PartyFireworks.queue(level, c, stack.copyWithCount(1), launch * 20 / Math.max(1, maxRockets));
                    launch++;
                }
                rocketsLeft -= n;
                if (stack.getCount() > n) {
                    spawnItem(level, c, stack.copyWithCount(stack.getCount() - n), 0.25);
                }
            } else {
                spawnItem(level, c, stack, 0.25);
            }
        }
        this.contents.clear();

        // the critters
        List<CompoundTag> released = new ArrayList<>(this.critters);
        this.critters.clear();
        critterChanged();
        int bees = releaseCritters(level, c, released, breaker);

        if (bees > 0) {
            Component msg = Component.translatable("message.pinataparty.bees", breaker.getDisplayName(), bees);
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

    /** Lets every critter out. Returns how many were bees. */
    private int releaseCritters(ServerLevel level, Vec3 c, List<CompoundTag> released, Player breaker) {
        if (released.isEmpty()) {
            return 0;
        }
        List<Player> targets = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0),
                p -> p.isAlive() && !p.isSpectator() && !p.getAbilities().instabuild);
        boolean angry = Config.ANGRY_BEES.get();
        boolean breakerOnly = Config.BEE_TARGETS.get() == Config.BeeTargets.BREAKER;
        int angerTicks = Config.BEE_ANGER_SECONDS.get() * 20;
        int leaveSeconds = Config.CRITTERS_LEAVE_AFTER_SECONDS.get();
        long now = level.getGameTime();
        int bees = 0;

        for (CompoundTag critter : released) {
            var made = CritterData.create(level, critter,
                    c.x + (this.random.nextDouble() - 0.5) * 0.6, c.y, c.z + (this.random.nextDouble() - 0.5) * 0.6,
                    this.random.nextFloat() * 360.0f);
            if (made.isEmpty()) {
                continue;
            }
            Entity e = made.get();
            e.setDeltaMovement(this.random.nextGaussian() * 0.25, 0.2 + this.random.nextDouble() * 0.15,
                    this.random.nextGaussian() * 0.25);
            e.addTag(CritterData.PARTY_TAG);
            var data = e.getPersistentData();

            if (e instanceof Bee bee) {
                bees++;
                data.putLong(CritterData.ANGRY_UNTIL, angry ? now + angerTicks : now);
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
            } else if ((e instanceof Chicken || e instanceof Rabbit) && e instanceof LivingEntity scared) {
                // run around in a panic for a bit
                scared.setLastHurtByMob(breaker);
            }

            if (leaveSeconds > 0 && e.getType().is(CritterData.LEAVES_AFTER_PARTY)) {
                long angryFor = (e instanceof Bee && angry) ? angerTicks : 0;
                data.putLong(CritterData.LEAVE_AT, now + angryFor + leaveSeconds * 20L);
            }
            level.addFreshEntity(e);
        }
        return bees;
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
                if (!this.contents.isEmpty() || !this.critters.isEmpty()) {
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

        // empty hand: give it a squeeze and see how full it is
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

        int room = Config.MAX_CRITTERS.get() - this.critters.size();

        // a bottled critter (or an old Bottled Bee)
        CompoundTag bottled = null;
        if (held.is(PinataParty.BOTTLED_BEE.get())) {
            bottled = CritterData.ofType(EntityType.BEE);
        } else if (held.is(PinataParty.BOTTLED_CRITTER.get())) {
            bottled = BottledCritterItem.critter(held);
        }
        if (bottled != null) {
            if (room <= 0) {
                return full(player, client, "message.pinataparty.full_critters");
            }
            if (!client) {
                addCritter(bottled);
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
                this.playSound(SoundEvents.BOTTLE_EMPTY, 1.0f, 1.0f);
                showFill(player);
            }
            return InteractionResult.sidedSuccess(client);
        }

        // a spawn egg for a bottleable mob (creative)
        if (held.getItem() instanceof SpawnEggItem egg && egg.getType(held).is(CritterData.BOTTLEABLE)) {
            if (room <= 0) {
                return full(player, client, "message.pinataparty.full_critters");
            }
            if (!client) {
                addCritter(CritterData.ofType(egg.getType(held)));
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
            if (room <= 0) {
                return full(player, client, "message.pinataparty.full_critters");
            }
            if (!client) {
                int moved = Math.min(room, occupants.size());
                for (int i = 0; i < moved; i++) {
                    addCritter(CritterData.ofBeeOccupant(occupants.get(i).entityData().copyTag()));
                }
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

    private void addCritter(CompoundTag critter) {
        this.critters.add(critter);
        critterChanged();
    }

    /** Action-bar line: how full this pinata is, against the server's limits. */
    private void showFill(Player player) {
        player.displayClientMessage(fillLine(this.contents, this.critters), true);
    }

    /** "Piñata: 5/27 stacks (214 items), 3/12 critters: 2 Bee, 1 Chicken" */
    public static Component fillLine(List<ItemStack> contents, List<CompoundTag> critters) {
        int items = 0;
        for (ItemStack stack : contents) {
            items += stack.getCount();
        }
        MutableComponent line = Component.translatable("message.pinataparty.fill",
                contents.size(), Config.maxStacks(), items, critters.size(), Config.maxCritters());
        Component breakdown = critterBreakdown(critters);
        if (breakdown != null) {
            line.append(": ").append(breakdown);
        }
        return line;
    }

    /** "2 Bee, 1 Chicken", or null when there are none. */
    public static Component critterBreakdown(List<CompoundTag> critters) {
        if (critters.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        for (CompoundTag c : critters) {
            String id = CritterData.typeId(c).toString();
            counts.merge(id, 1, Integer::sum);
            names.putIfAbsent(id, CritterData.typeName(c));
        }
        MutableComponent out = Component.empty();
        boolean first = true;
        for (var entry : counts.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            out.append(entry.getValue() + " ").append(names.get(entry.getKey()));
            first = false;
        }
        return out;
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
        ListTag critterList = new ListTag();
        for (CompoundTag c : this.critters) {
            critterList.add(c.copy());
        }
        tag.put(TAG_CRITTERS, critterList);
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
        this.critters.clear();
        ListTag critterList = tag.getList(TAG_CRITTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < critterList.size(); i++) {
            this.critters.add(critterList.getCompound(i).copy());
        }
        // 1.0.x saves: just a number of bees
        for (int i = 0; i < tag.getInt(TAG_BEES_OLD); i++) {
            this.critters.add(CritterData.ofType(EntityType.BEE));
        }
        critterChanged();
        this.entityData.set(DATA_VARIANT, Mth.clamp(tag.getInt(TAG_VARIANT), 0, VARIANTS - 1));
        this.maxHealth = tag.contains(TAG_MAX_HEALTH) ? tag.getFloat(TAG_MAX_HEALTH) : 20.0f;
        this.health = tag.contains(TAG_HEALTH) ? tag.getFloat(TAG_HEALTH) : this.maxHealth;
        if (this.health <= 0.0f) {
            this.health = this.maxHealth;
        }
    }

    /** Describes a saved filling (the pinata item's tooltip), in the same words as the readout. */
    public static Component describeFilling(CompoundTag tag, HolderLookup.Provider registries) {
        List<ItemStack> contents = new ArrayList<>();
        ListTag items = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = registries != null ? ItemStack.parseOptional(registries, items.getCompound(i)) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                contents.add(stack);
            }
        }
        List<CompoundTag> critters = new ArrayList<>();
        ListTag critterList = tag.getList(TAG_CRITTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < critterList.size(); i++) {
            critters.add(critterList.getCompound(i));
        }
        for (int i = 0; i < tag.getInt(TAG_BEES_OLD); i++) {
            critters.add(CritterData.ofType(EntityType.BEE));
        }
        return fillLine(contents, critters);
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
