package org.prin.WhiteForest.team

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import org.prin.WhiteForest.discord.DiscordVerify
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class Death(
    private val plugin: JavaPlugin,
    private val discordVerify: DiscordVerify,
    private val pylon: Pylon
) : Listener {

    val cooldowns = mutableMapOf<UUID, Long>()

    // -------------------- death.yml 로드 --------------------
    private val deathFile = File(plugin.dataFolder, "death.yml").apply {
        if (!exists()) {
            parentFile.mkdirs() // 폴더 없으면 생성
            createNewFile()    // 파일 생성
        }
    }
    private val deathConfig: YamlConfiguration = YamlConfiguration.loadConfiguration(deathFile)


    private fun saveDeathConfig() {
        deathConfig.save(deathFile)
    }

    // -------------------- PlayerDeathEvent --------------------
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer

        val mcName = victim.name
        val displayName = discordVerify.getDisplayName(mcName) ?: mcName
        val discordId = discordVerify.getDiscordId(mcName)
        event.deathMessage = "$displayName 이(가) 죽었습니다!"

        // ------------------- 팀 합류 --------------------]
        if (killer is Player) { // 가해자가 플레이어인지 확인
            val killerUUID = killer.uniqueId  // UUID
            val victimUUID = victim.uniqueId  // UUID

            pylon.joinTeam(victimUUID, killerUUID)
        }


        // -------------------- 죽음 횟수 --------------------
        val deathCountKey = "${victim.uniqueId}.deathCount"
        val deathCount = (deathConfig.getInt(deathCountKey, 0) + 1)
        deathConfig.set(deathCountKey, deathCount)
        saveDeathConfig()

        // -------------------- Totem reduction 확인 --------------------
        val reduction = CoolDownManager.getReduction(victim)
        println("총 Totem reduction = $reduction")

        // -------------------- 소생 쿨다운 계산 --------------------
        val baseCooldown = plugin.config.getInt("respawn.base-cooldown", 60) * 1000L
        val additional = plugin.config.getInt("respawn.additional-per-death", 10) * 1000L * (deathCount - 1)
        val finalCooldown = (baseCooldown + additional - reduction * 1000L).coerceAtLeast(0)

        cooldowns[victim.uniqueId] = System.currentTimeMillis() + finalCooldown

        // -------------------- 플레이어 킥 --------------------
        victim.kickPlayer(buildCooldownMessage(System.currentTimeMillis() + finalCooldown, (finalCooldown / 1000).toInt()))

        // -------------------- 디스코드 알림 --------------------
        if (discordId != null) {
            discordVerify.sendPrivateMessage(discordId, "<@$discordId> $displayName 이(가) 죽었습니다!")
        }
    }

    // -------------------- PlayerLoginEvent --------------------
    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val uuid = event.player.uniqueId
        val until = cooldowns[uuid] ?: return

        if (System.currentTimeMillis() < until) {
            val remain = ((until - System.currentTimeMillis()) / 1000).toInt()
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, buildCooldownMessage(until, remain))
        } else {
            cooldowns.remove(uuid)
        }
    }

    // -------------------- PlayerRespawnEvent --------------------
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val team = pylon.getOrCreateTeam(player.uniqueId)

        val activePylonLocations = team.pylonLocations
            .filter { key -> pylon.activeBeacons.contains(key) } // 활성화된 것만 필터
            .mapNotNull { key ->
                val parts = key.split(",")
                if (parts.size != 4) return@mapNotNull null
                val world = plugin.server.getWorld(parts[0]) ?: return@mapNotNull null
                val x = parts[1].toIntOrNull() ?: return@mapNotNull null
                val y = parts[2].toIntOrNull() ?: return@mapNotNull null
                val z = parts[3].toIntOrNull() ?: return@mapNotNull null
                Location(world, x.toDouble(), y.toDouble(), z.toDouble())
            }

        val respawnLocation = if (activePylonLocations.isNotEmpty()) {
            activePylonLocations.random() // 활성화된 파일런 중 랜덤 선택
        } else {
            // 활성화 파일런 없으면 기존 랜덤 리스폰
            val world = Bukkit.getWorld("WhiteForest")!!
            val radius = plugin.config.getInt("respawn.random-radius", 30000)
            val x = Random.nextInt(-radius, radius).toDouble()
            val z = Random.nextInt(-radius, radius).toDouble()
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble() + 1
            Location(world, x, y, z)
        }

        event.respawnLocation = respawnLocation
    }


    // -------------------- 쿨다운 메시지 --------------------
    private fun buildCooldownMessage(until: Long, remainSeconds: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val formatted = sdf.format(Date(until))
        return """
            아바타를 소생중입니다!
            소생시간: $formatted
            남은시간: ${remainSeconds}초
        """.trimIndent()
    }
}
