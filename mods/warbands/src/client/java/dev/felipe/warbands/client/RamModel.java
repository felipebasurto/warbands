package dev.felipe.warbands.client;

import dev.felipe.warbands.Warbands;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * A covered battering ram: a wheeled deck, four posts, a hide-covered A-frame roof, and an iron-capped
 * log hanging under it that sticks out the front. Forward is -Z; model y 24 is the ground.
 * Texture offsets must match {@code ram_texture()} in {@code tools/gen_textures.py} (128x128).
 */
public class RamModel extends EntityModel<LivingEntityRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warbands.id("ram"), "main");
	/** How far the roof panels slope down from the ridge, in radians. */
	private static final float ROOF_SLOPE = 0.6F;

	private final ModelPart[] wheels;
	private final ModelPart log;

	public RamModel(ModelPart root) {
		super(root);
		this.wheels = new ModelPart[] {
				root.getChild("wheel_front_left"), root.getChild("wheel_front_right"),
				root.getChild("wheel_back_left"), root.getChild("wheel_back_right")};
		this.log = root.getChild("log");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("deck", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -13.0F, 16.0F, 2.0F, 26.0F),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		CubeListBuilder post = CubeListBuilder.create().texOffs(84, 10).addBox(-1.0F, -16.0F, -1.0F, 2.0F, 8.0F, 2.0F);
		root.addOrReplaceChild("post_front_left", post, PartPose.offset(-7.0F, 24.0F, -11.0F));
		root.addOrReplaceChild("post_front_right", post, PartPose.offset(7.0F, 24.0F, -11.0F));
		root.addOrReplaceChild("post_back_left", post, PartPose.offset(-7.0F, 24.0F, 11.0F));
		root.addOrReplaceChild("post_back_right", post, PartPose.offset(7.0F, 24.0F, 11.0F));
		root.addOrReplaceChild("roof_left", CubeListBuilder.create().texOffs(0, 28).addBox(-11.0F, 0.0F, -14.0F, 11.0F, 1.0F, 28.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.0F, -ROOF_SLOPE));
		root.addOrReplaceChild("roof_right", CubeListBuilder.create().texOffs(0, 28).mirror().addBox(0.0F, 0.0F, -14.0F, 11.0F, 1.0F, 28.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.0F, ROOF_SLOPE));
		root.addOrReplaceChild("ridge", CubeListBuilder.create().texOffs(0, 96).addBox(-1.0F, -1.0F, -14.0F, 2.0F, 2.0F, 28.0F),
				PartPose.offset(0.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("log", CubeListBuilder.create()
						.texOffs(0, 58).addBox(-2.0F, -2.0F, -20.0F, 4.0F, 4.0F, 32.0F)
						.texOffs(84, 0).addBox(-3.0F, -3.0F, -23.0F, 6.0F, 6.0F, 3.0F),
				PartPose.offset(0.0F, 12.0F, 0.0F));
		CubeListBuilder wheel = CubeListBuilder.create().texOffs(84, 26).addBox(-1.0F, -3.5F, -3.5F, 2.0F, 7.0F, 7.0F);
		root.addOrReplaceChild("wheel_front_left", wheel, PartPose.offset(-9.0F, 20.5F, -8.0F));
		root.addOrReplaceChild("wheel_front_right", wheel, PartPose.offset(9.0F, 20.5F, -8.0F));
		root.addOrReplaceChild("wheel_back_left", wheel, PartPose.offset(-9.0F, 20.5F, 8.0F));
		root.addOrReplaceChild("wheel_back_right", wheel, PartPose.offset(9.0F, 20.5F, 8.0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		for (ModelPart wheel : this.wheels) {
			wheel.xRot = state.walkAnimationPos;
		}
		// The log swings a little on its ropes while the ram rolls.
		this.log.z = (float) Math.sin(state.walkAnimationPos * 0.5F) * state.walkAnimationSpeed * 1.5F;
	}
}
