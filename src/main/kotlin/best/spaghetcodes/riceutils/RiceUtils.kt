package best.spaghetcodes.riceutils

import best.spaghetcodes.riceutils.core.Config
import best.spaghetcodes.riceutils.core.KeyBindings
import best.spaghetcodes.riceutils.listeners.ChatListener
import best.spaghetcodes.riceutils.listeners.KeyInputListener
import best.spaghetcodes.riceutils.listeners.RenderListener
import best.spaghetcodes.riceutils.managers.StateManager
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent

@Mod(
    modid = RiceUtils.MODID,
    name = RiceUtils.NAME,
    version = RiceUtils.VERSION,
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.8.9]"
)
class RiceUtils {

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        println("$NAME $VERSION initializing...")
        
        // Load config
        Config.loadConfig()
        
        // Register keybindings
        KeyBindings.register()
        
        // Register event listeners
        MinecraftForge.EVENT_BUS.register(StateManager)
        MinecraftForge.EVENT_BUS.register(ChatListener)
        MinecraftForge.EVENT_BUS.register(RenderListener)
        MinecraftForge.EVENT_BUS.register(KeyInputListener)
        
        println("$NAME $VERSION initialized successfully!")
    }

    companion object {
        const val MODID = "riceutils"
        const val NAME = "RiceUtils"
        const val VERSION = "1.0.0"
        
        val mc: Minecraft = Minecraft.getMinecraft()
    }
}

