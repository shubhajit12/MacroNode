package macronode.macro.client.action;

import macronode.macro.client.engine.ExecContext;
import macronode.macro.client.engine.Step;

/** Shared "yield until condition" logic for sensor / reactive blocks. */
final class Waits {
	private Waits() {
	}

	/**
	 * Returns {@link Step#NEXT} once {@code condition} holds, otherwise {@link Step#WAITING} so the
	 * engine re-polls next frame. If the block's {@code timeout} (>0) expires, continues or stops
	 * the macro according to {@code onTimeout}.
	 */
	static Step until(ExecContext ctx, ActionBlock b, boolean condition) {
		if (condition) return Step.NEXT;
		if (timedOut(ctx, b)) return "STOP".equals(b.get("onTimeout")) ? Step.STOP : Step.NEXT;
		return Step.WAITING;
	}

	static boolean timedOut(ExecContext ctx, ActionBlock b) {
		long t = b.getNanos("timeout");
		return t > 0 && ctx.elapsedInAction() >= t;
	}
}
