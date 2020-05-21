package cn.netdiscovery.command

/**
 *
 * @FileName:
 *          cn.netdiscovery.command.Adb
 * @author: Tony Shen
 * @date: 2020-05-20 21:02
 * @version: V1.0 <描述当前版本功能>
 */
fun main() {

    val cmd = CommandBuilder("adb").addArg("devices").build()

    try {
        val pResult = CommandExecutor.execute(cmd, appender = object : Appender{
            override fun appendStdText(text: String) {
                println(text)
            }

            override fun appendErrText(text: String) {
                System.err.println(text)
            }
        })

        pResult.getExecutionResult()?.let {
            val commandLine = cmd.string()
            val exitCode = it.exitValue()
            println("command line: $commandLine\nexecution finished with exit code: $exitCode\n\n")
        }

    } catch (e: UnrecognisedCmdException) {
        System.err.println(e)
    }
}