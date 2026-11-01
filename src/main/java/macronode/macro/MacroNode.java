package macronode.macro;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. MacroNode is a client-only mod, so all real logic lives in the
 * client source set ({@code macronode.macro.client.MacroNodeClient}). This class only
 * holds shared constants.
 */
public class MacroNode implements ModInitializer {
	public static final String MOD_ID = "macronode";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("MacroNode common init (client-side mod)");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
