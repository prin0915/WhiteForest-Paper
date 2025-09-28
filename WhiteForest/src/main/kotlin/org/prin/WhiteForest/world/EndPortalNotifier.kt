package org.prin.WhiteForest.world

import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class EndPortalNotifier(private val plugin: JavaPlugin): Listener {

    private val notifiedTimes = mutableSetOf<String>() // 이미 알림 보낸 시간 기록

    fun start() {
        // 20틱 = 1초, 60초마다 체크
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val now = java.time.LocalTime.now()
            val allowedTimes = plugin.config.getMapList("end-portal.allowed-times")

            for (timeMap in allowedTimes) {
                val start = java.time.LocalTime.parse(timeMap["start"] as String)
                val end = java.time.LocalTime.parse(timeMap["end"] as String)
                val key = start.toString()

                if (now.hour == start.hour && now.minute == start.minute) {
                    if (key !in notifiedTimes) {
                        // 모든 플레이어에게 메시지 전송
                        Bukkit.getOnlinePlayers().forEach { player ->
                            player.sendMessage("§a엔더 차원이 열렸습니다!")
                        }
                        notifiedTimes.add(key)
                    }
                }

                // 허용 시간이 지나면 알림 기록 초기화
                if (now > end) {
                    notifiedTimes.remove(key)
                }
            }
        }, 0L, 20L * 1) // 1초마다 실행
    }
}
