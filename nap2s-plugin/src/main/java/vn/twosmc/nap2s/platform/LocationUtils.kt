package vn.twosmc.nap2s.platform

import org.bukkit.Location

/**
 * Extension function cho Location — serialize thành chuỗi.
 *
 * Format: "world,x,y,z,yaw,pitch"
 */
fun Location.asString(): String {
    return "${world?.name ?: "world"},${blockX},${blockY},${blockZ},${yaw},${pitch}"
}
