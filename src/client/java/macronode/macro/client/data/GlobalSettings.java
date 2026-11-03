package macronode.macro.client.data;

/** Global settings, stored in config/macronode/settings.json. */
public class GlobalSettings {
	/** Master switch: when false no macro triggers fire and no input is overridden. */
	public boolean masterEnabled = true;
	/** Action-bar feedback when macros start/stop. */
	public boolean feedback = true;
	/** Snap nodes to grid in the editor. */
	public boolean snapToGrid = true;
	public int gridSize = 10;
	/** Allow several macros to run at the same time. */
	public boolean allowConcurrent = true;
	/** Profile last opened in the [E] editor. */
	public String lastEditedProfile = "";
}
