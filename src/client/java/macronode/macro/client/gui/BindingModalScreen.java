package macronode.macro.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import macronode.macro.client.data.MacroProfile;
import macronode.macro.client.data.ProfileStore;
import macronode.macro.client.data.Trigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Profile creation / binding popup: name, Single vs Combo trigger, key recorder and the
 * High-Priority "Override Native Input" toggle. Edits a draft and writes back on Save.
 */
public class BindingModalScreen extends Screen {
	private final Screen parent;
	private final MacroProfile target;
	private final boolean isNew;

	private String name;
	private Trigger.Type type;
	private final List<InputConstants.Key> keys = new ArrayList<>();
	private boolean override;
	private boolean enabled;
	private boolean stopOnRepress;
	private boolean recording;

	private int x0, y0, mw, mh;

	public BindingModalScreen(Screen parent, MacroProfile target, boolean isNew) {
		super(Component.literal(isNew ? "Create Profile" : "Edit Binding"));
		this.parent = parent;
		this.target = target;
		this.isNew = isNew;
		this.name = target.name;
		this.type = target.trigger.type;
		this.keys.addAll(target.trigger.resolve());
		this.override = target.overrideNative;
		this.enabled = target.enabled;
		this.stopOnRepress = target.stopOnRepress;
	}

	private static Component lit(String s) {
		return Component.literal(s);
	}

	@Override
	protected void init() {
		mw = Math.min(width - 20, 290);
		mh = 222;
		x0 = (width - mw) / 2;
		y0 = (height - mh) / 2;
		int iw = mw - 20, lx = x0 + 10;

		EditBox nameBox = new EditBox(font, lx, y0 + 34, iw, 16, lit("Name"));
		nameBox.setMaxLength(48);
		nameBox.setValue(name);
		nameBox.setResponder(v -> name = v);
		addRenderableWidget(nameBox);

		int half = (iw - 4) / 2;
		addRenderableWidget(new DarkButton(lx, y0 + 66, half, 16, lit("Single Keybind"), b -> {
			type = Trigger.Type.SINGLE;
			if (keys.size() > 1) keys.subList(0, keys.size() - 1).clear();
			rebuildWidgets();
		}).selected(type == Trigger.Type.SINGLE));
		addRenderableWidget(new DarkButton(lx + half + 4, y0 + 66, half, 16, lit("Key Combination"), b -> {
			type = Trigger.Type.COMBO;
			rebuildWidgets();
		}).selected(type == Trigger.Type.COMBO).accent(Theme.VIOLET));

		addRenderableWidget(new DarkButton(lx, y0 + 98, iw - 54, 16,
				lit(recording ? "Press key(s)...  [Esc] cancels" : "\u25CF Record Trigger"), b -> {
			recording = !recording;
			if (recording) keys.clear();
			rebuildWidgets();
		}).selected(recording).accent(Theme.MINT));
		addRenderableWidget(new DarkButton(lx + iw - 50, y0 + 98, 50, 16, lit("Clear"), b -> {
			keys.clear();
			recording = false;
			rebuildWidgets();
		}));

		addRenderableWidget(new DarkButton(lx, y0 + 140, iw, 16,
				lit("High-Priority Override Native Input: " + (override ? "ON" : "OFF")), b -> {
			override = !override;
			rebuildWidgets();
		}).selected(override).accent(Theme.CYAN));
		addRenderableWidget(new DarkButton(lx, y0 + 168, half, 16, lit("Enabled: " + (enabled ? "ON" : "OFF")), b -> {
			enabled = !enabled;
			rebuildWidgets();
		}).selected(enabled).accent(Theme.MINT));
		addRenderableWidget(new DarkButton(lx + half + 4, y0 + 168, half, 16,
				lit("Stop on re-press: " + (stopOnRepress ? "ON" : "OFF")), b -> {
			stopOnRepress = !stopOnRepress;
			rebuildWidgets();
		}).selected(stopOnRepress).accent(Theme.VIOLET));

		int third = (iw - 8) / 3, by = y0 + mh - 24;
		addRenderableWidget(new DarkButton(lx, by, third, 16, lit("Cancel"), b -> onClose()));
		addRenderableWidget(new DarkButton(lx + third + 4, by, third, 16, lit("Save"), b -> {
			save();
			onClose();
		}).accent(Theme.MINT));
		addRenderableWidget(new DarkButton(lx + 2 * (third + 4), by, third, 16, lit("Save & Edit"), b -> {
			save();
			if (parent instanceof MacroEditorScreen) minecraft.setScreen(parent);
			else minecraft.setScreen(new MacroEditorScreen(parent, target));
		}).accent(Theme.VIOLET));
	}

	private void save() {
		target.name = name == null || name.isBlank() ? "Macro" : name.trim();
		List<String> names = new ArrayList<>();
		for (InputConstants.Key k : keys) names.add(k.getName());
		target.trigger = new Trigger(type, names);
		target.overrideNative = override;
		target.enabled = enabled;
		target.stopOnRepress = stopOnRepress;
		ProfileStore.get().save(target);
	}

	private void addKey(InputConstants.Key k) {
		if (type == Trigger.Type.SINGLE) keys.clear();
		if (!keys.contains(k)) keys.add(k);
		if (type == Trigger.Type.SINGLE || keys.size() >= 4) stopRecording();
	}

	private void stopRecording() {
		recording = false;
		rebuildWidgets();
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (recording) {
			if (e.key() == GLFW.GLFW_KEY_ESCAPE) stopRecording();
			else addKey(InputConstants.getKey(e));
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean keyReleased(KeyEvent e) {
		// Combo recording ends when the first key of the chord is released.
		if (recording && type == Trigger.Type.COMBO && !keys.isEmpty()) {
			stopRecording();
			return true;
		}
		return super.keyReleased(e);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (recording) {
			// Left/right click are reserved for the UI; middle and side buttons can be recorded.
			if (e.button() >= 2) addKey(InputConstants.Type.MOUSE.getOrCreate(e.button()));
			else stopRecording();
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (recording && type == Trigger.Type.COMBO && e.button() >= 2 && !keys.isEmpty()) {
			stopRecording();
			return true;
		}
		return super.mouseReleased(e);
	}

	private String conflict() {
		List<String> mine = new ArrayList<>();
		for (InputConstants.Key k : keys) mine.add(k.getName());
		if (mine.isEmpty()) return null;
		for (MacroProfile p : ProfileStore.get().profiles()) {
			if (!p.id.equals(target.id) && p.enabled && mine.equals(p.trigger.keys)) return p.name;
		}
		return null;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, Theme.SHADE);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Theme.panel(g, x0, y0, mw, mh, Theme.CARD);
		g.fill(x0, y0, x0 + mw, y0 + 2, Theme.CYAN);
		g.drawString(font, title.getString(), x0 + 10, y0 + 9, Theme.WHITE, false);
		int lx = x0 + 10;
		g.drawString(font, "Profile name", lx, y0 + 24, Theme.GRAY, false);
		g.drawString(font, "Trigger type", lx, y0 + 56, Theme.GRAY, false);
		g.drawString(font, type == Trigger.Type.SINGLE ? "One key or mouse button  (G, 2, Mouse 4)"
				: "Hold chord keys, activator last  (Ctrl + 1, T + K)", lx, y0 + 86, Theme.withAlpha(Theme.GRAY, 0xAA), false);

		int tx = lx, ty = y0 + 120;
		if (keys.isEmpty()) {
			g.drawString(font, recording ? "Listening..." : "Unbound", tx, ty + 2, Theme.GRAY, false);
		} else {
			for (int i = 0; i < keys.size(); i++) {
				if (i > 0) {
					g.drawString(font, "+", tx + 2, ty + 2, Theme.GRAY, false);
					tx += 10;
				}
				tx += Theme.tag(g, font, keys.get(i).getDisplayName().getString(), tx, ty, Theme.MINT) + 2;
			}
		}
		String c = conflict();
		if (c != null) g.drawString(font, "Conflicts with '" + c + "'", lx, y0 + 188, Theme.RED, false);
		g.drawString(font, override ? "Key is swallowed: vanilla never sees it" : "Key also reaches vanilla",
				lx, y0 + 158, Theme.withAlpha(Theme.GRAY, 0xAA), false);
		super.render(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
