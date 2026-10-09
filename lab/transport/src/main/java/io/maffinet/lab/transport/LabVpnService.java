package io.maffinet.lab.transport;

import android.app.*;
import android.content.Intent;
import android.net.*;
import android.os.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;

/** Isolated P01 owner. Deliberately absent from production app manifest/routes. */
public final class LabVpnService extends VpnService {
    static final String SELECTED = "io.maffinet.lab.helper.selected";
    static final LabEvents EVENTS = new LabEvents();
    private final ScheduledExecutorService lifecycle = Executors.newSingleThreadScheduledExecutor();
    private final LabStartTickets starts = new LabStartTickets();
    private ParcelFileDescriptor tun;
    private NativeTransport transport;
    private LabSocksServer relay;
    private long generation;
    private boolean failedStop;
    private String state = "Idle";

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "STOP".equals(intent.getAction())) {
            starts.cancelStarts();
            lifecycle.execute(this::stopLab);
        } else if ("START".equals(intent.getAction())) {
            foreground();
            // Capture admission before enqueueing. Cleanup never makes an old ticket valid.
            long ticket = starts.admitStart();
            boolean protectFailure = intent.getBooleanExtra("failProtect", false);
            boolean bindFailure = intent.getBooleanExtra("failBind", false);
            lifecycle.execute(() -> startLab(ticket, protectFailure, bindFailure));
        }
        return START_NOT_STICKY;
    }
    private void foreground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("p01", "P01 transport lab", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, LabActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "p01").setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Maffinet P01 lab").setContentText("Selected helper only").setContentIntent(open).build());
    }
    private void state(String value, String reason) {
        state = value;
        String[] descriptors = new File("/proc/self/fd").list();
        EVENTS.add("state", "state", value, "reason", reason,
                "fdCount", descriptors == null ? -1 : descriptors.length);
        snapshot();
    }
    private void snapshot() {
        try { Files.write(new File(getFilesDir(), "p01-events.jsonl").toPath(), EVENTS.snapshot().getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { EVENTS.add("snapshot-error", "reason", e.getClass().getSimpleName()); }
    }
    private void startLab(long ticket, boolean failProtect, boolean failBind) {
        if (!starts.current(ticket)) { EVENTS.add("start-cancelled", "ticket", ticket); return; }
        if (failedStop) { state("Failed", "Previous resources still owned"); return; }
        if (tun != null) { EVENTS.add("start-ignored", "reason", "Session already owned"); return; }
        generation++; EVENTS.generation(generation); state("Starting", "Consent granted");
        try {
            // A queued STOP may have removed the notification after this START was admitted.
            foreground();
            if (prepare(this) != null) throw new IllegalStateException("VPN consent missing");
            getPackageManager().getApplicationInfo(SELECTED, 0); // Absent selected helper is a hard error.
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            Network physical = cm.getActiveNetwork();
            NetworkCapabilities capabilities = physical == null ? null : cm.getNetworkCapabilities(physical);
            if (capabilities == null || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                throw new IllegalStateException("Start with a physical active network and no other VPN");
            LinkProperties links = cm.getLinkProperties(physical);
            if (links == null || links.getDnsServers().isEmpty()) throw new IllegalStateException("Physical DNS unavailable");
            Builder builder = new Builder().setSession("P01 selected helper").setMtu(1500)
                    .addAddress("198.18.0.1", 32).addAddress("fd00:1::1", 128)
                    .addRoute("0.0.0.0", 0).addRoute("::", 0).addAllowedApplication(SELECTED)
                    .setUnderlyingNetworks(new Network[]{physical});
            for (java.net.InetAddress dns : links.getDnsServers()) builder.addDnsServer(dns);
            relay = new LabSocksServer(new PhysicalSockets(this, physical, EVENTS, failProtect, failBind), EVENTS);
            tun = builder.establish();
            if (tun == null) throw new IllegalStateException("TUN establish rejected");
            transport = new NativeTransport(this, EVENTS);
            String config = "tunnel:\n  mtu: 1500\n  ipv4: 198.18.0.1\n  ipv6: 'fd00:1::1'\n" +
                    "socks5:\n  address: 127.0.0.1\n  port: " + relay.port() + "\n  udp: 'udp'\n" +
                    "misc:\n  max-session-count: 16\n  task-stack-size: 131072\n  connect-timeout: 8000\n" +
                    "  tcp-read-write-timeout: 8000\n  udp-read-write-timeout: 8000\n  log-level: error\n";
            int accepted = transport.start(config, tun.getFd());
            if (accepted != 0) throw new IllegalStateException("Native start " + accepted);
            long deadline = SystemClock.elapsedRealtime() + 2000;
            while (starts.current(ticket) && transport.status() == 1 && SystemClock.elapsedRealtime() < deadline) Thread.sleep(10);
            if (!starts.current(ticket)) { stopLab(); return; }
            if (transport.status() != 2) throw new IllegalStateException("Native readiness " + transport.status());
            state("Running", "Native TUN loop ready; forwarding not yet verified");
            long session = generation;
            lifecycle.schedule(() -> monitor(session, ticket), 250, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            EVENTS.add("start-error", "reason", e.getClass().getSimpleName(), "detail", String.valueOf(e.getMessage()));
            stopLab();
            if (!failedStop) state("Failed", "Start failed; resources released");
        }
    }
    private void monitor(long session, long ticket) {
        if (session != generation || !starts.current(ticket) || transport == null) return;
        if (transport.status() != 2) {
            EVENTS.add("native-exit", "outcome", transport.status());
            stopLab(); if (!failedStop) state("Failed", "Native worker exited");
        } else {
            snapshot();
            lifecycle.schedule(() -> monitor(session, ticket), 250, TimeUnit.MILLISECONDS);
        }
    }
    private void stopLab() {
        state("Stopping", "STOP/revoke/cleanup");
        boolean relayStopped = relay == null || relay.stop();
        if (relayStopped) relay = null;
        int result = transport == null ? 0 : transport.stop();
        if (result != -20) transport = null;
        if (!relayStopped || result == -20) {
            failedStop = true;
            state("Failed", "STOP timeout; " + (!relayStopped ? "relay " : "") +
                    (result == -20 ? "native " : "") + "ownership/TUN retained, restart refused");
            return;
        }
        failedStop = false;
        if (tun != null) { try { tun.close(); } catch (Exception e) { EVENTS.add("tun-close-error"); } tun = null; }
        state("Idle", "Native reaped, TUN closed; outcome " + result);
        stopForeground(STOP_FOREGROUND_REMOVE);
        // Keep service owner alive for explicit restart; no restored desired state.
    }
    @Override public void onRevoke() { starts.cancelStarts(); lifecycle.execute(this::stopLab); }
    @Override public void onDestroy() {
        starts.destroy();
        lifecycle.execute(this::stopLab);
        lifecycle.shutdown();
        super.onDestroy();
    }
}
