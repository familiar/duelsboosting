package best.spaghetcodes.riceutils.listeners

import best.spaghetcodes.riceutils.core.Config
import best.spaghetcodes.riceutils.managers.StateManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderGlobal
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.WorldRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.AxisAlignedBB
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11
import java.awt.Color

object RenderListener {
    
    enum class CommandTrigger {
        BOT_DETECTED,
        AFTER_DODGE
    }

    private val mc = Minecraft.getMinecraft()
    
    // Player tracking data class
    private data class PlayerData(
        val player: EntityPlayer,
        var classification: PlayerClassification = PlayerClassification.UNKNOWN,
        val initialPosX: Double,
        val initialPosY: Double,
        val initialPosZ: Double,
        val initialYaw: Float,
        val initialPitch: Float,
        val initialRotationYawHead: Float,
        val initialRenderYawOffset: Float
    )
    
    enum class PlayerClassification {
        UNKNOWN,    // Not yet classified
        BOT,        // Confirmed bot (floating, not moving)
        NOT_BOT     // Active player (movement detected)
    }
    
    private val trackedPlayers = mutableMapOf<String, PlayerData>()
    
    // Colors for classification
    private val botColor = Color(0, 255, 0)        // Green - Bot
    private val notBotColor = Color(255, 0, 0)     // Red - Not A Bot
    
    // Y-position tracking
    private var referenceYPosition: Double? = null
    private var ticksInGameLobby = 0
    private var shouldCaptureY = false
    
    // Dodge tracking
    private var hasDodged = false
    
    // Disconnect tracking
    private var hasDisconnected = false
    
    // Command execution tracking
    private var hasExecutedCommand = false
    private var commandTimer: java.util.Timer? = null

    fun addTrackedPlayer(player: EntityPlayer) {
        val name = player.displayNameString
        
        // Atomic check-and-add to prevent race conditions
        val shouldAddPlayer = synchronized(trackedPlayers) {
            if (!trackedPlayers.containsKey(name)) {
                val data = PlayerData(
                    player = player,
                    classification = PlayerClassification.UNKNOWN,
                    initialPosX = player.posX,
                    initialPosY = player.posY,
                    initialPosZ = player.posZ,
                    initialYaw = player.rotationYaw,
                    initialPitch = player.rotationPitch,
                    initialRotationYawHead = player.rotationYawHead,
                    initialRenderYawOffset = player.renderYawOffset
                )
                trackedPlayers[name] = data
                true // Return true to indicate we added the player
            } else {
                false // Player already tracked
            }
        }
        
        // Schedule bot check outside synchronized block to avoid holding the lock too long
        if (shouldAddPlayer) {
            // Wait 300ms before checking if this is a bot (leniency when joining)
            java.util.Timer().schedule(object : java.util.TimerTask() {
                override fun run() {
                    synchronized(trackedPlayers) {
                        val data = trackedPlayers[name]
                        if (data != null) {
                            checkIfBot(data)
                        }
                    }
                }
            }, 300L)
        }
    }

    fun removeTrackedPlayer(name: String) {
        synchronized(trackedPlayers) {
            trackedPlayers.remove(name)
        }
    }

    fun clearTrackedPlayers() {
        synchronized(trackedPlayers) {
            trackedPlayers.clear()
        }
        referenceYPosition = null
        ticksInGameLobby = 0
        shouldCaptureY = false
        hasDodged = false
        hasDisconnected = false
        hasExecutedCommand = false
        commandTimer?.cancel()
        commandTimer = null
    }
    
    fun hasNotBotPlayers(): Boolean {
        synchronized(trackedPlayers) {
            return trackedPlayers.values.any { it.classification == PlayerClassification.NOT_BOT }
        }
    }
    
    fun hasBotPlayers(): Boolean {
        synchronized(trackedPlayers) {
            return trackedPlayers.values.any { it.classification == PlayerClassification.BOT }
        }
    }
    
    // Check if we have at least the minimum number of bots required
    private fun hasMinimumBots(minBots: Int = 2): Boolean {
        synchronized(trackedPlayers) {
            val botCount = trackedPlayers.values.count { it.classification == PlayerClassification.BOT }
            return botCount >= minBots
        }
    }
    
    fun onGameLobbyEntered() {
        shouldCaptureY = true
        ticksInGameLobby = 0
        referenceYPosition = null
        hasDodged = false
        hasExecutedCommand = false
        commandTimer?.cancel()
        commandTimer = null
    }
    
    fun cancelPendingActions() {
        // Cancel any pending command execution timers
        commandTimer?.cancel()
        commandTimer = null
        hasExecutedCommand = true // Prevent any new commands from being scheduled
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (mc.theWorld == null) {
            clearTrackedPlayers()
            return
        }
        
        // Only run if bot is enabled or test mode is enabled
        if (!KeyInputListener.isBotEnabled() && !KeyInputListener.isTestModeEnabled()) {
            return
        }
        
        // Only track players in GAME state (game lobby), not during PLAYING
        if (StateManager.state != StateManager.States.GAME) {
            return
        }
        
        // Capture Y position after 20 ticks in game lobby
        if (shouldCaptureY) {
            ticksInGameLobby++
            if (ticksInGameLobby >= 20 && mc.thePlayer != null) {
                referenceYPosition = mc.thePlayer.posY
                shouldCaptureY = false
                
                // Notify in chat
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(
                        net.minecraft.util.ChatComponentText(
                            "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                            "${net.minecraft.util.EnumChatFormatting.YELLOW}Reference Y-Position: ${net.minecraft.util.EnumChatFormatting.WHITE}${String.format("%.2f", referenceYPosition)} " +
                            "${net.minecraft.util.EnumChatFormatting.GRAY}(Bot detection active)"
                        )
                    )
                }
            }
        }
        
        // Check for movement and update bot classification
        // Create a snapshot to avoid ConcurrentModificationException
        val playersToRemove = mutableListOf<String>()
        val playersSnapshot = synchronized(trackedPlayers) {
            trackedPlayers.toMap() // Create a thread-safe copy
        }
        
        for ((name, data) in playersSnapshot) {
            val player = data.player
            
            // Mark for removal if no longer valid
            if (!player.isEntityAlive || mc.thePlayer.getDistanceToEntity(player) > 80.0f) {
                playersToRemove.add(name)
                continue
            }
            
            // Check for movement
            if (data.classification == PlayerClassification.BOT || data.classification == PlayerClassification.UNKNOWN) {
                val hasMoved = hasPlayerMoved(data)
                if (hasMoved) {
                    if (data.classification != PlayerClassification.NOT_BOT) {
                        data.classification = PlayerClassification.NOT_BOT
                        notifyClassificationChange(name, false, null, null)
                        // Only dodge if not in test mode AND we don't have at least 2 bots
                        if (KeyInputListener.isBotEnabled() && !KeyInputListener.isTestModeEnabled()) {
                            // Only dodge if we have fewer than 2 bots
                            if (!hasMinimumBots(2)) {
                                executeDodge(name)
                            }
                        }
                    }
                } else if (data.classification == PlayerClassification.UNKNOWN) {
                    // Recheck if it's a bot
                    checkIfBot(data)
                }
            }
        }
        
        // Remove invalid players after iteration
        if (playersToRemove.isNotEmpty()) {
            synchronized(trackedPlayers) {
                playersToRemove.forEach { trackedPlayers.remove(it) }
            }
        }
    }
    
    private fun hasPlayerMoved(data: PlayerData): Boolean {
        val player = data.player
        val threshold = 0.001 // Very small threshold to detect any movement
        
        // Check position changes
        if (Math.abs(player.posX - data.initialPosX) > threshold) return true
        if (Math.abs(player.posY - data.initialPosY) > threshold) return true
        if (Math.abs(player.posZ - data.initialPosZ) > threshold) return true
        
        // Check rotation changes (yaw/pitch)
        if (Math.abs(player.rotationYaw - data.initialYaw) > threshold) return true
        if (Math.abs(player.rotationPitch - data.initialPitch) > threshold) return true
        
        // Check head rotation
        if (Math.abs(player.rotationYawHead - data.initialRotationYawHead) > threshold) return true
        
        // Check body rotation
        if (Math.abs(player.renderYawOffset - data.initialRenderYawOffset) > threshold) return true
        
        // Check if player is swinging (hit animation)
        if (player.isSwingInProgress) return true
        
        // Check if player took damage (hurtTime > 0)
        if (player.hurtTime > 0) return true
        
        // Check velocity (any movement)
        if (Math.abs(player.motionX) > threshold) return true
        if (Math.abs(player.motionY) > threshold) return true
        if (Math.abs(player.motionZ) > threshold) return true
        
        return false
    }
    
    private fun checkIfBot(data: PlayerData) {
        if (mc.thePlayer == null || referenceYPosition == null) return

        val player = data.player
        val ourY = referenceYPosition!!
        val theirY = player.posY
        val yDiff = theirY - ourY

        // Bot detection: Floating more than +0.8 Y-level above us and not on ground
        val isBot = yDiff > 0.8 && !player.onGround

        if (isBot) {
            data.classification = PlayerClassification.BOT
            notifyClassificationChange(player.displayNameString, true, theirY, yDiff)
            // Don't requeue if only bots are detected - we want to play against bots!
        } else {
            // Not a bot - this is a real player!
            data.classification = PlayerClassification.NOT_BOT
            notifyClassificationChange(player.displayNameString, false, theirY, yDiff)
            
            // Only take action if bot is enabled (not in test mode) AND we don't have at least 2 bots
            if (KeyInputListener.isBotEnabled() && !KeyInputListener.isTestModeEnabled()) {
                // Only dodge if we have fewer than 2 bots
                if (!hasMinimumBots(2)) {
                    // Check if instant disconnect is enabled and we're NOT in an active game
                    if (Config.instantDisconnectOnRealPlayer && StateManager.state != StateManager.States.PLAYING) {
                        executeInstantDisconnect(player.displayNameString)
                    } else {
                        // Normal dodge behavior
                        executeDodge(player.displayNameString)
                    }
                }
            }
        }
    }
    
    private fun executeInstantDisconnect(playerName: String) {
        if (hasDisconnected) return // Only disconnect once per lobby
        
        // Don't disconnect if game has already started
        if (StateManager.state == StateManager.States.PLAYING) {
            return
        }
        
        hasDisconnected = true
        
        // Pause the 8.5 second command loop
        KeyInputListener.pauseCommandLoop()
        
        // Cancel any pending bot detection command
        commandTimer?.cancel()
        commandTimer = null
        
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                net.minecraft.util.ChatComponentText(
                    "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                    "${net.minecraft.util.EnumChatFormatting.RED}${net.minecraft.util.EnumChatFormatting.BOLD}INSTANT DISCONNECT! ${net.minecraft.util.EnumChatFormatting.GRAY}Real player detected: $playerName"
                )
            )
            
            println("[RiceUtils] Instant disconnect triggered! Disconnecting from server...")
            
            // Disconnect immediately and reconnect after 5 seconds
            java.util.Timer().schedule(object : java.util.TimerTask() {
                override fun run() {
                    if (mc.theWorld != null) {
                        mc.theWorld.sendQuittingDisconnectingPacket()
                        mc.loadWorld(null)
                        
                        println("[RiceUtils] Disconnected! Reconnecting in 5 seconds...")
                        
                        // Wait 5 seconds, then reconnect
                        java.util.Timer().schedule(object : java.util.TimerTask() {
                            override fun run() {
                                StateManager.reconnectToServer()
                            }
                        }, 5000L)
                    }
                }
            }, 100L) // Small delay to ensure chat message is sent
        }
    }
    
    private fun executeDodge(playerName: String) {
        if (hasDodged) return // Only dodge once per lobby
        
        // Don't dodge if game has already started
        if (StateManager.state == StateManager.States.PLAYING) {
            return
        }
        
        hasDodged = true
        
        // Pause the 8.5 second command loop
        KeyInputListener.pauseCommandLoop()
        
        // Cancel any pending bot detection command
        commandTimer?.cancel()
        commandTimer = null
        
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                net.minecraft.util.ChatComponentText(
                    "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                    "${net.minecraft.util.EnumChatFormatting.RED}${net.minecraft.util.EnumChatFormatting.BOLD}DODGING! ${net.minecraft.util.EnumChatFormatting.GRAY}Real player detected: $playerName"
                )
            )
            
            // Choose delays based on slow mode
            val delay1 = 100L // Instant dodge - just a small delay to ensure chat message is sent
            val delay2 = if (ChatListener.isInSlowMode()) 3000L else 2000L
            val delay3 = if (ChatListener.isInSlowMode()) 1000L else 500L
            
            // Wait, then /l
            java.util.Timer().schedule(object : java.util.TimerTask() {
                override fun run() {
                    if (mc.thePlayer != null) {
                        mc.thePlayer.sendChatMessage("/l")
                        
                        // Wait for LOBBY state before running /p warp
                        waitForLobbyBeforeWarp(delay2, delay3)
                    }
                }
            }, delay1)
        }
    }
    
    private fun waitForLobbyBeforeWarp(delay2: Long, delay3: Long) {
        // Check state every 500ms, max 20 attempts (10 seconds)
        var attempts = 0
        val checkTimer = java.util.Timer()
        
        val checkTask = object : java.util.TimerTask() {
            override fun run() {
                attempts++
                
                // If we're in lobby, proceed with warps
                if (StateManager.state == StateManager.States.LOBBY) {
                    checkTimer.cancel()
                    proceedWithDodgeWarps(delay2, delay3)
                }
                // If max attempts reached, notify timeout
                else if (attempts >= 20) {
                    checkTimer.cancel()
                    if (mc.thePlayer != null) {
                        mc.thePlayer.addChatMessage(
                            net.minecraft.util.ChatComponentText(
                                "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                                "${net.minecraft.util.EnumChatFormatting.RED}Dodge: ${net.minecraft.util.EnumChatFormatting.GRAY}Timeout waiting for lobby state"
                            )
                        )
                    }
                }
            }
        }
        
        checkTimer.schedule(checkTask, 500L, 500L)
    }
    
    private fun proceedWithDodgeWarps(delay2: Long, delay3: Long) {
        // Check if party warp is disabled - skip directly to command execution
        if (!Config.partyWarpEnabled) {
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(
                    net.minecraft.util.ChatComponentText(
                        "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                        "${net.minecraft.util.EnumChatFormatting.YELLOW}Party Warp disabled, skipping to command execution..."
                    )
                )
            }
            scheduleCommandExecution(CommandTrigger.AFTER_DODGE)
            // Resume the 8.5 second command loop after dodge
            KeyInputListener.resumeCommandLoop()
            return
        }
        
        // Wait, then /p warp
        java.util.Timer().schedule(object : java.util.TimerTask() {
            override fun run() {
                if (mc.thePlayer != null && StateManager.state == StateManager.States.LOBBY) {
                    mc.thePlayer.sendChatMessage("/p warp")
                    
                    // Wait, then /p warp
                    java.util.Timer().schedule(object : java.util.TimerTask() {
                        override fun run() {
                            if (mc.thePlayer != null && StateManager.state == StateManager.States.LOBBY) {
                                mc.thePlayer.sendChatMessage("/p warp")
                                
                                // After dodge completes, schedule command execution
                                scheduleCommandExecution(CommandTrigger.AFTER_DODGE)
                                
                                // Resume the 8.5 second command loop after dodge
                                KeyInputListener.resumeCommandLoop()
                            }
                        }
                    }, delay3)
                }
            }
        }, delay2)
    }
    
    private fun scheduleCommandExecution(trigger: CommandTrigger) {
        if (hasExecutedCommand) return // Only execute once per lobby
        
        // Don't schedule if game has started
        if (StateManager.state == StateManager.States.PLAYING) {
            return
        }
        
        // Cancel any existing timer
        commandTimer?.cancel()
        
        // Random delay between 5-6 seconds (5000-6000ms)
        val delay = (5000..6000).random().toLong()
        
        commandTimer = java.util.Timer()
        commandTimer?.schedule(object : java.util.TimerTask() {
            override fun run() {
                // Double-check state hasn't changed to PLAYING before executing
                if (mc.thePlayer != null && !hasExecutedCommand && StateManager.state != StateManager.States.PLAYING) {
                    hasExecutedCommand = true
                    val command = Config.autoCommand
                    
                    if (command.isNotEmpty()) {
                        mc.thePlayer.sendChatMessage(command)
                        
                        val triggerText = when (trigger) {
                            CommandTrigger.BOT_DETECTED -> "Bot detected"
                            CommandTrigger.AFTER_DODGE -> "After dodge"
                        }
                        
                        mc.thePlayer.addChatMessage(
                            net.minecraft.util.ChatComponentText(
                                "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                                "${net.minecraft.util.EnumChatFormatting.YELLOW}Auto-command executed: ${net.minecraft.util.EnumChatFormatting.WHITE}$command " +
                                "${net.minecraft.util.EnumChatFormatting.GRAY}($triggerText)"
                            )
                        )
                    }
                }
            }
        }, delay)
    }
    
    private fun notifyClassificationChange(playerName: String, isBot: Boolean, theirY: Double? = null, yDiff: Double? = null) {
        if (mc.thePlayer != null) {
            val message = if (isBot) {
                val yInfo = if (theirY != null && yDiff != null && referenceYPosition != null) {
                    " ${net.minecraft.util.EnumChatFormatting.DARK_GRAY}[Y: ${String.format("%.2f", theirY)} vs ${String.format("%.2f", referenceYPosition)} = +${String.format("%.2f", yDiff)}]"
                } else ""
                "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                "${net.minecraft.util.EnumChatFormatting.GREEN}$playerName ${net.minecraft.util.EnumChatFormatting.GRAY}→ ${net.minecraft.util.EnumChatFormatting.GREEN}Bot ${net.minecraft.util.EnumChatFormatting.GRAY}(Floating >+0.8Y, no movement)$yInfo"
            } else {
                val reason = if (theirY != null && yDiff != null && referenceYPosition != null) {
                    if (yDiff <= 0.8) {
                        " ${net.minecraft.util.EnumChatFormatting.DARK_GRAY}[Y: ${String.format("%.2f", theirY)} vs ${String.format("%.2f", referenceYPosition)} = ${if (yDiff >= 0) "+" else ""}${String.format("%.2f", yDiff)}]"
                    } else {
                        " ${net.minecraft.util.EnumChatFormatting.GRAY}(Movement detected)"
                    }
                } else {
                    " ${net.minecraft.util.EnumChatFormatting.GRAY}(Movement detected)"
                }
                "${net.minecraft.util.EnumChatFormatting.DARK_GREEN}[${net.minecraft.util.EnumChatFormatting.GREEN}RiceUtils${net.minecraft.util.EnumChatFormatting.DARK_GREEN}] " +
                "${net.minecraft.util.EnumChatFormatting.RED}$playerName ${net.minecraft.util.EnumChatFormatting.GRAY}→ ${net.minecraft.util.EnumChatFormatting.RED}Not A Bot$reason"
            }
            mc.thePlayer.addChatMessage(net.minecraft.util.ChatComponentText(message))
        }
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        // Visual rendering disabled
        return
        
        /* ESP and nametag rendering disabled for performance
        if (mc.thePlayer == null || mc.theWorld == null) {
            return
        }
        
        // Only render if bot is enabled or test mode is enabled
        if (!KeyInputListener.isBotEnabled() && !KeyInputListener.isTestModeEnabled()) {
            return
        }
        
        for ((name, data) in trackedPlayers) {
            if (data.player.isEntityAlive) {
                // Only render if classified (not UNKNOWN)
                if (data.classification != PlayerClassification.UNKNOWN) {
                    val color = if (data.classification == PlayerClassification.BOT) botColor else notBotColor
                    val label = if (data.classification == PlayerClassification.BOT) "Bot" else "Not A Bot"
                    renderPlayerESP(data.player, color, event.partialTicks)
                    renderNameTag(data.player, label, color, event.partialTicks)
                }
            }
        }
        */
    }

    private fun renderPlayerESP(player: EntityPlayer, color: Color, partialTicks: Float) {
        val x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
        val y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
        val z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks

        val renderX = x - mc.renderManager.viewerPosX
        val renderY = y - mc.renderManager.viewerPosY
        val renderZ = z - mc.renderManager.viewerPosZ

        val boundingBox = AxisAlignedBB(
            renderX - player.width / 2,
            renderY,
            renderZ - player.width / 2,
            renderX + player.width / 2,
            renderY + player.height,
            renderZ + player.width / 2
        )

        GlStateManager.pushMatrix()
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)
        GlStateManager.disableCull()

        // Draw filled box with transparency
        drawFilledBox(boundingBox, color, 80)
        
        // Draw outline
        GL11.glLineWidth(2.0f)
        drawOutlinedBox(boundingBox, color, 255)

        GlStateManager.enableCull()
        GlStateManager.disableBlend()
        GlStateManager.enableDepth()
        GlStateManager.enableLighting()
        GlStateManager.enableTexture2D()
        GlStateManager.popMatrix()
    }

    private fun renderNameTag(player: EntityPlayer, text: String, color: Color, partialTicks: Float) {
        val x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
        val y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
        val z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks

        val renderX = x - mc.renderManager.viewerPosX
        val renderY = y - mc.renderManager.viewerPosY + player.height + 0.5
        val renderZ = z - mc.renderManager.viewerPosZ

        val distance = mc.thePlayer.getDistanceToEntity(player)
        val scale = (distance * 0.0018f).coerceAtLeast(0.02f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(renderX, renderY, renderZ)
        GL11.glNormal3f(0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(-mc.renderManager.playerViewY, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(mc.renderManager.playerViewX, 1.0f, 0.0f, 0.0f)
        GlStateManager.scale(-scale, -scale, scale)

        GlStateManager.disableLighting()
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)

        val fontRenderer = mc.fontRendererObj
        val width = fontRenderer.getStringWidth(text) / 2

        // Draw background
        GlStateManager.disableTexture2D()
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
        worldRenderer.pos((-width - 1).toDouble(), -1.0, 0.0).color(0.0f, 0.0f, 0.0f, 0.5f).endVertex()
        worldRenderer.pos((-width - 1).toDouble(), 8.0, 0.0).color(0.0f, 0.0f, 0.0f, 0.5f).endVertex()
        worldRenderer.pos((width + 1).toDouble(), 8.0, 0.0).color(0.0f, 0.0f, 0.0f, 0.5f).endVertex()
        worldRenderer.pos((width + 1).toDouble(), -1.0, 0.0).color(0.0f, 0.0f, 0.0f, 0.5f).endVertex()
        tessellator.draw()

        // Draw text with color from classification
        GlStateManager.enableTexture2D()
        val colorInt = (color.red shl 16) or (color.green shl 8) or color.blue
        fontRenderer.drawString(text, -width, 0, colorInt)

        GlStateManager.enableDepth()
        GlStateManager.disableBlend()
        GlStateManager.enableLighting()
        GlStateManager.popMatrix()
    }

    private fun drawFilledBox(box: AxisAlignedBB, color: Color, alpha: Int) {
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer

        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        val a = alpha / 255f

        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)

        // Bottom face
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()

        // Top face
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()

        // Front face
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()

        // Back face
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()

        // Left face
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()

        // Right face
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()

        tessellator.draw()
    }

    private fun drawOutlinedBox(box: AxisAlignedBB, color: Color, alpha: Int) {
        RenderGlobal.drawOutlinedBoundingBox(box, color.red, color.green, color.blue, alpha)
    }
}

