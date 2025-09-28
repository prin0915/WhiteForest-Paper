package org.prin.WhiteForest.team

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CoolDownManager {
    // 플레이어 UUID별 토템 감소 수치 저장
    private val reductionMap = mutableMapOf<UUID, Int>()
    private lateinit var plugin: JavaPlugin
    private lateinit var file: File
    private val gson = Gson()

    // 플러그인 시작 시 초기화
    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        file = File(plugin.dataFolder, "totemReduction.json")

        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        load()
    }

    // 토템 감소량 추가
    fun applyTotem(player: Player, reduction: Int) {
        val current = reductionMap[player.uniqueId] ?: 0
        reductionMap[player.uniqueId] = current + reduction
        save()
    }

    // 현재 감소량 가져오기
    fun getReduction(player: Player): Int {
        return reductionMap[player.uniqueId] ?: 0
    }

    // 특정 값으로 설정
    fun setReduction(player: Player, value: Int) {
        reductionMap[player.uniqueId] = value
        save()
    }

    // JSON 저장
    fun save() {
        val mapStringKey = reductionMap.mapKeys { it.key.toString() }
        file.writeText(gson.toJson(mapStringKey))
    }

    // JSON 불러오기
    private fun load() {
        if (!file.exists()) return
        val type = object : TypeToken<Map<String, Int>>() {}.type
        val data: Map<String, Int> = gson.fromJson(file.readText(), type)
        reductionMap.clear()
        data.forEach { (k, v) -> reductionMap[UUID.fromString(k)] = v }
    }
}
