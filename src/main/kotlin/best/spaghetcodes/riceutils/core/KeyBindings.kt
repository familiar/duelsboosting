package best.spaghetcodes.riceutils.core

import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.input.Keyboard

object KeyBindings {
    val openGuiKeyBinding = KeyBinding("Open RiceUtils GUI", Keyboard.KEY_RSHIFT, "RiceUtils")
    val toggleBotKeyBinding = KeyBinding("Toggle RiceUtils Bot", Keyboard.KEY_SEMICOLON, "RiceUtils")
    val toggleTestModeKeyBinding = KeyBinding("Toggle Test Mode (Detection Only)", Keyboard.KEY_P, "RiceUtils")
    
    fun register() {
        ClientRegistry.registerKeyBinding(openGuiKeyBinding)
        ClientRegistry.registerKeyBinding(toggleBotKeyBinding)
        ClientRegistry.registerKeyBinding(toggleTestModeKeyBinding)
    }
}

