package macronode.macro.client.action;

import java.util.List;

/** Declares one editable parameter of an action block. */
public record ParamDef(String key, String label, ParamKind kind, String defaultValue, List<String> options) {
	public static ParamDef integer(String key, String label, int def) {
		return new ParamDef(key, label, ParamKind.INT, Integer.toString(def), List.of());
	}

	public static ParamDef decimal(String key, String label, double def) {
		return new ParamDef(key, label, ParamKind.FLOAT, trim(def), List.of());
	}

	public static ParamDef text(String key, String label, String def) {
		return new ParamDef(key, label, ParamKind.STRING, def, List.of());
	}

	public static ParamDef time(String key, String label, String def) {
		return new ParamDef(key, label, ParamKind.TIME, def, List.of());
	}

	public static ParamDef bool(String key, String label, boolean def) {
		return new ParamDef(key, label, ParamKind.BOOL, Boolean.toString(def), List.of("true", "false"));
	}

	public static ParamDef choice(String key, String label, String def, String... options) {
		return new ParamDef(key, label, ParamKind.ENUM, def, List.of(options));
	}

	/** Safety cap for waiting blocks. "0" = wait forever. */
	public static ParamDef timeout() {
		return time("timeout", "Timeout (0=none)", "0");
	}

	/** What a waiting block does when its timeout expires. */
	public static ParamDef onTimeout() {
		return choice("onTimeout", "On timeout", "CONTINUE", "CONTINUE", "STOP");
	}

	private static String trim(double d) {
		return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
	}
}
