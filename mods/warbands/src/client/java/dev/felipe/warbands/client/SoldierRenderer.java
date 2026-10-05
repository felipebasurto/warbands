package dev.felipe.warbands.client;

import dev.felipe.warbands.entity.SoldierEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;

/**
 * Renders soldiers with the player model, a custom skin, and their real armor and weapons.
 * Raiders use the dark variant of the same skin ({@code raider_<unit>.png}, made by {@code tools/skins.py}).
 */
public class SoldierRenderer<T extends SoldierEntity> extends HumanoidMobRenderer<T, SoldierRenderer.State, HumanoidModel<SoldierRenderer.State>> {
	/** The humanoid state plus the faction, so the texture can follow it. */
	public static class State extends HumanoidRenderState {
		public boolean raider;
		public String campKind = "";
	}

	private final Identifier texture;
	private final Identifier raiderTexture;
	private final java.util.Map<String, Identifier> kindTextures = new java.util.HashMap<>();

	public SoldierRenderer(EntityRendererProvider.Context context, Identifier texture) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.texture = texture;
		String path = texture.getPath();
		int slash = path.lastIndexOf('/') + 1;
		this.raiderTexture = Identifier.fromNamespaceAndPath(texture.getNamespace(), path.substring(0, slash) + "raider_" + path.substring(slash));
		this.addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T soldier, State state, float partialTicks) {
		super.extractRenderState(soldier, state, partialTicks);
		state.raider = soldier.isRaider();
		state.campKind = soldier.campKind();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		if (!state.raider) {
			return texture;
		}
		return state.campKind.isEmpty() ? raiderTexture : kindTextures.computeIfAbsent(state.campKind, this::kindTexture);
	}

	/** {@code raider_<kind>_<unit>.png} when the kind has its own colors (bandits keep the plain kit). */
	private Identifier kindTexture(String kind) {
		String path = raiderTexture.getPath();
		int slash = path.lastIndexOf('/') + 1;
		Identifier own = Identifier.fromNamespaceAndPath(raiderTexture.getNamespace(),
				path.substring(0, slash) + "raider_" + kind + "_" + path.substring(slash + "raider_".length()));
		return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(own).isPresent() ? own : raiderTexture;
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(T mob, HumanoidArm arm) {
		if (mob.isAggressive()) {
			boolean mainArm = mob.getMainArm() == arm;
			// Hold the bow drawn while fighting, like skeletons do.
			if (mainArm && mob.getMainHandItem().is(Items.BOW)) {
				return HumanoidModel.ArmPose.BOW_AND_ARROW;
			}
			// Crossbow raised and aimed, like a pillager.
			if (mainArm && mob.getMainHandItem().is(Items.CROSSBOW)) {
				return HumanoidModel.ArmPose.CROSSBOW_HOLD;
			}
			// Combat stance: sword held forward, shield raised.
			if (mainArm && !mob.getMainHandItem().isEmpty()) {
				return HumanoidModel.ArmPose.ITEM;
			}
			if (!mainArm && mob.getOffhandItem().is(Items.SHIELD)) {
				return HumanoidModel.ArmPose.BLOCK;
			}
		}
		return super.getArmPose(mob, arm);
	}
}
