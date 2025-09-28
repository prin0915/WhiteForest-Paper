package org.prin.WhiteForest.abillity

import org.bukkit.NamespacedKey
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import kotlin.random.Random

class TridentMultiShot(val plugin: JavaPlugin) : Listener {

    private val key = NamespacedKey(plugin, "force_level")

    @EventHandler
    fun onTridentLaunch(event: ProjectileLaunchEvent) {
        val trident = event.entity as? Trident ?: return
        val shooter = trident.shooter as? Player ?: return

        val item = shooter.inventory.itemInMainHand
        val container = item.itemMeta?.persistentDataContainer ?: return
        val level = container.get(key, PersistentDataType.INTEGER) ?: 0

        if (level >= 10) {
            // 내 삼지창은 그대로 날아감
            trident.setMetadata("force_level", FixedMetadataValue(plugin, level))

            // 추가 가짜 삼지창 9개 생성
            shootExtraTridents(shooter, trident, amount = 9, spread = 0.2, speed = 2.0)
        } else {
            trident.setMetadata("force_level", FixedMetadataValue(plugin, level))
        }
    }


    fun shootExtraTridents(player: Player, original: Trident, amount: Int, spread: Double, speed: Double) {
        val world = player.world
        val eyeLocation = player.eyeLocation

        for (i in 0 until amount) {
            val extra = world.spawn(eyeLocation, Trident::class.java)
            extra.shooter = player
            extra.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
            extra.setMetadata("force_level", FixedMetadataValue(plugin, 10))

            val dir = eyeLocation.direction.clone().add(
                Vector(
                    Random.nextDouble(-spread, spread),
                    Random.nextDouble(-spread, spread),
                    Random.nextDouble(-spread, spread)
                )
            ).normalize()

            extra.velocity = dir.multiply(speed)
        }
    }

    private fun shootMultipleTridents(
        player: Player,
        amount: Int,
        spread: Double,
        speed: Double,
        level: Int
    ) {
        val world = player.world
        val eyeLocation = player.eyeLocation

        repeat(amount) {
            val trident: Trident = world.spawn(eyeLocation, Trident::class.java)

            trident.shooter = player
            trident.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED // 못줍게
            trident.setMetadata("force_level", FixedMetadataValue(plugin, level))

            val direction = eyeLocation.direction.clone().add(
                Vector(
                    Random.nextDouble(-spread, spread),
                    Random.nextDouble(-spread, spread),
                    Random.nextDouble(-spread, spread)
                )
            ).normalize()

            trident.velocity = direction.multiply(speed)
        }
    }

    @EventHandler
    fun onTridentHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (projectile !is Trident) return

        val level = if (projectile.hasMetadata("force_level")) {
            projectile.getMetadata("force_level")[0].asInt()
        } else 0

        // 10강 이상 + 엔티티 맞음 + 맞은 대상이 Player일 때만
        val hitEntity = event.hitEntity
        if (level >= 10 && hitEntity is Player) {
            hitEntity.world.strikeLightningEffect(hitEntity.location) // 데미지 없는 번개 효과
        }
    }

}
