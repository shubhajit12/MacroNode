package macronode.macro.client.action;

import macronode.macro.client.engine.ExecContext;
import macronode.macro.client.engine.Step;
import macronode.macro.client.engine.TimeSpec;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * Timing & Control Logic. Waits are handled here; Repeat Loop and Conditional Check are
 * interpreted by {@link macronode.macro.client.engine.MacroRun} because they move the program counter.
 */
public final class ControlActions {
	private ControlActions() {
	}

	static void register() {
		ActionRegistry.register(ActionType.WAIT_TICKS, (ctx, b) -> Step.after(b.getNanos("ticks")));
		ActionRegistry.register(ActionType.WAIT_MS, (ctx, b) -> Step.after(Math.round(b.getDouble("ms") * TimeSpec.NANOS_PER_MS)));
		// Control blocks never reach the registry, but keep a safe fallback.
		ActionRegistry.register(ActionType.REPEAT, (ctx, b) -> Step.NEXT);
		ActionRegistry.register(ActionType.CONDITION, (ctx, b) -> Step.NEXT);
	}

	/** Evaluates any condition-style block (before negation). */
	public static boolean evaluate(ExecContext ctx, ActionBlock b) {
		return switch (b.actionType()) {
			case CHECK_ITEM_COUNT -> LogicActions.itemCount(ctx, b);
			case IF_ELSE -> LogicActions.branch(ctx, b);
			default -> evaluateCondition(ctx, b);
		};
	}

	/** Evaluates a Conditional Check style "check"/"value" pair (before negation). */
	public static boolean evaluateCondition(ExecContext ctx, ActionBlock b) {
		LocalPlayer p = ctx.player();
		if (p == null) return false;
		String value = b.get("value").trim();
		return switch (b.get("check")) {
			case "HEALTH_BELOW" -> p.getHealth() < num(value, 10);
			case "HEALTH_ABOVE" -> p.getHealth() > num(value, 10);
			case "HOLDING_ITEM" -> {
				var m = ClientUtil.itemMatcher(value);
				yield m.test(p.getMainHandItem()) || m.test(p.getOffhandItem());
			}
			case "HAS_ITEM" -> ClientUtil.findInventory(p, ClientUtil.itemMatcher(value), 0, 41) >= 0;
			case "ON_GROUND" -> p.onGround();
			case "PLAYER_NEARBY" -> ClientUtil.nearestPlayer(p, num(value, 8), null) != null;
			case "USING_ITEM" -> p.isUsingItem();
			case "FALL_FLYING" -> p.isFallFlying();
			default -> false;
		};
	}

	private static double num(String s, double def) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
