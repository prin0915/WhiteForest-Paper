package org.prin.WhiteForest

import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.prin.WhiteForest.abillity.BootsDoubleJump
import org.prin.WhiteForest.abillity.TargetInvincibilityRemoval
import org.prin.WhiteForest.abillity.TridentMultiShot
import org.prin.WhiteForest.discord.DiscordBotOn
import org.prin.WhiteForest.discord.DiscordVerify
import org.prin.WhiteForest.drop.AmethystDrop
import org.prin.WhiteForest.drop.BlazePowderDrop
import org.prin.WhiteForest.drop.NetherFirePotionDrop
import org.prin.WhiteForest.recipes.StardustRecipe
import org.prin.WhiteForest.recipes.StarshardRecipe
import org.prin.WhiteForest.team.*
import org.prin.WhiteForest.ui.MagicEnchUI
import org.prin.WhiteForest.ui.MagicForceUI
import org.prin.WhiteForest.world.EndPortalEnterTimeLimit
import org.prin.WhiteForest.world.EndPortalNotifier
import org.prin.WhiteForest.world.EnderRegen
import org.prin.WhiteForest.world.NetherFire
import org.prin.WhiteForest.world.NetherPortal
import java.util.*

private lateinit var pylon: Pylon

class WhiteForest : JavaPlugin() {

    lateinit var discordBot: DiscordBotOn
        private set
    lateinit var discordVerify: DiscordVerify
        private set

    lateinit var pylon: Pylon
        private set

    lateinit var death: Death
        private set

    var enderDragonDefeated = false

    override fun onEnable() {
        saveDefaultConfig()
        CoolDownManager.init(this)

        // 개인 네더 미리 로드
        Bukkit.getOnlinePlayers().forEach { loadPlayerNether(it.uniqueId) }
        loadAllPlayerNethers(this)

        // Discord 통합
        discordBot = DiscordBotOn(this)
        discordBot.start()
        logger.info("DiscordBotOn 시작됨")

        discordVerify = DiscordVerify(this, discordBot)
        server.pluginManager.registerEvents(discordVerify, this)
        logger.info("DiscordVerify 이벤트 등록됨")

        // ---------------- Pylon 시스템 초기화 ----------------
        pylon = Pylon(this, discordVerify)
        death = Death(this, discordVerify, pylon)
        server.pluginManager.registerEvents(pylon, this)
        logger.info("Pylon 이벤트 등록됨")
        server.pluginManager.registerEvents(PylonUI(this, pylon, death), this)
        logger.info("PylonUI 이벤트 등록됨")
        pylon.startBeaconChecker()
        logger.info("BeaconChecker 시작됨")
        pylon.startProximityChecker()
        logger.info("ProximityChecker 시작됨")

        // ---------------- Death 이벤트 ----------------
        // 이제 pylon이 초기화되었으므로 안전하게 넘겨줄 수 있음
        server.pluginManager.registerEvents(death, this)
        logger.info("Death 이벤트 등록됨")
        server.pluginManager.registerEvents(TotemClickListener(this), this)
        logger.info("부활시간 쿨다운 토템 클릭 리스너 이벤트 등록됨")

        logger.info("baseCooldown = ${config.getInt("respawn.base-cooldown")}")
        logger.info("additionalPerDeath = ${config.getInt("respawn.additional-per-death")}")


        // ---------------- 나머지 이벤트 ----------------
        server.pluginManager.registerEvents(OnJoin(discordVerify, this), this)
        logger.info("OnJoin 이벤트 등록됨")

        server.pluginManager.registerEvents(MagicEnchUI(this), this)
        logger.info("MagicEnchUI 이벤트 등록됨")
        server.pluginManager.registerEvents(MagicForceUI(this), this)
        logger.info("MagicForceUI 이벤트 등록됨")

        server.pluginManager.registerEvents(AmethystDrop(), this)
        logger.info("AmethystDrop 이벤트 등록됨")
        server.pluginManager.registerEvents(BlazePowderDrop(), this)
        logger.info("BlazePowderDrop 이벤트 등록됨")
        server.pluginManager.registerEvents(NetherFirePotionDrop(this), this)
        logger.info("NetherFirePotionDrop 이벤트 등록됨")

        server.pluginManager.registerEvents(BootsDoubleJump(this), this)
        logger.info("BootsDoubleJump 이벤트 등록됨")
        server.pluginManager.registerEvents(TridentMultiShot(this), this)
        logger.info("TridentMultiShot 이벤트 등록됨")
        server.pluginManager.registerEvents(TargetInvincibilityRemoval(this),this)
        logger.info("TargetInvicibilityRemoval 이벤트 등록됨")

        server.pluginManager.registerEvents(NetherPortal(pylon), this)
        logger.info("NetherPortal 이벤트 등록됨")
        NetherFire(this).start()
        logger.info("NetherFire 시작됨")

        // ---------------- world 이벤트 ----------------
        val enderregen = EnderRegen(this,pylon)
        server.pluginManager.registerEvents(enderregen, this)
        logger.info("DragonKillListener 이벤트 등록됨")
        server.pluginManager.registerEvents(EndPortalEnterTimeLimit(this), this)

        server.pluginManager.registerEvents(EndPortalNotifier(this),this)

        StardustRecipe(this).register()
        logger.info("StardustRecipe 등록됨")
        StarshardRecipe(this).register()
        logger.info("StarshardRecipe 등록됨")

        logger.info("MagicEnch 활성화됨!")



        val notifier = EndPortalNotifier(this)
        notifier.start()

        val endWorldName = "world_the_end"
        val endWorld = Bukkit.getWorld(endWorldName)

        if (endWorld == null) {
            enderregen.endWorldGenerator()
        }

    }

    override fun onDisable() {
        if (::pylon.isInitialized) {
            pylon.saveTeams()
        }
        if (::discordBot.isInitialized) {
            discordBot.stop()
        }
        logger.info("MagicEnch 비활성화됨!")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val lastWorld = player.location.world ?: return

        // 마지막 위치가 네더라면 개인 네더로 이동
        if (lastWorld.environment == World.Environment.NETHER) {
            val netherWorld = getOrCreateNether(player)
            val newLoc = player.location.clone()
            newLoc.world = netherWorld
            player.teleport(newLoc)
        }
    }

    private fun getOrCreateNether(player: Player): World? {
        val worldName = "nether_${player.uniqueId}"
        var netherWorld = Bukkit.getWorld(worldName)
        if (netherWorld == null) {
            netherWorld = WorldCreator(worldName)
                .environment(World.Environment.NETHER)
                .type(WorldType.NORMAL)
                .createWorld()
            netherWorld?.setGameRule(GameRule.KEEP_INVENTORY, true)
        }
        return netherWorld
    }

    private fun loadPlayerNether(playerUUID: UUID) {
        val worldName = "nether_$playerUUID"
        if (Bukkit.getWorld(worldName) == null) {
            val netherWorld = WorldCreator(worldName)
                .environment(World.Environment.NETHER)
                .type(WorldType.NORMAL)
                .createWorld()
            netherWorld?.setGameRule(GameRule.KEEP_INVENTORY, true)
        }
    }

    fun loadAllPlayerNethers(plugin: JavaPlugin) {
        val worldRoot = plugin.server.worldContainer  // 서버 루트
        worldRoot.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.startsWith("nether_")) {
                val worldName = file.name
                if (Bukkit.getWorld(worldName) == null) {
                    val world = WorldCreator(worldName)
                        .environment(World.Environment.NETHER)
                        .type(WorldType.NORMAL)
                        .createWorld()
                    world?.setGameRule(GameRule.KEEP_INVENTORY, true)
                    plugin.logger.info("Loaded personal nether: $worldName")
                }
            }
        }
    }

}
