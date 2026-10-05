package dev.felipe.warbands.client;

import dev.felipe.warbands.entity.SmithEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * Draws the smith as a plains villager in the armorer's apron and welding mask, using vanilla's villager
 * model and profession layer. Only the looks are borrowed: the entity is not a Villager.
 */
public class SmithRenderer extends MobRenderer<SmithEntity, VillagerRenderState, VillagerModel> {
	private static final Identifier BASE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

	public SmithRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
		this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager",
				new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT)),
				new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT))));
	}

	@Override
	public Identifier getTextureLocation(VillagerRenderState state) {
		return BASE;
	}

	@Override
	public VillagerRenderState createRenderState() {
		return new VillagerRenderState();
	}

	@Override
	public void extractRenderState(SmithEntity entity, VillagerRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		var registries = entity.level().registryAccess();
		state.villagerData = new VillagerData(
				registries.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
				registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.ARMORER),
				3);
	}
}
