package org.prin.WhiteForest.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Command : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있습니다!")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§c사용법: /whiteForest <magic|ench|...>")
            return true
        }

        when (args[0].lowercase()) {
            "magic" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c사용법: /whiteForest magic <force|...>")
                    return true
                }

                when (args[1].lowercase()) {
                    "force" -> {
                        if (args.size < 3) {
                            sender.sendMessage("§c사용법: /whiteForest magic force <set|add|reset>")
                            return true
                        }

                        when (args[2].lowercase()) {
                            "set" -> {
                                val num = args.getOrNull(3)?.toIntOrNull()
                                if (num == null) {
                                    sender.sendMessage("§c숫자를 입력해주세요! (/whiteForest magic force set <숫자>)")
                                } else {
                                    sender.sendMessage("§aForce를 §e${num}§a 으로 설정했습니다!")
                                    // 여기에 로직 추가
                                }
                            }
                            "add" -> {
                                val num = args.getOrNull(3)?.toIntOrNull()
                                if (num == null) {
                                    sender.sendMessage("§c숫자를 입력해주세요! (/whiteForest magic force add <숫자>)")
                                } else {
                                    sender.sendMessage("§aForce에 §e${num}§a 만큼 추가했습니다!")
                                    // 여기에 로직 추가
                                }
                            }
                            "reset" -> {
                                sender.sendMessage("§eForce를 초기화했습니다!")
                                // 초기화 로직
                            }
                            else -> sender.sendMessage("§c잘못된 하위 명령어입니다. (set|add|reset)")
                        }
                    }
                    else -> sender.sendMessage("§c잘못된 magic 명령어입니다.")
                }
            }

            "ench" -> {
                sender.sendMessage("§dEnch 명령어 실행됨!")
                // 여기서 ench 관련 기능 실행
            }

            else -> sender.sendMessage("§c알 수 없는 명령어입니다. (/whiteForest magic | ench)")
        }
        return true
    }
}
