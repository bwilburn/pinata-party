package network.wilburn.pinataparty.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import network.wilburn.pinataparty.PinataParty;
import network.wilburn.pinataparty.entity.PinataEntity;

public class PinataRenderer extends EntityRenderer<PinataEntity> {
    /** Model scale: the model is drawn at 80% of mob-pixel size. */
    private static final float SCALE = 0.8f;
    /** Height of the string's tie point above the entity's feet, in blocks. */
    private static final float TIE_HEIGHT = (24.0f - PinataModel.STRING_Y) / 16.0f * SCALE;
    private static final int MAX_STRING_BLOCKS = 8;

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[PinataEntity.VARIANTS];
    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = PinataParty.id("textures/entity/pinata/pinata_" + i + ".png");
        }
    }

    private final PinataModel model;

    public PinataRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PinataModel(context.bakeLayer(PinataModel.LAYER));
        this.shadowRadius = 0.35f;
    }

    @Override
    public void render(PinataEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        boolean hanging = entity.isHanging();
        float stringBlocks = hanging ? stringLength(entity) : 0.0f;
        float hurt = entity.getHurtTime() - partialTick;
        float knock = hurt > 0.0f ? Mth.sin(hurt * 1.3f) * hurt * 2.2f * entity.getHurtDir() : 0.0f;

        pose.pushPose();

        if (hanging) {
            // swing like a pendulum around the point the string hangs from, and turn slowly on it
            float pivot = TIE_HEIGHT + stringBlocks;
            pose.translate(0.0f, pivot, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - entity.getYRot() + Mth.sin(age * 0.021f) * 25.0f));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.07f) * 3.0f + knock));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.cos(age * 0.05f) * 2.0f));
            pose.translate(0.0f, -pivot, 0.0f);
        } else {
            // standing on something: stays put, just rocks on its feet when it's hit
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - entity.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(knock * 0.5f));
        }

        // the usual mob transform: model space has y down and feet at y = 24 px
        pose.scale(SCALE, SCALE, SCALE);
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);

        this.model.setStringLength(stringBlocks / SCALE * 16.0f);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0f), OverlayTexture.v(entity.getHurtTime() > 0));
        this.model.renderToBuffer(pose, buffers.getBuffer(this.model.renderType(getTextureLocation(entity))),
                packedLight, overlay, 0xFFFFFFFF);

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /** Blocks of string from the tie point up to the first solid block, or 0 if there's none close. */
    private static float stringLength(PinataEntity entity) {
        Level level = entity.level();
        double tieY = entity.getY() + TIE_HEIGHT;
        BlockPos.MutableBlockPos pos = BlockPos.containing(entity.getX(), tieY, entity.getZ()).mutable();
        for (int i = 0; i <= MAX_STRING_BLOCKS; i++) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                double bottom = pos.getY() + state.getCollisionShape(level, pos).min(net.minecraft.core.Direction.Axis.Y);
                return (float) Math.max(0.0, bottom - tieY);
            }
            pos.move(0, 1, 0);
        }
        return 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(PinataEntity entity) {
        return TEXTURES[Mth.clamp(entity.getVariant(), 0, TEXTURES.length - 1)];
    }
}
