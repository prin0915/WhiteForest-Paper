package org.prin.WhiteForest.world

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.prin.WhiteForest.team.Pylon

class NetherPortal(private val pylon: Pylon) : Listener {


    @EventHandler
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val player: Player = event.player
        val to = event.to ?: return

        // 오버월드 → 플레이어 전용 네더
        if (to.world.environment == World.Environment.NETHER && event.from.world.environment == World.Environment.NORMAL) {
            // owner UUID 가져오기
            val ownerUUID = pylon.getOwnerUUIDByPlayer(player.uniqueId)

            val worldName = if (ownerUUID == null) {
                "nether_${player.uniqueId}" // 팀이 없으면 플레이어 UUID 사용
            } else {
                "nether_${ownerUUID}"      // 팀이 있으면 owner UUID 사용
            }

            var netherWorld = Bukkit.getWorld(worldName)
            if (netherWorld == null) {
                netherWorld = WorldCreator(worldName)
                    .environment(World.Environment.NETHER)
                    .type(WorldType.NORMAL)
                    .createWorld()
            }

            // null 체크 후 게임룰 설정
            netherWorld?.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true)

            // 좌표 변환 (오버월드 → 네더: /8)
            val loc = event.from.clone()
            loc.world = netherWorld
            loc.x /= 8.0
            loc.z /= 8.0

            event.setTo(loc)
        }

        // 플레이어 네더 → 오버월드
        else if (event.from.world.environment == World.Environment.NETHER) {
            val overworld = Bukkit.getWorld("world") ?: return

            // 좌표 변환 (네더 → 오버월드: *8)
            val loc = event.from.clone()
            loc.world = overworld
            loc.x *= 8.0
            loc.z *= 8.0

            event.setTo(loc)
        }
    }
}
