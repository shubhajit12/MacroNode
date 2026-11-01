package macronode.macro.client.action;

import java.util.List;

import static macronode.macro.client.action.ParamDef.*;

/**
 * Catalog of every block type. The enum name is the stable id written to JSON / share codes,
 * so never rename constants; add new ones instead.
 */
public enum ActionType {
	// ---------------------------------------------------------------- Targeting & Camera
	LOOK_NEAREST_PLAYER(ActionCategory.TARGETING, "Look at Nearest Player",
			decimal("range", "Range (blocks)", 32)),
	LOOK_PLAYER(ActionCategory.TARGETING, "Look at Player",
			text("username", "Username", "Steve"),
			decimal("range", "Range (blocks)", 64)),
	LOOK_NEAREST_ENTITY(ActionCategory.TARGETING, "Look at Nearest Entity",
			decimal("range", "Range (blocks)", 16),
			bool("hostileOnly", "Hostile only", false)),
	LOOK_ANGLE(ActionCategory.TARGETING, "Look Pitch / Yaw",
			text("pitch", "Pitch (90=down)", "90"),
			text("yaw", "Yaw (keep/deg)", "keep"),
			bool("relative", "Relative", false)),
	RESET_CAMERA(ActionCategory.TARGETING, "Reset Camera"),
	SMOOTH_TRACK(ActionCategory.TARGETING, "Smooth Camera Track",
			choice("target", "Target", "NEAREST_PLAYER", "NEAREST_PLAYER", "PLAYER", "NEAREST_ENTITY"),
			text("username", "Username", ""),
			time("duration", "Duration", "20t"),
			decimal("smoothing", "Smoothing 0-1", 0.35),
			decimal("range", "Range (blocks)", 32)),
	SMOOTH_AIM(ActionCategory.TARGETING, "Smooth Aim at Target",
			choice("target", "Target", "NEAREST_PLAYER", "NEAREST_PLAYER", "NEAREST_ENTITY", "LOCKED_ENTITY"),
			choice("aimPart", "Aim Part", "HEAD", "HEAD", "CHEST", "FEET"),
			time("duration", "Duration", "20t"),
			decimal("smoothing", "Smoothing 0-1", 0.35),
			decimal("range", "Range (blocks)", 15)),
	START_AIM_LOCK(ActionCategory.TARGETING, "Start Smooth Aim Lock",
			choice("target", "Target", "NEAREST_PLAYER", "NEAREST_PLAYER", "LOCKED_ENTITY"),
			text("username", "Username", ""),
			decimal("smoothing", "Smoothing 0-1", 0.15),
			decimal("range", "Range (blocks)", 15),
			choice("stopCondition", "Stop", "OUT_OF_RANGE", "OUT_OF_RANGE", "DEATH", "EXPLICIT")),
	STOP_AIM_LOCK(ActionCategory.TARGETING, "Stop Aim Lock"),

	// ---------------------------------------------------------------- Combat & Player Action
	ATTACK(ActionCategory.COMBAT, "Attack / Swing"),
	USE_ITEM(ActionCategory.COMBAT, "Use Item / Right Click",
			choice("mode", "Mode", "CLICK", "CLICK", "HOLD", "RELEASE"),
			time("duration", "Max hold", "32t"),
			choice("hand", "Hand", "MAIN", "MAIN", "OFF")),
	INTERACT_ENTITY(ActionCategory.COMBAT, "Interact Entity",
			decimal("range", "Range (blocks)", 4.5)),
	BREAK_BLOCK(ActionCategory.COMBAT, "Break Block (Hold)",
			time("duration", "Hold for", "20t")),
	CRIT_SWING(ActionCategory.COMBAT, "Critical Hit Swing",
			time("timeout", "Timeout", "12t")),
	AUTO_SHIELD(ActionCategory.COMBAT, "Auto Shield Block",
			time("duration", "Block for", "20t")),
	CRYSTAL(ActionCategory.COMBAT, "Crystal Place / Break",
			time("delay", "Place->Break", "1t"),
			decimal("range", "Break range", 5)),
	ANCHOR(ActionCategory.COMBAT, "Anchor Charge & Blow",
			time("delay", "Step delay", "1t"),
			bool("restore", "Restore slot", true)),

	// ---------------------------------------------------------------- Inventory & Equipment
	SELECT_SLOT(ActionCategory.INVENTORY, "Select Hotbar Slot",
			text("slot", "1-9 / previous / item", "1"),
			bool("fetch", "Pull from inventory", true)),
	SWAP_HANDS(ActionCategory.INVENTORY, "Swap Main / Offhand"),
	DROP_ITEM(ActionCategory.INVENTORY, "Drop Held Item",
			choice("mode", "Mode", "SINGLE", "SINGLE", "STACK")),
	INVENTORY_SCREEN(ActionCategory.INVENTORY, "Open / Close Inventory",
			choice("mode", "Mode", "OPEN", "OPEN", "CLOSE", "TOGGLE")),
	OFFHAND_ITEM(ActionCategory.INVENTORY, "Quick Move to Offhand",
			text("item", "Item id(s)", "totem_of_undying")),
	EQUIP_ARMOR(ActionCategory.INVENTORY, "Equip Armor Piece",
			choice("piece", "Piece", "CHESTPLATE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"),
			text("item", "Item id (opt.)", "")),
	AUTO_REFILL(ActionCategory.INVENTORY, "Auto Refill Slot",
			text("slot", "Slot (current/1-9)", "current"),
			text("item", "Item id (opt.)", ""),
			integer("threshold", "Refill at <=", 8)),
	QUICK_CLICK(ActionCategory.INVENTORY, "Quick Craft / Quick Click",
			integer("slot", "Menu slot id", 36),
			integer("button", "Button", 0),
			choice("click", "Click type", "PICKUP", "PICKUP", "QUICK_MOVE", "SWAP", "THROW", "PICKUP_ALL", "CLONE")),

	// ---------------------------------------------------------------- Movement & World
	JUMP(ActionCategory.MOVEMENT, "Jump"),
	SNEAK(ActionCategory.MOVEMENT, "Sneak",
			choice("mode", "Mode", "HOLD", "HOLD", "TOGGLE", "RELEASE"),
			time("duration", "Hold for", "10t")),
	SPRINT(ActionCategory.MOVEMENT, "Sprint",
			choice("mode", "Mode", "TOGGLE", "HOLD", "TOGGLE", "RELEASE"),
			time("duration", "Hold for", "10t")),
	STRAFE(ActionCategory.MOVEMENT, "Strafe Movement",
			choice("direction", "Direction", "LEFT", "LEFT", "RIGHT", "BACK", "FORWARD"),
			time("duration", "Hold for", "5t")),
	PLACE_BLOCK(ActionCategory.MOVEMENT, "Place Block at Target",
			choice("hand", "Hand", "MAIN", "MAIN", "OFF"),
			bool("waitClear", "Wait until clear", true),
			time("timeout", "Clear timeout", "10t")),
	PILLAR(ActionCategory.MOVEMENT, "Auto Pillar Jump",
			integer("count", "Blocks", 3)),
	ELYTRA_ROCKET(ActionCategory.MOVEMENT, "Toggle Elytra Rocket",
			bool("rocket", "Fire rocket", true)),

	// ---------------------------------------------------------------- Timing & Control Logic
	WAIT_TICKS(ActionCategory.CONTROL, "Wait (Ticks)",
			time("ticks", "Ticks", "1t")),
	WAIT_MS(ActionCategory.CONTROL, "Wait (Milliseconds)",
			decimal("ms", "Milliseconds", 50)),
	REPEAT(ActionCategory.CONTROL, "Repeat Loop",
			integer("times", "Times (0=inf)", 2),
			integer("body", "Repeat next N", 1)),
	CONDITION(ActionCategory.CONTROL, "Conditional Check",
			choice("check", "Check", "HEALTH_BELOW",
					"HEALTH_BELOW", "HEALTH_ABOVE", "HOLDING_ITEM", "HAS_ITEM", "ON_GROUND", "PLAYER_NEARBY", "USING_ITEM", "FALL_FLYING"),
			text("value", "Value", "10"),
			bool("negate", "Negate", false),
			choice("onFalse", "If false", "SKIP", "SKIP", "STOP"),
			integer("skip", "Skip next N", 1)),

	// ---------------------------------------------------------------- Reactive Combat & Sensors (no blind delays)
	ATTACK_IN_RANGE(ActionCategory.COMBAT, "Attack When in Range",
			choice("target", "Target", "PLAYER", "PLAYER", "LIVING"),
			decimal("maxRange", "Max range", 3.5),
			time("timeout", "Timeout (0=none)", "100t"),
			bool("aim", "Snap aim to target", true)),
	WAIT_CROSSHAIR_ENTITY(ActionCategory.COMBAT, "Wait Until Crosshair on Entity",
			choice("target", "Target", "ANY", "ANY", "PLAYER", "LIVING"),
			timeout(), onTimeout()),
	WAIT_HEALTH_BELOW(ActionCategory.COMBAT, "Wait Until Health <",
			decimal("value", "Health below", 10),
			timeout(), onTimeout()),
	WAIT_ABSORPTION(ActionCategory.COMBAT, "Wait Until Absorption Active",
			timeout(), onTimeout()),

	// ---------------------------------------------------------------- Defensive & Survival Automation
	DRINK_MILK(ActionCategory.DEFENSE, "Drink Milk Bucket",
			time("maxHold", "Max hold", "40t"),
			bool("restore", "Swap back", true)),
	WAIT_DEBUFF(ActionCategory.DEFENSE, "Wait Until Debuff Active",
			choice("effect", "Effect", "ANY_HARMFUL", "ANY_HARMFUL", "WEAKNESS", "SLOWNESS", "POISON", "WITHER",
					"MINING_FATIGUE", "BLINDNESS", "NAUSEA", "HUNGER", "LEVITATION", "DARKNESS"),
			timeout(), onTimeout()),
	MLG_WATER(ActionCategory.DEFENSE, "MLG Water Bucket Fallback",
			decimal("minFall", "Min fall (blocks)", 5),
			bool("waitForFall", "Wait for fall", false),
			bool("scoop", "Scoop water back", true),
			time("timeout", "Timeout", "100t")),

	// ---------------------------------------------------------------- Deep Inventory Intelligence
	FIND_MOVE_ITEM(ActionCategory.INVENTORY, "Find & Move Item to Slot",
			text("item", "Item id(s)", "golden_apple"),
			integer("slot", "Hotbar slot 1-9", 1),
			integer("threshold", "Depleted at <=", 0),
			bool("force", "Replace other items", false)),
	CHECK_ITEM_COUNT(ActionCategory.INVENTORY, "Check Item Count",
			text("item", "Item id(s)", "golden_apple"),
			choice("scope", "Where", "ALL", "ALL", "HOTBAR", "INVENTORY"),
			choice("compare", "Compare", "ABOVE", "ABOVE", "BELOW", "EQUAL"),
			integer("count", "Count N", 0),
			choice("onFalse", "If false", "SKIP", "SKIP", "STOP"),
			integer("skip", "Skip next N", 1)),
	AUTO_ARMOR_SWAP(ActionCategory.INVENTORY, "Auto Armor Swap",
			choice("mode", "Mode", "TOGGLE_ELYTRA", "TOGGLE_ELYTRA", "TO_ELYTRA", "TO_CHESTPLATE", "FULL_SET"),
			text("material", "Material filter", "")),

	// ---------------------------------------------------------------- Precision Movement & Positioning
	LOOK_AT_COORDS(ActionCategory.MOVEMENT, "Look at Coordinates",
			text("x", "X (~ = relative)", "0"),
			text("y", "Y (~ = relative)", "64"),
			text("z", "Z (~ = relative)", "0")),
	AUTO_TRAP(ActionCategory.MOVEMENT, "Auto Trap Placement",
			choice("block", "Block", "COBWEB", "COBWEB", "OBSIDIAN"),
			text("username", "Target (blank=nearest)", ""),
			decimal("range", "Reach", 4.5),
			bool("restore", "Swap back", true)),

	// ---------------------------------------------------------------- Advanced Logic & Branching
	IF_ELSE(ActionCategory.CONTROL, "If / Else Branch",
			choice("check", "Condition", "TARGET_HOLDING_SHIELD", "TARGET_HOLDING_SHIELD", "TARGET_BLOCKING",
					"TARGET_BURNING", "TARGET_IN_RANGE", "TARGET_HEALTH_BELOW", "TARGET_VISIBLE", "HEALTH_BELOW",
					"HOLDING_ITEM", "HAS_ITEM", "HAS_EFFECT", "ON_GROUND", "FALL_FLYING"),
			text("value", "Value", "3.5"),
			decimal("range", "Target search range", 8),
			bool("negate", "Negate", false)),
	WAIT_LOS(ActionCategory.CONTROL, "Wait Until Line of Sight",
			choice("target", "Target", "NEAREST_PLAYER", "NEAREST_PLAYER", "PLAYER", "NEAREST_ENTITY"),
			text("username", "Username", ""),
			decimal("range", "Range", 32),
			timeout(), onTimeout());

	public final ActionCategory category;
	public final String displayName;
	public final List<ParamDef> params;

	ActionType(ActionCategory category, String displayName, ParamDef... params) {
		this.category = category;
		this.displayName = displayName;
		this.params = List.of(params);
	}

	public ParamDef param(String key) {
		for (ParamDef p : params) if (p.key().equals(key)) return p;
		return null;
	}

	public static ActionType byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}
}
