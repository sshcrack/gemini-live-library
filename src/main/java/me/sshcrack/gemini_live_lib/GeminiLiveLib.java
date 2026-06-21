package me.sshcrack.gemini_live_lib;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import me.sshcrack.gemini_live_lib.provider.GeminiLiveSession;
import me.sshcrack.gemini_live_lib.provider.GeminiQuotaManager;
import me.sshcrack.mc_talking.api.provider.AiRegistry;
import me.sshcrack.mc_talking.api.provider.ConfigField;
import me.sshcrack.mc_talking.api.provider.PresetDefinition;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
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

    private static final Gson GSON = new Gson();

    public GeminiLiveLib() {
        registerProviders();
        registerPresets();
        registerConfigFields();
    }

    private void registerProviders() {
        var liveProvider = new me.sshcrack.gemini_live_lib.provider.GeminiLiveProvider();
        AiRegistry.register(liveProvider);

        var flashProvider = new me.sshcrack.gemini_live_lib.provider.GeminiFlashProvider();
        AiRegistry.register(flashProvider);

        var ttsProvider = new me.sshcrack.gemini_live_lib.provider.GeminiTtsProvider();
        AiRegistry.register(ttsProvider);

        var pregenProvider = new me.sshcrack.gemini_live_lib.provider.GeminiPregenerationProvider();
        AiRegistry.register(pregenProvider);

        AiRegistry.registerQuotaManager("gemini_live", new GeminiQuotaManager(3));

        LOGGER.info("[GeminiLiveLib] Registered providers: live, flash, tts, pregeneration");
    }

    private void registerPresets() {
        AiRegistry.registerPreset(new PresetDefinition(
            "live_live", "gemini_live_lib.preset.live_live", MOD_ID,
            PresetDefinition.PresetMode.BUNDLED,
            "gemini_live", null, null, null, null,
            Map.of()
        ));

        AiRegistry.registerPreset(new PresetDefinition(
            "flash_tts", "gemini_live_lib.preset.flash_tts", MOD_ID,
            PresetDefinition.PresetMode.PIPELINE,
            null, null, "gemini_flash", "gemini_tts", null,
            Map.of()
        ));

        AiRegistry.registerPreset(new PresetDefinition(
            "flash", "gemini_live_lib.preset.flash", MOD_ID,
            PresetDefinition.PresetMode.PIPELINE,
            null, null, "gemini_flash", null, null,
            Map.of()
        ));

        AiRegistry.registerPreset(new PresetDefinition(
            "live", "gemini_live_lib.preset.live", MOD_ID,
            PresetDefinition.PresetMode.BUNDLED,
            "gemini_live", null, null, null, null,
            Map.of()
        ));

        LOGGER.info("[GeminiLiveLib] Registered presets: live_live, flash_tts, flash, live");
    }

    private void registerConfigFields() {
        AiRegistry.registerConfigFields(MOD_ID, List.of(
            new ConfigField("apiKey", ConfigField.ConfigType.STRING, "", 0, 0),
            new ConfigField("maxConcurrent", ConfigField.ConfigType.INTEGER, 3, 1, 10)
        ));
    }

    public static String resolveApiKey() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.isEmpty()) return key;

        key = System.getProperty("gemini.apiKey");
        if (key != null && !key.isEmpty()) return key;

        return "";
    }
}
