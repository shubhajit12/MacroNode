package macronode.macro.client.recorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import macronode.macro.client.action.ActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static macronode.macro.client.action.ActionType.*;

/**
 * Captures player input during recording using state snapshot monitoring.
 * Records hotbar changes, item usage, attacks, movement toggles, and optional look events.
 * Monitors game state changes in ClientTickEvents.END_CLIENT_TICK to ensure accurate detection
 * of in-game actions regardless of key bindings.
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

	// State tracking for snapshot monitoring
	private int lastHotbarSlot = -1;
	private boolean lastUsingItem = false;
	private boolean lastHandSwinging = false;
	private boolean lastJumping = false;
	private boolean lastSneaking = false;
	private boolean lastSprinting = false;

	private MacroRecorder() {
	}

	public void startRecording() {
		if (recording) return;
		recording = true;
		recordingStartTime = System.currentTimeMillis();
		events.clear();
		holdStarts.clear();
		lastHotbarSlot = -1;
		lastUsingItem = false;
		lastHandSwinging = false;
		lastJumping = false;
		lastSneaking = false;
		lastSprinting = false;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			lastHotbarSlot = mc.player.getInventory().getSelectedSlot();
			lastUsingItem = mc.player.isUsingItem();
			lastHandSwinging = mc.player.handSwinging;
			lastJumping = mc.player.input.jumping;
			lastSneaking = mc.player.input.sneaking;
			lastSprinting = mc.player.input.sprinting;
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

	/**
	 * Called each client tick to monitor state changes via snapshot approach.
	 * This ensures accurate detection of in-game actions regardless of key bindings
	 * and prevents recording during GUI/chat input.
	 */
	public void pump() {
		if (!recording) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null) return; // Don't record when GUI is open

		long timestamp = System.currentTimeMillis() - recordingStartTime;

		// Check hotbar slot change
		int currentSlot = p.getInventory().getSelectedSlot();
		if (currentSlot != lastHotbarSlot && lastHotbarSlot != -1) {
			events.add(new RecordedEvent(timestamp, EventType.SLOT_CHANGE, String.valueOf(currentSlot), 0));
			lastHotbarSlot = currentSlot;
		}

		// Check item usage state transitions (isUsingItem)
		boolean currentlyUsingItem = p.isUsingItem();
		if (currentlyUsingItem && !lastUsingItem) {
			// Item usage started
			holdStarts.put("USE_ITEM", System.currentTimeMillis());
			events.add(new RecordedEvent(timestamp, EventType.HOLD_START, "USE_ITEM", 0));
		} else if (!currentlyUsingItem && lastUsingItem) {
			// Item usage ended
			Long start = holdStarts.remove("USE_ITEM");
			if (start != null) {
				long duration = System.currentTimeMillis() - start;
				if (duration > 50) {
					events.add(new RecordedEvent(timestamp, EventType.HOLD_END, "USE_ITEM", duration));
				} else {
					events.add(new RecordedEvent(timestamp, EventType.USE_ITEM, "", 0));
				}
			}
		}
		lastUsingItem = currentlyUsingItem;

		// Check hand swinging (attack execution)
		boolean currentlyHandSwinging = p.handSwinging;
		if (currentlyHandSwinging && !lastHandSwinging) {
			events.add(new RecordedEvent(timestamp, EventType.ATTACK, "", 0));
		}
		lastHandSwinging = currentlyHandSwinging;

		// Check jump state
		boolean currentlyJumping = p.input.jumping;
		if (currentlyJumping && !lastJumping) {
			events.add(new RecordedEvent(timestamp, EventType.JUMP, "", 0));
		}
		lastJumping = currentlyJumping;

		// Check sneak state transitions
		boolean currentlySneaking = p.input.sneaking;
		if (currentlySneaking && !lastSneaking) {
			events.add(new RecordedEvent(timestamp, EventType.SNEAK_PRESS, "", 0));
		} else if (!currentlySneaking && lastSneaking) {
			events.add(new RecordedEvent(timestamp, EventType.SNEAK_RELEASE, "", 0));
		}
		lastSneaking = currentlySneaking;

		// Check sprint state transitions
		boolean currentlySprinting = p.input.sprinting;
		if (currentlySprinting && !lastSprinting) {
			events.add(new RecordedEvent(timestamp, EventType.SPRINT_PRESS, "", 0));
		} else if (!currentlySprinting && lastSprinting) {
			events.add(new RecordedEvent(timestamp, EventType.SPRINT_RELEASE, "", 0));
		}
		lastSprinting = currentlySprinting;
	}

	public enum EventType {
		SLOT_CHANGE(SELECT_SLOT),
		HOLD_START(null),
		HOLD_END(ActionType.USE_ITEM),
		ATTACK(ActionType.ATTACK),
		USE_ITEM(ActionType.USE_ITEM),
		JUMP(ActionType.JUMP),
		SNEAK_PRESS(ActionType.SNEAK),
		SNEAK_RELEASE(ActionType.SNEAK),
		SPRINT_PRESS(ActionType.SPRINT),
		SPRINT_RELEASE(ActionType.SPRINT);

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
