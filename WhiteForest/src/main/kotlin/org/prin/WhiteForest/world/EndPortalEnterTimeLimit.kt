package org.prin.WhiteForest.world

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.prin.WhiteForest.WhiteForest
import java.time.DayOfWeek
import java.time.LocalTime

class EndPortalEnterTimeLimit(private val plugin: JavaPlugin) : Listener {

    private fun getAllowedDay(): DayOfWeek {
        val dayStr = plugin.config.getString("end-portal.allowed-day", "SUNDAY")!!
        return DayOfWeek.valueOf(dayStr.uppercase())
    }

    private fun isEnabled(): Boolean {
        return plugin.config.getBoolean("end-portal.enabled", true)
    }

    // 현재 시간이 허용 구간 중 하나인지 체크
    private fun isTimeAllowed(): Boolean {
        val now = LocalTime.now()
        val timeList = plugin.config.getMapList("end-portal.allowed-times")
        for (timeMap in timeList) {
            val start = LocalTime.parse(timeMap["start"] as String)
            val end = LocalTime.parse(timeMap["end"] as String)

            val matches = if (start <= end) {
                now in start..end
            } else {
                // 자정을 넘어가는 경우 예: 22:00~02:00
                now >= start || now <= end
            }

            if (matches){
                (plugin as WhiteForest).enderDragonDefeated = false
                return true
            }
        }
        return false
    }

    @EventHandler
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val player = event.player
        val toWorld = event.to?.world ?: return

        // 엔더월드로 들어가는 경우만 체크
        if (toWorld.environment != World.Environment.THE_END) return
        if (!isEnabled()) return

        val now = java.time.LocalDateTime.now()
        val allowedDay = getAllowedDay()
        val dayMatches = now.dayOfWeek == allowedDay
        val timeMatches = isTimeAllowed()

        // 엔더 드래곤이 이미 잡혔다면 포탈 진입 차단
        if ((plugin as WhiteForest).enderDragonDefeated) {
            event.isCancelled = true
            player.sendActionBar { Component.text("§c엔더 월드가 붕괴중이여서 진입할 수 없습니다!") }
            return
        }

        if (!dayMatches || !timeMatches) {
            event.isCancelled = true
            return
        }

        // 허용 시간일 경우 강제 엔더월드 이동
        val endWorld: World? = Bukkit.getWorld("world_the_end")
        if (endWorld != null) {
            val targetLocation = Location(endWorld, 0.0, 150.0, 0.0)
            event.to = targetLocation
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SLOW_FALLING, 20*13,1 , false, false, true
                )
            )
            player.teleport(targetLocation)
        }
    }
}
