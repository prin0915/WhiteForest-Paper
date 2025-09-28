package org.prin.WhiteForest.team

import org.bukkit.Bukkit
import org.bukkit.EntityEffect
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

// 상점 아이템 정의용 데이터 클래스
data class ShopItem(
    val material: Material,
    val costLevel: Int,
    val slot: Int,
    val displayName: String,
    val description: String,
    val reduction: Int? = null
)


class PylonUI(private val plugin: JavaPlugin, private val pylon: Pylon, private val death: Death) : Listener {

    // 상점 아이템 리스트
    private val shopItems = listOf(
        ShopItem(Material.PAPER, 10, 10, "§f별가루", "신비로운 힘을 가지고있다"),
        ShopItem(Material.PAPER, 10, 12, "§f별조각", "신비로운 힘을 가지고있다"),
        ShopItem(Material.TOTEM_OF_UNDYING, 30, 14, "§d부활 시간 감소", "부활 시간을 줄여준다", reduction = 10),
        ShopItem(Material.BEACON, 150, 16, "§d§b파일런", "")
    )

    @EventHandler
    fun onPylonClick(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock ?: return

        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (block.type != Material.BEACON) return

        val pylonData = pylon.getPylonByLocation(block.location) ?: return
        event.isCancelled = true

        val isOwner = pylonData.owner == player.uniqueId
        val isMember = pylonData.member.contains(player.uniqueId)

        if (!isOwner && !isMember) {
            player.sendMessage("§c자신의 파일런만 열 수 있습니다!")
            return
        }

        openPylonGUI(player, isOwner)
    }

    private fun openPylonGUI(player: Player, isOwner: Boolean) {
        val gui: Inventory = Bukkit.createInventory(null, 9, "파일런 UI")

        // 경험치 상점 버튼 (항상 표시)
        val expBottle = ItemStack(Material.EXPERIENCE_BOTTLE)
        val meta: ItemMeta = expBottle.itemMeta ?: return
        meta.setDisplayName("§a경험치 상점")
        expBottle.itemMeta = meta
        gui.setItem(7, expBottle)

        val totem = ItemStack(Material.BEACON)
        val ownerMeta = totem.itemMeta ?: return
        ownerMeta.setDisplayName("§6팀원 부활")
        totem.itemMeta = ownerMeta
        gui.setItem(1, totem)

        // 오너 전용 버튼
        if (isOwner) {

        }

        player.openInventory(gui)
    }

    private fun openExpShop(player: Player) {
        val shop: Inventory = Bukkit.createInventory(null, 27, "경험치 상점")

        for (item in shopItems) {
            val stack = ItemStack(item.material)
            val meta = stack.itemMeta ?: continue

            meta.setDisplayName(item.displayName)

            val lore = mutableListOf<String>()
            lore.add("§7${item.description}") // 설명

            // 별가루 / 별조각 CustomModelData 적용
            if (item.material == Material.PAPER) {
                meta.setCustomModelData(
                    when (item.displayName) {
                        "§f별가루" -> 1
                        "§f별조각" -> 2
                        else -> 0
                    }
                )
            } // 별가루 / 별조각 CustomModelData 및 NamespacedKey 적용
            if (item.material == Material.PAPER) {
                when (item.displayName) {
                    "§f별가루" -> {
                        meta.setCustomModelData(1)
                        val key = NamespacedKey(plugin, "stardust")
                        meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, 1)
                    }
                    "§f별조각" -> {
                        meta.setCustomModelData(2)
                        val key = NamespacedKey(plugin, "starshard")
                        meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, 1)
                    }
                }
            }

            // 부활 시간 감소 아이템이면 config.yml에서 시간 가져오기
            if (item.reduction != null) {
                val reductionTime = plugin.config.getConfigurationSection("respawn")?.getInt("revivalReductionTime") ?: 10
                lore.add("§f부활 시간 감소: ${reductionTime}초")
            }

            lore.add("§7필요 레벨: §e${item.costLevel}L") // 상점 UI에서만 표시
            meta.lore = lore

            stack.itemMeta = meta
            shop.setItem(item.slot, stack)
        }

        player.openInventory(shop)
    }

    private fun openmemberPhoenixGUI(player: Player) {
        val memberPhoenix: Inventory = Bukkit.createInventory(null, 27, "팀원 즉시 부활")

        val team = pylon.getOrCreateTeam(player.uniqueId)
        val members = team.memberUUIDs.toMutableList() // 팀원 UUID 리스트

        // 팀장 UUID 추가 (만약 이미 팀원 리스트에 없다면)
        if (team.ownerUUID !in members) {
            members.add(team.ownerUUID)
        }

        // 죽은 플레이어만 필터링
        val deadMembers = members.filter { death.cooldowns[it]?.minus(System.currentTimeMillis()) ?: 0 > 0 }

        for ((index, memberUUID) in deadMembers.withIndex()) {
            val memberPlayer = Bukkit.getOfflinePlayer(memberUUID)

            val remainingMillis = death.cooldowns[memberUUID]?.minus(System.currentTimeMillis()) ?: 0
            val remainingSeconds = (remainingMillis / 1000).coerceAtLeast(0).toInt()

            val emeraldsNeeded = if (remainingSeconds > 0) {
                Math.ceil(remainingSeconds / 10.0).toInt()
            } else 0

            // 머리 생성 (Paper 전용)
            val skull = createSkull(memberUUID, memberPlayer.name)
            val meta = skull.itemMeta as SkullMeta

            val lore = mutableListOf<String>()
            lore.add("§7남은 부활 시간: §e${remainingSeconds}s")
            lore.add("§7즉시 부활 필요 에메랄드: §a$emeraldsNeeded")
            meta.lore = lore

            skull.itemMeta = meta
            if (index < 27) memberPhoenix.setItem(index, skull)
        }

        player.openInventory(memberPhoenix)
    }




    @EventHandler
    fun onGUIClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val topInv = event.view.topInventory
        val clickedInv = event.clickedInventory ?: return

        when (event.view.title) {
            "팀원 즉시 부활" -> {
                event.isCancelled = true // 클릭 시 아이템 이동 방지

                val item = event.currentItem ?: return
                if (item.type != Material.PLAYER_HEAD) return

                val meta = item.itemMeta as? SkullMeta ?: return
                val target = meta.owningPlayer ?: return
                val targetUUID = target.uniqueId


                val remainingMillis = death.cooldowns[targetUUID]?.minus(System.currentTimeMillis()) ?: 0
                if (remainingMillis <= 0) {
                    player.sendMessage("§a이미 부활 가능한 상태입니다!")
                    return
                }

                val secondsRemaining = (remainingMillis / 1000).toInt()
                // 0~10초 구간도 1개, 10초마다 1개씩 에메랄드 필요
                val emeraldsNeeded = if (secondsRemaining > 0) {
                    Math.ceil(secondsRemaining / 10.0).toInt()
                } else 0

                val emeraldCount = player.inventory.all(Material.EMERALD).values.sumOf { it.amount }
                if (emeraldCount < emeraldsNeeded) {
                    player.sendMessage("§c에메랄드가 부족합니다! 필요: $emeraldsNeeded")
                    return
                }


                removeEmeralds(player, emeraldsNeeded) // 위에서 만든 removeEmeralds 함수 사용
                death.cooldowns.remove(targetUUID)      // 즉시 부활 처리
                val targetPlayer = Bukkit.getPlayer(targetUUID)
                targetPlayer?.sendMessage("§a$emeraldsNeeded 에메랄드를 사용하여 즉시 부활했습니다!")
                player.sendMessage("§a팀원을 즉시 부활시켰습니다! (${emeraldsNeeded} 에메랄드 사용)")

                player.playEffect(EntityEffect.PROTECTED_FROM_DEATH)
                player.world.playSound(player.location, "item.totem.use", 1f, 1f)

                player.closeInventory()

            }
            "파일런 UI" -> {
                event.isCancelled = true
                if (clickedInv == topInv && event.rawSlot == 7) {
                    openExpShop(player)
                }

                if (clickedInv == topInv && event.rawSlot == 1){
                    openmemberPhoenixGUI(player)
                }
            }
            "경험치 상점" -> {
                event.isCancelled = true
                if (clickedInv != topInv) return

                val shopItem = shopItems.find { it.slot == event.rawSlot } ?: return
                purchaseItem(player, shopItem)
            }
        }
    }

    private fun removeEmeralds(player: Player, amount: Int) {
        var remaining = amount
        val inv = player.inventory
        for (item in inv.contents) {
            if (item == null || item.type != Material.EMERALD) continue
            if (item.amount <= remaining) {
                remaining -= item.amount
                item.amount = 0
            } else {
                item.amount -= remaining
                remaining = 0
            }
            if (remaining <= 0) break
        }
    }

    private fun purchaseItem(player: Player, shopItem: ShopItem) {
        if (player.level < shopItem.costLevel) {
            player.sendMessage("§c레벨이 부족합니다! 필요한 레벨: ${shopItem.costLevel}")
            return
        }

        val item = ItemStack(shopItem.material)
        val meta = item.itemMeta ?: return
        meta.setDisplayName(shopItem.displayName)

        val lore = mutableListOf<String>()
        lore.add("§7${shopItem.description}") // 설명

        // 토템이면 config.yml에서 부활 시간 감소 가져오기
        if (shopItem.material == Material.TOTEM_OF_UNDYING && shopItem.reduction != null) {
            val reductionTime = plugin.config.getConfigurationSection("respawn")?.getInt("revivalReductionTime") ?: 10
            lore.add("§f부활 시간 감소: ${reductionTime}초")

            // PersistentDataContainer에 저장 (TotemClickListener에서 읽음)
            val key = NamespacedKey(plugin, "reduction")
            meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, reductionTime)
        }

        meta.lore = lore

        // NamespacedKey 적용 (별가루/별조각 등 기존 코드)
        if (shopItem.material == Material.PAPER) {
            when (shopItem.displayName) {
                "§f별가루" -> {
                    val key = NamespacedKey(plugin, "stardust")
                    meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1.toByte())
                    meta.setCustomModelData(1)
                }
                "§f별조각" -> {
                    val key = NamespacedKey(plugin, "starshard")
                    meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1.toByte())
                    meta.setCustomModelData(2)
                }
            }
        }

        item.itemMeta = meta

        // 인벤토리에 추가
        val leftovers = player.inventory.addItem(item)
        if (leftovers.isEmpty()) {
            player.level -= shopItem.costLevel
            player.sendMessage("§a${shopItem.displayName}를 구매했습니다! 레벨 -${shopItem.costLevel}")
        } else {
            player.sendMessage("§c인벤토리에 공간이 부족합니다!")
        }
    }

    fun createSkull(uuid: UUID, name: String?): ItemStack {
        val skull = ItemStack(Material.PLAYER_HEAD, 1)
        val meta = skull.itemMeta as SkullMeta

        // UUID와 이름으로 프로필 생성
        val profile = Bukkit.createProfile(uuid, name ?: "Unknown")

        // 스킨 데이터 로딩 (온라인 모드에서만 유효)
        profile.complete(true) // true = force remote fetch

        // 불러온 프로필을 스컬 메타에 세팅
        meta.playerProfile = profile
        meta.setDisplayName(name ?: "Unknown")

        skull.itemMeta = meta
        return skull
    }



}
