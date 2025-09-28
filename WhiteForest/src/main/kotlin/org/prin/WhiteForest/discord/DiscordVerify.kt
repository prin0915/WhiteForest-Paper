package org.prin.WhiteForest.discord

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.EmbedBuilder
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.EnumSet
import java.awt.Color

class DiscordVerify(private val plugin: JavaPlugin, private val discordBot: DiscordBotOn) : Listener {

    private val registeredPlayers = mutableMapOf<String, String>() // mcName(lowercase) -> displayName
    private val discordIdMap = mutableMapOf<String, String>() // discordId -> mcName(lowercase)
    private val dataFile: File
    private val discordFile: File
    private val discordConfig: YamlConfiguration

    init {
        // plugins/<plugin_name> 폴더 존재 확인
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        // playerRegistration.yml 경로
        dataFile = File(plugin.dataFolder, "playerRegistration.yml")
        if (!dataFile.exists()) {
            dataFile.createNewFile()
            plugin.logger.info("[DiscordVerify] playerRegistration.yml 파일 생성 완료")
        }

        // discord.yml 경로
        discordFile = File(plugin.dataFolder, "discord.yml")
        if (!discordFile.exists()) {
            plugin.saveResource("discord.yml", false)
            plugin.logger.info("[DiscordVerify] discord.yml 생성 완료")
        }
        discordConfig = YamlConfiguration.loadConfiguration(discordFile)

        // DiscordBot에 자신을 등록
        discordBot.setVerifyHandler(this)
        loadData()
    }

    private fun saveData() {
        val config = YamlConfiguration()
        registeredPlayers.forEach { (mcName, displayName) ->
            config.set("players.$mcName.displayName", displayName)
            val discordId = discordIdMap.entries.find { it.value == mcName }?.key
            if (discordId != null) config.set("players.$mcName.discordId", discordId)
        }
        config.save(dataFile)
    }

    private fun loadData() {
        if (!dataFile.exists()) return
        val config = YamlConfiguration.loadConfiguration(dataFile)
        val players = config.getConfigurationSection("players") ?: return
        for (mcName in players.getKeys(false)) {
            val displayName = config.getString("players.$mcName.displayName") ?: mcName
            val discordId = config.getString("players.$mcName.discordId") ?: ""
            registeredPlayers[mcName] = displayName
            if (discordId.isNotBlank()) discordIdMap[discordId] = mcName
        }
    }

    fun handleRegisterCommand(discordId: String, mcName: String, displayName: String) {
        val mcKey = mcName.lowercase()
        registeredPlayers[mcKey] = displayName
        discordIdMap[discordId] = mcKey
        saveData() // 저장

        plugin.logger.info("[DiscordRegister] 디스코드 ID $discordId -> MC닉네임 $mcName, 표시닉네임 $displayName 등록됨.")

        Bukkit.getPlayerExact(mcName)?.let { player ->
            applyDisplayName(player)
            player.sendMessage("§a[인증 완료] 닉네임이 `$displayName`로 등록되었습니다!")
        }

        // 임베드 전송
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val jda = discordBot.jda ?: run {
                plugin.logger.warning("임베드 전송 실패: JDA가 아직 초기화되지 않음")
                return@Runnable
            }

            val guild = jda.guilds.firstOrNull() ?: run {
                plugin.logger.warning("임베드 전송 실패: 길드 정보를 가져올 수 없음")
                return@Runnable
            }

            val channelId = discordConfig.getString("discord.channelId")?.trim() ?: ""
            if (channelId.isBlank()) {
                plugin.logger.warning("임베드 전송 실패: discord.yml에서 channelId를 읽을 수 없음")
                return@Runnable
            }

            val channel = guild.textChannels.find { it.id == channelId } ?: run {
                plugin.logger.warning("임베드 전송 실패: 채널 ID $channelId 를 찾을 수 없음")
                return@Runnable
            }

            sendRegisterEmbed(channel, discordId, mcName, displayName)
        })

        createPrivateChannelForUser(discordId, displayName)
    }

    private fun sendRegisterEmbed(channel: TextChannel, discordId: String, mcName: String, displayName: String) {
        val mcUUID = Bukkit.getOfflinePlayer(mcName).uniqueId.toString()
        val mcFaceUrl = "https://mc-heads.net/head/$mcUUID.png"
        val discordTag = discordBot.jda?.getUserById(discordId)?.asTag ?: "알 수 없음"

        val embed =
            EmbedBuilder().setTitle("디스코드 연결 성공 ✅").setColor(Color(0, 255, 0)).addField("디스코드", discordTag, false)
                .addField("마인크래프트", "$mcName ($mcUUID)", false).addField("닉네임", displayName, false).setImage(mcFaceUrl)
                .build()

        channel.sendMessageEmbeds(embed).queue(
            { plugin.logger.info("임베드 전송 성공") },
            { error -> plugin.logger.warning("임베드 전송 실패: ${error.message}") })
    }

    fun isRegistered(mcName: String): Boolean = registeredPlayers.containsKey(mcName.lowercase())

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!isRegistered(player.name)) {
            player.kickPlayer("§c연결 가능한 아바타가 없습니다.")
        } else {
            applyDisplayName(player)
        }
    }

    fun applyDisplayName(player: org.bukkit.entity.Player) {
        val displayName = registeredPlayers[player.name.lowercase()] ?: player.name
        player.setDisplayName(displayName)
        player.setPlayerListName(displayName)
    }

    fun createPrivateChannelForUser(discordId: String, displayName: String) {
        val guild = discordBot.jda?.guilds?.firstOrNull() ?: run {
            plugin.logger.warning("[DiscordRegister] 길드 정보를 가져올 수 없습니다.")
            return
        }

        guild.retrieveMemberById(discordId).queue({ member ->
            val channelName = "private-${displayName.lowercase()}"

            if (guild.textChannels.any { it.name.equals(channelName, ignoreCase = true) }) {
                plugin.logger.info("[DiscordRegister] 채널 $channelName 이미 존재")
                return@queue
            }

            guild.createTextChannel(channelName)
                .addPermissionOverride(guild.publicRole, null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(member, EnumSet.of(Permission.VIEW_CHANNEL), null).queue({ channel ->
                    channel.sendMessage("환영합니다, ${member.effectiveName}! 여기는 당신의 개인 채널입니다.").queue()
                    plugin.logger.info("[DiscordRegister] 개인 채널 $channelName 생성 완료.")
                }, { error ->
                    plugin.logger.warning("[DiscordRegister] 채널 생성 실패: ${error.message}")
                })
        }, { error ->
            plugin.logger.warning("[DiscordRegister] 멤버 조회 실패: ${error.message}")
        })
    }

    fun removeRegistration(mcName: String) {
        val mcKey = mcName.lowercase()
        val discordId = discordIdMap.entries.find { it.value == mcKey }?.key

        if (discordId != null) {
            val guild = discordBot.jda?.guilds?.firstOrNull() ?: null
            if (guild != null) {
                val displayName = registeredPlayers[mcKey] ?: mcKey
                val channelName = "private-${displayName.lowercase()}"
                val channel = guild.textChannels.find { it.name.equals(channelName, ignoreCase = true) }
                channel?.delete()?.queue()
            }
        }

        registeredPlayers.remove(mcKey)
        if (discordId != null) discordIdMap.remove(discordId)
        saveData()
    }

    fun getDiscordId(mcName: String): String? = discordIdMap.entries.find { it.value == mcName.lowercase() }?.key

    fun sendPrivateMessage(discordId: String, message: String) {
        val guild = discordBot.jda?.guilds?.firstOrNull() ?: run {
            plugin.logger.warning("sendPrivateMessage: 길드 정보 없음")
            return
        }

        val mcKey = discordIdMap[discordId] ?: return
        val displayName = registeredPlayers[mcKey] ?: mcKey

        val channelName = "private-${displayName.lowercase()}"
        val channel = guild.textChannels.find { it.name.equals(channelName, ignoreCase = true) }

        if (channel == null) {
            plugin.logger.warning("sendPrivateMessage: 채널 $channelName 존재하지 않음")
            return
        }

        channel.sendMessage(message).queue(
            { plugin.logger.info("sendPrivateMessage: 메시지 전송 성공 to $channelName") },
            { error -> plugin.logger.warning("sendPrivateMessage 실패: ${error.message}") })
    }

    fun getDisplayName(mcName: String): String? {
        return registeredPlayers[mcName.lowercase()]
    }


}

