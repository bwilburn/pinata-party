package network.wilburn.pinataparty.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import network.wilburn.pinataparty.PinataParty;
import network.wilburn.pinataparty.entity.PinataEntity;

/**
 * Places a pinata. Use it on the underside of a block to hang it there on a string;
 * use it anywhere else and it hovers in front of the face you clicked.
 * A pinata that was taken down keeps what was inside it (stored in CUSTOM_DATA).
 */
public class PinataItem extends Item {
    public PinataItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        PinataEntity probe = PinataParty.PINATA_ENTITY.get().create(level);
        if (probe == null) {
            return InteractionResult.FAIL;
        }
        double height = probe.getBbHeight();

        double x = clicked.getX() + 0.5;
        double z = clicked.getZ() + 0.5;
        double y;
        if (face == Direction.DOWN) {
            // hang below the block with about a block of string showing
            y = clicked.getY() - height - 0.25;
        } else if (face == Direction.UP) {
            y = clicked.getY() + 1.0;
        } else {
            // clicked the side of a block: stand it on the floor in front of that face
            BlockPos side = clicked.relative(face);
            x = side.getX() + 0.5;
            z = side.getZ() + 0.5;
            BlockPos below = side;
            for (int i = 0; i < 3 && level.getBlockState(below.below()).getCollisionShape(level, below.below()).isEmpty(); i++) {
                below = below.below();
            }
            y = below.getY();
        }
        boolean hanging = face == Direction.DOWN;

        AABB box = probe.getType().getDimensions().makeBoundingBox(x, y, z);
        if (!level.noCollision(box)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            probe.moveTo(x, y, z, 0.0f, 0.0f);
            probe.setHanging(hanging);
            Player player = context.getPlayer();
            // face the player who hung it
            probe.setYRot(player != null ? player.getYRot() + 180.0f : level.random.nextFloat() * 360.0f);

            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (!data.isEmpty()) {
                probe.loadFilling(data.copyTag(), level.registryAccess());
            } else {
                probe.randomizeNew();
            }
            level.addFreshEntity(probe);
            level.playSound(null, x, y, z, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.isEmpty()) {
            tooltip.add(Component.translatable("item.pinataparty.pinata.tooltip.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        CompoundTag tag = data.copyTag();
        int stacks = tag.getList(PinataEntity.TAG_ITEMS, 10).size();
        int bees = tag.getInt(PinataEntity.TAG_BEES);
        tooltip.add(Component.translatable("item.pinataparty.pinata.tooltip.filled", stacks, bees)
                .withStyle(ChatFormatting.GOLD));
        if (bees > 0) {
            tooltip.add(Component.translatable("item.pinataparty.pinata.tooltip.buzzing")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        }
    }
}
