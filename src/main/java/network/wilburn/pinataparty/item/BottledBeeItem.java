package network.wilburn.pinataparty.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * One bee in a glass bottle. Made by using an empty glass bottle on a bee
 * (see GameEvents). Use it on a pinata to put the bee inside, or on a block
 * to let the bee go.
 */
public class BottledBeeItem extends Item {
    public BottledBeeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        if (level instanceof ServerLevel serverLevel) {
            Bee bee = EntityType.BEE.spawn(serverLevel, pos, MobSpawnType.BUCKET);
            if (bee == null) {
                return InteractionResult.FAIL;
            }
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.NEUTRAL, 1.0f, 1.0f);
            if (player != null) {
                ItemStack stack = context.getItemInHand();
                player.setItemInHand(context.getHand(),
                        ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            } else {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.pinataparty.bottled_bee.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
