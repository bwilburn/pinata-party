package network.wilburn.pinataparty.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import network.wilburn.pinataparty.Config;
import network.wilburn.pinataparty.PinataParty;

@Mod(value = PinataParty.MODID, dist = Dist.CLIENT)
public class PinataPartyClient {
    /** The buzz currently playing for each pinata, by entity id. */
    private static final java.util.Map<Integer, PinataBuzzSound> BUZZES = new java.util.HashMap<>();

    public PinataPartyClient(IEventBus modBus) {
        network.wilburn.pinataparty.entity.PinataEntity.clientBuzzTicker = PinataPartyClient::keepBuzzing;
        modBus.addListener(PinataPartyClient::registerRenderers);
        modBus.addListener(PinataPartyClient::registerLayers);
        modBus.addListener(PinataPartyClient::registerGuiLayers);
    }

    /** Start (or restart, if the sound engine dropped it) the buzz for a pinata with bees in it. */
    private static void keepBuzzing(network.wilburn.pinataparty.entity.PinataEntity pinata) {
        var sounds = Minecraft.getInstance().getSoundManager();
        BUZZES.values().removeIf(PinataBuzzSound::isStopped);
        PinataBuzzSound sound = BUZZES.get(pinata.getId());
        if (sound == null || !sounds.isActive(sound)) {
            if (sound != null) {
                sounds.stop(sound);
            }
            sound = new PinataBuzzSound(pinata);
            BUZZES.put(pinata.getId(), sound);
            sounds.play(sound);
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PinataParty.PINATA_ENTITY.get(), PinataRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PinataModel.LAYER, PinataModel::createBodyLayer);
    }

    /**
     * The blindfold: a dark layer over the world, under the hotbar and chat. It's drawn
     * whatever the camera mode, so F5 doesn't let anyone peek.
     */
    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, PinataParty.id("blindfold"),
                PinataPartyClient::renderBlindfold);
    }

    private static void renderBlindfold(GuiGraphics graphics, DeltaTracker delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !player.getItemBySlot(EquipmentSlot.HEAD).is(PinataParty.BLINDFOLD.get())) {
            return;
        }
        double opacity = 0.96;
        if (Config.SPEC.isLoaded()) {
            opacity = Config.BLINDFOLD_OPACITY.get();
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int alpha = (int) Math.round(opacity * 255.0);
        graphics.fill(0, 0, w, h, (alpha << 24) | 0x0A0A12);

        // a darker rim, like cloth pressed against your face
        int rim = Math.max(8, h / 6);
        int rimAlpha = Math.min(255, alpha + 20);
        for (int i = 0; i < rim; i += 2) {
            int a = (int) (rimAlpha * (1.0 - (double) i / rim) * 0.5);
            int c = (a << 24);
            graphics.fill(0, i, w, i + 2, c);
            graphics.fill(0, h - i - 2, w, h - i, c);
        }
    }
}
