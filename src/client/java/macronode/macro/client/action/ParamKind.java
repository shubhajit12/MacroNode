package macronode.macro.client.action;

/** Value kinds for block parameters. Everything is stored as a String for simple JSON. */
public enum ParamKind {
	INT,
	FLOAT,
	STRING,
	/** Duration: "0t", "0.2t", "10ms", "1.5s". Bare numbers are ticks. */
	TIME,
	BOOL,
	ENUM
}
