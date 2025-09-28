package org.prin.WhiteForest

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin


class NBTMigrationListener(private val plugin: JavaPlugin) : Listener {

    private val oldNamespace = "magicench"
    private val newNamespace = "whiteForest"

    // 플레이어 접속 시 인벤토리/방어구/오프핸드/엔더체스트 마이그레이션
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        migratePlayer(player)
    }

    // 상자 등 인벤토리 열기 시 마이그레이션
    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val inv = event.inventory
        for (i in 0 until inv.size) {
            inv.setItem(i, migrateItemNBT(inv.getItem(i)))
        }
    }

    // 플레이어 전체 아이템 마이그레이션
    private fun migratePlayer(player: Player) {
        val inv = player.inventory
        for (i in 0 until inv.size) inv.setItem(i, migrateItemNBT(inv.getItem(i)))
        inv.armorContents = inv.armorContents.map { migrateItemNBT(it) }.toTypedArray()

        // offHand 수정
        inv.setItemInOffHand(migrateItemNBT(inv.itemInOffHand))

        player.enderChest.contents = player.enderChest.contents.map { migrateItemNBT(it) }.toTypedArray()
    }


    // 단일 아이템 마이그레이션
    private fun migrateItemNBT(item: ItemStack?): ItemStack? {
        if (item == null || !item.hasItemMeta()) return item
        val meta = item.itemMeta!!
        val pdc = meta.persistentDataContainer

        val keysToMigrate = pdc.keys.filter { it.namespace == oldNamespace }

        for (oldKey in keysToMigrate) {
            val newKey = NamespacedKey(newNamespace, oldKey.key)

            if (pdc.has(oldKey, PersistentDataType.INTEGER)) {
                pdc.get(oldKey, PersistentDataType.INTEGER)?.let { pdc.set(newKey, PersistentDataType.INTEGER, it) }
            } else if (pdc.has(oldKey, PersistentDataType.DOUBLE)) {
                pdc.get(oldKey, PersistentDataType.DOUBLE)?.let { pdc.set(newKey, PersistentDataType.DOUBLE, it) }
            } else if (pdc.has(oldKey, PersistentDataType.STRING)) {
                pdc.get(oldKey, PersistentDataType.STRING)?.let { pdc.set(newKey, PersistentDataType.STRING, it) }
            } else if (pdc.has(oldKey, PersistentDataType.BYTE)) {
                pdc.get(oldKey, PersistentDataType.BYTE)?.let { pdc.set(newKey, PersistentDataType.BYTE, it) }
            } else if (pdc.has(oldKey, PersistentDataType.LONG)) {
                pdc.get(oldKey, PersistentDataType.LONG)?.let { pdc.set(newKey, PersistentDataType.LONG, it) }
            } else if (pdc.has(oldKey, PersistentDataType.SHORT)) {
                pdc.get(oldKey, PersistentDataType.SHORT)?.let { pdc.set(newKey, PersistentDataType.SHORT, it) }
            } else if (pdc.has(oldKey, PersistentDataType.BYTE_ARRAY)) {
                pdc.get(oldKey, PersistentDataType.BYTE_ARRAY)?.let { pdc.set(newKey, PersistentDataType.BYTE_ARRAY, it) }
            } else if (pdc.has(oldKey, PersistentDataType.INTEGER_ARRAY)) {
                pdc.get(oldKey, PersistentDataType.INTEGER_ARRAY)?.let { pdc.set(newKey, PersistentDataType.INTEGER_ARRAY, it) }
            }

            pdc.remove(oldKey)
        }

        item.itemMeta = meta
        return item
    }


}
