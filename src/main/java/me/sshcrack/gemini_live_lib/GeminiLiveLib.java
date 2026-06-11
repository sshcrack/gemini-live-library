package me.sshcrack.gemini_live_lib;

//? < 1.17 {
/*import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;*/
//?} else {
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
//?}
/*? if forge {*/
/*import net.minecraftforge.fml.common.Mod;
 *//*?}*/
/*? if neoforge {*/
import net.neoforged.fml.common.Mod;
/*?}*/

//? if forge || neoforge {
@Mod(GeminiLiveLib.MOD_ID)
//?}
public class GeminiLiveLib {
    public static final String MOD_ID = /*$ mod_id*/ "gemini_live_lib";
    //? < 1.17 {
    /*public static final Logger LOGGER = LogManager.getLogger(MOD_ID);*/
    //?} else {
    public static final Logger LOGGER = LogUtils.getLogger();
    //?}
}
