package macronode.macro.client.recorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import macronode.macro.client.action.ActionBlock;
import macronode.macro.client.action.ActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static macronode.macro.client.action.ActionType.*;

/**
 * Captures player input during recording and converts it to ActionBlock chains.
 * Records hotbar changes, item usage, attacks, movement toggles, and optional look events.
 */
public final class MacroRecorder {
	private static final MacroRecorder INSTANCE = new MacroRecorder();

	public static MacroRecorder get() {
		return INSTANCE;
	}

	private boolean recording = false;
	private long recordingStartTime;
	private final List<RecordedEvent> events = new ArrayList<>();

	// Track hold start times for duration calculation
	private final Map<String, Long> holdStarts = new HashMap<>();

	// State tracking
	private int lastHotbarSlot = -1;
	private boolean sneakToggled = false;
	private boolean sprintToggled = false;

	private MacroRecorder() {
	}

	public void startRecording() {
		if (recording) return;
		recording = true;
		recordingStartTime = System.currentTimeMillis();
		events.clear();
		holdStarts.clear();
		lastHotbarSlot = -1;
		sneakToggled = false;
		sprintToggled = false;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			lastHotbarSlot = mc.player.getInventory().getSelectedSlot();
		}
	}

	public void stopRecording() {
		if (!recording) return;
		recording = false;

		// End any active holds
		for (Map.Entry<String, Long> entry : holdStarts.entrySet()) {
			long duration = System.currentTimeMillis() - entry.getValue();
			if (duration > 50) { // Only record holds longer than 50ms
				events.add(new RecordedEvent(System.currentTimeMillis() - recordingStartTime,
						EventType.HOLD_END, entry.getKey(), duration));
			}
		}
		holdStarts.clear();
	}

	public boolean isRecording() {
		return recording;
	}

	public List<RecordedEvent> getEvents() {
		return new ArrayList<>(events);
	}

	public void clearEvents() {
		events.clear();
	}

	// Called each client tick to check for state changes
	public void pump() {
		if (!recording) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;

		long timestamp = System.currentTimeMillis() - recordingStartTime;

		// Check hotbar slot change
		int currentSlot = p.getInventory().getSelectedSlot();
		if (currentSlot != lastHotbarSlot && lastHotbarSlot != -1) {
			events.add(new RecordedEvent(timestamp, EventType.SLOT_CHANGE, String.valueOf(currentSlot), 0));
			lastHotbarSlot = currentSlot;
		}

		// Check sneak toggle (key release)
		if (sneakToggled && !mc.options.keyShift.isDown()) {
			events.add(new RecordedEvent(timestamp, EventType.SNEAK_RELEASE, "", 0));
			sneakToggled = false;
		}

		// Check sprint toggle (key release)
		if (sprintToggled && !mc.options.keySprint.isDown()) {
			events.add(new RecordedEvent(timestamp, EventType.SPRINT_RELEASE, "", 0));
			sprintToggled = false;
		}
	}

	// Input event hooks
	public void onKeyPress(int keyCode) {
		if (!recording) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;

		long timestamp = System.currentTimeMillis() - recordingStartTime;

		// Check for hotbar keys (1-9)
		if (keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_9) {
			int slot = keyCode - InputConstants.KEY_1;
			events.add(new RecordedEvent(timestamp, EventType.SLOT_CHANGE, String.valueOf(slot), 0));
			lastHotbarSlot = slot;
			return;
		}

		// Sneak key
		if (keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT) {
			events.add(new RecordedEvent(timestamp, EventType.SNEAK_PRESS, "", 0));
			sneakToggled = true;
			return;
		}

		// Sprint key
		if (keyCode == InputConstants.KEY_LCONTROL || keyCode == InputConstants.KEY_RCONTROL) {
			events.add(new RecordedEvent(timestamp, EventType.SPRINT_PRESS, "", 0));
			sprintToggled = true;
			return;
		}

		// Jump key
		if (keyCode == InputConstants.KEY_SPACE) {
			events.add(new RecordedEvent(timestamp, EventType.JUMP, "", 0));
			return;
		}
	}

	public void onMousePress(int button) {
		if (!recording) return;

		long timestamp = System.currentTimeMillis() - recordingStartTime;

		if (button == 0) { // Left click
			events.add(new RecordedEvent(timestamp, EventType.ATTACK, "", 0));
		} else if (button == 1) { // Right click
			String key = "USE_ITEM";
			holdStarts.put(key, System.currentTimeMillis());
			events.add(new RecordedEvent(timestamp, EventType.HOLD_START, key, 0));
		}
	}

	public void onMouseRelease(int button) {
		if (!recording) return;

		long timestamp = System.currentTimeMillis() - recordingStartTime;

		if (button == 1) { // Right click release
			String key = "USE_ITEM";
			Long start = holdStarts.remove(key);
			if (start != null) {
				long duration = System.currentTimeMillis() - start;
				if (duration > 50) {
					events.add(new RecordedEvent(timestamp, EventType.HOLD_END, key, duration));
				} else {
					events.add(new RecordedEvent(timestamp, EventType.USE_ITEM, "", 0));
				}
			}
		}
	}

	public enum EventType {
		SLOT_CHANGE(SELECT_SLOT),
		HOLD_START(null),
		HOLD_END(USE_ITEM),
		ATTACK(ATTACK),
		USE_ITEM(USE_ITEM),
		JUMP(JUMP),
		SNEAK_PRESS(SNEAK),
		SNEAK_RELEASE(SNEAK),
		SPRINT_PRESS(SPRINT),
		SPRINT_RELEASE(SPRINT);

		private final ActionType actionType;

		EventType(ActionType actionType) {
			this.actionType = actionType;
		}

		public ActionType toActionType() {
			return actionType;
		}
	}

	public record RecordedEvent(long timestamp, EventType type, String data, long duration) {
	}
}
