package org.prin.WhiteForest.drop

import org.bukkit.Material
import org.bukkit.entity.Monster
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack

class AmethystDrop : Listener {

    @EventHandler
    fun onMobDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer ?: return // 플레이어가 처치한 경우만

        // 적대적 몹만 (Monster 인터페이스를 구현하는 애들)
        if (entity !is Monster) return

        // 확률
        val random = (1..100).random()
        val probability = 35 // n%

        if (random <= probability) {
            // 드롭 아이템 추가
            val drop = ItemStack(Material.AMETHYST_SHARD, (1..3).random()) // 1~3개 랜덤
            event.drops.add(drop)
        }
    }
}
