package macronode.macro.client.recorder;

import java.util.ArrayList;
import java.util.List;

import macronode.macro.client.action.ActionBlock;
import macronode.macro.client.action.ActionType;
import macronode.macro.client.data.MacroProfile;

/**
 * Converts recorded events into ActionBlock chains with automatic delay calculation.
 * Consolidates hold actions and inserts Wait nodes between actions.
 */
public final class RecordGenerator {
	private RecordGenerator() {
	}

	/**
	 * Generates a MacroProfile from recorded events.
	 * @param events List of recorded events in chronological order
	 * @param profileName Name for the generated profile
	 * @return A complete MacroProfile ready to save
	 */
	public static MacroProfile generateProfile(List<MacroRecorder.RecordedEvent> events, String profileName) {
		MacroProfile profile = new MacroProfile();
		profile.name = profileName;
		profile.enabled = true;
		profile.overrideNative = true;

		if (events.isEmpty()) {
			return profile;
		}

		List<ActionBlock> blocks = new ArrayList<>();
		ActionBlock lastBlock = null;
		long lastTimestamp = 0;

		for (MacroRecorder.RecordedEvent event : events) {
			// Calculate delay since last action
			long delay = event.timestamp - lastTimestamp;

			// Insert Wait node if delay is significant (>50ms)
			if (delay > 50 && lastBlock != null) {
				ActionBlock waitBlock = createWaitBlock(profile, delay);
				blocks.add(waitBlock);
				lastBlock.next = waitBlock.id;
				lastBlock = waitBlock;
			}

			// Convert event to ActionBlock
			ActionBlock actionBlock = convertEventToBlock(profile, event);
			if (actionBlock != null) {
				blocks.add(actionBlock);
				if (lastBlock != null) {
					lastBlock.next = actionBlock.id;
				}
				lastBlock = actionBlock;
			}

			lastTimestamp = event.timestamp;
		}

		// Link blocks into profile
		if (!blocks.isEmpty()) {
			profile.rootId = blocks.get(0).id;
			profile.blocks = blocks;

			// Layout blocks in a simple linear chain
			int x = profile.startX + 70;
			int y = profile.startY;
			for (ActionBlock block : blocks) {
				block.x = x;
				block.y = y;
				x += 130;
				if (x > 640) {
					x = profile.startX + 70;
					y += 60;
				}
			}
		}

		return profile;
	}

	private static ActionBlock createWaitBlock(MacroProfile profile, long delayMs) {
		ActionBlock block = new ActionBlock(profile.nextBlockId(), ActionType.WAIT_MS);
		// Convert to ticks (50ms per tick) for cleaner representation
		long ticks = delayMs / 50;
		if (ticks < 1) ticks = 1;
		block.params.put("ms", String.valueOf(delayMs));
		return block;
	}

	private static ActionBlock convertEventToBlock(MacroProfile profile, MacroRecorder.RecordedEvent event) {
		ActionType actionType = event.type().toActionType();
		if (actionType == null) return null;

		ActionBlock block = new ActionBlock(profile.nextBlockId(), actionType);

		if (event.type() == MacroRecorder.EventType.SLOT_CHANGE) {
			block.params.put("slot", event.data());
			block.params.put("fetch", "false");
		} else if (event.type() == MacroRecorder.EventType.HOLD_START) {
			return null; // Will be handled by HOLD_END
		} else if (event.type() == MacroRecorder.EventType.HOLD_END) {
			block.params.put("mode", "HOLD");
			block.params.put("hand", "MAIN");
			// Convert duration to ticks
			long ticks = event.duration() / 50;
			if (ticks < 1) ticks = 1;
			block.params.put("duration", ticks + "t");
		} else if (event.type() == MacroRecorder.EventType.USE_ITEM) {
			block.params.put("mode", "CLICK");
			block.params.put("hand", "MAIN");
		} else if (event.type() == MacroRecorder.EventType.SNEAK_PRESS) {
			block.params.put("mode", "TOGGLE");
		} else if (event.type() == MacroRecorder.EventType.SNEAK_RELEASE) {
			block.params.put("mode", "RELEASE");
		} else if (event.type() == MacroRecorder.EventType.SPRINT_PRESS) {
			block.params.put("mode", "TOGGLE");
		} else if (event.type() == MacroRecorder.EventType.SPRINT_RELEASE) {
			block.params.put("mode", "RELEASE");
		}

		return block;
	}
}
