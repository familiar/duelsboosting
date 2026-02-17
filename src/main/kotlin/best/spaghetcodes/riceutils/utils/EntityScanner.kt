package best.spaghetcodes.riceutils.utils

import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer

object EntityScanner {

    private val mc = Minecraft.getMinecraft()

    /**
     * Scans for all valid opponent entities based on the given criteria
     */
    fun scanForPlayers(): List<EntityPlayer> {
        val validPlayers = mutableListOf<EntityPlayer>()
        
        if (mc.theWorld == null || mc.thePlayer == null) {
            return validPlayers
        }
        
        for (entity in mc.theWorld.playerEntities) {
            if (shouldTarget(entity)) {
                validPlayers.add(entity)
            }
        }
        
        return validPlayers
    }

    /**
     * Determines if an entity should be targeted based on criteria:
     * - Entity is not null
     * - Both player and entity are alive
     * - Entity is not invisible
     * - Entity is within 80 blocks
     * - Entity has a different display name than the bot
     */
    private fun shouldTarget(entity: EntityPlayer?): Boolean {
        if (entity == null) {
            return false
        }
        
        if (mc.thePlayer == null) {
            return false
        }
        
        // Check if entity has different display name
        if (entity.displayNameString == mc.thePlayer.displayNameString) {
            return false
        }
        
        // Check if both are alive
        if (!mc.thePlayer.isEntityAlive || !entity.isEntityAlive) {
            return false
        }
        
        // Check if entity is invisible
        if (entity.isInvisible) {
            return false
        }
        
        // Check if within 80 blocks
        if (mc.thePlayer.getDistanceToEntity(entity) > 80.0f) {
            return false
        }
        
        return true
    }

    /**
     * Gets formatted coordinate string for an entity
     */
    fun getCoordinateString(entity: EntityPlayer): String {
        val x = String.format("%.2f", entity.posX)
        val y = String.format("%.2f", entity.posY)
        val z = String.format("%.2f", entity.posZ)
        return "X: $x, Y: $y, Z: $z"
    }
}


