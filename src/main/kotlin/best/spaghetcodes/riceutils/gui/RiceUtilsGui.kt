package best.spaghetcodes.riceutils.gui

import best.spaghetcodes.riceutils.core.Config
import best.spaghetcodes.riceutils.listeners.KeyInputListener
import best.spaghetcodes.riceutils.managers.StatsManager
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraft.util.ChatComponentText
import org.lwjgl.input.Keyboard
import java.io.IOException

class RiceUtilsGui : GuiScreen() {
    
    private var commandTextField: GuiTextField? = null
    private var normalDelayTextField: GuiTextField? = null
    private var slowModeDelayTextField: GuiTextField? = null
    private var lobbyDelayTextField: GuiTextField? = null
    private var saveButton: GuiButton? = null
    private var showStatsButton: GuiButton? = null
    private var resetStatsButton: GuiButton? = null
    private var partyWarpToggleButton: GuiButton? = null
    private var instantDisconnectToggleButton: GuiButton? = null
    
    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        
        val startY = height / 2 - 120
        
        // Command text field
        commandTextField = GuiTextField(0, fontRendererObj, width / 2 - 100, startY + 40, 200, 20)
        commandTextField?.text = Config.autoCommand
        commandTextField?.maxStringLength = 256
        
        // Delay text fields
        normalDelayTextField = GuiTextField(1, fontRendererObj, width / 2 - 100, startY + 90, 63, 20)
        normalDelayTextField?.text = Config.normalRequeueDelay.toString()
        normalDelayTextField?.maxStringLength = 6
        
        slowModeDelayTextField = GuiTextField(2, fontRendererObj, width / 2 - 31, startY + 90, 63, 20)
        slowModeDelayTextField?.text = Config.slowModeRequeueDelay.toString()
        slowModeDelayTextField?.maxStringLength = 6
        
        lobbyDelayTextField = GuiTextField(3, fontRendererObj, width / 2 + 38, startY + 90, 62, 20)
        lobbyDelayTextField?.text = Config.lobbyRequeueDelay.toString()
        lobbyDelayTextField?.maxStringLength = 6
        
        // Party Warp toggle button
        partyWarpToggleButton = GuiButton(100, width / 2 - 100, startY + 120, 200, 20, getPartyWarpToggleText())
        buttonList.add(partyWarpToggleButton)
        
        // Instant Disconnect toggle button
        instantDisconnectToggleButton = GuiButton(101, width / 2 - 100, startY + 145, 200, 20, getInstantDisconnectToggleText())
        buttonList.add(instantDisconnectToggleButton)
        
        // Stats buttons
        showStatsButton = GuiButton(102, width / 2 - 100, startY + 175, 97, 20, "Show Stats")
        buttonList.add(showStatsButton)
        
        resetStatsButton = GuiButton(103, width / 2 + 3, startY + 175, 97, 20, "Reset Stats")
        buttonList.add(resetStatsButton)
        
        // Save button
        saveButton = GuiButton(104, width / 2 - 100, startY + 205, 200, 20, "Save Settings")
        buttonList.add(saveButton)
        
        // Close button
        buttonList.add(GuiButton(105, width / 2 - 100, startY + 235, 200, 20, "Close"))
    }
    
    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
    }
    
    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        
        val startY = height / 2 - 120
        
        // Title
        val title = "§a§lRiceUtils Configuration"
        drawCenteredString(fontRendererObj, title, width / 2, startY - 10, 0xFFFFFF)
        
        // Bot status
        val statusText = if (KeyInputListener.isBotEnabled()) {
            "§aBot Status: §l§2ENABLED"
        } else if (KeyInputListener.isTestModeEnabled()) {
            "§bTest Mode: §l§3ENABLED"
        } else {
            "§cBot Status: §l§4DISABLED"
        }
        drawCenteredString(fontRendererObj, statusText, width / 2, startY + 10, 0xFFFFFF)
        drawCenteredString(fontRendererObj, "§7Press §f; §7to toggle bot | Press §fP §7for test mode", width / 2, startY + 20, 0xAAAAAA)
        
        // Label for command field
        drawCenteredString(fontRendererObj, "§7Auto-Command (after bot detection or dodge):", width / 2, startY + 30, 0xAAAAAA)
        
        // Draw command text field
        commandTextField?.drawTextBox()
        
        // Labels for delay fields
        drawCenteredString(fontRendererObj, "§7Requeue Delays (ms):", width / 2, startY + 70, 0xAAAAAA)
        
        // Draw delay text fields with labels underneath
        normalDelayTextField?.drawTextBox()
        slowModeDelayTextField?.drawTextBox()
        lobbyDelayTextField?.drawTextBox()
        
        // Small labels under each field
        drawCenteredString(fontRendererObj, "§8Normal", width / 2 - 68, startY + 112, 0x888888)
        drawCenteredString(fontRendererObj, "§8Slow", width / 2, startY + 112, 0x888888)
        drawCenteredString(fontRendererObj, "§8Lobby", width / 2 + 69, startY + 112, 0x888888)
        
        super.drawScreen(mouseX, mouseY, partialTicks)
    }
    
    @Throws(IOException::class)
    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            100 -> { // Party Warp Toggle
                Config.partyWarpEnabled = !Config.partyWarpEnabled
                partyWarpToggleButton?.displayString = getPartyWarpToggleText()
                mc.thePlayer?.addChatMessage(
                    ChatComponentText(
                        "§a[RiceUtils] §7Party Warp: " + 
                        if (Config.partyWarpEnabled) "§aEnabled" else "§cDisabled"
                    )
                )
            }
            101 -> { // Instant Disconnect Toggle
                Config.instantDisconnectOnRealPlayer = !Config.instantDisconnectOnRealPlayer
                instantDisconnectToggleButton?.displayString = getInstantDisconnectToggleText()
                mc.thePlayer?.addChatMessage(
                    ChatComponentText(
                        "§a[RiceUtils] §7Instant Disconnect: " + 
                        if (Config.instantDisconnectOnRealPlayer) "§aEnabled" else "§cDisabled"
                    )
                )
            }
            104 -> { // Save
                Config.autoCommand = commandTextField?.text ?: ""
                
                // Parse and validate delay values
                try {
                    val normalDelay = normalDelayTextField?.text?.toLongOrNull() ?: 2000L
                    val slowModeDelay = slowModeDelayTextField?.text?.toLongOrNull() ?: 4000L
                    val lobbyDelay = lobbyDelayTextField?.text?.toLongOrNull() ?: 3000L
                    
                    // Validate delays are positive
                    Config.normalRequeueDelay = if (normalDelay > 0) normalDelay else 2000L
                    Config.slowModeRequeueDelay = if (slowModeDelay > 0) slowModeDelay else 4000L
                    Config.lobbyRequeueDelay = if (lobbyDelay > 0) lobbyDelay else 3000L
                    
                    Config.saveConfig()
                    mc.thePlayer?.addChatMessage(ChatComponentText("§a[RiceUtils] §7Settings saved!"))
                } catch (e: Exception) {
                    mc.thePlayer?.addChatMessage(ChatComponentText("§c[RiceUtils] §7Invalid delay values! Using defaults."))
                }
            }
            105 -> { // Close
                mc.displayGuiScreen(null)
            }
            102 -> { // Show stats
                StatsManager.displayStats(mc)
            }
            103 -> { // Reset stats
                StatsManager.resetSession()
                mc.thePlayer?.addChatMessage(
                    ChatComponentText("§a[RiceUtils] §7Stats reset! Session timer restarted.")
                )
            }
        }
    }
    
    private fun getPartyWarpToggleText(): String {
        return if (Config.partyWarpEnabled) {
            "§aParty Warp: §l§2ENABLED"
        } else {
            "§cParty Warp: §l§4DISABLED"
        }
    }
    
    private fun getInstantDisconnectToggleText(): String {
        return if (Config.instantDisconnectOnRealPlayer) {
            "§aInstant Disconnect: §l§2ENABLED"
        } else {
            "§cInstant Disconnect: §l§4DISABLED"
        }
    }
    
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null)
        } else {
            commandTextField?.textboxKeyTyped(typedChar, keyCode)
            normalDelayTextField?.textboxKeyTyped(typedChar, keyCode)
            slowModeDelayTextField?.textboxKeyTyped(typedChar, keyCode)
            lobbyDelayTextField?.textboxKeyTyped(typedChar, keyCode)
        }
    }
    
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
        commandTextField?.mouseClicked(mouseX, mouseY, mouseButton)
        normalDelayTextField?.mouseClicked(mouseX, mouseY, mouseButton)
        slowModeDelayTextField?.mouseClicked(mouseX, mouseY, mouseButton)
        lobbyDelayTextField?.mouseClicked(mouseX, mouseY, mouseButton)
    }
    
    override fun updateScreen() {
        commandTextField?.updateCursorCounter()
        normalDelayTextField?.updateCursorCounter()
        slowModeDelayTextField?.updateCursorCounter()
        lobbyDelayTextField?.updateCursorCounter()
    }
    
    override fun doesGuiPauseGame(): Boolean {
        return false
    }
}

