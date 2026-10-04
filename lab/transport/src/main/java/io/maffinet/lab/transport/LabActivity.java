package io.maffinet.lab.transport;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.*;

public final class LabActivity extends Activity {
    private boolean failProtect, failBind;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        TextView text = new TextView(this);
        text.setText("P01 experiment. Only io.maffinet.lab.helper.selected enters TUN.\n" +
                "Control helper remains on physical network. Production Maffinet must be stopped.\n" +
                "Device/network acceptance is pending. Metadata stays in app-private storage.");
        layout.addView(text);
        CheckBox protect = new CheckBox(this); protect.setText("Inject external protect failure"); layout.addView(protect);
        CheckBox bind = new CheckBox(this); bind.setText("Inject external bind failure"); layout.addView(bind);
        Button start = new Button(this); start.setText("Start selected helper TUN"); layout.addView(start);
        start.setOnClickListener(v -> {
            failProtect = protect.isChecked(); failBind = bind.isChecked();
            Intent consent = VpnService.prepare(this);
            if (consent == null) startLab(); else startActivityForResult(consent, 1);
        });
        Button stop = new Button(this); stop.setText("Stop"); layout.addView(stop);
        stop.setOnClickListener(v -> startService(new Intent(this, LabVpnService.class).setAction("STOP")));
        Button inspect = new Button(this); inspect.setText("Show lab events"); layout.addView(inspect);
        inspect.setOnClickListener(v -> text.setText(LabVpnService.EVENTS.snapshot()));
        setContentView(layout);
        command(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); command(intent); }
    private void command(Intent intent) {
        String operation = intent.getStringExtra("command");
        if ("STOP".equals(operation)) startService(new Intent(this, LabVpnService.class).setAction("STOP"));
        else if ("START".equals(operation)) {
            failProtect = intent.getBooleanExtra("failProtect", false);
            failBind = intent.getBooleanExtra("failBind", false);
            Intent consent = VpnService.prepare(this);
            if (consent == null) startLab(); else startActivityForResult(consent, 1);
        }
    }
    private void startLab() {
        startForegroundService(new Intent(this, LabVpnService.class).setAction("START")
                .putExtra("failProtect", failProtect).putExtra("failBind", failBind));
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1 && result == RESULT_OK) startLab();
    }
}
