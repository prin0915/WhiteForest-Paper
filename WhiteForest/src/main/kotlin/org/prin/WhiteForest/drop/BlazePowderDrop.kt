package org.prin.WhiteForest.drop

import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack

class BlazePowderDrop : Listener {

    @EventHandler
    fun onMobDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer ?: return

        // 블레이즈만 해당
        if (entity.type != EntityType.BLAZE) return

        val roll = (1..100).random()
        val probability = 40 // 10% 확률

        if (roll <= probability) {
            val drop = ItemStack(Material.BLAZE_ROD, (1..3).random())
            event.drops.add(drop)
        }
    }
}