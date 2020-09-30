package cn.netdiscovery.command

import java.io.File
import java.io.IOException
import java.util.concurrent.*

/**
 *
 * @FileName:
 *          cn.netdiscovery.command.CommandExecutor
 * @author: Tony Shen
 * @date: 2020-05-19 17:17
 * @version: V1.0 <描述当前版本功能>
 */
object CommandExecutor {

    private val pb = ProcessBuilder()
    private var WORKERS = Executors.newCachedThreadPool()
    internal val NEW_LINE = System.getProperty("line.separator")

    /**
     * 支持自定义线程池，通过这个线程池来执行命令
     */
    @JvmStatic
    fun setWorkerExecutors(executorService: ExecutorService):CommandExecutor {
        this.WORKERS = executorService
        return this
    }

    @JvmStatic
    fun execute(cmdLine: String): ProcessResult = execute(CommandBuilder.buildRawCommand(cmdLine), null )

    @JvmOverloads
    @JvmStatic
    fun execute(cmdLine: String, directory: File?=null, appender: Appender): ProcessResult = execute(CommandBuilder.buildRawCommand(cmdLine), directory, ExecutionOutputPrinter(appender))

    @JvmOverloads
    @JvmStatic
    fun execute(cmd: Command, directory: File?=null, appender: Appender): ProcessResult = execute(cmd, directory, ExecutionOutputPrinter(appender))

    @JvmStatic
    inline fun execute(cmd: ()->Command): ProcessResult = execute(cmd.invoke())

    @JvmOverloads
    @JvmStatic
    fun execute(cmd: Command, directory: File?=null, outputPrinter: ExecutionOutputPrinter = ExecutionOutputPrinter.DEFAULT_OUTPUT_PRINTER): ProcessResult {

        try {
            val p = executeCommand(cmd, directory)
            recordOutput(p, outputPrinter)
            val futureReport = WORKERS.submit(ExecutionCallable(p, cmd))
            return ProcessResult(cmd, p, futureReport)
        } catch (e:UnrecognisedCmdException) {

            WORKERS.execute { outputPrinter.handleErrMessage(e.toString()) }

            val executionResult = object :ExecutionResult {
                override fun command() = cmd

                override fun exitValue() = -1
            }

            val futureReport = object :Future<ExecutionResult> {
                override fun cancel(mayInterruptIfRunning: Boolean) = true

                override fun isCancelled() = true

                override fun isDone() = true

                override fun get(): ExecutionResult = executionResult

                override fun get(timeout: Long, unit: TimeUnit): ExecutionResult = executionResult
            }

            return ProcessResult(cmd, null, futureReport)
        }
    }

    @JvmOverloads
    @JvmStatic
    @Throws(UnrecognisedCmdException::class)
    fun executeSync(cmd: Command, directory: File?=null, timeout:Long?=null,unit: TimeUnit?=null, appender: Appender): ProcessResult = executeSyncOutputPrinter(cmd, directory, timeout, unit, ExecutionOutputPrinter(appender))

    @JvmOverloads
    @JvmStatic
    @Throws(UnrecognisedCmdException::class)
    fun executeSyncOutputPrinter(cmd: Command, directory: File?=null, timeout:Long?=null,unit: TimeUnit?=null, outputPrinter: ExecutionOutputPrinter = ExecutionOutputPrinter.DEFAULT_OUTPUT_PRINTER): ProcessResult {
        val p = executeCommand(cmd, directory)

        val future1 = WORKERS.submit { outputPrinter.handleStdStream(p.inputStream) }
        val future2 =  WORKERS.submit { outputPrinter.handleErrStream(p.errorStream) }
        val futureReport = WORKERS.submit(ExecutionCallable(p, cmd))

        if (timeout!=null && unit!=null) {
            try {
                futureReport.get(timeout,unit)
                future1.get(timeout,unit)
                future2.get(timeout,unit)
            } catch (e:Exception) {
                p.destroyForcibly()
                try {
                    p.waitFor()
                } catch (e: InterruptedException) {
                    //do nothing.
                }
            }
        } else {
            futureReport.get()
            future1.get()
            future2.get()
        }

        return ProcessResult(cmd, p, futureReport)
    }

    @Throws(UnrecognisedCmdException::class)
    private fun executeCommand(cmd: Command, directory: File?): Process {
        synchronized(pb) {
            return try {
                pb.directory(directory)
                pb.command(cmd.executable())
                pb.start()
            } catch (e: IOException) {
                throw UnrecognisedCmdException(cmd.string())
            }
        }
    }

    private fun recordOutput(p: Process, outputPrinter: ExecutionOutputPrinter) {

        WORKERS.execute { outputPrinter.handleStdStream(p.inputStream) }
        WORKERS.execute { outputPrinter.handleErrStream(p.errorStream) }
    }

    private class ExecutionCallable(private val p: Process, private val cmd: Command) : Callable<ExecutionResult> {

        @Throws(Exception::class)
        override fun call(): ExecutionResult {
            try {
                p.waitFor()
            } catch (e: InterruptedException) {
                //nothing.
            }
            return ExecutionResult.makeReport(cmd, p.exitValue())
        }
    }
}