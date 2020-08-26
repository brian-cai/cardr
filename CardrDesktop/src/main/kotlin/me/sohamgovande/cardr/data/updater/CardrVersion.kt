package me.sohamgovande.cardr.data.updater

import me.sohamgovande.cardr.util.OS
import me.sohamgovande.cardr.util.getOSType

data class CardrVersion(
    val name: String,
    val build: Int,
    val urlWindows: String,
    val urlMacOS: String,
    val downloadFileWin: String?,
    val downloadFileMac: String?,
    val finalFileMac: String?,
    val disabledMac: Boolean?,
    val disabledWindows: Boolean?
) {
    fun isAutoUpdaterEnabled(): Boolean {
        return if (isMac())
            disabledMac == null || !disabledMac
        else
            disabledWindows == null || !disabledWindows
    }

    fun getURL(): String {
        return if (isMac()) urlMacOS else urlWindows
    }

    fun shouldExtract(): Boolean = isMac()

    fun getDownloadFilename(): String {
        return if (isMac()) {
            downloadFileMac ?: "cardr-${name}.zip"
        }  else {
            downloadFileWin ?: "cardr-${name}.msi"
        }
    }

    fun getFinalFilename(): String {
        return if (isMac()) {
            finalFileMac ?: "cardr-${name}.pkg"
        }  else {
            downloadFileWin ?: "cardr-${name}.msi"
        }
    }
}
