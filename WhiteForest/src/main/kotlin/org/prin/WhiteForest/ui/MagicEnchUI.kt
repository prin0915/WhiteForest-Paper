package org.prin.WhiteForest.ui


import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*


class MagicEnchUI(private val plugin: JavaPlugin) : Listener {
    private val clickCooldown = HashMap<UUID, Long>()

    val enchUI: Inventory = Bukkit.createInventory(null, 27, "§f箱日")

    var consol: ConsoleCommandSender = Bukkit.getConsoleSender()
    var map: HashMap<UUID?, Int?> = HashMap<UUID?, Int?>()


    @EventHandler
    fun onClickEvent(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock

        val holder = EnchUIHolder(player)
        val enchUI = holder.getInventory()


        if (event.action == Action.RIGHT_CLICK_BLOCK && block?.type == Material.ENCHANTING_TABLE) {
            event.isCancelled = true
            player.openInventory(enchUI)
        }

    }

    var targetslot: Int? = null

    @EventHandler
    fun onInventoryClickEvent(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val uuid = player.uniqueId



        val slot = event.slot
        val clickedItem = event.currentItem ?: return
        val holder = event.inventory.holder as? EnchUIHolder ?: return
        val enchUI = holder.getInventory()

        val container = clickedItem.persistentDataContainer
        val key = NamespacedKey(plugin, "force_level")
        val currentLevel = container.get(key, PersistentDataType.INTEGER) ?: 0

        if (event.view.title == "§f箱日") {

            // 100ms 이내 중복 클릭 방지
            val now = System.currentTimeMillis()
            val lastClick = clickCooldown[uuid] ?: 0
            if (now - lastClick < 200) {
                event.isCancelled = true
                return
            }
            clickCooldown[uuid] = now
            event.isCancelled = true

            if(currentLevel == 0 || currentLevel == null) {
                // 13번 슬롯이 아닌 경우, 대상 슬롯에 아이템 복사
                if (event.rawSlot != 4 && clickedItem.canEnchant) {
                    enchUI.setItem(4, clickedItem.clone())
                    map[uuid] = event.slot
                }

                // 13번 슬롯 클릭 시 인첸트
                if (event.rawSlot == 4 && clickedItem.canEnchant) {
                    if (useStarDust(player)) {
                        val ei: ItemStack = enchant(clickedItem)
                        enchUI.setItem(slot, ei.clone())
                        map[uuid]?.let { updateTarget(player, it, ei) }
                        player.playSound(player.location, Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.4f)
                    } else {
                        player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.85f)
                    }
                }
            }
        }
    }

    private val enchantCountKey = NamespacedKey(plugin, "enchant_count")

    fun enchant(item: ItemStack, a: Double = .5): ItemStack {
        val meta = item.itemMeta ?: return item
        val container = meta.persistentDataContainer
        val enchantCount = container.get(enchantCountKey, PersistentDataType.INTEGER) ?: 0

        val existing = meta.enchants.keys
        val allNormal = Enchantment.values().filter { !it.isCursed }
        val allCurses = Enchantment.values().filter { it.isCursed }

        // --- 기존 인첸트 분리 ---
        val curses = existing.filter { it.isCursed }
        val normal = existing.filter { !it.isCursed }

        // 기존 일반 인첸트 제거 (리젠용)
        for (ench in normal) {
            meta.removeEnchant(ench)
        }

        // 현재 총 줄 수 (저주 포함)
        var lineCount = existing.size
        if (lineCount == 0) lineCount = 1 // 최소 1줄 보장

        // --- 확률적으로 새 줄 추가 ---
        val chance = a * Math.pow(0.8, (lineCount - 1).toDouble())

        if (Math.random() < chance) {
            lineCount++ // 줄 하나 증가
        }

        // --- 일반 인첸트 붙이기 ---
        val availableNormal = allNormal.toMutableList()
        val normalToAdd = (lineCount - curses.size).coerceAtLeast(0)

        repeat(normalToAdd) {
            if (availableNormal.isEmpty()) return@repeat
            val chosen = availableNormal.random()
            availableNormal.remove(chosen)

            val level = getRandomLevel(chosen.maxLevel)
            meta.addEnchant(chosen, level, true)
        }

        // --- 저주 처리 (0.5% 확률) ---
        for (curse in allCurses) {
            if (curse !in curses) {
                if (Math.random() < 0.005) { // 0.5%
                    meta.addEnchant(curse, 1, true)
                }
            } else {
                // 이미 있던 저주는 항상 유지
                meta.addEnchant(curse, 1, true)
            }
        }

        // 인첸트 횟수 증가
        container.set(enchantCountKey, PersistentDataType.INTEGER, enchantCount + 1)
        item.itemMeta = meta
        return item
    }







    fun getRandomLevel(maxLevel: Int): Int {
        // 1. 확률 분포 계산
        val weights = (1..maxLevel).map { level ->
            2.5 / level
        }

        val total = weights.sum()

        var roll = Math.random() * total

        for ((index, weight) in weights.withIndex()) {
            roll -= weight
            if (roll <= 0) {
                return index + 1
            }
        }

        return maxLevel // 혹시 남으면 마지막 레벨
    }

    fun useStarDust(player: Player): Boolean {
        val inventory = player.inventory
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            val meta = item.itemMeta ?: continue

            // Stardust 태그 확인
            val key = NamespacedKey(plugin, "stardust")
            if (meta.persistentDataContainer.has(key, PersistentDataType.BYTE)) {
                // 아이템 1개 소모
                if (item.amount > 1) {
                    item.amount -= 1
                } else {
                    inventory.setItem(slot, null)
                }
                return true
            }
        }
        return false
    }


    fun updateTarget(player: Player, slot: Int, item: ItemStack) {

        player.inventory.setItem(slot, item)


    }

}

private val ItemStack.canEnchant: Boolean
    get() = Enchantment.values().any { enchantment ->
        enchantment.canEnchantItem(this)
    }



class EnchUIHolder(val owner: Player) : InventoryHolder {
    private val enchUI: Inventory = Bukkit.createInventory(this, 27, "§f箱日")

    override fun getInventory(): Inventory = enchUI
}

