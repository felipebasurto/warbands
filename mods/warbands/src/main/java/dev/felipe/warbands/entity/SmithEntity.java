package dev.felipe.warbands.entity;

import dev.felipe.warbands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The smith who works the Blacksmith's anvil. Drawn with the villager model and the armorer's apron,
 * but deliberately not a Villager: no job site or bed claims, no trades, no gossip, no iron golem
 * spawning, no raids or zombie conversion. It stands at the anvil, hammers now and then, and tells the
 * player where upgrades are bought. Scenery rules in {@link BuildingDecor}.
 */
public class SmithEntity extends PathfinderMob {
	private @Nullable BlockPos anchor;

	public SmithEntity(EntityType<? extends SmithEntity> type, Level level) {
		super(type, level);
		this.setInvulnerable(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	void setAnchor(BlockPos anchor) {
		this.anchor = anchor.immutable();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel)) {
			return;
		}
		if (this.tickCount % 40 == 0 && BuildingDecor.anchorGone(this.level(), anchor, ModBlocks.BLACKSMITH)) {
			this.discard();
			return;
		}
		if (this.random.nextInt(140) == 0) {
			this.playSound(SoundEvents.ANVIL_USE, 0.25F, 0.9F + this.random.nextFloat() * 0.2F);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.level() instanceof ServerLevel) {
			player.sendOverlayMessage(Component.translatable("message.warbands.smith_talk"));
			this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("Anchor", BlockPos.CODEC, anchor);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.anchor = input.read("Anchor", BlockPos.CODEC).orElse(null);
	}
}
