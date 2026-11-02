package macronode.macro.client.recorder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class MacroRecorder {
    private static final MacroRecorder INSTANCE = new MacroRecorder();

    public static MacroRecorder get() {
        return INSTANCE;
    }

    private boolean recording = false;
    private long recordingStartTime = 0L;
    private final List<RecordedEvent> events = new ArrayList<>();

    // State snapshot tracking
    private int lastHotbarSlot = -1;
    private boolean lastSneaking = false;
    private boolean lastSprinting = false;
    private boolean lastJumping = false;
    private boolean lastUsingItem = false;
    private boolean lastSwinging = false;
    private Long useItemStartMs = null;

    private MacroRecorder() {
    }

    public void startRecording() {
        if (recording) return;

        recording = true;
        recordingStartTime = System.currentTimeMillis();
        events.clear();

        lastHotbarSlot = -1;
        lastSneaking = false;
        lastSprinting = false;
        lastJumping = false;
        lastUsingItem = false;
        lastSwinging = false;
        useItemStartMs = null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            lastHotbarSlot = mc.player.getInventory().getSelectedSlot();
            lastSneaking = mc.player.input != null && mc.player.input.sneaking;
            lastSprinting = mc.player.isSprinting();
            lastJumping = mc.player.input != null && mc.player.input.jumping;
            lastUsingItem = mc.player.isUsingItem();
            lastSwinging = mc.player.swinging;
        }
    }

    public void stopRecording() {
        if (!recording) return;

        finalizeUseItemIfNeeded();
        recording = false;
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

    public void pump() {
        if (!recording) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        long timestamp = now - recordingStartTime;

        int currentSlot = player.getInventory().getSelectedSlot();
        if (lastHotbarSlot != -1 && currentSlot != lastHotbarSlot) {
            events.add(new RecordedEvent(timestamp, EventType.SLOT_CHANGE, String.valueOf(currentSlot), 0L));
        }
        lastHotbarSlot = currentSlot;

        boolean jumping = player.input != null && player.input.jumping;
        if (jumping && !lastJumping) {
            events.add(new RecordedEvent(timestamp, EventType.JUMP, "", 0L));
        }
        lastJumping = jumping;

        boolean sneaking = player.input != null && player.input.sneaking;
        if (sneaking != lastSneaking) {
            if (sneaking) {
                events.add(new RecordedEvent(timestamp, EventType.SNEAK_PRESS, "", 0L));
            } else {
                events.add(new RecordedEvent(timestamp, EventType.SNEAK_RELEASE, "", 0L));
            }
        }
        lastSneaking = sneaking;

        boolean sprinting = player.isSprinting();
        if (sprinting != lastSprinting) {
            if (sprinting) {
                events.add(new RecordedEvent(timestamp, EventType.SPRINT_PRESS, "", 0L));
            } else {
                events.add(new RecordedEvent(timestamp, EventType.SPRINT_RELEASE, "", 0L));
            }
        }
        lastSprinting = sprinting;

        boolean usingItem = player.isUsingItem();
        if (usingItem && !lastUsingItem) {
            useItemStartMs = now;
        } else if (!usingItem && lastUsingItem && useItemStartMs != null) {
            long duration = now - useItemStartMs;
            if (duration > 0L) {
                events.add(new RecordedEvent(timestamp, EventType.USE_ITEM, "", duration));
            }
            useItemStartMs = null;
        }
        lastUsingItem = usingItem;

        boolean swinging = player.swinging;
        if (swinging && !lastSwinging) {
            events.add(new RecordedEvent(timestamp, EventType.ATTACK, "", 0L));
        }
        lastSwinging = swinging;
    }

    private void finalizeUseItemIfNeeded() {
        if (useItemStartMs == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            useItemStartMs = null;
            return;
        }

        long now = System.currentTimeMillis();
        long duration = now - useItemStartMs;
        if (duration > 0L) {
            events.add(new RecordedEvent(now - recordingStartTime, EventType.USE_ITEM, "", duration));
        }
        useItemStartMs = null;
    }

    public void onKeyPress(int keyCode) {
        // Recorder intentionally does not rely on raw GLFW key events.
        // The proper capture path is END_CLIENT_TICK state snapshotting in pump().
    }

    public void onMousePress(int button) {
        // Recorder intentionally does not rely on raw mouse callbacks.
        // Attack/use item are determined from the in-game player state in pump().
    }

    public void onMouseRelease(int button) {
        // Recorder intentionally does not rely on raw mouse callbacks.
    }

    public enum EventType {
        SLOT_CHANGE,
        ATTACK,
        JUMP,
        SNEAK_PRESS,
        SNEAK_RELEASE,
        SPRINT_PRESS,
        SPRINT_RELEASE,
        USE_ITEM
    }

    public record RecordedEvent(long timestamp, EventType type, String data, long duration) {
    }
}
