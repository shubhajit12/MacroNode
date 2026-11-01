package macronode.macro.client.engine;

/**
 * Result of executing one step of an action.
 * <ul>
 *   <li>{@link Kind#NEXT}: advance immediately (same frame).</li>
 *   <li>{@link Kind#NEXT_AFTER}: advance, then sleep N nanos. 0 = zero-tick, no yield.</li>
 *   <li>{@link Kind#REPEAT_AFTER}: re-invoke the same action after N nanos. ALWAYS yields to the next pump,
 *       so stateful actions can poll world state frame-by-frame.</li>
 *   <li>{@link Kind#STOP}: terminate the macro.</li>
 * </ul>
 */
public record Step(Kind kind, long nanos) {
	public enum Kind { NEXT, NEXT_AFTER, REPEAT_AFTER, STOP }

	public static final Step NEXT = new Step(Kind.NEXT, 0);
	public static final Step STOP = new Step(Kind.STOP, 0);
	/** WAITING: condition not met yet - yield and poll again next frame/tick (no blind delay). */
	public static final Step WAITING = new Step(Kind.REPEAT_AFTER, 0);

	public static Step after(long nanos) {
		return new Step(Kind.NEXT_AFTER, Math.max(0, nanos));
	}

	public static Step repeat(long nanos) {
		return new Step(Kind.REPEAT_AFTER, Math.max(0, nanos));
	}

	public static Step repeatNextFrame() {
		return new Step(Kind.REPEAT_AFTER, 0);
	}
}
