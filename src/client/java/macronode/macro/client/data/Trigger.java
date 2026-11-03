package macronode.macro.client.data;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Key trigger of a profile.
 * <p>
 * SINGLE: one key or mouse button (e.g. {@code G}, {@code 2}, {@code Mouse 4}).<br>
 * COMBO: chord; every key except the last must already be held, the LAST key is the
 * activator (e.g. {@code Ctrl + 1}, {@code T + K}, {@code Shift + R}).
 * <p>
 * Keys are stored as vanilla translation names ({@code key.keyboard.g}, {@code key.mouse.4})
 * so they survive keyboard layout changes and are readable in JSON.
 */
public class Trigger {
	public enum Type { SINGLE, COMBO }

	public Type type = Type.SINGLE;
	public List<String> keys = new ArrayList<>();

	public Trigger() {
	}

	public Trigger(Type type, List<String> keys) {
		this.type = type;
		this.keys = new ArrayList<>(keys);
	}

	public boolean isBound() {
		return keys != null && !keys.isEmpty();
	}

	public List<InputConstants.Key> resolve() {
		List<InputConstants.Key> out = new ArrayList<>();
		if (keys == null) return out;
		for (String k : keys) {
			try {
				InputConstants.Key key = InputConstants.getKey(k);
				if (key != InputConstants.UNKNOWN) out.add(key);
			} catch (Exception ignored) {
			}
		}
		return out;
	}

	public Trigger copy() {
		return new Trigger(type, keys == null ? List.of() : keys);
	}

	/** Display string like {@code Ctrl + 1}. */
	public String display() {
		if (!isBound()) return "Unbound";
		StringBuilder sb = new StringBuilder();
		for (InputConstants.Key k : resolve()) {
			if (!sb.isEmpty()) sb.append(" + ");
			sb.append(k.getDisplayName().getString());
		}
		return sb.isEmpty() ? "Unbound" : sb.toString();
	}
}
