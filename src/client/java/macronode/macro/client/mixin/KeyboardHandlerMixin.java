package macronode.macro.client.mixin;

import macronode.macro.client.input.InputInterceptor;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * High-priority key hook. Injected at HEAD of KeyboardHandler#keyPress - the first Java code that
 * sees a GLFW key event - so we run before KeyMapping.set/click, hotbar selection, F3 combos, etc.
 * Cancelling here means vanilla never learns the key was pressed.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void macronode$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (window != this.minecraft.getWindow().handle()) return;
		if (InputInterceptor.onKey(action, event)) ci.cancel();
	}
}
