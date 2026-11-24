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
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
