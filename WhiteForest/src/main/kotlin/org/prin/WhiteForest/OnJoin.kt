package org.prin.WhiteForest

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.prin.WhiteForest.discord.DiscordVerify

import java.io.File

class OnJoin(
    private val discordVerify: DiscordVerify,
    private val plugin: JavaPlugin
) : Listener {

    private val firstJoinFile: File = File(plugin.dataFolder, "firstJoin.yml")
    private val firstJoinConfig: YamlConfiguration

    init {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        if (!firstJoinFile.exists()) firstJoinFile.createNewFile()
        firstJoinConfig = YamlConfiguration.loadConfiguration(firstJoinFile)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()

        // 디스코드 등록 체크
        if (!discordVerify.isRegistered(player.name)) {
            player.kickPlayer("§c연결 가능한 아바타가 없습니다.")
            return
        } else {
            discordVerify.applyDisplayName(player)
        }

        // 등록 후 첫 접속인지 확인 (UUID 기준)
        if (!firstJoinConfig.getBoolean(uuid, false)) {
            // 랜덤 스폰
            val spawnLocation = getRandomSpawnLocation()
            player.teleport(spawnLocation)

            // 파일런 지급
            val pylonItem = ItemStack(Material.BEACON)
            val meta: ItemMeta = pylonItem.itemMeta!!
            meta.setDisplayName("§b파일런")
            pylonItem.itemMeta = meta
            player.inventory.addItem(pylonItem)

            // YML 기록
            firstJoinConfig.set(uuid, true)
            firstJoinConfig.save(firstJoinFile)
        }

        event.joinMessage = null
        plugin.server.broadcastMessage("§e${player.name} 님이 아바타에 접속하셨습니다!")
    }

    // 랜덤 스폰 생성
    private fun getRandomSpawnLocation(): Location {
        val world = Bukkit.getWorld("WhiteForest")!!
        val radius = plugin.config.getInt("respawn.random-radius", 500)
        var loc: Location
        do {
            val x = (-radius..radius).random().toDouble()
            val z = (-radius..radius).random().toDouble()
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble() + 1
            loc = Location(world, x, y, z)
        } while (!isLocationSafe(loc))
        return loc
    }

    // 주변 플레이어와 겹치지 않도록 체크
    private fun isLocationSafe(loc: Location): Boolean {
        val radius = 300.0
        val nearby = loc.world!!.getNearbyEntities(loc, radius, radius, radius)
        return nearby.none { it is Player }
    }

}
