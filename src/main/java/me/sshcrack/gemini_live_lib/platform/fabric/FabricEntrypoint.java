package me.sshcrack.gemini_live_lib.platform.fabric;

//? fabric {

import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ModInitializer;

@Entrypoint("main")
public class FabricEntrypoint implements ModInitializer {

	@Override
	public void onInitialize() {
		GeminiLiveLib.LOGGER.info("Initializing {}", GeminiLiveLib.MOD_ID);
	}
}
//?}
