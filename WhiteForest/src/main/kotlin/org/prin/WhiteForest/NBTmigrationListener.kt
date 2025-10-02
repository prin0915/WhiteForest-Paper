package org.prin.WhiteForest

import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID

class NBTMigrationListener(private val plugin: JavaPlugin) : Listener {

    private val oldNamespace = "magicench"
    private val newNamespace = "whiteforest"

    // 플레이어 접속 시 마이그레이션
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        migratePlayer(event.player)
    }

    // 인벤토리 열 때마다 자동 마이그레이션
    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val inv = event.inventory
        for (i in 0 until inv.size) {
            inv.setItem(i, migrateItem(inv.getItem(i)))
        }

        // 블록 인벤토리면 내부 아이템도 마이그레이션
        val holder = inv.holder
        if (holder is BlockInventoryHolder) {
            migrateBlockInventory(holder.block)
        }
    }

    // 플레이어 전체 아이템 마이그레이션
    private fun migratePlayer(player: Player) {
        val inv = player.inventory

        for (i in 0 until inv.size) inv.setItem(i, migrateItem(inv.getItem(i)))
        inv.armorContents = inv.armorContents.map { migrateItem(it) }.toTypedArray()
        inv.setItemInOffHand(migrateItem(inv.itemInOffHand))
        player.enderChest.contents = player.enderChest.contents.map { migrateItem(it) }.toTypedArray()
    }

    // 단일 아이템 마이그레이션
    private fun migrateItem(item: ItemStack?): ItemStack? {
        if (item == null || !item.hasItemMeta()) return item
        val meta = item.itemMeta!!
        val pdc = meta.persistentDataContainer

        // 1️⃣ PDC 마이그레이션
        val keysToMigrate = pdc.keys.filter { it.namespace == oldNamespace }
        for (oldKey in keysToMigrate) {
            val newKey = NamespacedKey(newNamespace, oldKey.key)
            when {
                pdc.has(oldKey, PersistentDataType.INTEGER) ->
                    pdc.get(oldKey, PersistentDataType.INTEGER)?.let { pdc.set(newKey, PersistentDataType.INTEGER, it) }
                pdc.has(oldKey, PersistentDataType.DOUBLE) ->
                    pdc.get(oldKey, PersistentDataType.DOUBLE)?.let { pdc.set(newKey, PersistentDataType.DOUBLE, it) }
                pdc.has(oldKey, PersistentDataType.LONG) ->
                    pdc.get(oldKey, PersistentDataType.LONG)?.let { pdc.set(newKey, PersistentDataType.LONG, it) }
                pdc.has(oldKey, PersistentDataType.SHORT) ->
                    pdc.get(oldKey, PersistentDataType.SHORT)?.let { pdc.set(newKey, PersistentDataType.SHORT, it) }
                pdc.has(oldKey, PersistentDataType.BYTE) ->
                    pdc.get(oldKey, PersistentDataType.BYTE)?.let { pdc.set(newKey, PersistentDataType.BYTE, it) }
                pdc.has(oldKey, PersistentDataType.STRING) ->
                    pdc.get(oldKey, PersistentDataType.STRING)?.let {
                        val newValue = it.replace("$oldNamespace:", "$newNamespace:")
                        pdc.set(newKey, PersistentDataType.STRING, newValue)
                    }
                pdc.has(oldKey, PersistentDataType.BYTE_ARRAY) ->
                    pdc.get(oldKey, PersistentDataType.BYTE_ARRAY)?.let { pdc.set(newKey, PersistentDataType.BYTE_ARRAY, it) }
                pdc.has(oldKey, PersistentDataType.INTEGER_ARRAY) ->
                    pdc.get(oldKey, PersistentDataType.INTEGER_ARRAY)?.let { pdc.set(newKey, PersistentDataType.INTEGER_ARRAY, it) }
            }
            pdc.remove(oldKey)
        }

        // 2️⃣ Lore / DisplayName 치환
        meta.lore = meta.lore?.map { it.replace("$oldNamespace:", "$newNamespace:") }
        meta.setDisplayName(meta.displayName?.replace("$oldNamespace:", "$newNamespace:"))

        // 3️⃣ Attribute 마이그레이션 (중복 제거 후 재추가)
        val attrChanged = migrateAttributes(meta)
        if (attrChanged) {
            plugin.logger.info("[NBTMigration] 어트리뷰트 치환 발생: ${item.type}")
        }

        // 반드시 itemMeta 반영
        item.itemMeta = meta

        // 4️⃣ Shulker Box 내부 아이템 재귀 마이그레이션
        val blockMeta = item.itemMeta
        if (blockMeta is org.bukkit.inventory.meta.BlockStateMeta) {
            val blockState = blockMeta.blockState
            if (blockState is ShulkerBox) {
                val contents = blockState.inventory.contents
                for (i in contents.indices) {
                    contents[i] = migrateItem(contents[i])
                }
                blockState.inventory.contents = contents
                blockState.update(true, true)
                blockMeta.blockState = blockState
                item.itemMeta = blockMeta
            }
        }

        return item
    }

    // 어트리뷰트 마이그레이션: magicench -> whiteforest (중복 제거 후 대체)
    private fun migrateAttributes(meta: ItemMeta): Boolean {
        val allModifiers = meta.attributeModifiers ?: return false
        var changed = false

        for ((attribute, modifiers) in allModifiers.asMap()) {
            var whiteforestModifier: AttributeModifier? = null
            var magicenchModifier: AttributeModifier? = null

            for (modifier in modifiers) {
                if (modifier.key?.namespace == "whiteforest") whiteforestModifier = modifier
                if (modifier.key?.namespace == "magicench") magicenchModifier = modifier
            }

            // magicench가 존재하면 값을 whiteforest에 덮어쓰기
            if (magicenchModifier != null) {
                val newKey = whiteforestModifier?.key ?: NamespacedKey("whiteforest", "migrated_${magicenchModifier.amount}")
                val newModifier = if (magicenchModifier.slotGroup != null) {
                    AttributeModifier(newKey, magicenchModifier.amount, magicenchModifier.operation, magicenchModifier.slotGroup)
                } else {
                    AttributeModifier(newKey, magicenchModifier.amount, magicenchModifier.operation, EquipmentSlotGroup.ANY)
                }

                // 기존 whiteforest 제거 후 새 Modifier 적용
                if (whiteforestModifier != null) meta.removeAttributeModifier(attribute, whiteforestModifier)
                meta.addAttributeModifier(attribute, newModifier)

                // magicench 제거
                meta.removeAttributeModifier(attribute, magicenchModifier)

                changed = true
            }
        }

        return changed
    }


    // EquipmentSlot -> EquipmentSlotGroup 간 단순 매핑 (필요시 확장)
    private fun mapSlotToGroup(slot: EquipmentSlot?): EquipmentSlotGroup? {
        return when (slot) {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND -> EquipmentSlotGroup.HAND
            EquipmentSlot.FEET -> EquipmentSlotGroup.FEET
            EquipmentSlot.LEGS -> EquipmentSlotGroup.LEGS
            EquipmentSlot.CHEST -> EquipmentSlotGroup.ARMOR
            EquipmentSlot.HEAD -> EquipmentSlotGroup.HEAD
            else -> null
        }
    }

    // 블록 인벤토리(상자, 셜커 등) 마이그레이션
    private fun migrateBlockInventory(block: Block) {
        val holder = block.state as? BlockInventoryHolder ?: return
        val inv = holder.inventory
        var changed = false
        for (i in 0 until inv.size) {
            val before = inv.getItem(i)
            val after = migrateItem(before)
            if (before != after) {
                inv.setItem(i, after)
                changed = true
            }
        }
        if (changed) {
            block.state.update(true, true)
          //  plugin.logger.info("[NBTMigration] 블록 인벤토리 업데이트 @ ${block.location}")
        }
    }
}
