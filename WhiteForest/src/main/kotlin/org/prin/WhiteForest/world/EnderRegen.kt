package org.prin.WhiteForest.world

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EnderDragon
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.prin.WhiteForest.WhiteForest
import org.prin.WhiteForest.team.Pylon
import kotlin.random.Random

class EnderRegen(
    private val plugin: JavaPlugin, private val pylon: Pylon
) : Listener {

    // Stardust 생성
    private fun createStardust(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta!!
        meta.setDisplayName("§f별가루")
        meta.lore = listOf("§7신비로운 힘이 담겨있다.")
        meta.setCustomModelData(1)
        meta.persistentDataContainer.set(plugin.getNamespacedKey("stardust"), PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    // Starshard 생성
    private fun createStarshard(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta!!
        meta.setDisplayName("§f별조각")
        meta.lore = listOf("§7신비로운 힘이 담겨있다.")
        meta.setCustomModelData(2)
        meta.persistentDataContainer.set(plugin.getNamespacedKey("starshard"), PersistentDataType.BYTE, 2)
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onDragonDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity is EnderDragon) {
            val plugin = JavaPlugin.getPlugin(WhiteForest::class.java)
            plugin.enderDragonDefeated = true

            val endWorld = entity.world
            if (endWorld.environment == World.Environment.THE_END) {

                // 1️⃣ 엔더월드 플레이어에게 즉시 알림
                endWorld.players.forEach { p ->
                    p.sendMessage("§c엔더 드래곤이 격파되었습니다! 엔드 차원이 곧 무너집니다!")
                }
                //1.1 엔더 월드 밖 사람은
                Bukkit.getOnlinePlayers()
                    .filter { it.world != endWorld } // 엔더월드 제외
                    .forEach { p ->
                        p.sendMessage("§a엔더 드래곤이 격파되었습니다!")
                    }

                // 2️⃣ 115초 뒤부터 카운트다운 시작
                for (i in 5 downTo 1) {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        endWorld.players.forEach { p ->
                            p.sendMessage("§c$i 초 뒤 엔드 차원이 붕괴 됩니다!")
                        }
                    }, 20L * (115 + (5 - i))) // 115초 + (5-i)
                }

                // 3️⃣ 120초 뒤 실제 추방
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    for (player in endWorld.players) {
                        val team = pylon.getOrCreateTeam(player.uniqueId)

                        val activePylonLocations =
                            team.pylonLocations.filter { key -> pylon.activeBeacons.contains(key) }.mapNotNull { key ->
                                    val parts = key.split(",")
                                    if (parts.size != 4) return@mapNotNull null
                                    val world = plugin.server.getWorld(parts[0]) ?: return@mapNotNull null
                                    val x = parts[1].toIntOrNull() ?: return@mapNotNull null
                                    val y = parts[2].toIntOrNull() ?: return@mapNotNull null
                                    val z = parts[3].toIntOrNull() ?: return@mapNotNull null
                                    Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                                }

                        val targetLocation = if (activePylonLocations.isNotEmpty()) {
                            activePylonLocations.random()
                        } else {
                            val world = Bukkit.getWorld("WhiteForest")!!
                            val radius = plugin.config.getInt("respawn.random-radius", 30000)
                            val x = Random.nextInt(-radius, radius).toDouble()
                            val z = Random.nextInt(-radius, radius).toDouble()
                            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble() + 1
                            Location(world, x, y, z)
                        }

                        player.teleport(targetLocation)

                    }

                    endWorldGenerator()

                }, 20L * 120)

                // 4️⃣ Stardust & Starshard 80개 랜덤 드롭
                // 4️⃣ Stardust, Starshard & 희귀 Shulker Box 랜덤 드롭
                repeat(200) {
                    // 색상별 셜커 + 매칭 글자 색상
                    val shulkerColors = mapOf(
                        Material.WHITE_SHULKER_BOX to 'f',
                        Material.ORANGE_SHULKER_BOX to '6',
                        Material.MAGENTA_SHULKER_BOX to 'd',
                        Material.LIGHT_BLUE_SHULKER_BOX to 'b',
                        Material.YELLOW_SHULKER_BOX to 'e',
                        Material.LIME_SHULKER_BOX to 'a',
                        Material.PINK_SHULKER_BOX to 'd',
                        Material.GRAY_SHULKER_BOX to '7',
                        Material.LIGHT_GRAY_SHULKER_BOX to '7',
                        Material.CYAN_SHULKER_BOX to 'b',
                        Material.PURPLE_SHULKER_BOX to '5',
                        Material.BLUE_SHULKER_BOX to '9',
                        Material.BROWN_SHULKER_BOX to '6',
                        Material.GREEN_SHULKER_BOX to '2',
                        Material.RED_SHULKER_BOX to 'c',
                        Material.BLACK_SHULKER_BOX to '0'
                    )

                    // 드롭 아이템 생성

                    val dropItem = if (Random.nextInt(200) < 2) { // 1% 확률
                        val material = shulkerColors.keys.random()
                        val colorCode = shulkerColors[material] ?: 'f'
                        val shulker = ItemStack(material)
                        val meta = shulker.itemMeta!!
                        meta.setDisplayName("§${colorCode}희귀 셜커 상자")
                        shulker.itemMeta = meta
                        shulker
                    } else if (Random.nextBoolean()) {
                        createStardust()
                    } else {
                        createStarshard()
                    }

                    val dropLoc = Location(endWorld, 5.0, 150.0, 5.0)
                    endWorld.dropItemNaturally(dropLoc, dropItem)
                }


            }
        }
    }

    fun endWorldGenerator() {
        val endWorldName = "world_the_end"
        val endWorld = Bukkit.getWorld(endWorldName)

        if (endWorld != null) {
            Bukkit.unloadWorld(endWorld, false) // 월드 언로드
            val worldFolder = endWorld.worldFolder
            worldFolder.deleteRecursively()       // 월드 폴더 삭제
        }

        // 새 엔더월드 생성
        val newEndWorld = org.bukkit.WorldCreator(endWorldName).environment(World.Environment.THE_END)
            .type(org.bukkit.WorldType.NORMAL).createWorld()

        newEndWorld?.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true)

    }

    @EventHandler
    fun onDragonSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        if (entity is EnderDragon && entity.world.environment == World.Environment.THE_END) {
            val maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH)
            if (maxHealthAttr != null) {
                maxHealthAttr.baseValue = 600.0   // 최대 체력 600으로 변경
                entity.health = 600.0             // 현재 체력도 600으로 맞춤
            }
            plugin.logger.info("엔더 드래곤 체력이 800으로 설정되었습니다!")
        }
    }
}

// 확장함수: NamespacedKey 간단 생성
fun JavaPlugin.getNamespacedKey(key: String) = org.bukkit.NamespacedKey(this, key)
