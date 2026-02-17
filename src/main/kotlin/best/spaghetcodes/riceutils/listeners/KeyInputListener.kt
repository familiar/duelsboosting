package best.spaghetcodes.riceutils.listeners

import best.spaghetcodes.riceutils.RiceUtils
import best.spaghetcodes.riceutils.core.Config
import best.spaghetcodes.riceutils.core.KeyBindings
import best.spaghetcodes.riceutils.gui.RiceUtilsGui
import net.minecraft.client.Minecraft
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import java.util.*
import kotlin.concurrent.schedule

object KeyInputListener {
    
    private val mc = Minecraft.getMinecraft()
    private var botEnabled = false
    private var wasInWorld = false
    private var testModeEnabled = false
    
    // 8.5 second command loop
    private var commandLoopTimer: Timer? = null
    private var commandLoopPaused = false
    
    fun isBotEnabled(): Boolean = botEnabled
    fun isTestModeEnabled(): Boolean = testModeEnabled
    
    // Methods to pause/resume the command loop
    fun pauseCommandLoop() {
        commandLoopPaused = true
    }
    
    fun resumeCommandLoop() {
        commandLoopPaused = false
    }
    
    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (KeyBindings.openGuiKeyBinding.isPressed) {
            mc.displayGuiScreen(RiceUtilsGui())
        }
        
        if (KeyBindings.toggleBotKeyBinding.isPressed) {
            toggleBot()
        }
        
        if (KeyBindings.toggleTestModeKeyBinding.isPressed) {
            toggleTestMode()
        }
    }
    
    private fun toggleBot() {
        botEnabled = !botEnabled
        
        if (mc.thePlayer != null) {
            if (botEnabled) {
                mc.thePlayer.addChatMessage(
                    net.minecraft.util.ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}${EnumChatFormatting.BOLD}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.GREEN}${EnumChatFormatting.BOLD}BOT ENABLED! ${EnumChatFormatting.GRAY}Starting automation..."
                    )
                )
                
                // Execute command immediately when bot is enabled
                val command = Config.autoCommand
                if (command.isNotEmpty()) {
                    mc.thePlayer.sendChatMessage(command)
                    mc.thePlayer.addChatMessage(
                        net.minecraft.util.ChatComponentText(
                            "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                            "${EnumChatFormatting.YELLOW}Executing: ${EnumChatFormatting.WHITE}$command"
                        )
                    )
                }
                
                // Start the 8.5 second command loop
                startCommandLoop()
            } else {
                mc.thePlayer.addChatMessage(
                    net.minecraft.util.ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}${EnumChatFormatting.BOLD}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.RED}${EnumChatFormatting.BOLD}BOT DISABLED! ${EnumChatFormatting.GRAY}Stopping automation..."
                    )
                )
                
                // Stop the command loop
                stopCommandLoop()
                
                // Clear any tracked players when disabling
                RenderListener.clearTrackedPlayers()
            }
        }
    }
    
    private fun startCommandLoop() {
        // Cancel existing timer if any
        stopCommandLoop()
        
        // Reset pause state
        commandLoopPaused = false
        
        // Create new timer that runs every 8.5 seconds
        commandLoopTimer = Timer()
        commandLoopTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                // Only run if not paused and bot is still enabled
                if (!commandLoopPaused && botEnabled && mc.thePlayer != null) {
                    val command = Config.autoCommand
                    if (command.isNotEmpty()) {
                        mc.thePlayer.sendChatMessage(command)
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.GRAY}Auto-command: ${EnumChatFormatting.WHITE}$command"
                            )
                        )
                    }
                }
            }
        }, 8500L, 8500L) // First run after 8.5 seconds, then every 8.5 seconds
    }
    
    private fun stopCommandLoop() {
        commandLoopTimer?.cancel()
        commandLoopTimer = null
        commandLoopPaused = false
    }
    
    private fun toggleTestMode() {
        testModeEnabled = !testModeEnabled
        
        if (mc.thePlayer != null) {
            if (testModeEnabled) {
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}${EnumChatFormatting.BOLD}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.AQUA}${EnumChatFormatting.BOLD}TEST MODE ENABLED! ${EnumChatFormatting.GRAY}Detection active, dodging disabled."
                    )
                )
            } else {
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}${EnumChatFormatting.BOLD}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.GRAY}${EnumChatFormatting.BOLD}TEST MODE DISABLED!"
                    )
                )
                
                // Clear any tracked players when disabling test mode
                RenderListener.clearTrackedPlayers()
            }
        }
    }
    
    @SubscribeEvent
    fun onEntityJoinWorld(event: EntityJoinWorldEvent) {
        // Detect player reconnecting to server
        if (event.entity == mc.thePlayer && mc.thePlayer != null) {
            // Check if this is a reconnect (bot was enabled and we weren't in a world)
            if (botEnabled && !wasInWorld) {
                wasInWorld = true
                handleReconnect()
            } else {
                wasInWorld = true
            }
        }
        
        // Detect disconnect (player leaving world)
        if (mc.theWorld == null) {
            wasInWorld = false
        }
    }
    
    private fun handleReconnect() {
        if (mc.thePlayer == null) return
        
        mc.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.YELLOW}${EnumChatFormatting.BOLD}RECONNECT DETECTED! ${EnumChatFormatting.GRAY}Running failsafe..."
            )
        )
        
        // Choose delays based on slow mode
        val delay1 = if (ChatListener.isInSlowMode()) 4000L else 2000L
        val delay2 = if (ChatListener.isInSlowMode()) 3000L else 2000L
        val delay3 = if (ChatListener.isInSlowMode()) 1000L else 500L
        
        // Wait, then /l
        Timer().schedule(delay1) {
            if (mc.thePlayer != null && botEnabled) {
                mc.thePlayer.sendChatMessage("/l")
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.YELLOW}Failsafe: ${EnumChatFormatting.WHITE}/l"
                    )
                )
                
                // Wait for LOBBY state before running /p warp
                waitForLobbyThenWarp(delay2, delay3)
            }
        }
    }
    
    private fun waitForLobbyThenWarp(delay2: Long, delay3: Long) {
        // Check state every 500ms, max 20 attempts (10 seconds)
        var attempts = 0
        val checkTimer = Timer()
        
        val checkTask = object : java.util.TimerTask() {
            override fun run() {
                attempts++
                
                // If we're in lobby, proceed with warps
                if (best.spaghetcodes.riceutils.managers.StateManager.state == best.spaghetcodes.riceutils.managers.StateManager.States.LOBBY) {
                    checkTimer.cancel()
                    proceedWithWarps(delay2, delay3)
                }
                // If max attempts reached, notify timeout
                else if (attempts >= 20) {
                    checkTimer.cancel()
                    if (mc.thePlayer != null && botEnabled) {
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.RED}Failsafe: ${EnumChatFormatting.GRAY}Timeout waiting for lobby state"
                            )
                        )
                    }
                }
            }
        }
        
        checkTimer.schedule(checkTask, 500L, 500L)
    }
    
    private fun proceedWithWarps(delay2: Long, delay3: Long) {
        // Check if party warp is disabled - skip warps
        if (!best.spaghetcodes.riceutils.core.Config.partyWarpEnabled) {
            if (mc.thePlayer != null && botEnabled) {
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.YELLOW}Party Warp disabled, skipping warps..."
                    )
                )
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.GREEN}Failsafe complete! ${EnumChatFormatting.GRAY}Resuming automation..."
                    )
                )
            }
            return
        }
        
        // Wait, then /p warp
        Timer().schedule(delay2) {
            if (mc.thePlayer != null && botEnabled && best.spaghetcodes.riceutils.managers.StateManager.state == best.spaghetcodes.riceutils.managers.StateManager.States.LOBBY) {
                mc.thePlayer.sendChatMessage("/p warp")
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.YELLOW}Failsafe: ${EnumChatFormatting.WHITE}/p warp (1/2)"
                    )
                )
                
                // Wait, then /p warp
                Timer().schedule(delay3) {
                    if (mc.thePlayer != null && botEnabled && best.spaghetcodes.riceutils.managers.StateManager.state == best.spaghetcodes.riceutils.managers.StateManager.States.LOBBY) {
                        mc.thePlayer.sendChatMessage("/p warp")
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.YELLOW}Failsafe: ${EnumChatFormatting.WHITE}/p warp (2/2)"
                            )
                        )
                        
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.GREEN}Failsafe complete! ${EnumChatFormatting.GRAY}Resuming automation..."
                            )
                        )
                    }
                }
            }
        }
    }
}

