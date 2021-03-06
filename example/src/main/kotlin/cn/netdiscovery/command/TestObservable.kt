package cn.netdiscovery.command

import cn.netdiscovery.command.rxjava3.asObservable

/**
 *
 * @FileName:
 *          cn.netdiscovery.command.TestObservable
 * @author: Tony Shen
 * @date: 2020-05-21 00:14
 * @version: V1.0 <描述当前版本功能>
 */
fun main() {

    val cmd = CommandBuilder.buildCompositeCommand("ps aux | grep java")

    CommandExecutor.execute(cmd)
        .asObservable()
        .subscribe {

            val commandLine = cmd.string()
            val exitCode = it.exitValue()
            println("command line: $commandLine\nexecution finished with exit code: $exitCode\n\n")
        }
}