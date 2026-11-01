package macronode.macro.client.action;

import macronode.macro.client.gui.Theme;

/** Palette categories shown in the node editor. Each has an accent color for node headers. */
public enum ActionCategory {
	TARGETING("Targeting & Camera", 0xFF06B6D4),
	COMBAT("Combat & Player Action", 0xFFEF4444),
	DEFENSE("Defense & Survival", 0xFF3B82F6),
	INVENTORY("Inventory & Equipment", 0xFFF59E0B),
	MOVEMENT("Movement & World", 0xFF10B981),
	CONTROL("Timing & Control Logic", Theme.VIOLET);

	public final String label;
	public final int color;

	ActionCategory(String label, int color) {
		this.label = label;
		this.color = color;
	}
}
