package me.sshcrack.gemini_live_lib;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(GeminiLiveLib.MODID)
public class GeminiLiveLib {
    public static final String MODID = "gemini_live_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructor for the mod class.
     * Registers event listeners, configurations, and initializes necessary components.
     *
     * @param modEventBus  The mod event bus to register events
     * @param modContainer The mod container for configuration
     */
    public GeminiLiveLib(IEventBus modEventBus, ModContainer modContainer) {

    }
}
