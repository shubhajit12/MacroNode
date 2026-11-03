package macronode.macro.client.action;

import java.util.function.Predicate;

import macronode.macro.client.engine.ExecContext;
import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Advanced Logic & Branching. If / Else is interpreted by MacroRun: true continues along the
 * normal (right) link, false jumps to the chain wired to the red bottom port ({@code elseNext}).
 */
public final class LogicActions {
	private LogicActions() {
	}

	static void register() {
		ActionRegistry.register(ActionType.IF_ELSE, (ctx, b) -> Step.NEXT); // interpreted by MacroRun

		// WAITING while a block obstructs the line of sight to the target.
		ActionRegistry.register(ActionType.WAIT_LOS, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			double range = b.getDouble("range");
			Entity t = switch (b.get("target")) {
				case "PLAYER" -> ClientUtil.nearestPlayer(p, range, b.get("username"));
				case "NEAREST_ENTITY" -> ClientUtil.nearestLiving(p, range, false);
				default -> ClientUtil.nearestPlayer(p, range, null);
			};
			return Waits.until(ctx, b, t != null && p.hasLineOfSight(t));
		});
	}

	/** If / Else condition (before negation). Target conditions use the nearest player within "range". */
	public static boolean branch(ExecContext ctx, ActionBlock b) {
		LocalPlayer p = ctx.player();
		Player t = ClientUtil.nearestPlayer(p, b.getDouble("range"), null);
		double value = num(b.get("value"), 0);
		return switch (b.get("check")) {
			case "TARGET_HOLDING_SHIELD" -> t != null && (t.getMainHandItem().is(Items.SHIELD) || t.getOffhandItem().is(Items.SHIELD));
			case "TARGET_BLOCKING" -> t != null && t.isBlocking();
			case "TARGET_BURNING" -> t != null && t.isOnFire();
			case "TARGET_IN_RANGE" -> t != null && p.distanceTo(t) <= (value > 0 ? value : 3.5);
			case "TARGET_HEALTH_BELOW" -> t != null && t.getHealth() < value;
			case "TARGET_VISIBLE" -> t != null && p.hasLineOfSight(t);
			case "HAS_EFFECT" -> DefenseActions.effectActive(p, b.get("value"));
			default -> ControlActions.evaluateCondition(ctx, b); // HEALTH_BELOW, HOLDING_ITEM, HAS_ITEM, ON_GROUND, FALL_FLYING
		};
	}

	/** Check Item Count condition: total matching items in the chosen scope compared to N. */
	public static boolean itemCount(ExecContext ctx, ActionBlock b) {
		LocalPlayer p = ctx.player();
		Predicate<ItemStack> m = ClientUtil.itemMatcher(b.get("item"));
		int from = 0, to = 41; // 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand
		switch (b.get("scope")) {
			case "HOTBAR" -> to = 9;
			case "INVENTORY" -> {
				from = 9;
				to = 36;
			}
			default -> {
			}
		}
		int total = 0;
		for (int i = from; i < to; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (m.test(s)) total += s.getCount();
		}
		int n = b.getInt("count");
		return switch (b.get("compare")) {
			case "BELOW" -> total < n;
			case "EQUAL" -> total == n;
			default -> total > n;
		};
	}

	private static double num(String s, double def) {
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
