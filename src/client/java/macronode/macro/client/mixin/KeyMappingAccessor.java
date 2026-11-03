package macronode.macro.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raw write to KeyMapping.isDown, bypassing ToggleKeyMapping's toggle behaviour. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("isDown")
	void macronode$setDown(boolean down);
}
