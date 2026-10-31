package com.yunx.app.util

import java.io.File
import java.util.concurrent.TimeUnit

/** macOS 专用的系统能力，统一走系统自带命令，不依赖额外原生库 */
object MacSupport {
    val isMac: Boolean = System.getProperty("os.name", "").lowercase().contains("mac")

    private var caffeinate: Process? = null

    private fun output(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) text else null
    }.getOrNull()

    fun open(vararg args: String): Boolean = runCatching {
        ProcessBuilder(listOf("open") + args).start()
        true
    }.getOrDefault(false)

    fun systemIsDark(): Boolean =
        output("defaults", "read", "-g", "AppleInterfaceStyle")?.trim().equals("Dark", ignoreCase = true)

    /** 下载期间阻止系统睡眠；进程随本应用退出（-w） */
    @Synchronized
    fun keepAwake(on: Boolean) {
        caffeinate?.destroy()
        caffeinate = null
        if (on) caffeinate = runCatching {
            ProcessBuilder("caffeinate", "-i", "-w", ProcessHandle.current().pid().toString()).start()
        }.getOrNull()
    }

    fun notify(title: String, message: String) {
        val quote = { s: String -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
        runCatching {
            ProcessBuilder(
                "osascript", "-e", "display notification ${quote(message)} with title ${quote(title)}"
            ).start()
        }
    }

    /** 当前 .app 路径；开发运行时为 null */
    fun appBundle(): File? = System.getProperty("jpackage.app-path")?.let { File(it) }
        ?.parentFile?.parentFile?.parentFile?.takeIf { it.name.endsWith(".app") }

    /** 等本进程退出后重新打开 .app */
    fun restart(): Boolean {
        val app = appBundle() ?: return false
        return runCatching {
            ProcessBuilder("/bin/sh", "-c", "sleep 2; open \"\$0\"", app.absolutePath).start()
            true
        }.getOrDefault(false)
    }

    /** 读取「系统设置 > 网络」里的 HTTP/HTTPS 代理；键值与 Windows 的 ProxyServer 解析共用 */
    fun systemProxy(): Triple<Boolean, String?, String?> {
        val map = output("scutil", "--proxy").orEmpty().lines().mapNotNull { line ->
            val parts = line.split(" : ", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
        val pac = map["ProxyAutoConfigURLString"]
            ?.takeIf { map["ProxyAutoConfigEnable"] == "1" }
        for (kind in listOf("HTTPS", "HTTP")) {
            if (map["${kind}Enable"] == "1") {
                val host = map["${kind}Proxy"] ?: continue
                val port = map["${kind}Port"] ?: continue
                return Triple(true, "$host:$port", pac)
            }
        }
        return Triple(false, null, pac)
    }
}
