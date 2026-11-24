package network.wilburn.pinataparty.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import network.wilburn.pinataparty.PinataParty;
import network.wilburn.pinataparty.entity.CritterData;

/**
 * A glass bottle with a mob in it (see GameEvents for the catching). The mob's saved data
 * lives in CUSTOM_DATA under "Critter". Use it on a pinata to put the critter in, or on a
 * block to let it go.
 */
public class BottledCritterItem extends Item {
    public static final String KEY = "Critter";

    /** Icon index per critter, for the item model's "pinataparty:critter" property. 0 = generic. */
    private static final List<String> ICONS = List.of(
            "minecraft:bee", "minecraft:chicken", "minecraft:rabbit", "minecraft:bat",
            "minecraft:frog", "minecraft:slime", "minecraft:silverfish");

    public BottledCritterItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(CompoundTag critter) {
        ItemStack stack = new ItemStack(PinataParty.BOTTLED_CRITTER.get());
        CompoundTag data = new CompoundTag();
        data.put(KEY, critter);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    /** The critter's data, or null for an empty or broken bottle. */
    public static CompoundTag critter(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(KEY, 10) ? tag.getCompound(KEY) : null;
    }

    /** Model property: which icon to show. Reads without copying, since it runs every frame. */
    @SuppressWarnings("deprecation")
    public static float iconIndex(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0.0f;
        }
        String id = data.getUnsafe().getCompound(KEY).getString("id");
        int i = ICONS.indexOf(id);
        return i < 0 ? 0.0f : (i + 1) / 10.0f;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag critter = critter(stack);
        if (critter == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.pinataparty.bottled_critter.of", CritterData.typeName(critter));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        CompoundTag critter = critter(context.getItemInHand());
        if (critter == null) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        if (level instanceof ServerLevel serverLevel) {
            var made = CritterData.create(serverLevel, critter, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    player != null ? player.getYRot() + 180.0f : 0.0f);
            if (made.isEmpty()) {
                return InteractionResult.FAIL;
            }
            serverLevel.addFreshEntity(made.get());
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.NEUTRAL, 1.0f, 1.0f);
            if (player != null) {
                player.setItemInHand(context.getHand(),
                        ItemUtils.createFilledResult(context.getItemInHand(), player, new ItemStack(Items.GLASS_BOTTLE)));
            } else {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag critter = critter(stack);
        if (critter == null) {
            return;
        }
        if (critter.contains("CustomName", 8) && context.registries() != null) {
            // the saved name is JSON text; show it plainly in quotes
            Component name = Component.Serializer.fromJson(critter.getString("CustomName"), context.registries());
            if (name != null) {
                tooltip.add(Component.literal("“").append(name).append("”").withStyle(ChatFormatting.WHITE));
            }
        }
        if (critter.getInt("Age") < 0) {
            tooltip.add(Component.translatable("item.pinataparty.bottled_critter.baby").withStyle(ChatFormatting.GRAY));
        }
        ResourceLocation id = CritterData.typeId(critter);
        String key = "item.pinataparty.bottled_critter.tooltip." + id.getPath();
        if (Language.getInstance().has(key)) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
