package network.wilburn.pinataparty;

import java.util.EnumMap;
import java.util.List;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import network.wilburn.pinataparty.entity.PinataEntity;
import network.wilburn.pinataparty.entity.CritterData;
import network.wilburn.pinataparty.item.BottledBeeItem;
import network.wilburn.pinataparty.item.BottledCritterItem;
import network.wilburn.pinataparty.item.PinataBatItem;
import network.wilburn.pinataparty.item.PinataItem;
import network.wilburn.pinataparty.test.PinataTests;

@Mod(PinataParty.MODID)
public class PinataParty {
    public static final String MODID = "pinataparty";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    // --- registers -------------------------------------------------------

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // --- entity ----------------------------------------------------------

    public static final DeferredHolder<EntityType<?>, EntityType<PinataEntity>> PINATA_ENTITY =
            ENTITY_TYPES.register("pinata", () -> EntityType.Builder.<PinataEntity>of(PinataEntity::new, MobCategory.MISC)
                    .sized(0.9f, 1.5f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build("pinata"));

    // --- blindfold "armor" (worn on the head, no protection) --------------

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BLINDFOLD_MATERIAL =
            ARMOR_MATERIALS.register("blindfold", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        for (ArmorItem.Type type : ArmorItem.Type.values()) {
                            map.put(type, 0);
                        }
                    }),
                    0,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(ItemTags.WOOL),
                    List.of(new ArmorMaterial.Layer(id("blindfold"))),
                    0.0f,
                    0.0f));

    // --- items -----------------------------------------------------------

    public static final DeferredItem<PinataItem> PINATA = ITEMS.register("pinata",
            () -> new PinataItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<PinataBatItem> PINATA_BAT = ITEMS.register("pinata_bat",
            () -> new PinataBatItem(new Item.Properties().durability(250)
                    .attributes(PinataBatItem.createAttributes())));

    public static final DeferredItem<ArmorItem> BLINDFOLD = ITEMS.register("blindfold",
            () -> new ArmorItem(BLINDFOLD_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)));

    /** Kept so 1.0.x Bottled Bees still work; new bottles are Bottled Critters. Not in the creative tab. */
    public static final DeferredItem<BottledBeeItem> BOTTLED_BEE = ITEMS.register("bottled_bee",
            () -> new BottledBeeItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<BottledCritterItem> BOTTLED_CRITTER = ITEMS.register("bottled_critter",
            () -> new BottledCritterItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("pinataparty",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pinataparty"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> PINATA.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(PINATA.get());
                        output.accept(PINATA_BAT.get());
                        output.accept(BLINDFOLD.get());
                        for (var type : java.util.List.of(net.minecraft.world.entity.EntityType.BEE,
                                net.minecraft.world.entity.EntityType.CHICKEN, net.minecraft.world.entity.EntityType.RABBIT,
                                net.minecraft.world.entity.EntityType.BAT, net.minecraft.world.entity.EntityType.FROG,
                                net.minecraft.world.entity.EntityType.SLIME, net.minecraft.world.entity.EntityType.SILVERFISH)) {
                            CompoundTag critter = CritterData.ofType(type);
                            if (type == net.minecraft.world.entity.EntityType.SLIME) {
                                critter.putInt("Size", 0); // the smallest slime
                            }
                            output.accept(BottledCritterItem.of(critter));
                        }
                    })
                    .build());

    public PinataParty(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        ARMOR_MATERIALS.register(modBus);
        CREATIVE_TABS.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.register(GameEvents.class);
        NeoForge.EVENT_BUS.register(PartyFireworks.class);
        modBus.addListener(RegisterGameTestsEvent.class, event -> event.register(PinataTests.class));
    }
}
