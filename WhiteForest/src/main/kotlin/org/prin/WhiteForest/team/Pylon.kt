package org.prin.WhiteForest.team

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Beacon
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.prin.WhiteForest.discord.DiscordVerify
import java.io.File
import java.util.*
import kotlin.math.sqrt

data class Team(
    val ownerUUID: UUID,
    val memberUUIDs: MutableList<UUID> = mutableListOf(),
    val pylonLocations: MutableList<String> = mutableListOf()
)

class Pylon(
    private val plugin: JavaPlugin, private val discordVerify: DiscordVerify
) : Listener {

    val activeBeacons = mutableSetOf<String>()
    private val proximityAlerts = mutableMapOf<Pair<UUID, String>, Long>()
    private val teams = mutableMapOf<UUID, Team>() // key: 팀장 UUID

    private val teamFile = File(plugin.dataFolder, "teams.yml")
    private val teamConfig = if (teamFile.exists()) YamlConfiguration.loadConfiguration(teamFile)
    else YamlConfiguration()

    // ---------------------------
    // 초기화
    // ---------------------------
    init {
        plugin.saveDefaultConfig()
        loadTeams()
    }

    // ---------------------------
    // 팀 데이터 저장/로드
    // ---------------------------
    fun loadTeams() {
        val pylonSection = teamConfig.getConfigurationSection("pylon") ?: return
        for (teamKey in pylonSection.getKeys(false)) {
            val ownerStr = pylonSection.getString("$teamKey.owner") ?: continue
            val ownerUUID = UUID.fromString(ownerStr)

            val membersList = pylonSection.getStringList("$teamKey.member").map { UUID.fromString(it) }.toMutableList()
            val locationsList = pylonSection.getStringList("$teamKey.pylonLocation").toMutableList()

            teams[ownerUUID] = Team(ownerUUID, membersList, locationsList)
        }
    }

    fun saveTeams() {
        teamConfig.set("pylon", null) // 초기화
        for ((ownerUUID, team) in teams) {
            val path = "pylon.${ownerUUID}"
            teamConfig.set("$path.owner", team.ownerUUID.toString())
            teamConfig.set("$path.member", team.memberUUIDs.map { it.toString() })
            teamConfig.set("$path.pylonLocation", team.pylonLocations)
        }
        teamConfig.save(teamFile)
    }
    // ---------------------------
    // 특정 플레이어 UUID로 owner UUID 가져오기
    // ---------------------------
    fun getOwnerUUIDByPlayer(playerUUID: UUID): UUID? {
        // 플레이어가 팀장인지 먼저 확인
        if (teams.containsKey(playerUUID)) return playerUUID

        // 팀원인지 확인
        for ((ownerUUID, team) in teams) {
            if (team.memberUUIDs.contains(playerUUID)) return ownerUUID
        }

        // 해당 플레이어가 팀에 속하지 않으면 null 반환
        return null
    }

    // ---------------------------
    // PylonData 위치로 owner UUID 가져오기
    // ---------------------------
    fun getOwnerUUIDByLocation(location: org.bukkit.Location): UUID? {
        val pylonData = getPylonByLocation(location) ?: return null
        return pylonData.owner
    }

    // ---------------------------
    // 파일런 설치
    // ---------------------------
    @EventHandler
    fun onBeaconPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        if (block.type != Material.BEACON) return

        val seaLevel = block.world.seaLevel
        if (block.y > seaLevel) {
            event.isCancelled = true
            event.player.sendMessage("§c파일런은 해수면 이하에만 설치할 수 있습니다.")
            return
        }

        val key = locationKey(block)
        val playerUUID = event.player.uniqueId
        val team = getOrCreateTeam(playerUUID)

        // ✅ 본인 팀이 파일런을 이미 가지고 있는 경우만 거리 제한 적용
        if (team.pylonLocations.isNotEmpty()) {
            var inRange = false
            for (pylonKey in team.pylonLocations) {
                val parts = pylonKey.split(",")
                if (parts.size != 4) continue
                val world = plugin.server.getWorld(parts[0]) ?: continue
                if (block.world != world) continue
                val x = parts[1].toDoubleOrNull() ?: continue
                val y = parts[2].toDoubleOrNull() ?: continue
                val z = parts[3].toDoubleOrNull() ?: continue

                val dx = block.x - x
                val dy = block.y - y
                val dz = block.z - z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)

                if (distance <= 125) {
                    inRange = true
                    break
                }
            }

            if (!inRange) {
                event.isCancelled = true
                event.player.sendMessage("§c새 파일런은 기존 팀 파일런으로부터 125블록 이내에 설치해야 합니다!")
                return
            }
        }

        // ---------------------------
        // 설치 가능 -> 파일런 추가
        // ---------------------------
        if (!team.pylonLocations.contains(key)) {
            team.pylonLocations.add(key)
        }
        saveTeams()

        plugin.server.consoleSender.sendMessage(
            "파일런 설치: ${event.player.name} -> 팀장 ${plugin.server.getOfflinePlayer(team.ownerUUID).name} -> $key"
        )
        event.player.sendMessage("§a파일런이 설치되었습니다!")
    }


    // ---------------------------
    // 파일런 파괴
// ---------------------------
    @EventHandler
    fun onBeaconBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.BEACON) return

        event.isDropItems = false  // drop 취소
        block.type = Material.AIR  // Beacon 블록 제거

        val key = locationKey(block)
        for ((_, team) in teams) {
            if (team.pylonLocations.contains(key)) {
                team.pylonLocations.remove(key)
                activeBeacons.remove(key)
                saveTeams()

                val owner = plugin.server.getOfflinePlayer(team.ownerUUID)
                if (owner.isOnline) {
                    owner.player?.sendMessage("${ChatColor.DARK_RED}팀 파일런이 파괴되었습니다!")
                }
                owner.name?.let { name ->
                    val discordId = discordVerify.getDiscordId(name)
                    if (discordId != null) {
                        discordVerify.sendPrivateMessage(discordId, "<@$discordId> §c팀 파일런이 파괴되었습니다!")
                    }
                }

                // Barrier 제거
                val parts = key.split(",")
                if (parts.size == 4) {
                    val world = plugin.server.getWorld(parts[0]) ?: continue
                    val x = parts[1].toIntOrNull() ?: continue
                    val y = parts[2].toIntOrNull() ?: continue
                    val z = parts[3].toIntOrNull() ?: continue

                    for (yy in (y + 1)..world.maxHeight) {
                        val barrierBlock = world.getBlockAt(x, yy, z)
                        if (barrierBlock.type != Material.AIR) {
                            barrierBlock.type = Material.AIR
                        }
                    }
                }

                break
            }
        }
    }


    // ---------------------------
    // 활성 파일런 체크
    // ---------------------------
    fun startBeaconChecker() {
        object : BukkitRunnable() {
            override fun run() {
                for ((_, team) in teams) {
                    for (key in team.pylonLocations) {
                        val parts = key.split(",")
                        if (parts.size != 4) continue

                        val world = plugin.server.getWorld(parts[0]) ?: continue
                        val x = parts[1].toIntOrNull() ?: continue
                        val y = parts[2].toIntOrNull() ?: continue
                        val z = parts[3].toIntOrNull() ?: continue

                        val block = world.getBlockAt(x, y, z)
                        if (block.type != Material.BEACON) continue

                        val beacon = block.state as? Beacon ?: continue
                        val isActive = beacon.tier > 0

                        val owner = plugin.server.getOfflinePlayer(team.ownerUUID)

                        if (isActive && key !in activeBeacons) {
                            if (owner.isOnline) owner.player?.sendMessage("팀 파일런이 활성화되었습니다!")
                            activeBeacons.add(key)

                            // Beacon 위부터 최대 높이까지 Barrier 설치
                            for (yy in (y + 1)..world.maxHeight) {
                                val barrierBlock = world.getBlockAt(x, yy, z)
                                if (barrierBlock.type != Material.BARRIER) {
                                    barrierBlock.type = Material.BARRIER
                                }
                            }
                        } else if (!isActive && key in activeBeacons) {
                            if (owner.isOnline) owner.player?.sendMessage("${ChatColor.DARK_RED}팀 파일런이 비활성화되었습니다!")
                            activeBeacons.remove(key)

                            // Barrier 제거
                            for (yy in (y + 1)..world.maxHeight) {
                                val barrierBlock = world.getBlockAt(x, yy, z)
                                if (barrierBlock.type != Material.AIR) {
                                    barrierBlock.type = Material.AIR
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L)
    }


    // ---------------------------
// 플레이어 근접 체크
// ---------------------------
    fun startProximityChecker() {
        object : BukkitRunnable() {
            override fun run() {
                val now = System.currentTimeMillis()
                val alertInterval = 5 * 60 * 1000

                for (player in plugin.server.onlinePlayers) {
                    for ((_, team) in teams) {
                        for (key in team.pylonLocations) {
                            if (!activeBeacons.contains(key)) continue
                            if (team.ownerUUID == player.uniqueId || team.memberUUIDs.contains(player.uniqueId)) continue

                            val parts = key.split(",")
                            if (parts.size != 4) continue
                            val world = plugin.server.getWorld(parts[0]) ?: continue
                            if (player.world != world) continue
                            val x = parts[1].toDoubleOrNull() ?: continue
                            val y = parts[2].toDoubleOrNull() ?: continue
                            val z = parts[3].toDoubleOrNull() ?: continue

                            val dx = player.location.x - x
                            val dy = player.location.y - y
                            val dz = player.location.z - z
                            val distance = sqrt(dx * dx + dy * dy + dz * dz)

                            val keyPair = player.uniqueId to key

                            if (distance <= 125) {
                                // 발광 효과 추가
                                player.addPotionEffect(
                                    PotionEffect(
                                        PotionEffectType.GLOWING, 100, 1, false, false, false
                                    )
                                )
                                player.addPotionEffect(
                                    PotionEffect(
                                        PotionEffectType.MINING_FATIGUE, 100, 1, false, false, true
                                    )
                                )
                                player.addPotionEffect(
                                    PotionEffect(
                                        PotionEffectType.SLOWNESS, 100, 1, false, false, true
                                    )
                                )
                                val lastAlert = proximityAlerts[keyPair] ?: 0L
                                if (now - lastAlert >= alertInterval) {
                                    val owner = plugin.server.getOfflinePlayer(team.ownerUUID)
                                    if (owner.isOnline) owner.player?.sendMessage("적이 팀 파일런 보호구역에 침입했습니다!")

                                    owner.name?.let { name ->
                                        val discordId = discordVerify.getDiscordId(name)
                                        if (discordId != null) {
                                            discordVerify.sendPrivateMessage(
                                                discordId, "<@$discordId> §c적이 팀 파일런 보호구역에 침입했습니다!"
                                            )
                                        }
                                    }

                                    proximityAlerts[keyPair] = now
                                }
                            } else {
                                // 영역 밖이면 발광 제거
                                if (player.hasPotionEffect(PotionEffectType.GLOWING)) {
                                    player.removePotionEffect(PotionEffectType.GLOWING)
                                }
                                proximityAlerts.remove(keyPair)
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L * 5)
    }

    // ---------------------------
// 모든 블록 설치 제한
// ---------------------------
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val nearbyOwnerUUID = findNearbyActiveTeamOwner(player, 125.0)

        // 보호구역 안이고, 자신의 팀 보호구역이 아닌 경우
        if (nearbyOwnerUUID != null) {
            val playerTeam = getOrCreateTeam(player.uniqueId)
            if (playerTeam.ownerUUID != nearbyOwnerUUID) {
                event.isCancelled = true
            }
        }
    }

    // ---------------------------
    // 모든 블록 파괴 제한
    // ---------------------------
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val nearbyOwnerUUID = findNearbyActiveTeamOwner(player, 125.0)

        if (nearbyOwnerUUID != null) {
            val playerTeam = getOrCreateTeam(player.uniqueId)
            if (playerTeam.ownerUUID != nearbyOwnerUUID) {
                event.isCancelled = true
            }
        }
    }


    // ---------------------------
    // 파일런 위치 키
    // ---------------------------
    private fun locationKey(block: org.bukkit.block.Block): String {
        val loc = block.location
        return "${loc.world?.name},${loc.blockX},${loc.blockY},${loc.blockZ}"
    }

    // ---------------------------
    // 팀 조회 또는 생성
    // ---------------------------
    fun getOrCreateTeam(playerUUID: UUID): Team {
        // 팀에 속한 경우 팀 반환
        for ((_, team) in teams) {
            if (team.ownerUUID == playerUUID || team.memberUUIDs.contains(playerUUID)) return team
        }
        // 없으면 새 팀 생성
        val newTeam = Team(playerUUID)
        teams[playerUUID] = newTeam
        saveTeams()
        return newTeam
    }

    // ---------------------------
    // 활성 파일런 여부 확인 (자신의 팀)
    // ---------------------------
    private fun hasActiveBeacons(playerUUID: UUID): Boolean {
        val team = getOrCreateTeam(playerUUID)
        return team.pylonLocations.any { activeBeacons.contains(it) }
    }

    // ---------------------------
    // 주변 활성 파일런 소유자 찾기
    // ---------------------------
    private fun findNearbyActiveTeamOwner(player: org.bukkit.entity.Player, range: Double = 125.0): UUID? {
        for ((_, team) in teams) {
            if (team.ownerUUID == player.uniqueId || team.memberUUIDs.contains(player.uniqueId)) continue

            for (key in team.pylonLocations) {
                if (!activeBeacons.contains(key)) continue

                val parts = key.split(",")
                if (parts.size != 4) continue
                val world = plugin.server.getWorld(parts[0]) ?: continue
                if (player.world != world) continue
                val x = parts[1].toDoubleOrNull() ?: continue
                val y = parts[2].toDoubleOrNull() ?: continue
                val z = parts[3].toDoubleOrNull() ?: continue
                val dx = player.location.x - x
                val dy = player.location.y - y
                val dz = player.location.z - z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)
                if (distance <= range) return team.ownerUUID
            }
        }
        return null
    }

    fun joinTeam(playerUUID: UUID, teamOwnerUUID: UUID) {
        //plugin.logger.info("=== joinTeam 호출 ===")
        //plugin.logger.info("플레이어: $playerUUID, 합류하려는 팀장: $teamOwnerUUID")

        val newTeam = teams[teamOwnerUUID]
        if (newTeam == null) {
        //    plugin.logger.info("합류할 팀을 찾을 수 없습니다. 함수 종료")
            return
        }
        //plugin.logger.info("합류할 팀 발견: 팀장 $teamOwnerUUID, 멤버 ${newTeam.memberUUIDs}")

        // 플레이어가 현재 속한 팀(victimTeam) 확인
        val victimTeam = teams.values.find { it.ownerUUID == playerUUID || it.memberUUIDs.contains(playerUUID) }
        //plugin.logger.info("플레이어 현재 팀: ${victimTeam?.ownerUUID}, 멤버 ${victimTeam?.memberUUIDs}")

        // 합류 불가 조건: 플레이어 자신의 팀에 활성 파일런이 하나라도 있으면 합류 불가
        val isAnyPylonActive = victimTeam?.pylonLocations?.any { activeBeacons.contains(it) } ?: false
        //plugin.logger.info("플레이어 팀 활성 파일런 존재 여부: $isAnyPylonActive")

        if (isAnyPylonActive) {
        //    plugin.logger.info("플레이어 자신의 팀에 활성 파일런이 있어 합류할 수 없습니다.")
            return
        }

        // 기존 팀 처리 (플레이어가 팀에 속해 있는 경우)
        if (victimTeam != null) {
            if (victimTeam.ownerUUID == playerUUID) {
                val oldPylons = victimTeam.pylonLocations.toList()
                victimTeam.pylonLocations.clear()
                teams.remove(playerUUID)
           //     plugin.logger.info("플레이어가 팀장인 경우 기존 팀 제거, 파일런 이동: $oldPylons")

                newTeam.pylonLocations.addAll(oldPylons)
                activeBeacons.addAll(oldPylons)
           //     plugin.logger.info("새 팀으로 파일런 이동 완료. 활성 파일런 반영: $activeBeacons")
            } else {
                victimTeam.memberUUIDs.remove(playerUUID)
          //      plugin.logger.info("플레이어가 기존 팀 멤버인 경우 팀에서 제거")
            }
        }

        // 새 팀에 합류
        if (!newTeam.memberUUIDs.contains(playerUUID)) {
            newTeam.memberUUIDs.add(playerUUID)
        //    plugin.logger.info("플레이어 새 팀에 합류 완료")
        } else {
      //      plugin.logger.info("플레이어 이미 새 팀에 포함되어 있음")
        }

        saveTeams()
     //   plugin.logger.info("팀 상태 저장 완료")

        // 플레이어에게 메시지 전송 (온라인일 경우)
        plugin.server.getPlayer(playerUUID)?.sendMessage(
            "당신은 ${plugin.server.getOfflinePlayer(teamOwnerUUID).name} 팀으로 합류했습니다!"
        )
     //   plugin.logger.info("플레이어에게 메시지 전송 완료")
    }




    fun isPylonActive(key: String): Boolean {
        return activeBeacons.contains(key)
    }


    // ✅ 위치로 파일런 찾기
    fun getPylonByLocation(location: org.bukkit.Location): PylonData? {
        val key = "${location.world?.name},${location.blockX},${location.blockY},${location.blockZ}"
        for ((_, team) in teams) {
            if (team.pylonLocations.contains(key)) {
                return PylonData(
                    owner = team.ownerUUID, location = location, member = team.memberUUIDs.toList() // 멤버 포함
                )
            }
        }
        return null
    }

}

data class PylonData(
    val owner: UUID,
    val location: org.bukkit.Location,
    val member: List<UUID> = listOf(),

)

