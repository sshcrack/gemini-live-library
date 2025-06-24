package me.sshcrack.websocket_lib;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(WebsocketLibMod.MODID)
public class WebsocketLibMod {
    public static final String MODID = "websocket_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructor for the mod class.
     * Registers event listeners, configurations, and initializes necessary components.
     *
     * @param modEventBus  The mod event bus to register events
     * @param modContainer The mod container for configuration
     */
    public WebsocketLibMod(IEventBus modEventBus, ModContainer modContainer) {

    }
}
