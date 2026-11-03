package macronode.macro.client.action;

import java.util.EnumMap;
import java.util.Map;

import macronode.macro.client.engine.ExecContext;
import macronode.macro.client.engine.HeldKeys;
import macronode.macro.client.engine.Step;
import net.minecraft.client.KeyMapping;

/** Maps every {@link ActionType} to its handler. Handlers are grouped per category class. */
public final class ActionRegistry {
	private static final Map<ActionType, ActionHandler> HANDLERS = new EnumMap<>(ActionType.class);

	static {
		TargetingActions.register();
		CombatActions.register();
		InventoryActions.register();
		MovementActions.register();
		ControlActions.register();
		ReactiveCombatActions.register();
		DefenseActions.register();
		InventoryIntelActions.register();
		PrecisionMovementActions.register();
		LogicActions.register();
	}

	private ActionRegistry() {
	}

	static void register(ActionType type, ActionHandler handler) {
		HANDLERS.put(type, handler);
	}

	public static Step execute(ActionType type, ExecContext ctx, ActionBlock block) {
		ActionHandler h = type == null ? null : HANDLERS.get(type);
		return h == null ? Step.NEXT : h.run(ctx, block);
	}

	/** Shared helper: hold a key for a duration, blocking the chain until released. */
	static Step holdFor(ExecContext ctx, KeyMapping key, long nanos) {
		if (ctx.stage == 0) {
			ctx.hold(key, HeldKeys.FOREVER);
			ctx.setStage(1);
			return Step.repeat(nanos);
		}
		ctx.release(key);
		return Step.NEXT;
	}

	/** Shared helper: toggle / hold / release for sneak & sprint style keys. */
	static Step holdMode(ExecContext ctx, ActionBlock b, KeyMapping key) {
		switch (b.get("mode")) {
			case "TOGGLE" -> {
				if (HeldKeys.get().isToggled(key)) HeldKeys.get().release(key);
				else HeldKeys.get().hold(key, HeldKeys.FOREVER, null);
				return Step.NEXT;
			}
			case "RELEASE" -> {
				HeldKeys.get().release(key);
				return Step.NEXT;
			}
			default -> {
				return holdFor(ctx, key, b.getNanos("duration"));
			}
		}
	}
}
