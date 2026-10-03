package io.maffinet.android.core.dpibypass

// HEV registers this Maffinet class through JNI_OnLoad. The audited fixed-width
// namespace rebind preserves its custom (String, Int, Boolean) tunnel ABI.

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
