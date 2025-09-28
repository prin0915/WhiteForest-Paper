//package org.prin.magicEnch.avatar
//
//import org.bukkit.Location
//import org.bukkit.entity.Husk
//import org.bukkit.entity.Player
//import org.bukkit.inventory.ItemStack
//
//fun spawnAvatarHusk(player: Player, location: Location, skinValue: String, skinSignature: String): Husk {
//    val husk = location.world!!.spawn(location, Husk::class.java)
//    husk.customName = "${player.name}의 아바타"
//    husk.isCustomNameVisible = true
//    husk.isAI = false
//    husk.isInvulnerable = true
//    husk.setPose(org.bukkit.entity.EntityPose.SLEEPING) // 누운 자세
//    // 스킨 적용
//    val skull = ItemStack(Material.PLAYER_HEAD)
//    val meta = skull.itemMeta as SkullMeta
//    val profile = GameProfile(null, "")
//    profile.properties.put("textures", Property("textures", skinValue, skinSignature))
//    val profileField = meta.javaClass.getDeclaredField("profile")
//    profileField.isAccessible = true
//    profileField.set(meta, profile)
//    skull.itemMeta = meta
//    husk.equipment.setHelmet(skull)
//    return husk
//}
//
//
//data class Avatar(
//    val owner: Player,
//    val inventory: MutableList<ItemStack> = mutableListOf(),
//    var isActive: Boolean = false,
//    var husk: Husk? = null // Husk 엔티티 참조 추가
//)
//
//
//object AvatarManager {
//    private val avatars = mutableMapOf<Player, Avatar>()
//
//    fun createAvatar(player: Player): Avatar {
//        val avatar = Avatar(owner = player)
//        avatars[player] = avatar
//        return avatar
//    }
//
//    fun getAvatar(player: Player): Avatar? = avatars[player]
//
//    fun removeAvatar(player: Player) {
//        avatars.remove(player)
//    }
//
//    fun setActive(player: Player, active: Boolean) {
//        avatars[player]?.isActive = active
//    }
//
//    fun isActive(player: Player) = avatars[player]?.isActive ?: false
//}
//
//