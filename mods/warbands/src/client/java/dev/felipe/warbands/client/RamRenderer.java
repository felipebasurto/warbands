package dev.felipe.warbands.client;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.entity.RamEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Renders the battering ram as a timber cart, not a player skin. The raiders' ram has a black-and-crimson roof. */
public class RamRenderer extends MobRenderer<RamEntity, RamRenderer.State, RamModel> {
	private static final Identifier TEXTURE = Warbands.id("textures/entity/ram.png");
	private static final Identifier RAIDER_TEXTURE = Warbands.id("textures/entity/raider_ram.png");

	/** The living state plus the faction, so the texture can follow it. */
	public static class State extends LivingEntityRenderState {
		public boolean raider;
	}

	public RamRenderer(EntityRendererProvider.Context context) {
		super(context, new RamModel(context.bakeLayer(RamModel.LAYER)), 0.7F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RamEntity ram, State state, float partialTicks) {
		super.extractRenderState(ram, state, partialTicks);
		state.raider = ram.isRaider();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.raider ? RAIDER_TEXTURE : TEXTURE;
	}
}
