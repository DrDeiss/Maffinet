package com.rupleide.netfix.core.dpibypass

// Binary compatibility only: upstream's bundled HEV libraries RegisterNatives
// against this exact class name and the custom (String, Int, Boolean) ABI.
// The applicationId, manifest components and remaining code belong to Maffinet.

object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int, isSmartTv: Boolean)

    @JvmStatic
    external fun TProxyStopService()

    @JvmStatic
    @Suppress("unused")
    external fun TProxyGetStats(): LongArray
}
