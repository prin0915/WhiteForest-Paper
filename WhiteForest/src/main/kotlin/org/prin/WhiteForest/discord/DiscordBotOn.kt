package org.prin.WhiteForest.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.bukkit.plugin.java.JavaPlugin

class DiscordBotOn(private val plugin: JavaPlugin) : ListenerAdapter() {

    var jda: JDA? = null
        private set

    private val token: String
    private val registerChannelId: String

    // DiscordVerify 연결
    private var verifyHandler: DiscordVerify? = null

    fun setVerifyHandler(handler: DiscordVerify) {
        verifyHandler = handler
    }

    init {
        // plugins/<plugin> 폴더 확인
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()

        // discord.yml 로드
        val file = plugin.dataFolder.resolve("discord.yml")
        if (!file.exists()) plugin.saveResource("discord.yml", false)
        val cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)

        token = cfg.getString("discord.token")?.trim() ?: ""
        registerChannelId = cfg.getString("discord.channelId")?.trim() ?: ""

        plugin.logger.info("[DiscordBotOn] discord.yml channelId='$registerChannelId'")

        if (token.isBlank()) plugin.logger.warning("discord.yml: discord.token 이 비어있습니다.")
        if (registerChannelId.isBlank()) plugin.logger.warning("discord.yml: discord.channelId 이 비어있습니다.")
    }

    // -----------------------------
    // JDA 시작
    fun start() {
        if (token.isBlank()) return
        try {
            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setActivity(Activity.playing("마인크래프트"))
                .addEventListeners(this)
                .build()
                .awaitReady()  // <- JDA 완전 초기화까지 대기

            plugin.logger.info("[Discord] JDA started and ready.")
        } catch (e: Exception) {
            plugin.logger.warning("[Discord] JDA 시작 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    // JDA 종료
    fun stop() {
        jda?.shutdownNow()
        jda = null
        plugin.logger.info("[Discord] JDA stopped.")
    }

    // 채널에 메시지 보내기
    fun sendMessage(channelId: String, message: String) {
        jda?.getTextChannelById(channelId)?.sendMessage(message)?.queue()
    }

    // -----------------------------
    // 메시지 이벤트 처리
    override fun onMessageReceived(event: MessageReceivedEvent) {
        //println("[DEBUG] Message received from ${event.author.name} in channel ${event.channel.id}: ${event.message.contentRaw}")
        //println("[DEBUG] registerChannelId='$registerChannelId'")

        if (event.author.isBot) return

        // 다른 채널 무시
        if (registerChannelId.isNotBlank() && event.channel.id.trim() != registerChannelId.trim()) {
            return
        }

        val content = event.message.contentRaw.trim()
        val args = content.split(Regex("\\s+"))

        if (args.isEmpty()) return

        when (args[0].trim()) {
            "!등록" -> {
                //println("[DEBUG] !등록 명령 감지")
                val mcName = args.getOrNull(1) ?: return
                val displayName = args.getOrNull(2) ?: mcName
                if (verifyHandler == null) {
                    //println("[DEBUG] verifyHandler가 null임! 등록 처리 불가")
                    return
                }
                verifyHandler?.handleRegisterCommand(event.author.id, mcName, displayName)
            }
            "!등록해제" -> {
                //("[DEBUG] !등록해제 명령 감지")
                val mcName = args.getOrNull(1) ?: return
                verifyHandler?.removeRegistration(mcName)
            }
        }
    }
}
