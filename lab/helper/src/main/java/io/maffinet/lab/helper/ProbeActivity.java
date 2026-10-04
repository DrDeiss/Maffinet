package io.maffinet.lab.helper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;

public final class ProbeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView output;
    private volatile boolean running;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);
        output = new TextView(this); output.setText(getPackageName() + "\nExplicit lab endpoint required. No automatic public probes."); view.addView(output);
        Spinner protocol = new Spinner(this);
        protocol.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"tcp", "udp", "tls", "https", "system-dns", "dns-udp", "dns-tcp"})); view.addView(protocol);
        EditText ip = new EditText(this); ip.setHint("Numeric lab IPv4 or IPv6"); view.addView(ip);
        EditText port = new EditText(this); port.setHint("Port"); port.setInputType(2); view.addView(port);
        EditText name = new EditText(this); name.setHint("Original TLS hostname / DNS QNAME"); view.addView(name);
        Button go = new Button(this); go.setText("Run one probe"); view.addView(go);
        go.setOnClickListener(v -> {
            try { run(protocol.getSelectedItem().toString(), ip.getText().toString(), Integer.parseInt(port.getText().toString()), name.getText().toString(), 1, 1); }
            catch (Exception e) { output.setText("Explicit numeric port required"); }
        });
        setContentView(view);
        fromIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); fromIntent(intent); }
    private void fromIntent(Intent intent) {
        if (!intent.getBooleanExtra("run", false)) return;
        run(intent.getStringExtra("protocol"), intent.getStringExtra("ip"), intent.getIntExtra("port", 0),
                intent.getStringExtra("name"), intent.getIntExtra("qtype", 1), intent.getIntExtra("count", 1));
    }
    private void run(String protocol, String ip, int port, String name, int qtype, int count) {
        if (running || count < 1 || count > 100) { output.setText("Busy or invalid count (1..100)"); return; }
        running = true;
        worker.execute(() -> {
            StringBuilder rows = new StringBuilder();
            File resultFile = new File(getFilesDir(), "p01-results.jsonl");
            for (int i = 0; i < count && !Thread.currentThread().isInterrupted(); i++) {
                JSONObject row = ProbeRunner.run(getPackageName(), protocol, ip, port, name, qtype);
                rows.append(row).append('\n');
                try { Files.write(resultFile.toPath(), rows.toString().getBytes(StandardCharsets.UTF_8)); }
                catch (Exception e) { runOnUiThread(() -> output.setText("Private result write failed")); break; }
                runOnUiThread(() -> output.setText(row.toString()));
            }
            running = false;
        });
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
