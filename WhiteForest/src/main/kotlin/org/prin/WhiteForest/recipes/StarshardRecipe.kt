package org.prin.WhiteForest.recipes

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class StarshardRecipe(private val plugin: JavaPlugin) {

    private val key = NamespacedKey(plugin, "starshard")

    fun createStarshard(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta!!

        // 태그
        meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 2.toByte())

        // 외형
        meta.setDisplayName("별조각")
        meta.lore = listOf("§7신비로운 힘이 담겨있다.")
        meta.setCustomModelData(2)

        item.itemMeta = meta
        return item
    }

    fun register() {
        val starshard = createStarshard()

        val recipeKey = NamespacedKey(plugin, "starshard_recipe")
        val recipe = ShapelessRecipe(recipeKey, starshard)

        recipe.addIngredient(Material.AMETHYST_SHARD)
        recipe.addIngredient(Material.COPPER_INGOT)
        recipe.addIngredient(Material.DIAMOND)

        Bukkit.addRecipe(recipe)
    }

    fun isStardust(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        val meta = item.itemMeta!!
        return meta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }
}