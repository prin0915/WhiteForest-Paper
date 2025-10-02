package org.prin.WhiteForest.drop

import org.bukkit.Material
import org.bukkit.entity.IronGolem
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

class NetherFirePotionDrop(private val plugin: JavaPlugin) : Listener {

    private val dropChance = 40 // 40% 확률

    @EventHandler
    fun onIronGolemDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity is IronGolem) {
            if (Random.nextInt(100) < dropChance) {
                val potion = ItemStack(Material.POTION, 1)
                val meta = potion.itemMeta as PotionMeta

                // 1시간 지속 (20 ticks * 60초 * 60분)
                val durationTicks = 20 * 60 * 60
                meta.addCustomEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, durationTicks, 0), true)
                meta.setDisplayName("§6흑요석 포션")

                potion.itemMeta = meta
                event.drops.add(potion)
            }
        }
    }
}
