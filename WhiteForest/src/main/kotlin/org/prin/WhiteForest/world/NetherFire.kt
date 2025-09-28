package org.prin.WhiteForest.world

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType


class NetherFire(private val plugin: JavaPlugin) {

    fun start() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                if (player.world.environment.name == "NETHER") {
                    val hasFireResist = player.activePotionEffects
                        .any { it.type == PotionEffectType.FIRE_RESISTANCE }

                    if (!hasFireResist) {
                        player.setFireTicks(20) // 1초 동안 불
                    }
                }
            }
        }, 0L, 20L)
    }
}
