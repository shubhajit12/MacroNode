package macronode.macro.client.recorder;

import java.util.ArrayList;
import java.util.List;

import macronode.macro.client.action.ActionBlock;
import macronode.macro.client.action.ActionType;
import macronode.macro.client.data.MacroProfile;

public final class RecordGenerator {
    private RecordGenerator() {
    }

    public static MacroProfile generateProfile(List<MacroRecorder.RecordedEvent> events, String profileName) {
        MacroProfile profile = new MacroProfile();
        profile.name = profileName;
        profile.enabled = true;
        profile.overrideNative = true;

        if (events == null || events.isEmpty()) {
            return profile;
        }

        List<ActionBlock> blocks = new ArrayList<>();
        ActionBlock lastBlock = null;
        long lastTimestamp = 0L;

        for (MacroRecorder.RecordedEvent event : events) {
            long delayMs = event.timestamp() - lastTimestamp;

            if (delayMs > 50L && lastBlock != null) {
                ActionBlock waitBlock = createWaitBlock(profile, delayMs);
                blocks.add(waitBlock);
                lastBlock.next = waitBlock.id;
                lastBlock = waitBlock;
            }

            ActionBlock actionBlock = convertEventToBlock(profile, event);
            if (actionBlock != null) {
                blocks.add(actionBlock);
                if (lastBlock != null) {
                    lastBlock.next = actionBlock.id;
                }
                lastBlock = actionBlock;
            }

            lastTimestamp = event.timestamp();
        }

        if (!blocks.isEmpty()) {
            for (int i = 0; i < blocks.size() - 1; i++) {
                blocks.get(i).next = blocks.get(i + 1).id;
            }
            profile.rootId = blocks.get(0).id;
            profile.blocks = blocks;

            int x = profile.startX + 70;
            int y = profile.startY;
            for (ActionBlock block : blocks) {
                block.x = x;
                block.y = y;
                x += 150;
                if (x > 850) {
                    x = profile.startX + 70;
                    y += 90;
                }
            }
        }

        return profile;
    }

    private static ActionBlock createWaitBlock(MacroProfile profile, long delayMs) {
        ActionBlock block = new ActionBlock(profile.nextBlockId(), ActionType.WAIT_MS);
        long ticks = delayMs / 50L;
        if (ticks < 1L) ticks = 1L;
        block.params.put("ms", String.valueOf(delayMs));
        block.params.put("ticks", String.valueOf(ticks));
        return block;
    }

    private static ActionBlock convertEventToBlock(MacroProfile profile, MacroRecorder.RecordedEvent event) {
        ActionType actionType = switch (event.type()) {
            case SLOT_CHANGE -> ActionType.SELECT_SLOT;
            case ATTACK -> ActionType.ATTACK;
            case JUMP -> ActionType.JUMP;
            case SNEAK_PRESS, SNEAK_RELEASE -> ActionType.SNEAK;
            case SPRINT_PRESS, SPRINT_RELEASE -> ActionType.SPRINT;
            case USE_ITEM -> ActionType.USE_ITEM;
            default -> null;
        };

        if (actionType == null) return null;

        ActionBlock block = new ActionBlock(profile.nextBlockId(), actionType);

        switch (event.type()) {
            case SLOT_CHANGE -> {
                block.params.put("slot", event.data());
                block.params.put("fetch", "false");
            }
            case USE_ITEM -> {
                if (event.duration() > 50L) {
                    long holdTicks = Math.max(1L, Math.round(event.duration() / 50.0));
                    block.params.put("mode", "HOLD");
                    block.params.put("hand", "MAIN");
                    block.params.put("duration", holdTicks + "t");
                } else {
                    block.params.put("mode", "CLICK");
                    block.params.put("hand", "MAIN");
                }
            }
            case SNEAK_PRESS -> block.params.put("mode", "TOGGLE");
            case SNEAK_RELEASE -> block.params.put("mode", "RELEASE");
            case SPRINT_PRESS -> block.params.put("mode", "TOGGLE");
            case SPRINT_RELEASE -> block.params.put("mode", "RELEASE");
            default -> {
            }
        }

        return block;
    }
}
