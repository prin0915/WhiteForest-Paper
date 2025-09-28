//package org.prin.magicEnch.avatar
//
//import org.bukkit.Bukkit
//import org.bukkit.event.EventHandler
//import org.bukkit.event.Listener
//import org.bukkit.event.player.PlayerJoinEvent
//import org.bukkit.event.player.PlayerQuitEvent
//
//class AvatarListener : Listener {
//
//    @EventHandler
//    fun onPlayerJoin(event: PlayerJoinEvent) {
//        val player = event.player
//        val avatar = AvatarManager.createAvatar(player)
//        AvatarManager.setActive(player, true)
//        player.sendMessage("아바타에 접속했습니다!")
//    }
//
//    @EventHandler
//    fun onPlayerQuit(event: PlayerQuitEvent) {
//        val player = event.player
//        AvatarManager.setActive(player, false)
//
//        // 1초 후 아바타 제거
//        Bukkit.getScheduler().runTaskLater(
//            Bukkit.getPluginManager().plugins.first { it.name == "MagicEnch" },
//            Runnable {
//                AvatarManager.removeAvatar(player)
//                Bukkit.broadcastMessage("${player.name}의 아바타 접속이 종료되어 인벤토리가 안전하지 않습니다!")
//            }, 20L
//        )
//    }
//}
