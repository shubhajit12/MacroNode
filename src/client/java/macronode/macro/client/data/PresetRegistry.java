package macronode.macro.client.data;

import java.util.List;
import java.util.function.Supplier;

import macronode.macro.client.action.ActionType;

/** Official built-in presets offered for 1-click import in the Config Hub. */
public final class PresetRegistry {
	public record Preset(String name, String description, Supplier<MacroProfile> factory) {
		public MacroProfile create() {
			return factory.get();
		}
	}

	private PresetRegistry() {
	}

	public static final List<Preset> PRESETS = List.of(
			new Preset("Quick Heal / Gapple",
					"Swaps to a golden apple, eats it, then returns to your previous slot.",
					PresetRegistry::quickHeal),
			new Preset("Track Player",
					"Locks onto a named player, swaps to your bow and draws it.",
					PresetRegistry::trackPlayer),
			new Preset("Totem Auto-Offhand",
					"Opens inventory, moves a Totem of Undying to offhand, closes inventory.",
					PresetRegistry::totemOffhand),
			new Preset("Instant Pillar",
					"Jumps, looks straight down, places a block under you and resets the camera.",
					PresetRegistry::instantPillar));

	private static MacroProfile base(String name) {
		MacroProfile p = new MacroProfile();
		p.name = name;
		p.overrideNative = true;
		return p;
	}

	/** Select Slot [Gapple] -> Wait 0t -> Use Item (Hold) -> Wait 10t -> Select Slot [Previous] */
	private static MacroProfile quickHeal() {
		MacroProfile p = base("Quick Heal / Gapple");
		p.append(ActionType.SELECT_SLOT).with("slot", "golden_apple,enchanted_golden_apple");
		p.append(ActionType.WAIT_TICKS).with("ticks", "0t");
		// HOLD releases automatically once eating finishes (or after 40t max).
		p.append(ActionType.USE_ITEM).with("mode", "HOLD").with("duration", "40t");
		p.append(ActionType.WAIT_TICKS).with("ticks", "10t");
		p.append(ActionType.SELECT_SLOT).with("slot", "previous");
		return p;
	}

	/** Look at Player [Username] -> Select Slot [Bow] -> Use Item (Draw Bow) */
	private static MacroProfile trackPlayer() {
		MacroProfile p = base("Track Player");
		p.append(ActionType.LOOK_PLAYER).with("username", "Steve");
		p.append(ActionType.SELECT_SLOT).with("slot", "bow");
		p.append(ActionType.USE_ITEM).with("mode", "HOLD").with("duration", "20t");
		return p;
	}

	/** Open Inventory -> Quick Move Totem to Offhand -> Close Inventory */
	private static MacroProfile totemOffhand() {
		MacroProfile p = base("Totem Auto-Offhand");
		p.append(ActionType.INVENTORY_SCREEN).with("mode", "OPEN");
		p.append(ActionType.OFFHAND_ITEM).with("item", "totem_of_undying");
		p.append(ActionType.INVENTORY_SCREEN).with("mode", "CLOSE");
		return p;
	}

	/**
	 * Jump -> Look Pitch [down] -> Wait 0.2t -> Place Block -> Reset Camera.
	 * Minecraft pitch +90 is straight down (the spec's "-90" means "look down").
	 * Place Block waits until the player has risen clear of the target space.
	 */
	private static MacroProfile instantPillar() {
		MacroProfile p = base("Instant Pillar");
		p.append(ActionType.JUMP);
		p.append(ActionType.LOOK_ANGLE).with("pitch", "90").with("yaw", "keep");
		p.append(ActionType.WAIT_TICKS).with("ticks", "0.2t");
		p.append(ActionType.PLACE_BLOCK).with("waitClear", "true").with("timeout", "10t");
		p.append(ActionType.RESET_CAMERA);
		return p;
	}
}
