package network.wilburn.pinataparty.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import network.wilburn.pinataparty.PinataParty;
import network.wilburn.pinataparty.entity.PinataEntity;

/**
 * A classic donkey-shaped pinata, in model pixels (y grows downward, feet at y = 24,
 * head toward -z). The texture layout is mirrored in tools/make_textures.py.
 */
public class PinataModel extends HierarchicalModel<PinataEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PinataParty.id("pinata"), "main");

    /** Where the string ties on, in model pixels: top of the back, middle of the body. */
    public static final float STRING_Y = 11.0f;

    private final ModelPart root;
    private final ModelPart string;

    public PinataModel(ModelPart root) {
        this.root = root;
        this.string = root.getChild("string");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();

        p.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5f, 11.0f, -6.0f, 7, 8, 12), PartPose.ZERO);
        p.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-2.0f, 3.0f, -7.0f, 4, 9, 4), PartPose.ZERO);
        p.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 21)
                .addBox(-3.0f, -2.0f, -12.0f, 6, 5, 8), PartPose.ZERO);
        p.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(30, 21)
                .addBox(-2.5f, -5.0f, -6.0f, 2, 3, 1), PartPose.ZERO);
        p.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(38, 21)
                .addBox(0.5f, -5.0f, -6.0f, 2, 3, 1), PartPose.ZERO);
        p.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(30, 27)
                .addBox(-1.0f, 12.0f, 6.0f, 2, 2, 4), PartPose.ZERO);

        CubeListBuilder frontLeg = CubeListBuilder.create().texOffs(0, 36).addBox(-1.5f, 0.0f, -1.5f, 3, 5, 3);
        CubeListBuilder backLeg = CubeListBuilder.create().texOffs(14, 36).addBox(-1.5f, 0.0f, -1.5f, 3, 5, 3);
        p.addOrReplaceChild("leg_front_left", frontLeg, PartPose.offset(-2.0f, 19.0f, -4.0f));
        p.addOrReplaceChild("leg_front_right", frontLeg, PartPose.offset(2.0f, 19.0f, -4.0f));
        p.addOrReplaceChild("leg_back_left", backLeg, PartPose.offset(-2.0f, 19.0f, 4.0f));
        p.addOrReplaceChild("leg_back_right", backLeg, PartPose.offset(2.0f, 19.0f, 4.0f));

        // 16 px of string going straight up from the back; the renderer stretches it to the ceiling
        p.addOrReplaceChild("string", CubeListBuilder.create().texOffs(58, 0)
                .addBox(-0.5f, -16.0f, -0.5f, 1, 16, 1), PartPose.offset(0.0f, STRING_Y, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Stretch the string; 0 hides it. Length is in model pixels. */
    public void setStringLength(float pixels) {
        this.string.visible = pixels > 0.1f;
        this.string.yScale = Math.max(pixels, 0.0f) / 16.0f;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(PinataEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // all the motion is done on the pose stack in PinataRenderer
    }
}
