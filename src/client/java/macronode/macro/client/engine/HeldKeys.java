package macronode.macro.client.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import macronode.macro.client.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Simulated key holds. Macros "press" vanilla KeyMappings (use, attack, jump, sneak...) so the
 * normal client code path runs on the next client tick - this keeps actions in sync with the
 * server exactly like real input. Holds expire at a nanosecond deadline checked every frame.
 * <p>
 * The raw {@code isDown} field is written through an accessor to bypass ToggleKeyMapping logic
 * (toggle-sneak / toggle-sprint options).
 */
public final class HeldKeys {
	public static final long FOREVER = Long.MAX_VALUE;
	private static final HeldKeys INSTANCE = new HeldKeys();

	private record Hold(long releaseAt, MacroRun owner) {
	}

	private final Map<KeyMapping, Hold> holds = new HashMap<>();

	public static HeldKeys get() {
		return INSTANCE;
	}

	public void hold(KeyMapping key, long nanos, MacroRun owner) {
		setRaw(key, true);
		long at = nanos == FOREVER ? FOREVER : System.nanoTime() + nanos;
		holds.put(key, new Hold(at, owner));
	}

	public boolean isHeld(KeyMapping key) {
		return holds.containsKey(key);
	}

	/** True when held with no deadline and no owner (a persistent toggle). */
	public boolean isToggled(KeyMapping key) {
		Hold h = holds.get(key);
		return h != null && h.releaseAt == FOREVER && h.owner == null;
	}

	public void release(KeyMapping key) {
		holds.remove(key);
		setRaw(key, physicallyDown(key));
	}

	public void pump(long now) {
		Iterator<Map.Entry<KeyMapping, Hold>> it = holds.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<KeyMapping, Hold> e = it.next();
			if (now >= e.getValue().releaseAt) {
				it.remove();
				setRaw(e.getKey(), physicallyDown(e.getKey()));
			} else if (!e.getKey().isDown()) {
				// Something (screen open, focus loss) released the key; re-assert our hold.
				setRaw(e.getKey(), true);
			}
		}
	}

	/** Releases indefinite holds owned by a finished/stopped run. Timed holds expire naturally. */
	public void releaseOwned(MacroRun run) {
		holds.entrySet().removeIf(e -> {
			if (e.getValue().owner == run && e.getValue().releaseAt == FOREVER) {
				setRaw(e.getKey(), physicallyDown(e.getKey()));
				return true;
			}
			return false;
		});
	}

	public void releaseAll() {
		for (KeyMapping k : holds.keySet()) setRaw(k, physicallyDown(k));
		holds.clear();
	}

	private static void setRaw(KeyMapping key, boolean down) {
		((KeyMappingAccessor) key).macronode$setDown(down);
	}

	/** Whether the user is physically holding the key bound to this mapping (in-game only). */
	public static boolean physicallyDown(KeyMapping mapping) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null) return false;
		return isKeyDown(KeyBindingHelper.getBoundKeyOf(mapping));
	}

	public static boolean isKeyDown(InputConstants.Key key) {
		if (key == null || key == InputConstants.UNKNOWN) return false;
		Window window = Minecraft.getInstance().getWindow();
		return switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
			default -> false;
		};
	}
}
