package org.prin.WhiteForest.abillity

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class BootsDoubleJump(val plugin: JavaPlugin) : Listener {


    // 플레이어가 공중에서 두 번째 점프를 할 수 있도록
    @EventHandler
    fun onPlayerToggleFlight(event: PlayerToggleFlightEvent) {

        val player = event.player
        val boots = player.inventory.boots ?: return
        val container = boots.persistentDataContainer
        val key = NamespacedKey(plugin, "force_level")
        val currentLevel = container.get(key, PersistentDataType.INTEGER) ?: 0

        // 크리에이티브 모드에서는 무시
        if (player.gameMode == GameMode.CREATIVE && boots.type != Material.NETHERITE_BOOTS || currentLevel < 10) return

        event.isCancelled = true // 일반 비행 방지

        player.isFlying = false
        player.allowFlight = false

        // 착지 전까지 다시 점프 못하게

        // 점프 효과 적용
        val vector = player.location.apply {
            pitch = 0.0F
        }.direction.multiply(1.2).apply{ y += 0.75}
        player.velocity = vector

    }

    // 착지하면 다시 점프 가능하게
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val boots = player.inventory.boots ?: return
        val container = boots.persistentDataContainer
        val key = NamespacedKey(plugin, "force_level")
        val currentLevel = container.get(key, PersistentDataType.INTEGER) ?: 0

        if (player.gameMode == GameMode.CREATIVE && boots.type != Material.NETHERITE_BOOTS || currentLevel < 10) return

        if (player.isOnGround && !player.allowFlight) {
            player.allowFlight = true
        }
    }
}