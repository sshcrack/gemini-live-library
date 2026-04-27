package me.sshcrack.gemini_live_lib;

import com.mojang.logging.LogUtils;
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
}
