package me.sshcrack.gemini_live_lib.platform.fabric;

//? fabric {

import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;

@Entrypoint("client")
public class FabricClientEntrypoint implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		GeminiLiveLib.LOGGER.info("Initializing {} Client", GeminiLiveLib.MOD_ID);
	}
}
//?}
