package macronode.macro.client.mixin;

import macronode.macro.client.input.InputInterceptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Same as {@link KeyboardHandlerMixin} for mouse buttons (Mouse 4, Mouse 5, middle click...). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void macronode$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (window != this.minecraft.getWindow().handle()) return;
		if (InputInterceptor.onMouse(info.button(), action)) ci.cancel();
	}
}
