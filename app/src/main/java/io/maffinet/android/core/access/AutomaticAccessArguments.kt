package io.maffinet.android.core.access

/** Short connection-level fallback, independent of a service/domain catalog. */
object AutomaticAccessArguments {
    const val POLICY_VERSION = "auto-access-v1"

    fun create(ip: String, port: Int, observeHosts: Boolean = true): Array<String> {
        require(port in 1..65535)
        val args = mutableListOf("ciadpi", "-i$ip", "-p$port", "-c512", "-b65536")
        if (observeHosts) args.add("--auto-access")
        // Reconnect only TLS handshake candidates. Plain HTTP is never replayed here.
        args.addAll(listOf("-Kt", "-o1", "-r-5+se", "-R1",
            "-At,r,s,c", "-Kt", "-d1", "-s1+s", "-r1+s", "--group-pacing=20", "-R1",
            "-At,r,s,c", "-Kt", "-s1+s", "-r1+s", "--group-pacing=20", "-R1",
            "-At,r,s,c", "-Kt", "-s2", "-r2", "--group-pacing=20", "-R1",
            "-At,r,s,c", "-Kt", "-R1", // ordinary TLS is the last handshake candidate
            "-An", "-Kh", "-s1", "-R1",
            "-An")) // UDP and other protocols retain ordinary forwarding.
        return args.toTypedArray()
    }
}
