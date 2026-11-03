package macronode.macro.client.action;

import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Reactive Combat & Sensors: every block here polls world state each frame instead of sleeping blindly. */
final class ReactiveCombatActions {
	private ReactiveCombatActions() {
	}

	static void register() {
		// WAITING until a target is inside maxRange with clear line of sight, then swing instantly.
		ActionRegistry.register(ActionType.ATTACK_IN_RANGE, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			double range = b.getDouble("maxRange");
			Entity target = "LIVING".equals(b.get("target"))
					? ClientUtil.nearestLiving(p, range, false)
					: ClientUtil.nearestPlayer(p, range, null);
			boolean confirmed = target != null && p.distanceTo(target) <= range && p.hasLineOfSight(target);
			if (confirmed && !b.getBool("aim")) {
				// Without aim assist the crosshair raycast itself must be on the target.
				confirmed = ctx.mc.crosshairPickEntity == target;
			}
			if (confirmed && ctx.mc.gameMode != null) {
				if (b.getBool("aim")) ClientUtil.lookAt(p, ClientUtil.aimPoint(target));
				ctx.mc.gameMode.attack(p, target);
				p.swing(InteractionHand.MAIN_HAND);
				return Step.NEXT;
			}
			return Waits.timedOut(ctx, b) ? Step.NEXT : Step.WAITING;
		});

		ActionRegistry.register(ActionType.WAIT_CROSSHAIR_ENTITY, (ctx, b) -> {
			Entity e = ctx.mc.crosshairPickEntity;
			boolean hit = switch (b.get("target")) {
				case "PLAYER" -> e instanceof Player && e != ctx.player();
				case "LIVING" -> e instanceof LivingEntity && e.isAlive();
				default -> e != null;
			};
			return Waits.until(ctx, b, hit);
		});

		ActionRegistry.register(ActionType.WAIT_HEALTH_BELOW, (ctx, b) ->
				Waits.until(ctx, b, ctx.player().getHealth() < b.getDouble("value")));

		ActionRegistry.register(ActionType.WAIT_ABSORPTION, (ctx, b) ->
				Waits.until(ctx, b, ctx.player().getAbsorptionAmount() > 0));
	}
}
