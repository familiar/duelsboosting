package best.spaghetcodes.riceutils.managers

import best.spaghetcodes.riceutils.RiceUtils
import best.spaghetcodes.riceutils.core.Config
import best.spaghetcodes.riceutils.listeners.ChatListener
import best.spaghetcodes.riceutils.listeners.KeyInputListener
import best.spaghetcodes.riceutils.listeners.RenderListener
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*
import kotlin.concurrent.schedule

object StateManager {
    
    private var lastOpponent: String = "Unknown"
    private var hasRequeuedThisGame = false
    private val allTimers = mutableListOf<Timer>()
    private var isRecoveryInProgress = false

    enum class States {
        LOBBY,      // Public lobby
        GAME,       // In game lobby (waiting for game to start)
        PLAYING     // Game is active
    }

    var state = States.LOBBY
        private set(value) {
            if (field != value) {
                field = value
                onStateChange(value)
            }
        }
    var gameFull = false

    private fun onStateChange(newState: States) {
        val message = when (newState) {
            States.LOBBY -> {
                // Reset all visuals and detection data for fresh game
                RenderListener.clearTrackedPlayers()
                ChatListener.resetDetection()
                stopAllMovement() // Stop all movement keys when returning to lobby
                hasRequeuedThisGame = false
                isRecoveryInProgress = false // Reset recovery flag
                "${EnumChatFormatting.YELLOW}State: Public Lobby ${EnumChatFormatting.GRAY}(Detection inactive)"
            }
            States.GAME -> {
                // Start Y position capture when entering game lobby
                RenderListener.onGameLobbyEntered()
                stopAllMovement() // Stop all movement keys when entering new game lobby
                "${EnumChatFormatting.GREEN}State: Game Lobby ${EnumChatFormatting.GRAY}(Detection active)"
            }
            States.PLAYING -> {
                // Game started - disable detection and cancel any pending actions
                RenderListener.cancelPendingActions()
                ChatListener.cancelAllPendingActions()
                cancelAllPendingActions()
                "${EnumChatFormatting.AQUA}${EnumChatFormatting.BOLD}Game Started! ${EnumChatFormatting.GRAY}Detection & dodging disabled."
            }
        }
        sendMessage(message)
    }
    
    private fun cancelAllPendingActions() {
        // Cancel all timers immediately
        allTimers.forEach { it.cancel() }
        allTimers.clear()
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onChat(ev: ClientChatReceivedEvent) {
        val unformatted = ev.message.unformattedText
        
        // Track opponent name when game starts (using DuckDueller's approach)
        if ((unformatted.contains("Opponent:") || unformatted.contains("Opponents:")) && 
            !unformatted.contains(": Opponent") && !unformatted.contains(": Opponents")) {
            val opponentText = if (unformatted.contains("Opponent:")) {
                unformatted.substringAfter("Opponent:")
            } else {
                unformatted.substringAfter("Opponents:")
            }
            lastOpponent = opponentText.trim().split(",").firstOrNull()?.trim() ?: "Unknown"
        }
        
        // Detect win
        if (unformatted.contains("won the duel!") && RiceUtils.mc.thePlayer != null) {
            if (unformatted.contains(RiceUtils.mc.thePlayer.name)) {
                StatsManager.recordWin(lastOpponent)
            } else {
                StatsManager.recordLoss(lastOpponent)
            }
        }
        
        // Detect loss/game end with stats
        if (unformatted.contains("Accuracy:") || unformatted.contains("Health Regenerated:")) {
            // If we haven't recorded this game yet, it might be a loss
            // This is a backup check
        }
        
        // EMERGENCY FAILSAFE: Check for "The game starts in 2 seconds!" (DISABLED)
        // if (unformatted.contains("The game starts in 2 seconds!") && !unformatted.contains(":")) {
        //     handleGameStartingFailsafe()
        // }
        
        // FAILSAFE: Check for server connection error in lobby
        if (unformatted.contains("Something went wrong trying to send you to that server!") && !unformatted.contains(":")) {
            handleServerConnectionError()
        }
        
        // FAILSAFE: Check for AFK detection (similar to Opponent: detection)
        if (unformatted.contains("You are AFK") && !unformatted.contains(": You are AFK")) {
            handleAFKDetection()
        }
        
        // FAILSAFE: Check for rate limit detection
        if (unformatted.contains("Please don't spam the command!") && !unformatted.contains(":")) {
            handleRateLimitDetection()
        }
        
        // Check for game start first (highest priority) - using DuckDueller's approach
        if ((unformatted.contains("Opponent:") || unformatted.contains("Opponents:")) && 
            !unformatted.contains(": Opponent") && !unformatted.contains(": Opponents")) {
            // Game has started (only if not from player chat)
            state = States.PLAYING
            
            // Start moving forward when game starts
            if (KeyInputListener.isBotEnabled() && !KeyInputListener.isTestModeEnabled()) {
                startMovingForward()
            }
        }
        // Detect game lobby join messages
        else if (unformatted.matches(Regex(".* has joined \\(./2\\)!"))) {
            state = States.GAME
            if (unformatted.matches(Regex(".* has joined \\(2/2\\)!"))) {
                gameFull = true
            }
        } else if (unformatted.matches(Regex(".* has joined \\(./4\\)!"))) {
            state = States.GAME
            if (unformatted.matches(Regex(".* has joined \\(4/4\\)!"))) {
                gameFull = true
            }
        } else if (unformatted.matches(Regex(".* has joined \\(./8\\)!"))) {
            state = States.GAME
            if (unformatted.matches(Regex(".* has joined \\(8/8\\)!"))) {
                gameFull = true
            }
        }
        // Detect game end
        else if ((unformatted.contains("Accuracy") || unformatted.contains("won the duel!")) && !unformatted.contains(": Accuracy")) {
            // Game ended, back to game lobby (filter out player chat with ": Accuracy")
            state = States.GAME
            gameFull = false
            
            // Auto-requeue if bot is enabled
            if (KeyInputListener.isBotEnabled() && !hasRequeuedThisGame) {
                hasRequeuedThisGame = true
                handleRequeue()
            }
        } else if (unformatted.contains("has quit!")) {
            gameFull = false
        }
    }

    @SubscribeEvent
    fun onJoinWorld(ev: EntityJoinWorldEvent) {
        if (RiceUtils.mc.thePlayer != null && ev.entity == RiceUtils.mc.thePlayer) {
            // Player joined a new world (teleported to lobby/server)
            state = States.LOBBY
            gameFull = false
        }
    }

    private fun sendMessage(message: String) {
        if (RiceUtils.mc.thePlayer != null) {
            RiceUtils.mc.thePlayer.addChatMessage(
                ChatComponentText("${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] ${EnumChatFormatting.RESET}$message")
            )
        }
    }
    
    private fun handleRequeue() {
        if (RiceUtils.mc.thePlayer == null || !KeyInputListener.isBotEnabled()) return
        
        val command = Config.autoCommand
        if (command.isEmpty()) {
            RiceUtils.mc.thePlayer.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.YELLOW}Game ended! ${EnumChatFormatting.GRAY}No auto-command configured."
                )
            )
            return
        }
        
        // Choose delay based on slow mode
        val delay = if (ChatListener.isInSlowMode()) Config.slowModeRequeueDelay else Config.normalRequeueDelay
        
        RiceUtils.mc.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.GREEN}Game ended! ${EnumChatFormatting.GRAY}Requeueing in ${delay/1000}s..."
            )
        )
        
        val requeueTimer = Timer()
        allTimers.add(requeueTimer)
        requeueTimer.schedule(delay) {
            if (RiceUtils.mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                RiceUtils.mc.thePlayer.sendChatMessage(command)
                RiceUtils.mc.thePlayer.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.YELLOW}Requeue: ${EnumChatFormatting.WHITE}$command"
                    )
                )
            }
        }
    }
    
    private fun handleGameStartingFailsafe() {
        // Only run if bot is enabled, not in test mode, and no recovery in progress
        if (!KeyInputListener.isBotEnabled() || KeyInputListener.isTestModeEnabled() || isRecoveryInProgress) {
            return
        }
        
        // HIGHEST PRIORITY: Check if there are any real players (NOT_BOT classification)
        if (RenderListener.hasNotBotPlayers()) {
            isRecoveryInProgress = true
            
            RiceUtils.mc.thePlayer?.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.RED}${EnumChatFormatting.BOLD}EMERGENCY FAILSAFE! ${EnumChatFormatting.GRAY}Real player detected!"
                )
            )
            RiceUtils.mc.thePlayer?.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.YELLOW}Disconnecting in 3...2...1..."
                )
            )
            
            // Disconnect from server
            val disconnectTimer = Timer()
            allTimers.add(disconnectTimer)
            disconnectTimer.schedule(100L) {
                if (RiceUtils.mc.theWorld != null) {
                    RiceUtils.mc.addScheduledTask {
                        RiceUtils.mc.theWorld.sendQuittingDisconnectingPacket()
                        RiceUtils.mc.loadWorld(null)
                        RiceUtils.mc.displayGuiScreen(net.minecraft.client.gui.GuiMultiplayer(net.minecraft.client.gui.GuiMainMenu()))
                    }
                    
                    // Wait 10 seconds, then reconnect
                    val reconnectTimer = Timer()
                    allTimers.add(reconnectTimer)
                    reconnectTimer.schedule(10000L) {
                        reconnectToServer()
                    }
                }
            }
        }
        // LOWER PRIORITY: Check if there are no bots at all
        else if (!RenderListener.hasBotPlayers()) {
            isRecoveryInProgress = true
            
            RiceUtils.mc.thePlayer?.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.YELLOW}${EnumChatFormatting.BOLD}WARNING! ${EnumChatFormatting.GRAY}No bots detected in lobby!"
                )
            )
            RiceUtils.mc.thePlayer?.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.YELLOW}Leaving game lobby and requeueing..."
                )
            )
            
            // Leave game lobby
            val leaveTimer = Timer()
            allTimers.add(leaveTimer)
            leaveTimer.schedule(100L) {
                if (RiceUtils.mc.thePlayer != null) {
                    RiceUtils.mc.thePlayer.sendChatMessage("/l")
                    
                    // Wait for LOBBY state, then requeue
                    waitForLobbyThenRequeue()
                }
            }
        }
        else {
            // No real players detected, safe to play
            RiceUtils.mc.thePlayer?.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.GREEN}Failsafe check: ${EnumChatFormatting.GRAY}Only bots detected, safe to play!"
                )
            )
        }
    }
    
    private fun handleServerConnectionError() {
        // Only run if bot is enabled, we're in the lobby, and no recovery in progress
        if (!KeyInputListener.isBotEnabled() || state != States.LOBBY || isRecoveryInProgress) {
            return
        }
        
        isRecoveryInProgress = true
        
        RiceUtils.mc.thePlayer?.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.YELLOW}${EnumChatFormatting.BOLD}SERVER ERROR! ${EnumChatFormatting.GRAY}Requeueing..."
            )
        )
        
        // Wait 2 seconds, then requeue
        val errorTimer = Timer()
        allTimers.add(errorTimer)
        errorTimer.schedule(2000L) {
            if (RiceUtils.mc.thePlayer != null && KeyInputListener.isBotEnabled() && Config.autoCommand.isNotEmpty()) {
                RiceUtils.mc.thePlayer.sendChatMessage(Config.autoCommand)
                
                RiceUtils.mc.thePlayer?.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.GREEN}Requeueing: ${EnumChatFormatting.GRAY}${Config.autoCommand}"
                    )
                )
            }
        }
    }
    
    private fun handleAFKDetection() {
        // Only run if bot is enabled and no recovery in progress
        if (!KeyInputListener.isBotEnabled() || isRecoveryInProgress) {
            return
        }
        
        isRecoveryInProgress = true
        
        RiceUtils.mc.thePlayer?.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.YELLOW}${EnumChatFormatting.BOLD}AFK DETECTED! ${EnumChatFormatting.GRAY}Recovering..."
            )
        )
        
        // Wait 5 seconds, then /l
        val afkTimer1 = Timer()
        allTimers.add(afkTimer1)
        afkTimer1.schedule(5000L) {
            if (RiceUtils.mc.thePlayer != null && KeyInputListener.isBotEnabled()) {
                RiceUtils.mc.thePlayer.sendChatMessage("/l")
                
                RiceUtils.mc.thePlayer?.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.YELLOW}Leaving game lobby..."
                    )
                )
                
                // Wait another 5 seconds, then requeue
                val afkTimer2 = Timer()
                allTimers.add(afkTimer2)
                afkTimer2.schedule(5000L) {
                    if (RiceUtils.mc.thePlayer != null && KeyInputListener.isBotEnabled() && Config.autoCommand.isNotEmpty()) {
                        RiceUtils.mc.thePlayer.sendChatMessage(Config.autoCommand)
                        
                        RiceUtils.mc.thePlayer?.addChatMessage(
                            ChatComponentText(
                                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                "${EnumChatFormatting.GREEN}Requeueing: ${EnumChatFormatting.GRAY}${Config.autoCommand}"
                            )
                        )
                    }
                }
            }
        }
    }
    
    private fun handleRateLimitDetection() {
        // Only run if bot is enabled and no recovery in progress
        if (!KeyInputListener.isBotEnabled() || isRecoveryInProgress) {
            return
        }
        
        isRecoveryInProgress = true
        
        RiceUtils.mc.thePlayer?.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.YELLOW}${EnumChatFormatting.BOLD}RATE LIMITED! ${EnumChatFormatting.GRAY}Waiting before requeueing..."
            )
        )
        
        // Wait 3 seconds, then requeue
        val rateLimitTimer = Timer()
        allTimers.add(rateLimitTimer)
        rateLimitTimer.schedule(3000L) {
            if (RiceUtils.mc.thePlayer != null && KeyInputListener.isBotEnabled() && Config.autoCommand.isNotEmpty()) {
                RiceUtils.mc.thePlayer.sendChatMessage(Config.autoCommand)
                
                RiceUtils.mc.thePlayer?.addChatMessage(
                    ChatComponentText(
                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                        "${EnumChatFormatting.GREEN}Requeueing: ${EnumChatFormatting.GRAY}${Config.autoCommand}"
                    )
                )
            }
        }
    }
    
    fun reconnectToServer() {
        if (RiceUtils.mc.theWorld == null) {
            if (RiceUtils.mc.currentScreen is net.minecraft.client.gui.GuiMultiplayer) {
                RiceUtils.mc.addScheduledTask {
                    println("[RiceUtils] Reconnecting to Hypixel...")
                    net.minecraftforge.fml.client.FMLClientHandler.instance().setupServerList()
                    net.minecraftforge.fml.client.FMLClientHandler.instance().connectToServer(
                        RiceUtils.mc.currentScreen,
                        net.minecraft.client.multiplayer.ServerData("hypixel", "mc.hypixel.net", false)
                    )
                }
                
                // Once reconnected, wait then resume automation
                val resumeTimer = Timer()
                allTimers.add(resumeTimer)
                resumeTimer.schedule(5000L) {
                    resumeAutomationAfterReconnect()
                }
            } else {
                // Not on multiplayer screen yet, show it first
                if (RiceUtils.mc.theWorld == null && RiceUtils.mc.currentScreen !is net.minecraft.client.multiplayer.GuiConnecting) {
                    RiceUtils.mc.addScheduledTask {
                        println("[RiceUtils] Showing multiplayer screen...")
                        RiceUtils.mc.displayGuiScreen(net.minecraft.client.gui.GuiMultiplayer(net.minecraft.client.gui.GuiMainMenu()))
                        // Try again after showing the screen
                        val retryTimer = Timer()
                        allTimers.add(retryTimer)
                        retryTimer.schedule(1000L) {
                            reconnectToServer()
                        }
                    }
                }
            }
        }
    }
    
    private fun resumeAutomationAfterReconnect() {
        // Wait for the player to be in the world
        var attempts = 0
        val checkTimer = Timer()
        allTimers.add(checkTimer)
        
        val checkTask = object : java.util.TimerTask() {
            override fun run() {
                attempts++
                
                if (RiceUtils.mc.thePlayer != null && RiceUtils.mc.theWorld != null) {
                    checkTimer.cancel()
                    
                    RiceUtils.mc.thePlayer?.addChatMessage(
                        ChatComponentText(
                            "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                            "${EnumChatFormatting.GREEN}Reconnected! ${EnumChatFormatting.GRAY}Resuming automation..."
                        )
                    )
                    
                    // Run party warp sequence if enabled
                    if (Config.partyWarpEnabled) {
                        val warpTimer1 = Timer()
                        allTimers.add(warpTimer1)
                        warpTimer1.schedule(2000L) {
                            if (RiceUtils.mc.thePlayer != null) {
                                RiceUtils.mc.thePlayer.sendChatMessage("/p warp")
                                
                                val warpTimer2 = Timer()
                                allTimers.add(warpTimer2)
                                warpTimer2.schedule(1000L) {
                                    if (RiceUtils.mc.thePlayer != null) {
                                        RiceUtils.mc.thePlayer.sendChatMessage("/p warp")
                                        
                                        // Then requeue
                                        val queueTimer = Timer()
                                        allTimers.add(queueTimer)
                                        queueTimer.schedule(2000L) {
                                            if (RiceUtils.mc.thePlayer != null && Config.autoCommand.isNotEmpty()) {
                                                RiceUtils.mc.thePlayer.sendChatMessage(Config.autoCommand)
                                                
                                                RiceUtils.mc.thePlayer?.addChatMessage(
                                                    ChatComponentText(
                                                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                                        "${EnumChatFormatting.GREEN}Queuing: ${EnumChatFormatting.GRAY}${Config.autoCommand}"
                                                    )
                                                )
                                                
                                                // Resume the 8.5 second command loop
                                                best.spaghetcodes.riceutils.listeners.KeyInputListener.resumeCommandLoop()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Skip party warp, just requeue
                        val directQueueTimer = Timer()
                        allTimers.add(directQueueTimer)
                        directQueueTimer.schedule(2000L) {
                            if (RiceUtils.mc.thePlayer != null && Config.autoCommand.isNotEmpty()) {
                                RiceUtils.mc.thePlayer.sendChatMessage(Config.autoCommand)
                                
                                RiceUtils.mc.thePlayer?.addChatMessage(
                                    ChatComponentText(
                                        "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                        "${EnumChatFormatting.GREEN}Queuing: ${EnumChatFormatting.GRAY}${Config.autoCommand}"
                                    )
                                )
                                
                                // Resume the 8.5 second command loop
                                best.spaghetcodes.riceutils.listeners.KeyInputListener.resumeCommandLoop()
                            }
                        }
                    }
                } else if (attempts >= 40) { // 20 seconds timeout
                    checkTimer.cancel()
                    println("[RiceUtils] Timeout waiting for player after reconnect")
                }
            }
        }
        
        checkTimer.schedule(checkTask, 500L, 500L)
    }
    
    private fun waitForLobbyThenRequeue() {
        // Wait for the player to reach LOBBY state
        var attempts = 0
        val checkTimer = Timer()
        allTimers.add(checkTimer)
        
        println("[RiceUtils] waitForLobbyThenRequeue: Starting wait for lobby state")
        
        val checkTask = object : java.util.TimerTask() {
            override fun run() {
                attempts++
                println("[RiceUtils] waitForLobbyThenRequeue: Attempt $attempts, Current state: $state")
                
                if (state == States.LOBBY) {
                    checkTimer.cancel()
                    println("[RiceUtils] waitForLobbyThenRequeue: LOBBY state reached! Scheduling requeue...")
                    
                    RiceUtils.mc.thePlayer?.addChatMessage(
                        ChatComponentText(
                            "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                            "${EnumChatFormatting.GREEN}Back in lobby! ${EnumChatFormatting.GRAY}Requeueing in ${Config.lobbyRequeueDelay/1000} seconds..."
                        )
                    )
                    
                    // Now requeue
                    val requeueDelayTimer = Timer()
                    allTimers.add(requeueDelayTimer)
                    requeueDelayTimer.schedule(Config.lobbyRequeueDelay) {
                        if (RiceUtils.mc.thePlayer != null && Config.autoCommand.isNotEmpty()) {
                            println("[RiceUtils] waitForLobbyThenRequeue: Sending requeue command: ${Config.autoCommand}")
                            RiceUtils.mc.thePlayer.sendChatMessage(Config.autoCommand)
                            
                            RiceUtils.mc.thePlayer?.addChatMessage(
                                ChatComponentText(
                                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                                    "${EnumChatFormatting.GREEN}Requeueing: ${EnumChatFormatting.GRAY}${Config.autoCommand}"
                                )
                            )
                        } else {
                            println("[RiceUtils] waitForLobbyThenRequeue: Cannot requeue - Player null: ${RiceUtils.mc.thePlayer == null}, AutoCommand empty: ${Config.autoCommand.isEmpty()}")
                        }
                    }
                } else if (attempts >= 40) { // 20 seconds timeout
                    checkTimer.cancel()
                    println("[RiceUtils] waitForLobbyThenRequeue: TIMEOUT after $attempts attempts")
                    RiceUtils.mc.thePlayer?.addChatMessage(
                        ChatComponentText(
                            "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                            "${EnumChatFormatting.RED}Timeout: ${EnumChatFormatting.GRAY}Could not return to lobby"
                        )
                    )
                }
            }
        }
        
        checkTimer.schedule(checkTask, 500L, 500L)
    }
    
    private fun startMovingForward() {
        // Press W key to move forward (DuckDueller style)
        if (RiceUtils.mc.thePlayer != null && RiceUtils.mc.theWorld != null) {
            RiceUtils.mc.addScheduledTask {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindForward.keyCode, 
                    true
                )
            }
            
            RiceUtils.mc.thePlayer?.addChatMessage(
                ChatComponentText(
                    "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                    "${EnumChatFormatting.AQUA}Game started! ${EnumChatFormatting.GRAY}Moving forward..."
                )
            )
        }
    }
    
    private fun stopMovingForward() {
        // Release W key to stop moving forward
        if (RiceUtils.mc.thePlayer != null) {
            RiceUtils.mc.addScheduledTask {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindForward.keyCode, 
                    false
                )
            }
        }
    }
    
    private fun stopAllMovement() {
        // Release all movement keys (W, A, S, D, Space, Shift)
        if (RiceUtils.mc.thePlayer != null) {
            RiceUtils.mc.addScheduledTask {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindForward.keyCode, 
                    false
                )
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindBack.keyCode, 
                    false
                )
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindLeft.keyCode, 
                    false
                )
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindRight.keyCode, 
                    false
                )
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindJump.keyCode, 
                    false
                )
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    RiceUtils.mc.gameSettings.keyBindSneak.keyCode, 
                    false
                )
            }
        }
    }
}

