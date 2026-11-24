package network.wilburn.pinataparty;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.Difficulty;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import network.wilburn.pinataparty.entity.PinataEntity;

/** Game-bus events. Registered in PinataParty's constructor. */
public final class GameEvents {
    private GameEvents() {}

    /** Empty glass bottle on a bee: the bee goes in the bottle. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Bee bee) || !bee.isAlive()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(Items.GLASS_BOTTLE)) {
            return;
        }
        Player player = event.getEntity();
        if (!event.getLevel().isClientSide()) {
            event.getLevel().playSound(null, bee.getX(), bee.getY(), bee.getZ(),
                    SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 1.3f);
            bee.discard();
            player.setItemInHand(event.getHand(),
                    ItemUtils.createFilledResult(held, player, new ItemStack(PinataParty.BOTTLED_BEE.get())));
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

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

    private static boolean isPartyBee(Object entity) {
        return entity instanceof Bee bee && bee.getTags().contains(PinataEntity.PARTY_BEE_TAG);
    }

    /**
     * A vanilla bee stings once, loses its stinger, calms down and later dies. Pinata bees
     * shouldn't: the sting is cancelled and dealt again as an ordinary hit from the bee, so
     * vanilla never marks the bee as having stung and it keeps attacking while it's angry.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.STING) || !isPartyBee(event.getSource().getEntity())) {
            return;
        }
        Bee bee = (Bee) event.getSource().getEntity();
        var target = event.getEntity();
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

    /** Keeps pinata bees angry for their whole window, then sends them off. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!isPartyBee(event.getEntity()) || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Bee bee = (Bee) event.getEntity();
        var data = bee.getPersistentData();
        long now = level.getGameTime();
        long angryUntil = data.getLong(PinataEntity.ANGRY_UNTIL);
        if (now < angryUntil && !bee.isAngry()) {
            // something calmed it early (it lost its target, or a vanilla sting got through)
            bee.setRemainingPersistentAngerTime((int) (angryUntil - now));
        }
        long leaveAt = data.getLong(PinataEntity.LEAVE_AT);
        if (leaveAt > 0 && now >= leaveAt) {
            level.sendParticles(ParticleTypes.POOF, bee.getX(), bee.getY() + 0.2, bee.getZ(), 6, 0.15, 0.15, 0.15, 0.02);
            bee.discard();
        }
    }

    /** Pinata bees sting, but (by default) can't finish anyone off. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Bee bee) || !bee.getTags().contains(PinataEntity.PARTY_BEE_TAG)) {
            return;
        }
        if (Config.PARTY_BEES_CAN_KILL.get()) {
            return;
        }
        float cap = Math.max(0.0f, player.getHealth() - 1.0f);
        if (event.getNewDamage() > cap) {
            event.setNewDamage(cap);
        }
    }
}
