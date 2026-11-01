package macronode.macro.client.action;

import java.util.LinkedHashMap;
import java.util.Map;

import macronode.macro.client.engine.TimeSpec;

/**
 * A single node in a macro graph. Serialized directly by Gson.
 * <p>
 * Graph model: each block points at the block executed after it via {@link #next}
 * (-1 = end of chain). The profile's {@code rootId} is the block wired to the Start node.
 */
public class ActionBlock {
	public int id;
	public String type;
	public Map<String, String> params = new LinkedHashMap<>();
	/** Canvas position (editor only). */
	public int x;
	public int y;
	public int next = -1;
	/** If / Else nodes only: chain executed when the condition is false (-1 = end macro). */
	public int elseNext = -1;

	public ActionBlock() {
	}

	public ActionBlock(int id, ActionType type) {
		this.id = id;
		this.type = type.name();
		for (ParamDef p : type.params) params.put(p.key(), p.defaultValue());
	}

	public ActionType actionType() {
		return ActionType.byName(type);
	}

	public ActionBlock with(String key, String value) {
		params.put(key, value);
		return this;
	}

	public String get(String key) {
		String v = params.get(key);
		if (v != null) return v;
		ActionType t = actionType();
		ParamDef def = t == null ? null : t.param(key);
		return def == null ? "" : def.defaultValue();
	}

	public int getInt(String key) {
		try {
			return (int) Math.round(Double.parseDouble(get(key).trim()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public double getDouble(String key) {
		try {
			return Double.parseDouble(get(key).trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public float getFloat(String key) {
		try {
			return Float.parseFloat(get(key).trim());
		} catch (NumberFormatException e) {
			return 0f;
		}
	}

	public boolean getBool(String key) {
		return Boolean.parseBoolean(get(key).trim());
	}

	public long getNanos(String key) {
		return TimeSpec.parseNanos(get(key));
	}

	public ActionBlock copy() {
		ActionBlock b = new ActionBlock();
		b.id = id;
		b.type = type;
		b.params = new LinkedHashMap<>(params);
		b.x = x;
		b.y = y;
		b.next = next;
		b.elseNext = elseNext;
		return b;
	}

	private String mode() {
		String m = get("mode");
		return m.isEmpty() ? "" : m.charAt(0) + m.substring(1).toLowerCase();
	}

	/** Short human readable form, e.g. {@code Wait 0.2t} or {@code Select Slot [golden_apple]}. */
	public String summary() {
		ActionType t = actionType();
		if (t == null) return "Unknown(" + type + ")";
		return switch (t) {
			case WAIT_TICKS -> "Wait " + TimeSpec.pretty(get("ticks"));
			case WAIT_MS -> "Wait " + get("ms") + "ms";
			case SELECT_SLOT -> "Select Slot [" + get("slot") + "]";
			case USE_ITEM -> "Use Item (" + mode() + ")";
			case LOOK_PLAYER -> "Look at Player [" + get("username") + "]";
			case LOOK_ANGLE -> "Look Pitch [" + get("pitch") + "\u00B0]";
			case REPEAT -> "Repeat x" + (getInt("times") <= 0 ? "\u221E" : getInt("times")) + " {next " + getInt("body") + "}";
			case CONDITION -> "If " + (getBool("negate") ? "NOT " : "") + get("check") + " " + get("value");
			case OFFHAND_ITEM -> "Quick Move [" + get("item") + "] to Offhand";
			case EQUIP_ARMOR -> "Equip " + mode();
			case DROP_ITEM -> "Drop (" + mode() + ")";
			case INVENTORY_SCREEN -> mode() + " Inventory";
			case STRAFE -> "Strafe " + get("direction").toLowerCase();
			case SNEAK, SPRINT -> t.displayName + " (" + mode() + ")";
			default -> t.displayName;
		};
	}

	/** The value shown in the inline mint-green tag on a node, or null. */
	public String inlineTag() {
		ActionType t = actionType();
		if (t == null) return null;
		return switch (t) {
			case WAIT_TICKS -> TimeSpec.pretty(get("ticks"));
			case WAIT_MS -> get("ms") + "ms";
			case SELECT_SLOT -> get("slot");
			case USE_ITEM -> get("mode").equals("HOLD") ? "hold " + TimeSpec.pretty(get("duration")) : get("mode").toLowerCase();
			case BREAK_BLOCK, AUTO_SHIELD, SMOOTH_TRACK, SMOOTH_AIM, STRAFE -> TimeSpec.pretty(get("duration"));
			case SNEAK, SPRINT -> get("mode").toLowerCase();
			case LOOK_PLAYER -> get("username");
			case LOOK_ANGLE -> get("pitch") + "\u00B0";
			case REPEAT -> "x" + (getInt("times") <= 0 ? "\u221E" : getInt("times"));
			case CONDITION -> get("check").toLowerCase();
			case PILLAR -> "x" + getInt("count");
			case OFFHAND_ITEM -> get("item");
			case EQUIP_ARMOR -> get("piece").toLowerCase();
			case DROP_ITEM, INVENTORY_SCREEN -> get("mode").toLowerCase();
			case ATTACK_IN_RANGE -> get("maxRange") + "b";
			case WAIT_HEALTH_BELOW -> "hp < " + get("value");
			case WAIT_DEBUFF -> get("effect").toLowerCase();
			case WAIT_CROSSHAIR_ENTITY, WAIT_LOS -> get("target").toLowerCase();
			case MLG_WATER -> ">" + get("minFall") + "b";
			case FIND_MOVE_ITEM -> get("item") + " > " + get("slot");
			case CHECK_ITEM_COUNT -> get("compare").toLowerCase() + " " + get("count");
			case AUTO_ARMOR_SWAP -> get("mode").toLowerCase();
			case LOOK_AT_COORDS -> get("x") + " " + get("y") + " " + get("z");
			case AUTO_TRAP -> get("block").toLowerCase();
			case START_AIM_LOCK -> get("stopCondition").toLowerCase();
			case IF_ELSE -> (getBool("negate") ? "!" : "") + get("check").toLowerCase();
			default -> null;
		};
	}
}
