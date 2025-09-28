package org.prin.WhiteForest.abillity

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.plugin.java.JavaPlugin

class TargetInvincibilityRemoval(private val plugin: JavaPlugin) : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onPlayerAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return

        // 공격자가 10강 달성 무기를 들고 있는지 확인
        val weapon = attacker.inventory.itemInMainHand
        val forceLevel = getForceLevel(weapon)

        target.noDamageTicks = 0
    }

    private fun getForceLevel(item: org.bukkit.inventory.ItemStack): Int {
        val meta = item.itemMeta ?: return 0
        val key = org.bukkit.NamespacedKey(plugin, "force_level")
        return meta.persistentDataContainer.get(key, org.bukkit.persistence.PersistentDataType.INTEGER) ?: 0
    }
}
