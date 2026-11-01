package macronode.macro.client;

import com.mojang.blaze3d.platform.InputConstants;
import macronode.macro.MacroNode;
import macronode.macro.client.data.ProfileStore;
import macronode.macro.client.engine.MacroEngine;
import macronode.macro.client.gui.ConfigScreen;
import macronode.macro.client.gui.MacroEditorScreen;
import macronode.macro.client.recorder.MacroRecorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint: loads profiles, registers the [O]/[E]/stop hotkeys and hooks the
 * executor into the client tick (the per-frame pump lives in MinecraftMixin).
 */
public class MacroNodeClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MacroNode.id("main"));

	public static KeyMapping OPEN_CONFIG;
	public static KeyMapping OPEN_EDITOR;
	public static KeyMapping STOP_ALL;
	public static KeyMapping RECORD;

	@Override
	public void onInitializeClient() {
		ProfileStore.get().load();

		OPEN_CONFIG = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.macronode.config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));
		OPEN_EDITOR = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.macronode.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY));
		STOP_ALL = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.macronode.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		RECORD = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.macronode.record", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			MacroEngine.get().pump();
			MacroRecorder.get().pump();
			// Fallback for hotkeys rebound to mouse buttons (keyboard presses are caught earlier by the mixin).
			while (OPEN_CONFIG.consumeClick()) {
				if (client.screen == null) client.setScreen(new ConfigScreen(null));
			}
			while (OPEN_EDITOR.consumeClick()) {
				if (client.screen == null) client.setScreen(MacroEditorScreen.openDefault(null));
			}
			while (STOP_ALL.consumeClick()) MacroEngine.get().stopAll();
			while (RECORD.consumeClick()) {
				MacroRecorder recorder = MacroRecorder.get();
				if (recorder.isRecording()) {
					recorder.stopRecording();
				} else {
					recorder.startRecording();
				}
			}
		});

		MacroNode.LOGGER.info("MacroNode client ready: [O] config hub, [E] macro editor");
	}
}
