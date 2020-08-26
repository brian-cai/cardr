package me.sohamgovande.cardr.util

fun getOSType(): OS? {
    val os = System.getProperty("os.name").toLowerCase()
    if (os.contains("win")) {
        return OS.WINDOWS
    } else if (os.contains("osx") || os.contains("mac") || os.contains("os x")) {
        return OS.MAC
    } else if (os.contains("nix") || os.contains("aix") || os.contains("nux")) {
        return OS.LINUX
    }
    return null
}

fun isWindows(): Boolean {
    return System.getProperty("os.name").toLowerCase().contains("win") 
}

fun isMac(): Boolean {
    val os = System.getProperty("os.name").toLowerCase()
    return (os.contains("osx") || os.contains("mac") || os.contains("os x")
}

fun isLinux(): Boolean {
    val os = System.getProperty("os.name").toLowerCase()
    return (os.contains("nix") || os.contains("aix") || os.contains("nux"))
}

fun getProcessorBits(): Int {
    return System.getProperty("sun.arch.data.model", "32").toInt()
}

enum class OS {
    WINDOWS, MAC, LINUX
}