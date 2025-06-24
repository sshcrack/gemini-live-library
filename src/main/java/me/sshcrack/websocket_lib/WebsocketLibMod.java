package me.sshcrack.websocket_lib;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import me.sshcrack.mc_talking.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import org.slf4j.Logger;

@Mod(WebsocketLibMod.MODID)
public class WebsocketLibMod {
    public static final String MODID = "websocket_java_lib";
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
