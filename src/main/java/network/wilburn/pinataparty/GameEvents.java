package network.wilburn.pinataparty;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import network.wilburn.pinataparty.entity.CritterData;
import network.wilburn.pinataparty.item.BottledCritterItem;

/** Game-bus events. Registered in PinataParty's constructor. */
public final class GameEvents {
    private GameEvents() {}

    static boolean isPartyCritter(Entity entity) {
        return entity != null && entity.getTags().contains(CritterData.PARTY_TAG);
    }

    // --- catching critters ---------------------------------------------------

    /** Empty glass bottle on a bottleable mob: the mob goes in the bottle. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        ItemStack held = event.getItemStack();
        if (!held.is(Items.GLASS_BOTTLE) || !target.getType().is(CritterData.BOTTLEABLE)) {
            return;
        }
        Player player = event.getEntity();
        boolean client = event.getLevel().isClientSide();
        event.setCancellationResult(InteractionResult.sidedSuccess(client));
        event.setCanceled(true);
        if (client) {
            return;
        }

        Component refusal = CritterData.refusal(target);
        CompoundTag critter = refusal == null ? CritterData.capture(target) : null;
        if (critter == null) {
            player.displayClientMessage(refusal != null ? refusal
                    : Component.translatable("message.pinataparty.cant_bottle"), true);
            return;
        }
        event.getLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 1.3f);
        target.discard();
        player.setItemInHand(event.getHand(),
                ItemUtils.createFilledResult(held, player, BottledCritterItem.of(critter)));
    }

    // --- the blindfold ---------------------------------------------------------

    /** Putting the blindfold on spins you to face somewhere random. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.HEAD || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean putOn = event.getTo().is(PinataParty.BLINDFOLD.get()) && !event.getFrom().is(PinataParty.BLINDFOLD.get());
        // event.getFrom() is empty when the player has just logged in; don't spin them then
        if (!putOn || player.tickCount < 40 || !Config.BLINDFOLD_SPIN.get()) {
            return;
        }
        float yaw = player.getYRot() + 90.0f + player.getRandom().nextFloat() * 180.0f;
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, player.getXRot());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    // --- after the pinata bursts ------------------------------------------------

    /**
     * Two kinds of damage get rewritten:
     * - Pinata rockets don't hurt anyone.
     * - A vanilla bee stings once, loses its stinger, calms down and later dies. Pinata bees
     *   shouldn't: the sting is cancelled and dealt again as an ordinary hit from the bee, so
     *   vanilla never marks the bee as having stung and it keeps attacking while it's angry.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (source.is(DamageTypes.FIREWORKS) && source.getDirectEntity() != null
                && source.getDirectEntity().getTags().contains(PartyFireworks.ROCKET_TAG)) {
            event.setCanceled(true);
            return;
        }
        if (!source.is(DamageTypes.STING) || !(source.getEntity() instanceof Bee bee) || !isPartyCritter(bee)) {
            return;
        }
        LivingEntity target = event.getEntity();
        float amount = event.getAmount();
        event.setCanceled(true);
        if (target.hurt(bee.damageSources().mobAttack(bee), amount)) {
            bee.playSound(SoundEvents.BEE_STING, 1.0f, 1.0f);
            Difficulty difficulty = bee.level().getDifficulty();
            if (difficulty != Difficulty.PEACEFUL && difficulty != Difficulty.EASY) {
                // a short dose of poison per sting; vanilla's 10-18 s would stack up fast
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), bee);
            }
        }
    }

    /** Keeps pinata bees angry for their whole window, and sends the leaving kinds off afterwards. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!isPartyCritter(entity) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        var data = entity.getPersistentData();
        long now = level.getGameTime();
        if (entity instanceof Bee bee) {
            long angryUntil = data.getLong(CritterData.ANGRY_UNTIL);
            if (now < angryUntil && !bee.isAngry()) {
                // something calmed it early (it lost its target, or a vanilla sting got through)
                bee.setRemainingPersistentAngerTime((int) (angryUntil - now));
            }
        }
        long leaveAt = data.getLong(CritterData.LEAVE_AT);
        if (leaveAt > 0 && now >= leaveAt) {
            level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.2, entity.getZ(),
                    6, 0.15, 0.15, 0.15, 0.02);
            entity.discard();
        }
    }

    /** Pinata critters don't break blocks: no silverfish hiding in the stone around the party. */
    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (isPartyCritter(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    /** Pinata critters hurt, but (by default) can't finish anyone off. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || !isPartyCritter(event.getSource().getEntity())) {
            return;
        }
        if (Config.PARTY_CRITTERS_CAN_KILL.get()) {
            return;
        }
        float cap = Math.max(0.0f, player.getHealth() - 1.0f);
        if (event.getNewDamage() > cap) {
            event.setNewDamage(cap);
        }
    }
}
