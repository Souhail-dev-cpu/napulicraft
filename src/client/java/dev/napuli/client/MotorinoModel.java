package dev.napuli.client;

import dev.napuli.Napuli;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Ground at y=24, front at negative z. UV layout is painted by GenNewTextures. */
public class MotorinoModel extends EntityModel<MotorinoModel.State> {
    public static final class State extends LivingEntityRenderState {boolean police;}
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Napuli.id("motorino"), "main");
    private final ModelPart frontWheel;
    private final ModelPart rearWheel;

    public MotorinoModel(ModelPart root) {
        super(root);
        frontWheel = root.getChild("front_wheel");
        rearWheel = root.getChild("rear_wheel");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("front_wheel", wheel(), PartPose.offset(0, 20, -12));
        root.addOrReplaceChild("rear_wheel", wheel(), PartPose.offset(0, 20, 13));
        box(root, "deck", 0, 18, -5, 17, -11, 10, 2, 16);
        box(root, "body", 54, 18, -6, 9, 1, 12, 9, 18);
        box(root, "driver_seat", 0, 40, -4, 7, -3, 8, 2, 10);
        box(root, "passenger_seat", 0, 40, -4, 7, 7, 8, 2, 11);
        box(root, "shield", 76, 0, -6, 4, -12, 12, 14, 3);
        box(root, "fork", 24, 0, -2, 13, -14, 4, 6, 3);
        box(root, "column", 40, 0, -1, 0, -12, 2, 5, 3);
        box(root, "handlebar", 40, 12, -8, 0, -13, 16, 2, 2);
        box(root, "headlight", 108, 0, -2.5f, 2, -14, 5, 4, 2);
        box(root, "tail_light", 108, 8, -2.5f, 11, 19, 5, 2, 1);
        box(root, "grab_rail", 72, 54, -4, 6, 18, 8, 3, 2);
        box(root, "exhaust", 72, 62, 5, 15, 6, 2, 3, 13);
        box(root, "left_mirror_stem", 40, 0, -8, -4, -12, 1, 5, 1);
        box(root, "right_mirror_stem", 40, 0, 7, -4, -12, 1, 5, 1);
        box(root, "left_mirror", 108, 0, -10, -5, -13, 4, 2, 1);
        box(root, "right_mirror", 108, 0, 6, -5, -13, 4, 2, 1);
        box(root, "front_fender", 76, 0, -3, 15, -17, 6, 1, 10);
        box(root, "rear_fender", 54, 18, -3, 15, 9, 6, 1, 10);
        box(root, "plate", 108, 0, -3, 14, 20, 6, 3, 1);
        box(root, "beacon", 54, 18, -2, -2, 15, 4, 4, 4);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static CubeListBuilder wheel() {
        return CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -4, -4, 3, 8, 8);
    }

    private static void box(PartDefinition root, String name, int u, int v, float x, float y, float z,
                            int width, int height, int depth) {
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth), PartPose.ZERO);
    }

    @Override
    public void setupAnim(State state) {
        super.setupAnim(state);
        root.xScale=root.yScale=root.zScale=1.12f;
        root.y=24*(1-1.12f);
        var beacon=root.getChild("beacon");beacon.visible=state.police;beacon.yScale=1+(float)Math.sin(state.ageInTicks*.6)*.15f;
        float spin = state.walkAnimationPos * 1.4f;
        frontWheel.xRot = spin;
        rearWheel.xRot = spin;
    }
}
