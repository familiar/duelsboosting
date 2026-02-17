package best.spaghetcodes.riceutils.managers

import best.spaghetcodes.riceutils.core.Config
import best.spaghetcodes.riceutils.utils.WebHook
import com.google.gson.JsonArray
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting
import java.text.DecimalFormat
import java.util.*

object StatsManager {
    
    var wins = 0
        private set
    var losses = 0
        private set
    var sessionStartTime = System.currentTimeMillis()
        private set
    
    private val df = DecimalFormat("0.00")
    
    fun recordWin(opponent: String) {
        wins++
        saveStats()
        sendWinLossWebhook(true, opponent)
    }
    
    fun recordLoss(opponent: String) {
        losses++
        saveStats()
        sendWinLossWebhook(false, opponent)
    }
    
    fun resetSession() {
        wins = 0
        losses = 0
        sessionStartTime = System.currentTimeMillis()
        saveStats()
    }
    
    fun getWLR(): Double {
        return if (losses == 0) {
            wins.toDouble()
        } else {
            wins.toDouble() / losses.toDouble()
        }
    }
    
    fun getWinsPerHour(): Double {
        val hoursElapsed = (System.currentTimeMillis() - sessionStartTime) / 3600000.0
        return if (hoursElapsed > 0) wins / hoursElapsed else 0.0
    }
    
    private fun saveStats() {
        // Stats are stored in memory for the session
        // Could be saved to file if needed
    }
    
    private fun sendWinLossWebhook(isWin: Boolean, opponent: String) {
        if (!Config.sendWebhook || Config.webhookURL.isEmpty()) return
        
        val wlr = getWLR()
        val wph = getWinsPerHour()
        val sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000
        val hours = sessionDuration / 3600
        val minutes = (sessionDuration % 3600) / 60
        val seconds = sessionDuration % 60
        
        val fields = WebHook.buildFields(arrayListOf(
            mapOf("name" to "Result", "value" to if (isWin) "✅ WIN" else "❌ LOSS", "inline" to "true"),
            mapOf("name" to "Opponent", "value" to opponent, "inline" to "true"),
            mapOf("name" to "Wins", "value" to "$wins", "inline" to "true"),
            mapOf("name" to "Losses", "value" to "$losses", "inline" to "true"),
            mapOf("name" to "W/L Ratio", "value" to df.format(wlr), "inline" to "true"),
            mapOf("name" to "Wins/Hour", "value" to df.format(wph), "inline" to "true"),
            mapOf("name" to "Session Duration", "value" to "${hours}h ${minutes}m ${seconds}s", "inline" to "false"),
            mapOf("name" to "Mode", "value" to Config.gameMode.name.replace("_", " "), "inline" to "true")
        ))
        
        val footer = WebHook.buildFooter("RiceUtils Stats Tracker • ${Date()}")
        
        val embed = WebHook.buildEmbed(
            if (isWin) "🎉 Game WON!" else "💀 Game LOST",
            "Session stats updated",
            fields,
            footer,
            if (isWin) 0x00FF00 else 0xFF0000  // Green for win, red for loss
        )
        
        WebHook.sendEmbed(Config.webhookURL, embed)
    }
    
    fun sendStatsUpdate() {
        if (!Config.sendWebhook || Config.webhookURL.isEmpty()) return
        
        val wlr = getWLR()
        val wph = getWinsPerHour()
        val sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000
        val hours = sessionDuration / 3600
        val minutes = (sessionDuration % 3600) / 60
        
        val fields = WebHook.buildFields(arrayListOf(
            mapOf("name" to "Wins", "value" to "$wins", "inline" to "true"),
            mapOf("name" to "Losses", "value" to "$losses", "inline" to "true"),
            mapOf("name" to "W/L Ratio", "value" to df.format(wlr), "inline" to "true"),
            mapOf("name" to "Wins/Hour", "value" to df.format(wph), "inline" to "true"),
            mapOf("name" to "Session Time", "value" to "${hours}h ${minutes}m", "inline" to "false")
        ))
        
        val footer = WebHook.buildFooter("RiceUtils Stats Update • ${Date()}")
        
        val embed = WebHook.buildEmbed(
            "📊 Session Stats",
            "Current session statistics",
            fields,
            footer,
            0x00BFFF  // Blue
        )
        
        WebHook.sendEmbed(Config.webhookURL, embed)
    }
    
    fun displayStats(minecraft: net.minecraft.client.Minecraft) {
        if (minecraft.thePlayer == null) return
        
        val wlr = getWLR()
        val wph = getWinsPerHour()
        
        minecraft.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.DARK_GREEN}[${EnumChatFormatting.GREEN}RiceUtils${EnumChatFormatting.DARK_GREEN}] " +
                "${EnumChatFormatting.GOLD}Session Stats:"
            )
        )
        minecraft.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.GRAY}Wins: ${EnumChatFormatting.GREEN}$wins ${EnumChatFormatting.GRAY}| " +
                "Losses: ${EnumChatFormatting.RED}$losses ${EnumChatFormatting.GRAY}| " +
                "W/L: ${EnumChatFormatting.AQUA}${df.format(wlr)}"
            )
        )
        minecraft.thePlayer.addChatMessage(
            ChatComponentText(
                "${EnumChatFormatting.GRAY}Wins/Hour: ${EnumChatFormatting.YELLOW}${df.format(wph)}"
            )
        )
    }
}


