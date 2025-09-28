package org.prin.WhiteForest.ui

import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.*
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class MagicForceUI(private val plugin: JavaPlugin) : Listener {

    var consol: ConsoleCommandSender = Bukkit.getConsoleSender()
    var map: java.util.HashMap<UUID?, Int?> = HashMap<UUID?, Int?>()

    private val forceUI: Inventory = Bukkit.createInventory(null, 27, "§f箱学")

    @EventHandler
    fun onClickEvent(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock

        val holder = forceUIHolder(player)
        val forceUI = holder.getInventory()


        if (event.action == Action.RIGHT_CLICK_BLOCK &&
            (block?.type == Material.ANVIL ||
                    block?.type == Material.DAMAGED_ANVIL ||
                    block?.type == Material.CHIPPED_ANVIL)) {

            event.isCancelled = true
            player.openInventory(forceUI)
        }

    }

    val forceMap = HashMap<UUID, Int>()
    var targetslot: Int? = null

    @EventHandler
    fun onInventoryClickEvent(event: InventoryClickEvent) {
        // 1) 이 UI가 아니면 바로 종료
        if (event.view.title != "§f箱学") return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val topInv = event.view.topInventory
        val bottomInv = event.view.bottomInventory
        val clickedInv = event.clickedInventory ?: return
        val clickedItem = event.currentItem ?: return

        // 2) 플레이어 인벤토리에서 대상 아이템 선택
        if (clickedInv == bottomInv && clickedItem.canForce) {
            // 플레이어 인벤토리의 '상대 슬롯'을 저장해야 나중에 정확히 갱신 가능
            forceMap[player.uniqueId] = event.slot
            topInv.setItem(13, clickedItem.clone())
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
            return
        }

        // 3) 상단 UI 13번 슬롯을 클릭하면 강화 시도
        if (clickedInv == topInv && event.rawSlot == 13 && clickedItem.canForce) {
            val currentLevel = getForceLevel(clickedItem, plugin)
            val requiredShards = currentLevel + 1

            if (currentLevel < 10 && useStarshard(player, requiredShards)) {
                val result = force(clickedItem, player, event.slot) // 강화 로직 수행
                topInv.setItem(13, result.clone())

                // 선택된 '플레이어 인벤토리 슬롯'에 결과 반영
                forceMap[player.uniqueId]?.let { targetSlot ->
                    player.inventory.setItem(targetSlot, result)
                    player.updateInventory() // 클라이언트 즉시 동기화
                }

                player.playSound(player.location, Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.4f)
            }
            return
        }
    }


    fun useStarshard(player: Player, required: Int): Boolean {
        val inventory = player.inventory
        var totalFound = 0
        val key = NamespacedKey(plugin, "starshard")

        // 먼저 총 개수 확인
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            val meta = item.itemMeta ?: continue
            if (meta.persistentDataContainer.has(key, PersistentDataType.BYTE)) {
                totalFound += item.amount
            }
        }

        if (totalFound < required) {
            return false
        }

        // 실제 소모
        var toConsume = required
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            val meta = item.itemMeta ?: continue
            if (meta.persistentDataContainer.has(key, PersistentDataType.BYTE)) {
                if (item.amount > toConsume) {
                    item.amount -= toConsume
                    return true
                } else {
                    toConsume -= item.amount
                    inventory.setItem(slot, null)
                    if (toConsume <= 0) return true
                }
            }
        }

        return true
    }


    fun force(item: ItemStack, player: Player, slot: Int): ItemStack {
        val meta = item.itemMeta ?: return item

        val key = NamespacedKey(plugin, "force_level")
        val container = meta.persistentDataContainer

        val currentLevel = container.get(key, PersistentDataType.INTEGER) ?: 0

        // 최대 강화 단계 도달 시 강화 불가
        if (currentLevel >= 10) return item

        val successTable = intArrayOf(100, 90, 80, 70, 60, 50, 50, 50, 50, 50)
        val destroyTable = intArrayOf(0, 0, 0, 0, 0, 10, 20, 30, 40, 99)

        val successRate = successTable[currentLevel]
        val destroyRate = destroyTable[currentLevel]
        var roll = (1..100).random()

        if (roll <= successRate) {
            val newLevel = currentLevel + 1
            container.set(key, PersistentDataType.INTEGER, newLevel)
            addOrUpdateStarLore(meta, newLevel)

            item.itemMeta = meta
            var updatedItem = applyStatBonus(item, newLevel)
            player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1.0f, 0.85f)

            if (newLevel == 10) {
                val itemName = updatedItem.itemMeta?.displayName ?: updatedItem.type.name
                val itemType = updatedItem.type
                val category = getItemCategory(itemType)
                val newName = "§d${player.name}의 ${category}"

                val updatedMeta = updatedItem.itemMeta!!
                updatedMeta.setDisplayName(newName)
                updatedItem.itemMeta = updatedMeta

                // 10강 특수효과
                updatedItem = applySpecialEffect(updatedItem, player)

                Bukkit.broadcastMessage("§6[강화 알림] §e${player.name}님이 §b${itemName}§e을(를) §c10강§e 강화에 성공했습니다!")
            }


            // 강화 성공 시에는 확률 lore 없는 상태로 능력치 및 설명 재적용

            return updatedItem
        }
        roll = (1..100).random()
        val originalMeta = item.itemMeta
        if (roll <= destroyRate) {
            val itemName = originalMeta?.displayName ?: item.type.name
            val loreText = originalMeta?.lore?.joinToString("\n") ?: "아이템 정보 없음"

            val component = TextComponent("누군가의 ")
            val itemComponent = TextComponent("§e$itemName")
            val restComponent = TextComponent("§f 이(가) 연기가 되어 사라졌습니다")

            // Hover 이벤트에 lore 텍스트 적용 (NMS 없이)
            itemComponent.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
                net.md_5.bungee.api.chat.hover.content.Text(loreText)
            )

            component.addExtra(itemComponent)
            component.addExtra(restComponent)

            Bukkit.getOnlinePlayers().forEach { p ->
                p.spigot().sendMessage(component)
            }

            player.playSound(player.location, Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.85f)
            return ItemStack(Material.AIR)

        } else {
            if (currentLevel >= 5) {
                val newLevel = currentLevel - 1
                container.set(key, PersistentDataType.INTEGER, newLevel)
                addOrUpdateStarLore(meta, newLevel)
                item.itemMeta = meta

                val updatedItem = applyStatBonus(item, newLevel)
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.4f)

                return updatedItem

            } else {
                // 실패했지만 단계 유지
                addOrUpdateStarLore(meta, currentLevel)
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.4f)

                item.itemMeta = meta
                return item
            }
        }
    }

    fun getForceLevel(item: ItemStack, plugin: JavaPlugin): Int {
        val meta = item.itemMeta ?: return 0
        val key = NamespacedKey(plugin, "force_level")
        return meta.persistentDataContainer.get(key, PersistentDataType.INTEGER) ?: 0
    }

    fun removeChanceLore(item: ItemStack): ItemStack {
        val meta = item.itemMeta ?: return item
        val filteredLore = meta.lore?.filterNot {
            it.contains("강화 성공 확률") ||
                    it.contains("강화 실패 확률") ||
                    it.contains("아이템 파괴 확률")
        }?.toList() ?: emptyList()

        meta.lore = filteredLore
        item.itemMeta = meta
        return item
    }

    fun getStarLore(level: Int, maxLevel: Int = 10): String {
        val filled = "★".repeat(level.coerceAtMost(maxLevel))
        val empty = "☆".repeat((maxLevel - level).coerceAtLeast(0))
        return "$filled$empty"
    }

    fun updateTarget(player: Player, slot: Int, item: ItemStack) {
        player.inventory.setItem(slot, item)
        player.updateInventory() // ★ 클라이언트에 즉시 반영
    }


    fun getItemCategory(type: Material): String {
        return when {
            type.name.contains("SWORD") -> "검"
            type.name.contains("AXE") -> "도끼"
            type.name.contains("BOW") -> "활"
            type.name.contains("CROSSBOW") -> "석궁"
            type.name.contains("TRIDENT") -> "삼지창"
            type.name.contains("HELMET") -> "투구"
            type.name.contains("CHESTPLATE") -> "갑옷"
            type.name.contains("LEGGINGS") -> "바지"
            type.name.contains("BOOTS") -> "신발"
            type.name.contains("ELYTRA") -> "날개"
            else -> "장비"
        }
    }

    fun applyStatBonus(item: ItemStack, level: Int): ItemStack {
        val meta = item.itemMeta ?: return item
        val type = item.type

        // ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
        // ┃ 슬롯 그룹 체크 헬퍼 함수 ┃
        // ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
        fun isInSlotGroup(slot: EquipmentSlot?, group: EquipmentSlotGroup): Boolean {
            return when (group) {
                EquipmentSlotGroup.HAND -> slot == EquipmentSlot.HAND || slot == EquipmentSlot.OFF_HAND
                EquipmentSlotGroup.FEET -> slot == EquipmentSlot.FEET
                EquipmentSlotGroup.LEGS -> slot == EquipmentSlot.LEGS
                EquipmentSlotGroup.ARMOR -> slot == EquipmentSlot.CHEST
                EquipmentSlotGroup.HEAD -> slot == EquipmentSlot.HEAD
                else -> false
            }
        }

        // ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
        // ┃ Modifier 제거 헬퍼 함수 ┃
        // ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
        fun removeModifier(attribute: Attribute, key: NamespacedKey, group: EquipmentSlotGroup) {
            val oldMods = meta.getAttributeModifiers(attribute) ?: return
            for (mod in oldMods) {
                if (mod.key == key && isInSlotGroup(mod.slot, group)) {
                    meta.removeAttributeModifier(attribute, mod)
                }
            }
        }

        // ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
        // ┃ 기본 lore 처리 ┃
        // ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
        val baseLore = meta.lore?.toMutableList() ?: mutableListOf()
        val starLine = baseLore.find { it.startsWith("§b") }
        val filteredLore = baseLore.filterNot { it.startsWith("§b") }.toMutableList()

        // ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
        // ┃ Attribute 적용 ┃
        // ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
        when {
            type.name.contains("SWORD") -> {
                val speedKey = NamespacedKey(plugin, "sword_attack_speed")
                val damageKey = NamespacedKey(plugin, "sword_attack_damage")

                removeModifier(Attribute.ATTACK_SPEED, speedKey, EquipmentSlotGroup.HAND)
                removeModifier(Attribute.ATTACK_DAMAGE, damageKey, EquipmentSlotGroup.HAND)

                val baseDamage = when (type) {
                    Material.WOODEN_SWORD -> 4.0
                    Material.STONE_SWORD -> 5.0
                    Material.IRON_SWORD -> 6.0
                    Material.GOLDEN_SWORD -> 4.0
                    Material.DIAMOND_SWORD -> 7.0
                    Material.NETHERITE_SWORD -> 8.0
                    else -> 0.0
                }

                val bonusSpeed = level * 0.1

                // ★ 공격속도 적용
                var speedModifierValue = bonusSpeed

                // ★ 10강 달성 시 쿨타임 제거
                if (level >= 10) {
                    speedModifierValue = 100.0 // 사실상 쿨타임 제거
                }

                val speedModifier = AttributeModifier(
                    speedKey,
                    speedModifierValue,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HAND
                )
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier)

                val damageModifier = AttributeModifier(
                    damageKey,
                    baseDamage,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HAND
                )
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier)
            }

            type.name.contains("AXE") -> {
                val key = NamespacedKey(plugin, "axe_attack_damage")
                removeModifier(Attribute.ATTACK_DAMAGE, key, EquipmentSlotGroup.HAND)

                val baseDamage = when (type) {
                    Material.WOODEN_AXE -> 7.0
                    Material.STONE_AXE -> 9.0
                    Material.IRON_AXE -> 9.0
                    Material.GOLDEN_AXE -> 7.0
                    Material.DIAMOND_AXE -> 9.0
                    Material.NETHERITE_AXE -> 10.0
                    else -> 0.0
                }

                val totalDamage = baseDamage + baseDamage * (level * 0.1)

                val modifier = AttributeModifier(
                    key,
                    totalDamage,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HAND
                )

                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, modifier)
            }

            type.name.contains("HELMET") -> {
                // 1) 기본 방어력 / 방어강도 설정
                val baseDefence = when (type) {
                    Material.LEATHER_HELMET -> 1.0
                    Material.CHAINMAIL_HELMET -> 2.0
                    Material.IRON_HELMET -> 2.0
                    Material.GOLDEN_HELMET -> 2.0
                    Material.DIAMOND_HELMET -> 3.0
                    Material.NETHERITE_HELMET -> 3.0
                    else -> 0.0
                }
                val baseDefenceToughness = when (type) {
                    Material.LEATHER_HELMET -> 0.0
                    Material.CHAINMAIL_HELMET -> 0.0
                    Material.IRON_HELMET -> 0.0
                    Material.GOLDEN_HELMET -> 0.0
                    Material.DIAMOND_HELMET -> 2.0
                    Material.NETHERITE_HELMET -> 3.0
                    else -> 0.0
                }

                // 2) MAX_HEALTH 강화
                val healthKey = NamespacedKey(plugin, "HELMET_MAX_HEALTH")
                removeModifier(Attribute.MAX_HEALTH, healthKey, EquipmentSlotGroup.HEAD)
                val bonusHealth = level * 1.0
                val healthModifier = AttributeModifier(
                    healthKey,
                    bonusHealth,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HEAD
                )
                meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier)

                // 3) ARMOR / ARMOR_TOUGHNESS Modifier 재설정 (기존 값 보존 + 강화 레벨 적용)
                val armourKey = NamespacedKey(plugin, "HELMET_ARMOR")
                val toughnessKey = NamespacedKey(plugin, "HELMET_ARMOR_TOUGHNESS")

                removeModifier(Attribute.ARMOR, armourKey, EquipmentSlotGroup.HEAD)
                removeModifier(Attribute.ARMOR_TOUGHNESS, toughnessKey, EquipmentSlotGroup.HEAD)

                val totalDefence = baseDefence // + (level * 0.5) // 레벨당 보너스 예시
                val totalToughness = baseDefenceToughness // + (level * 0.25) // 레벨당 보너스 예시

                val defenceModifier = AttributeModifier(
                    armourKey,
                    totalDefence,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HEAD
                )
                val toughnessModifier = AttributeModifier(
                    toughnessKey,
                    totalToughness,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HEAD
                )

                meta.addAttributeModifier(Attribute.ARMOR, defenceModifier)
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, toughnessModifier)

                // 4) 넉백 저항 (네더라이트 헬멧만)
                if (type == Material.NETHERITE_HELMET) {
                    val knockbackKey = NamespacedKey(plugin, "HELMET_KNOCKBACK")
                    removeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackKey, EquipmentSlotGroup.HEAD)

                    val knockbackModifier = AttributeModifier(
                        knockbackKey,
                        1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.HEAD
                    )
                    meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackModifier)
                }
            }


            type.name.contains("CHESTPLATE") -> {
                val baseDefence = when (type) {
                    Material.LEATHER_CHESTPLATE -> 3.0
                    Material.CHAINMAIL_CHESTPLATE -> 5.0
                    Material.IRON_CHESTPLATE -> 6.0
                    Material.GOLDEN_CHESTPLATE -> 5.0
                    Material.DIAMOND_CHESTPLATE -> 8.0
                    Material.NETHERITE_CHESTPLATE -> 8.0
                    else -> 0.0
                }
                val baseDefenceToughness = when (type) {
                    Material.LEATHER_CHESTPLATE -> 0.0
                    Material.CHAINMAIL_CHESTPLATE -> 0.0
                    Material.IRON_CHESTPLATE -> 0.0
                    Material.GOLDEN_CHESTPLATE -> 0.0
                    Material.DIAMOND_CHESTPLATE -> 2.0
                    Material.NETHERITE_CHESTPLATE -> 3.0
                    else -> 0.0
                }

                val healthKey = NamespacedKey(plugin, "CHESTPLATE_MAX_HEALTH")
                removeModifier(Attribute.MAX_HEALTH, healthKey, EquipmentSlotGroup.ARMOR)
                val bonusHealth = level * 1.0
                val healthModifier = AttributeModifier(
                    healthKey,
                    bonusHealth,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ARMOR
                )
                meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier)

                val armourKey = NamespacedKey(plugin, "CHESTPLATE_ARMOR")
                val toughnessKey = NamespacedKey(plugin, "CHESTPLATE_ARMOR_TOUGHNESS")

                removeModifier(Attribute.ARMOR, armourKey, EquipmentSlotGroup.ARMOR)
                removeModifier(Attribute.ARMOR_TOUGHNESS, toughnessKey, EquipmentSlotGroup.ARMOR)

                val totalDefence = baseDefence // + (level * 0.5) // 레벨당 보너스 예시
                val totalToughness = baseDefenceToughness //+ (level * 0.25) // 레벨당 보너스 예시

                val defenceModifier = AttributeModifier(
                    armourKey,
                    totalDefence,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ARMOR
                )
                val toughnessModifier = AttributeModifier(
                    toughnessKey,
                    totalToughness,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ARMOR
                )

                meta.addAttributeModifier(Attribute.ARMOR, defenceModifier)
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, toughnessModifier)

                // 넉백 (네더라이트만)
                if (type == Material.NETHERITE_CHESTPLATE) {
                    val knockbackKey = NamespacedKey(plugin, "CHESTPLATE_KNOCKBACK")
                    removeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackKey, EquipmentSlotGroup.ARMOR)

                    val knockbackModifier = AttributeModifier(
                        knockbackKey,
                        1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.ARMOR
                    )
                    meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackModifier)
                }
            }

            type.name.contains("ELYTRA") -> {

                val healthKey = NamespacedKey(plugin, "ELYTRA_MAX_HEALTH")
                removeModifier(Attribute.MAX_HEALTH, healthKey, EquipmentSlotGroup.ARMOR)
                val bonusHealth = level * 1.0
                val healthModifier = AttributeModifier(
                    healthKey,
                    bonusHealth,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ARMOR
                )
                meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier)


            }

            type.name.contains("LEGGINGS") -> {
                val baseDefence = when (type) {
                    Material.LEATHER_LEGGINGS -> 2.0
                    Material.CHAINMAIL_LEGGINGS -> 4.0
                    Material.IRON_LEGGINGS -> 5.0
                    Material.GOLDEN_LEGGINGS -> 3.0
                    Material.DIAMOND_LEGGINGS -> 6.0
                    Material.NETHERITE_LEGGINGS -> 6.0
                    else -> 0.0
                }
                val baseDefenceToughness = when (type) {
                    Material.LEATHER_LEGGINGS -> 0.0
                    Material.CHAINMAIL_LEGGINGS -> 0.0
                    Material.IRON_LEGGINGS -> 0.0
                    Material.GOLDEN_LEGGINGS -> 0.0
                    Material.DIAMOND_LEGGINGS -> 2.0
                    Material.NETHERITE_LEGGINGS -> 3.0
                    else -> 0.0
                }

                val healthKey = NamespacedKey(plugin, "LEGGINGS_MAX_HEALTH")
                removeModifier(Attribute.MAX_HEALTH, healthKey, EquipmentSlotGroup.LEGS)
                val healthModifier = AttributeModifier(
                    healthKey,
                    level * 1.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.LEGS
                )
                meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier)

                val armourKey = NamespacedKey(plugin, "LEGGINGS_ARMOR")
                val toughnessKey = NamespacedKey(plugin, "LEGGINGS_ARMOR_TOUGHNESS")
                removeModifier(Attribute.ARMOR, armourKey, EquipmentSlotGroup.LEGS)
                removeModifier(Attribute.ARMOR_TOUGHNESS, toughnessKey, EquipmentSlotGroup.LEGS)

                val defenceModifier = AttributeModifier(
                    armourKey,
                    baseDefence,// + level * 0.5,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.LEGS
                )
                val toughnessModifier = AttributeModifier(
                    toughnessKey,
                    baseDefenceToughness,// + level * 0.25,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.LEGS
                )
                meta.addAttributeModifier(Attribute.ARMOR, defenceModifier)
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, toughnessModifier)

                if (type == Material.NETHERITE_LEGGINGS) {
                    val knockbackKey = NamespacedKey(plugin, "LEGGINGS_KNOCKBACK")
                    removeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackKey, EquipmentSlotGroup.LEGS)
                    val knockbackModifier = AttributeModifier(
                        knockbackKey,
                        1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.LEGS
                    )
                    meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackModifier)
                }
            }

            type.name.contains("BOOTS") -> {
                val baseDefence = when (type) {
                    Material.LEATHER_BOOTS -> 1.0
                    Material.CHAINMAIL_BOOTS -> 1.0
                    Material.IRON_BOOTS -> 2.0
                    Material.GOLDEN_BOOTS -> 1.0
                    Material.DIAMOND_BOOTS -> 3.0
                    Material.NETHERITE_BOOTS -> 3.0
                    else -> 0.0
                }
                val baseDefenceToughness = when (type) {
                    Material.LEATHER_BOOTS -> 0.0
                    Material.CHAINMAIL_BOOTS -> 0.0
                    Material.IRON_BOOTS -> 0.0
                    Material.GOLDEN_BOOTS -> 0.0
                    Material.DIAMOND_BOOTS -> 2.0
                    Material.NETHERITE_BOOTS -> 3.0
                    else -> 0.0
                }

                val healthKey = NamespacedKey(plugin, "BOOTS_MAX_HEALTH")
                removeModifier(Attribute.MAX_HEALTH, healthKey, EquipmentSlotGroup.FEET)
                val healthModifier = AttributeModifier(
                    healthKey,
                    level * 1.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.FEET
                )
                meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier)

                val armourKey = NamespacedKey(plugin, "BOOTS_ARMOR")
                val toughnessKey = NamespacedKey(plugin, "BOOTS_ARMOR_TOUGHNESS")
                removeModifier(Attribute.ARMOR, armourKey, EquipmentSlotGroup.FEET)
                removeModifier(Attribute.ARMOR_TOUGHNESS, toughnessKey, EquipmentSlotGroup.FEET)

                val defenceModifier = AttributeModifier(
                    armourKey,
                    baseDefence, //+ level * 0.5,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.FEET
                )
                val toughnessModifier = AttributeModifier(
                    toughnessKey,
                    baseDefenceToughness,// + level * 0.25,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.FEET
                )
                meta.addAttributeModifier(Attribute.ARMOR, defenceModifier)
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, toughnessModifier)

                if (type == Material.NETHERITE_BOOTS) {
                    val knockbackKey = NamespacedKey(plugin, "BOOTS_KNOCKBACK")
                    removeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackKey, EquipmentSlotGroup.FEET)
                    val knockbackModifier = AttributeModifier(
                        knockbackKey,
                        1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.FEET
                    )
                    meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackModifier)
                }

                val speedKey = NamespacedKey(plugin, "BOOTS_MOVEMENT_SPEED")


                removeModifier(Attribute.MOVEMENT_SPEED, speedKey, EquipmentSlotGroup.FEET)
                val movementSpeed = 0.1 + (level * 0.1 * 0.05)
                val speedModifier = AttributeModifier(
                    speedKey,
                    movementSpeed,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.FEET
                )
                meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, speedModifier)
            }
        }

        // ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
        // ┃ 별 줄 다시 추가 ┃
        // ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
        val finalLore = mutableListOf<String>()
        if (starLine != null) finalLore.add(starLine)
        finalLore.addAll(filteredLore)
        meta.lore = finalLore

        item.itemMeta = meta
        return item
    }


    fun addOrUpdateStarLore(meta: ItemMeta, level: Int) {
        val lore = meta.lore?.toMutableList() ?: mutableListOf()
        // 기존 별 라인 제거
        val starLinePrefix = "§6"
        val filtered = lore.filterNot { it.startsWith(starLinePrefix) }.toMutableList()
        // 새로운 별 라인 추가
        filtered.add("§6${getStarLore(level)}")
        meta.lore = filtered
    }


    fun applySpecialEffect(item: ItemStack, player: Player): ItemStack {
        val meta = item.itemMeta ?: return item
        val type = item.type

        // 내구도 무제한
        item.durability = 0
        item.type = item.type // 유지

        item.itemMeta = meta
        return item
    }


    fun addChanceLore(item: ItemStack): ItemStack {
        val meta = item.itemMeta ?: return item
        val key = NamespacedKey(plugin, "force_level")
        val level = meta.persistentDataContainer.get(key, PersistentDataType.INTEGER) ?: 0

        val successTable = intArrayOf(100, 90, 80, 70, 60, 50, 50, 50, 50, 50)
        val destroyTable = intArrayOf(0, 0, 0, 0, 0, 10, 20, 30, 40, 99)

        val success = successTable.getOrNull(level) ?: 0
        val destroy = destroyTable.getOrNull(level) ?: 0
        val fail = 100 - success - destroy

        val lore = meta.lore?.toMutableList() ?: mutableListOf()
        lore += "§7강화 성공 확률: §a${success}%"
        lore += "§7강화 실패 확률: §e${fail}%"
        lore += "§7아이템 파괴 확률: §c${destroy}%"

        meta.lore = lore
        item.itemMeta = meta
        return item
    }


}


private val ItemStack.canForce: Boolean
    get() {
        if(this.type.name.contains("PICKAXE")) return false
        val keywords =
            listOf("HELMET", "CHESTPLATE", "ELYTRA", "LEGGINGS", "BOOTS", "SWORD", "AXE", "TRIDENT", "BOW", "CROSSBOW")
        return keywords.any { keyword ->
            this.type.name.contains(keyword, ignoreCase = true)
        }

    }

class forceUIHolder(val owner: Player) : InventoryHolder {
    private val forceUI: Inventory = Bukkit.createInventory(this, 27, "§f箱学")

    override fun getInventory(): Inventory = forceUI
}