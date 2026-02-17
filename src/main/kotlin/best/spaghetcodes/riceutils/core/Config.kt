package best.spaghetcodes.riceutils.core

import com.google.gson.GsonBuilder
import java.io.File

object Config {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File("config/riceutils.json")

    enum class GameMode {
        NORMAL_DUELS,
        BRIDGE_DUELS
    }

    var autoCommand: String = "" // Configurable command
    var gameMode: GameMode = GameMode.NORMAL_DUELS // Detection mode
    var partyWarpEnabled: Boolean = true // Enable/disable party warp feature
    var instantDisconnectOnRealPlayer: Boolean = false // Instant disconnect when real player detected
    
    // Configurable delays (in milliseconds)
    var normalRequeueDelay: Long = 2000L // Normal requeue delay after game end
    var slowModeRequeueDelay: Long = 4000L // Slow mode requeue delay
    var lobbyRequeueDelay: Long = 3000L // Delay after reaching lobby before requeue
    
    // Hardcoded webhook settings - edit these values directly
    var webhookURL: String = "https://discord.com/api/webhooks/1429975644753760297/aMqiQFOfJtADg6M7wZru4dMBTKO1o454FOIfNL9f5_6jWF-a5eYMU1K-7u1YZJV7M6EW"
    var sendWebhook: Boolean = true // Webhooks enabled

    fun loadConfig() {
        if (configFile.exists()) {
            try {
                val configData = gson.fromJson(configFile.readText(), ConfigData::class.java)
                autoCommand = configData.autoCommand
                gameMode = try {
                    GameMode.valueOf(configData.gameMode)
                } catch (e: Exception) {
                    GameMode.NORMAL_DUELS
                }
                partyWarpEnabled = configData.partyWarpEnabled
                instantDisconnectOnRealPlayer = configData.instantDisconnectOnRealPlayer
                normalRequeueDelay = configData.normalRequeueDelay
                slowModeRequeueDelay = configData.slowModeRequeueDelay
                lobbyRequeueDelay = configData.lobbyRequeueDelay
            } catch (e: Exception) {
                println("Error loading RiceUtils config: ${e.message}")
                e.printStackTrace()
            }
        } else {
            saveConfig()
        }
    }

    fun saveConfig() {
        try {
            configFile.parentFile.mkdirs()
            val configData = ConfigData(
                autoCommand, 
                gameMode.name, 
                partyWarpEnabled, 
                instantDisconnectOnRealPlayer,
                normalRequeueDelay,
                slowModeRequeueDelay,
                lobbyRequeueDelay
            )
            configFile.writeText(gson.toJson(configData))
        } catch (e: Exception) {
            println("Error saving RiceUtils config: ${e.message}")
            e.printStackTrace()
        }
    }

    private data class ConfigData(
        val autoCommand: String,
        val gameMode: String = "NORMAL_DUELS",
        val partyWarpEnabled: Boolean = true,
        val instantDisconnectOnRealPlayer: Boolean = false,
        val normalRequeueDelay: Long = 2000L,
        val slowModeRequeueDelay: Long = 4000L,
        val lobbyRequeueDelay: Long = 3000L
    )
}
