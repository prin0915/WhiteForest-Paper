package org.prin.WhiteForest.recipes

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class StardustRecipe(private val plugin: JavaPlugin) {

    private val key = NamespacedKey(plugin, "stardust")

    fun createStardust(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta!!

        // 태그
        meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1.toByte())

        // 외형
        meta.setDisplayName("별가루")
        meta.lore = listOf("§7신비로운 힘이 담겨있다.")
        meta.setCustomModelData(1)

        item.itemMeta = meta
        return item
    }

    fun register() {
        val stardust = createStardust()

        val recipeKey = NamespacedKey(plugin, "stardust_recipe")
        val recipe = ShapelessRecipe(recipeKey, stardust)

        recipe.addIngredient(Material.GUNPOWDER)
        recipe.addIngredient(Material.GOLD_INGOT)

        Bukkit.addRecipe(recipe)
    }

    fun isStardust(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        val meta = item.itemMeta!!
        return meta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }
}
