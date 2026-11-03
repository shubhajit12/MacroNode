package macronode.macro.client.mixin;

import macronode.macro.client.engine.MacroEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pumps the sub-tick executor once per frame, before input polling and the client tick. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void macronode$pumpFrame(boolean advanceGameTime, CallbackInfo ci) {
		MacroEngine.get().pump();
	}
}
