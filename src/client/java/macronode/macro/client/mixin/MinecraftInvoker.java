package macronode.macro.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla left/right-click logic so macros behave exactly like real clicks. */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
	@Invoker("startAttack")
	boolean macronode$startAttack();

	@Invoker("startUseItem")
	void macronode$startUseItem();
}
