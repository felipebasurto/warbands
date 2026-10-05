package dev.felipe.warbands.gametest;

import static dev.felipe.warbands.gametest.Run.activeRaiders;
import static dev.felipe.warbands.gametest.Run.army;
import static dev.felipe.warbands.gametest.Run.check;
import static dev.felipe.warbands.gametest.Run.player;
import static dev.felipe.warbands.gametest.Run.raider;
import static dev.felipe.warbands.gametest.Run.soldier;
import static dev.felipe.warbands.gametest.Run.townCenter;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import dev.felipe.warbands.gametest.WarbandsClientGameTest.Scenario;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.raid.RaidComposition;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.ArmyOrders;
import dev.felipe.warbands.town.UnitType;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Who fights whom: friendly fire, targeting, Hold discipline, and a whole army against a whole raid. */
final class BattleScenarios {
	private BattleScenarios() {
	}

	static void register(List<Scenario> all) {
		all.add(new Scenario("battles.friendly-fire", "raiders can't hurt raiders, you and your soldiers can't hurt each other, both sides can hurt the other",
				BattleScenarios::friendlyFire));
		all.add(new Scenario("battles.targets", "raiders go for you and your soldiers, never each other; your soldiers go for raiders in Follow",
				BattleScenarios::targets));
		all.add(new Scenario("battles.horn-orders", "the War Horn orders the whole army, far soldiers too, and a new recruit starts under that order",
				BattleScenarios::hornOrders));
		all.add(new Scenario("battles.hold", "soldiers on Hold fight raiders that come close but don't chase one standing off", BattleScenarios::hold));
		all.add(new Scenario("battles.army-vs-raid", "a real fight: 8 of your soldiers at the Town Center beat a Feudal raid, and it pays",
				BattleScenarios::armyVsRaid));
	}

	private static void friendlyFire(Run run) {
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Vec3 at = player.position().add(0, 0, -6);
			SoldierEntity mine = soldier(level, UnitType.MILITIA, at, player);
			SoldierEntity mine2 = soldier(level, UnitType.ARCHER, at.add(1, 0, 0), player);
			SoldierEntity enemy = raider(level, UnitType.MILITIA, at.add(0, 0, -4), Age.FEUDAL, null);
			SoldierEntity enemy2 = raider(level, UnitType.ARCHER, at.add(1, 0, -4), Age.FEUDAL, null);
			for (SoldierEntity s : List.of(mine, mine2, enemy, enemy2)) {
				s.setNoAi(true);
			}
			check(SoldierEntity.isFriendlyFire(enemy, enemy2) && SoldierEntity.isFriendlyFire(enemy2, enemy), "raiders can't hurt each other");
			check(SoldierEntity.isFriendlyFire(mine, mine2) && SoldierEntity.isFriendlyFire(mine2, mine), "your soldiers can't hurt each other");
			check(SoldierEntity.isFriendlyFire(mine, player) && SoldierEntity.isFriendlyFire(player, mine), "you and your soldiers can't hurt each other");
			check(!SoldierEntity.isFriendlyFire(enemy, mine) && !SoldierEntity.isFriendlyFire(mine, enemy), "your soldiers and raiders hurt each other");
			check(!SoldierEntity.isFriendlyFire(player, enemy) && !SoldierEntity.isFriendlyFire(enemy, player), "you and raiders hurt each other");
			check(SoldierEntity.isRaiderSide(enemy) && !SoldierEntity.isRaiderSide(mine), "sides are told apart");
		});
	}

	private static void targets(Run run) {
		BlockPos ground = run.ground;
		UUID[] ids = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Vec3 at = player.position();
			SoldierEntity mine = soldier(level, UnitType.MILITIA, at.add(3, 0, -4), player);
			SoldierEntity enemy = raider(level, UnitType.MILITIA, at.add(-3, 0, -10), Age.FEUDAL, null);
			enemy.setNoAi(true); // stands still, so it's the soldier who has to pick it
			// Two raiders alone, 60 blocks away from everyone: they must not turn on each other.
			Vec3 far = Vec3.atBottomCenterOf(ground.offset(60, 1, -40));
			SoldierEntity a = raider(level, UnitType.MILITIA, far, Age.FEUDAL, null);
			SoldierEntity b = raider(level, UnitType.ARCHER, far.add(3, 0, 0), Age.FEUDAL, null);
			return new UUID[] {mine.getUUID(), enemy.getUUID(), a.getUUID(), b.getUUID()};
		});
		run.waitFor(server -> server.overworld().getEntity(ids[0]) instanceof SoldierEntity mine && mine.getTarget() != null
				&& mine.getTarget().getUUID().equals(ids[1]), "your soldier on Follow picks the raider as its target", 100);
		run.server(server -> {
			if (server.overworld().getEntity(ids[1]) instanceof SoldierEntity enemy) {
				enemy.setNoAi(false);
			}
		});
		run.waitFor(server -> server.overworld().getEntity(ids[1]) instanceof SoldierEntity enemy && enemy.getTarget() != null
				&& !SoldierEntity.isRaiderSide(enemy.getTarget()), "a raider goes for you or your soldiers", 100);
		run.holds(server -> List.of(ids[2], ids[3]).stream().allMatch(id -> !(server.overworld().getEntity(id) instanceof SoldierEntity s)
				|| s.getTarget() == null || !SoldierEntity.isRaiderSide(s.getTarget())), "raiders never target each other", 60);
	}

	private static void hornOrders(Run run) {
		BlockPos ground = run.ground;
		UUID[] ids = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			SoldierEntity near = soldier(level, UnitType.MILITIA, Vec3.atBottomCenterOf(ground.offset(2, 1, 0)), player);
			SoldierEntity far = soldier(level, UnitType.ARCHER, Vec3.atBottomCenterOf(ground.offset(60, 1, 0)), player);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WAR_HORN));
			ModItems.WAR_HORN.use(level, player, InteractionHand.MAIN_HAND);
			check(ArmyOrders.get(server, player.getUUID()) == Stance.HOLD, "one blow: the army's order is Hold");
			check(near.stance() == Stance.HOLD && far.stance() == Stance.HOLD, "near and far soldiers both hold (60 blocks away too)");
			SoldierEntity recruit = soldier(level, UnitType.MILITIA, Vec3.atBottomCenterOf(ground.offset(-2, 1, 0)), player);
			return new UUID[] {recruit.getUUID()};
		});
		run.waitFor(server -> server.overworld().getEntity(ids[0]) instanceof SoldierEntity recruit && recruit.stance() == Stance.HOLD,
				"a soldier trained after the horn takes up Hold", 60);
		// A wrong order is fixed at once: the horn's cooldown is a quarter second, and Shift goes back one.
		run.waitFor(server -> !player(server).getCooldowns().isOnCooldown(player(server).getMainHandItem()),
				"the horn is ready again within a second", 20);
		run.server(server -> {
			ServerPlayer player = player(server);
			player.setShiftKeyDown(true);
			ModItems.WAR_HORN.use(server.overworld(), player, InteractionHand.MAIN_HAND);
			player.setShiftKeyDown(false);
			player.stopUsingItem();
			check(ArmyOrders.get(server, player.getUUID()) == Stance.FOLLOW, "Shift + blow goes back one order: Hold -> Follow");
		});
	}

	private static void hold(Run run) {
		BlockPos ground = run.ground;
		BlockPos post = ground.offset(0, 1, 0);
		UUID[] ids = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			SoldierEntity guard = soldier(level, UnitType.MILITIA, Vec3.atBottomCenterOf(post), player);
			ArmyOrders.set(server, player.getUUID(), Stance.HOLD); // soldiers follow the army's order
			guard.command(Stance.HOLD);
			// Standing off 20 blocks away (outside the 12-block Hold area), in plain sight.
			SoldierEntity far = raider(level, UnitType.MILITIA, Vec3.atBottomCenterOf(post.offset(0, 0, -20)), Age.FEUDAL, null);
			far.setNoAi(true);
			return new UUID[] {guard.getUUID(), far.getUUID()};
		});
		run.holds(server -> server.overworld().getEntity(ids[0]) instanceof SoldierEntity guard && guard.blockPosition().distSqr(post) <= 14 * 14,
				"a soldier on Hold doesn't chase a raider outside its area", 100);
		UUID near = run.compute(server -> {
			SoldierEntity raider = raider(server.overworld(), UnitType.MILITIA, Vec3.atBottomCenterOf(post.offset(4, 0, -4)), Age.FEUDAL, null);
			raider.setNoAi(true);
			return raider.getUUID();
		});
		for (int i = 0; ; i++) {
			if (run.compute(server -> !(server.overworld().getEntity(near) instanceof LivingEntity r) || !r.isAlive())) {
				check(true, "a soldier on Hold kills a raider that comes into its area");
				break;
			}
			if (i >= 20 * 30) {
				String state = run.compute(server -> {
					ServerLevel level = server.overworld();
					SoldierEntity guard = (SoldierEntity) level.getEntity(ids[0]);
					LivingEntity target = guard.getTarget();
					return "guard at " + guard.blockPosition().subtract(post) + ", home " + guard.getHomePosition() + " r" + guard.getHomeRadius()
							+ ", target " + (target == null ? "none" : target.getName().getString() + " at " + target.blockPosition().subtract(post)
							+ (target.getUUID().equals(ids[1]) ? " (the far one)" : ""))
							+ ", near raider at " + level.getEntity(near).blockPosition().subtract(post);
				});
				throw new AssertionError("Timed out: a soldier on Hold kills a raider that comes into its area; " + state);
			}
			run.context.waitTick();
		}
		run.server(server -> check(server.overworld().getEntity(ids[0]) instanceof SoldierEntity guard && guard.stance() == Stance.HOLD
				&& guard.blockPosition().distSqr(post) <= 14 * 14, "and stays on Hold at its post"));
	}

	private static void armyVsRaid(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Vec3 at = Vec3.atBottomCenterOf(town.center().offset(0, 1, 8));
			for (UnitType type : List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA,
					UnitType.SPEARMAN, UnitType.SPEARMAN, UnitType.ARCHER, UnitType.ARCHER)) {
				soldier(level, type, at, player);
			}
		});
		int gold = run.compute(server -> townCenter(server.overworld(), core).stock(Resource.GOLD));
		run.server(server -> {
			check(Raids.startNow(server.overworld(), core), "a raid starts");
			Raids.skipWarning(server.overworld(), core);
		});
		run.waitFor(server -> !activeRaiders(server, core).isEmpty(), "the raiders arrive", 100);
		long start = System.currentTimeMillis();
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core), "the fight is over", 20 * 150);
		run.server(server -> {
			ServerLevel level = server.overworld();
			int survivors = army(server).size();
			Warbands.LOGGER.info("[gametest] battle: {} of 8 soldiers survived, {} s", survivors, (System.currentTimeMillis() - start) / 1000);
			check(RaidRegistry.get(level, core).orElseThrow().won() == 1, "your army beat the raid (not plundered, not timed out)");
			int paid = townCenter(level, core).stock(Resource.GOLD) - gold;
			check(paid == Raids.sizeFor(level, core, Age.FEUDAL, 0, false) * RaidComposition.goldPerRaider(Age.FEUDAL),
					"the won raid paid 15 gold per raider (" + paid + ")");
			check(survivors > 0, "some of your soldiers survived (" + survivors + ")");
		});
	}
}
