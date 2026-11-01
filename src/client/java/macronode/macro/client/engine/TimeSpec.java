package macronode.macro.client.engine;

import java.util.Locale;

/**
 * Parses MacroNode duration strings into nanoseconds.
 * <ul>
 *   <li>{@code 0t}   - zero-tick: executes in the same frame, no yield.</li>
 *   <li>{@code 0.2t} - fractional tick: 0.2 * 50ms = 10ms (sub-tick, resolved per rendered frame).</li>
 *   <li>{@code 10ms} - milliseconds.</li>
 *   <li>{@code 1.5s} - seconds.</li>
 *   <li>bare number   - ticks.</li>
 * </ul>
 */
public final class TimeSpec {
	public static final long NANOS_PER_TICK = 50_000_000L;
	public static final long NANOS_PER_MS = 1_000_000L;

	private TimeSpec() {
	}

	public static long parseNanos(String spec) {
		if (spec == null) return 0;
		String s = spec.trim().toLowerCase(Locale.ROOT).replace(" ", "");
		if (s.isEmpty()) return 0;
		try {
			if (s.endsWith("ms")) return Math.max(0, Math.round(Double.parseDouble(s.substring(0, s.length() - 2)) * NANOS_PER_MS));
			if (s.endsWith("t")) return Math.max(0, Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * NANOS_PER_TICK));
			if (s.endsWith("s")) return Math.max(0, Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000_000_000L));
			return Math.max(0, Math.round(Double.parseDouble(s) * NANOS_PER_TICK));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static boolean isValid(String spec) {
		if (spec == null) return false;
		return spec.trim().toLowerCase(Locale.ROOT).matches("\\d*\\.?\\d+\\s*(ms|t|s)?");
	}

	/** Normalizes display: "0.2" -> "0.2t". */
	public static String pretty(String spec) {
		if (spec == null || spec.isBlank()) return "0t";
		String s = spec.trim();
		return s.matches("\\d*\\.?\\d+") ? s + "t" : s;
	}

	public static String formatNanos(long nanos) {
		if (nanos % NANOS_PER_TICK == 0) return (nanos / NANOS_PER_TICK) + "t";
		double ms = nanos / (double) NANOS_PER_MS;
		return (ms == Math.rint(ms) ? Long.toString((long) ms) : String.format(Locale.ROOT, "%.1f", ms)) + "ms";
	}
}
