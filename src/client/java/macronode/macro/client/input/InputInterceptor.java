package macronode.macro.client.input;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import macronode.macro.client.MacroNodeClient;
import macronode.macro.client.data.MacroProfile;
import macronode.macro.client.data.ProfileStore;
import macronode.macro.client.engine.HeldKeys;
import macronode.macro.client.engine.MacroEngine;
import macronode.macro.client.gui.ConfigScreen;
import macronode.macro.client.gui.MacroEditorScreen;
import macronode.macro.client.recorder.MacroRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * Decides whether a raw key/mouse event triggers a macro and whether vanilla should see it.
 * <p>
 * Matching: a profile matches when its LAST key equals the event key and every earlier key
 * (modifiers / chord keys) is currently held. If several profiles match (e.g. "1" and "Ctrl + 1")
 * the most specific (longest) chord wins. Left/right Ctrl, Shift, Alt and Super are interchangeable.
 * <p>
 * Override: if the winning profile has overrideNative, the PRESS, REPEAT and RELEASE of the
 * activator key are all swallowed, so e.g. "2" will not select hotbar slot 2.
 */
public final class InputInterceptor {
	private static final Map<Integer, Integer> MOD_TWIN = Map.of(
			InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL,
			InputConstants.KEY_RCONTROL, InputConstants.KEY_LCONTROL,
			InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT,
			InputConstants.KEY_RSHIFT, InputConstants.KEY_LSHIFT,
			InputConstants.KEY_LALT, InputConstants.KEY_RALT,
			InputConstants.KEY_RALT, InputConstants.KEY_LALT,
			InputConstants.KEY_LSUPER, InputConstants.KEY_RSUPER,
			InputConstants.KEY_RSUPER, InputConstants.KEY_LSUPER);

	private InputInterceptor() {
	}

	private static boolean inGame(Minecraft mc) {
		return mc.screen == null && mc.player != null && mc.getOverlay() == null;
	}

	/** @return true to cancel the vanilla key handling. */
	public static boolean onKey(int action, KeyEvent event) {
		Minecraft mc = Minecraft.getInstance();
		if (!inGame(mc)) return false;

		// Feed recorder before any macro handling
		if (MacroRecorder.get().isRecording()) {
			if (action == InputConstants.PRESS) {
				InputConstants.Key key = InputConstants.getKey(event);
				MacroRecorder.get().onKeyPress(key.getValue());
			}
		}

		if (action == InputConstants.PRESS) {
			// MacroNode's own hotkeys also take priority (E would otherwise open the vanilla inventory).
			if (MacroNodeClient.OPEN_CONFIG.matches(event)) {
				mc.setScreen(new ConfigScreen(null));
				return true;
			}
			if (MacroNodeClient.OPEN_EDITOR.matches(event)) {
				mc.setScreen(MacroEditorScreen.openDefault(null));
				return true;
			}
			if (!MacroNodeClient.STOP_ALL.isUnbound() && MacroNodeClient.STOP_ALL.matches(event)) {
				MacroEngine.get().stopAll();
				return true;
			}
		}
		return handle(InputConstants.getKey(event), action);
	}

	/** @return true to cancel the vanilla mouse button handling. */
	public static boolean onMouse(int button, int action) {
		Minecraft mc = Minecraft.getInstance();
		if (!inGame(mc)) return false;

		// Feed recorder before any macro handling
		if (MacroRecorder.get().isRecording()) {
			if (action == InputConstants.PRESS) {
				MacroRecorder.get().onMousePress(button);
			} else if (action == InputConstants.RELEASE) {
				MacroRecorder.get().onMouseRelease(button);
			}
		}

		return handle(InputConstants.Type.MOUSE.getOrCreate(button), action);
	}

	private static boolean handle(InputConstants.Key key, int action) {
		ProfileStore store = ProfileStore.get();
		if (!store.settings().masterEnabled || key == InputConstants.UNKNOWN) return false;
		MacroProfile best = null;
		int bestSize = 0;
		for (MacroProfile p : store.profiles()) {
			if (!p.enabled || !p.trigger.isBound()) continue;
			List<InputConstants.Key> keys = p.trigger.resolve();
			if (keys.isEmpty() || !same(keys.get(keys.size() - 1), key)) continue;
			boolean chordHeld = true;
			for (int i = 0; i < keys.size() - 1; i++) {
				if (!isDown(keys.get(i))) {
					chordHeld = false;
					break;
				}
			}
			if (chordHeld && keys.size() > bestSize) {
				best = p;
				bestSize = keys.size();
			}
		}
		if (best == null) return false;
		if (action == InputConstants.PRESS) MacroEngine.get().onTrigger(best);
		return best.overrideNative;
	}

	public static boolean same(InputConstants.Key a, InputConstants.Key b) {
		if (a.equals(b)) return true;
		if (a.getType() == InputConstants.Type.KEYSYM && b.getType() == InputConstants.Type.KEYSYM) {
			Integer twin = MOD_TWIN.get(a.getValue());
			return twin != null && twin == b.getValue();
		}
		return false;
	}

	private static boolean isDown(InputConstants.Key k) {
		if (HeldKeys.isKeyDown(k)) return true;
		Integer twin = k.getType() == InputConstants.Type.KEYSYM ? MOD_TWIN.get(k.getValue()) : null;
		return twin != null && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), twin);
	}
}
