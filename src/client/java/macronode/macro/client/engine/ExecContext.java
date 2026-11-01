package macronode.macro.client.engine;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Per-run mutable state handed to every action handler. */
public final class ExecContext {
	public final MacroRun run;
	public final Minecraft mc = Minecraft.getInstance();

	/** Stage index for multi-step actions; reset to 0 when the program counter advances. */
	public int stage;
	public long actionStart;
	public long stageStart;
	/** Scratch storage for the current action (cleared on advance). */
	public final Map<String, Object> scratch = new HashMap<>();

	/** Camera rotation when the macro started (used by Reset Camera). */
	public float startYaw;
	public float startPitch;
	/** Hotbar slot before the macro first changed it (used by "Select Slot [previous]"). */
	public int previousSlot = -1;

	ExecContext(MacroRun run) {
		this.run = run;
		LocalPlayer p = mc.player;
		if (p != null) {
			startYaw = p.getYRot();
			startPitch = p.getXRot();
		}
		resetAction();
	}

	public LocalPlayer player() {
		return mc.player;
	}

	void resetAction() {
		stage = 0;
		scratch.clear();
		actionStart = stageStart = System.nanoTime();
	}

	public void setStage(int s) {
		stage = s;
		stageStart = System.nanoTime();
	}

	public long elapsedInAction() {
		return System.nanoTime() - actionStart;
	}

	public long elapsedInStage() {
		return System.nanoTime() - stageStart;
	}

	/** Selects a hotbar slot, remembering the original slot for "previous". */
	public void selectSlot(int index) {
		LocalPlayer p = player();
		if (p == null || index < 0 || index > 8) return;
		if (previousSlot < 0) previousSlot = p.getInventory().getSelectedSlot();
		p.getInventory().setSelectedSlot(index);
	}

	public void hold(KeyMapping key, long nanos) {
		HeldKeys.get().hold(key, nanos, run);
	}

	public void release(KeyMapping key) {
		HeldKeys.get().release(key);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, T def) {
		Object o = scratch.get(key);
		return o == null ? def : (T) o;
	}
}
