package me.sshcrack.gemini_live_lib;

import com.mojang.logging.LogUtils;
import me.sshcrack.gemini_live_lib.provider.GeminiFlashLlm;
import me.sshcrack.gemini_live_lib.provider.GeminiLiveSessionProvider;
import me.sshcrack.gemini_live_lib.provider.GeminiTtsImpl;
import me.sshcrack.mc_talking.api.provider.AiProviderRegistry;
import org.slf4j.Logger;
/*? if forge {*/
/*import net.minecraftforge.fml.common.Mod;
 *//*?}*/
/*? if neoforge {*/
import net.neoforged.fml.common.Mod;
/*?}*/

@Mod(GeminiLiveLib.MOD_ID)
public class GeminiLiveLib {
    public static final String MOD_ID = /*$ mod_id*/ "gemini_live_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    /*? if neoforge {*/
    public GeminiLiveLib() {
        registerProviders();
    }
    /*?}*/
    /*? if forge {*/
    /*public GeminiLiveLib() {
        registerProviders();
    }
    *//*?}*/

    private static void registerProviders() {
        AiProviderRegistry.register(new GeminiLiveSessionProvider());
        AiProviderRegistry.register(new GeminiFlashLlm());
        AiProviderRegistry.register(new GeminiTtsImpl());
        LOGGER.info("Registered Gemini AI providers");
    }
}
