package best.spaghetcodes.riceutils.listeners

import best.spaghetcodes.riceutils.core.Config
import best.spaghetcodes.riceutils.managers.StateManager
import best.spaghetcodes.riceutils.utils.EntityScanner
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*
import kotlin.concurrent.schedule

object ChatListener {

    private val mc = Minecraft.getMinecraft()
    
    // Regex patterns to match join messages
    private val joinPattern2v2 = Regex(".* has joined \\(./2\\)!")
    private val joinPattern4v4 = Regex(".* has joined \\(./4\\)!")
    private val joinPattern8v8 = Regex(".* has joined \\(./8\\)!")
    
    private var scanTimer: Timer? = null
    private var scanCount = 0
    private val alreadyDetected = mutableSetOf<String>()
    private var isRecovering = false // Prevent infinite recovery loops
    private var isSlowMode = false // Temporarily slow down after spam
    private var slowModeTimer: Timer? = null
    private val allTimers = mutableListOf<Timer>()

    fun resetDetection() {
        scanTimer?.cancel()
        scanTimer = null
        scanCount = 0
        alreadyDetected.clear()
    }
    
    fun isInSlowMode(): Boolean = isSlowMode
    
    fun cancelAllPendingActions() {
        // Cancel all timers immediately
        scanTimer?.cancel()
        scanTimer = null
        slowModeTimer?.cancel()
        slowModeTimer = null
        
        // Cancel all tracked timers
        allTimers.forEach { it.cancel() }
        allTimers.clear()
        
        // Reset recovery flag
        isRecovering = false
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        val unformatted = event.message.unformattedText
        
        // Check for spam/rate limit messages (only if NOT from a player chat - no ":")
        if (!unformatted.contains(":") && 
            (unformatted.contains("Woah there, slow down!") || 
             unformatted.contains("You are sending commands too fast! Please slow down."))) {
            if (KeyInputListener.isBotEnabled() && !isRecovering) {
                handleSpamDetection()
            }
            return
        }
        
        // Trigger when bot is enabled OR test mode is enabled, and in GAME state
        if ((KeyInputListener.isBotEnabled() || KeyInputListener.isTestModeEnabled()) && StateManager.state == StateManager.States.GAME) {
            // Check if message matches any join pattern
            if (joinPattern2v2.matches(unformatted) || joinPattern4v4.matches(unformatted) || joinPattern8v8.matches(unformatted)) {
                startScanning()
            }
        }
    }

    private fun startScanning() {
        // Cancel any existing scan timer
        scanTimer?.cancel()
        
        // Clear detection list for fresh scan
        alreadyDetected.clear()
        scanCount = 0
        
        sendMessage("${EnumChatFormatting.GREEN}Starting player detection (Game Lobby)...")
        
        // Create new timer for repeated scanning
        scanTimer = Timer()
        allTimers.add(scanTimer!!)
        
        // Scan immediately
        performScan()
        
        // Then scan every 500ms for 10 seconds (20 times)
        for (i in 1..20) {
            scanTimer?.schedule(i * 500L) {
                performScan()
            }
        }
    }

    private fun performScan() {
        if (mc.thePlayer == null) {
            return
        }
        
        scanCount++
        
        // Scan for all valid players
        val players = EntityScanner.scanForPlayers()
        
        // Find new players we haven't detected yet
        val newPlayers = players.filter { it.displayNameString !in alreadyDetected }
        
        if (newPlayers.isNotEmpty()) {
            // Log information for each newly detected player
            for (player in newPlayers) {
                val name = player.displayNameString
                val coords = EntityScanner.getCoordinateString(player)
                
                sendMessage("${EnumChatFormatting.AQUA}Player: ${EnumChatFormatting.WHITE}$name")
                sendMessage("${EnumChatFormatting.GOLD}Position: ${EnumChatFormatting.WHITE}$coords")
                sendMessage("${EnumChatFormatting.GRAY}Classifying in 300ms... ${EnumChatFormatting.DARK_GRAY}(checking Y-position & movement)")
                
                // Wait 300ms before checking them (give server time to update position)
                val classifyTimer = Timer()
                allTimers.add(classifyTimer)
                classifyTimer.schedule(300L) {
                    // Add player to render list after delay
                    RenderListener.addTrackedPlayer(player)
                }
                
                // Mark as detected
                alreadyDetected.add(name)
            }
        }
    }

    private fun sendMessage(message: String) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(ChatComponentText("${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] ${EnumChatFormatting.RESET}$message"))
        }
    }
    
    private fun handleSpamDetection() {
        if (mc.thePlayer == null || isRecovering) return
        
        // Set recovery flag to prevent infinite loops
        isRecovering = true
        
        // Enable slow mode for 3 minutes
        enableSlowMode()
        
        // Notify user
        mc.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.RED}${EnumChatFormatting.BOLD}SPAM DETECTED! ${EnumChatFormatting.GRAY}Initiating recovery sequence..."
            )
        )
        mc.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.YELLOW}Slow mode enabled for 3 minutes..."
            )
        )
        
        // Step 1: Try to leave game lobby repeatedly
        tryLeaveGameLobby()
    }
    
    private fun tryLeaveGameLobby() {
        // Check state every 2 seconds, and if in GAME lobby, send /l
        var attempts = 0
        val checkTimer = Timer()
        allTimers.add(checkTimer)
        
        val checkTask = object : TimerTask() {
            override fun run() {
                attempts++
                
                // If we're in lobby, proceed with warps
                if (StateManager.state == StateManager.States.LOBBY) {
                    checkTimer.cancel()
                    if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.GREEN}Recovery: ${EnumChatFormatting.GRAY}Successfully left game lobby!"
                            )
                        )
                    }
                    proceedWithPartyWarps()
                }
                // If in GAME lobby, send /l
                else if (StateManager.state == StateManager.States.GAME) {
                    if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                        mc.thePlayer.sendChatMessage("/l")
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.YELLOW}Recovery: ${EnumChatFormatting.WHITE}/l ${EnumChatFormatting.GRAY}(attempt ${attempts})"
                            )
                        )
                    }
                }
                // If max attempts reached (30 seconds), force proceed
                else if (attempts >= 15) {
                    checkTimer.cancel()
                    if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.YELLOW}Recovery: ${EnumChatFormatting.GRAY}Timeout - proceeding anyway..."
                            )
                        )
                    }
                    proceedWithPartyWarps()
                }
            }
        }
        
        // Check immediately, then every 2 seconds
        checkTimer.schedule(checkTask, 0L, 2000L)
    }
    
    private fun proceedWithPartyWarps() {
        if (mc.thePlayer == null || !KeyInputListener.isBotEnabled()) {
            isRecovering = false
            return
        }
        
        // Check if party warp is disabled - skip directly to command execution
        if (!Config.partyWarpEnabled) {
            mc.thePlayer.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.YELLOW}Party Warp disabled, skipping to command execution..."
                )
            )
            
            // Wait 30 seconds, then run user command and reset flag
            val commandTimer = Timer()
            allTimers.add(commandTimer)
            commandTimer.schedule(30000L) {
                if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                    val command = Config.autoCommand
                    if (command.isNotEmpty()) {
                        mc.thePlayer.sendChatMessage(command)
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.GREEN}Recovery complete! ${EnumChatFormatting.GRAY}Resuming automation..."
                            )
                        )
                    }
                }
                // Reset recovery flag after everything completes
                isRecovering = false
            }
            return
        }
        
        mc.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.GREEN}In lobby! ${EnumChatFormatting.GRAY}Running party warps..."
            )
        )
        
        // Step 3: Wait 3 seconds, then /p warp
        val warpTimer1 = Timer()
        allTimers.add(warpTimer1)
        warpTimer1.schedule(3000L) {
            if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                mc.thePlayer.sendChatMessage("/p warp")
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.YELLOW}Recovery: ${EnumChatFormatting.WHITE}/p warp (1/2)"
                    )
                )
                
                // Step 4: Wait 1 second, then /p warp
                val warpTimer2 = Timer()
                allTimers.add(warpTimer2)
                warpTimer2.schedule(1000L) {
                    if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                        mc.thePlayer.sendChatMessage("/p warp")
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.YELLOW}Recovery: ${EnumChatFormatting.WHITE}/p warp (2/2)"
                            )
                        )
                        
                        // Step 5: Wait 30 seconds, then run user command and reset flag
                        val finalTimer = Timer()
                        allTimers.add(finalTimer)
                        finalTimer.schedule(30000L) {
                            if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                                val command = Config.autoCommand
                                if (command.isNotEmpty()) {
                                    mc.thePlayer.sendChatMessage(command)
                                    mc.thePlayer.addChatMessage(
                                        ChatComponentText(
                                            "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                            "${EnumChatFormatting.GREEN}Recovery complete! ${EnumChatFormatting.GRAY}Resuming automation..."
                                        )
                                    )
                                }
                            }
                            // Reset recovery flag after everything completes
                            isRecovering = false
                        }
                    }
                }
            }
        }
    }
    
    private fun enableSlowMode() {
        isSlowMode = true
        
        // Cancel any existing slow mode timer
        slowModeTimer?.cancel()
        
        // Reset to normal speed after 3 minutes (180 seconds)
        slowModeTimer = Timer()
        allTimers.add(slowModeTimer!!)
        slowModeTimer?.schedule(180000L) {
            isSlowMode = false
            if (mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.GREEN}Slow mode disabled. ${EnumChatFormatting.GRAY}Returning to normal speed..."
                    )
                )
            }
        }
    }
}

