package macronode.macro.client.engine;

import java.util.ArrayList;
import java.util.List;

import macronode.macro.client.data.MacroProfile;
import macronode.macro.client.data.ProfileStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Sub-tick executor. {@link #pump()} is invoked at the HEAD of every {@code Minecraft.runTick}
 * (i.e. once per rendered frame, before input and the client tick are processed) and again at
 * END_CLIENT_TICK. Delays are nanosecond deadlines, so fractional ticks (0.2t = 10ms) and
 * millisecond waits resolve at frame granularity instead of 50ms tick granularity, while 0t
 * continues in the same pump without yielding.
 */
public final class MacroEngine {
	private static final MacroEngine INSTANCE = new MacroEngine();
	private final List<MacroRun> runs = new ArrayList<>();
	private boolean pumping;

	public static MacroEngine get() {
		return INSTANCE;
	}

	public void pump() {
		if (pumping) return;
		pumping = true;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				if (!runs.isEmpty()) stopAll();
				return;
			}
			HeldKeys.get().pump(System.nanoTime());
			AimLockManager.get().pump();
			if (runs.isEmpty()) return;
			for (MacroRun run : new ArrayList<>(runs)) {
				run.pump();
				if (run.isFinished()) runs.remove(run);
			}
		} finally {
			pumping = false;
		}
	}

	/** Called by the input interceptor when a profile's trigger is pressed. */
	public void onTrigger(MacroProfile profile) {
		MacroRun running = find(profile.id);
		if (running != null) {
			if (profile.stopOnRepress) stop(profile);
			return;
		}
		start(profile);
	}

	public void start(MacroProfile profile) {
		if (!ProfileStore.get().settings().allowConcurrent) stopAllQuiet();
		MacroRun existing = find(profile.id);
		if (existing != null) {
			existing.finish();
			runs.remove(existing);
		}
		MacroRun run = new MacroRun(profile);
		runs.add(run);
		feedback("\u25B6 " + profile.name, ChatFormatting.AQUA);
		run.pump(); // run zero-tick prefix immediately, in the same frame as the key press
		if (run.isFinished()) runs.remove(run);
	}

	public void stop(MacroProfile profile) {
		MacroRun run = find(profile.id);
		if (run != null) {
			run.finish();
			runs.remove(run);
			feedback("\u25A0 " + profile.name + " stopped", ChatFormatting.GRAY);
		}
	}

	public void stopAll() {
		boolean any = !runs.isEmpty();
		stopAllQuiet();
		HeldKeys.get().releaseAll();
		if (any) feedback("\u25A0 All macros stopped", ChatFormatting.GRAY);
	}

	private void stopAllQuiet() {
		for (MacroRun r : runs) r.finish();
		runs.clear();
	}

	public boolean isRunning(String profileId) {
		return find(profileId) != null;
	}

	public int runningCount() {
		return runs.size();
	}

	/** Block id currently executing for a profile (editor highlight), or -1. */
	public int currentBlock(String profileId) {
		MacroRun r = find(profileId);
		return r == null ? -1 : r.currentBlockId();
	}

	private MacroRun find(String id) {
		for (MacroRun r : runs) if (r.profile.id.equals(id) && !r.isFinished()) return r;
		return null;
	}

	private static void feedback(String msg, ChatFormatting color) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && ProfileStore.get().settings().feedback) {
			mc.player.displayClientMessage(Component.literal(msg).withStyle(color), true);
		}
	}
}
