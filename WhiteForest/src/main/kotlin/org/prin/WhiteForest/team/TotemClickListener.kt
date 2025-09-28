package org.prin.WhiteForest.team

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class TotemClickListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onTotemClick(event: PlayerInteractEvent) {
        val player = event.player
        val item: ItemStack = event.item ?: return
        if (item.type != Material.TOTEM_OF_UNDYING) return

        val reduction = item.itemMeta?.persistentDataContainer?.get(
            NamespacedKey(plugin, "reduction"), PersistentDataType.INTEGER
        )
        if (reduction != null){
            // 적용
            CoolDownManager.applyTotem(player, reduction)
            player.sendMessage("§a토템 효과가 적용되었습니다! 소생시간 ${reduction}초 감소")

            // 인벤토리에서 1개 소모
            item.amount = item.amount - 1
            if (item.amount <= 0) player.inventory.remove(item)
        }

    }
}

