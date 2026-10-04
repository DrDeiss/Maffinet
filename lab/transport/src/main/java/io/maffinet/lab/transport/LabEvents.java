package io.maffinet.lab.transport;

import android.os.SystemClock;
import org.json.JSONObject;
import java.util.ArrayDeque;

final class LabEvents {
    private final ArrayDeque<String> rows = new ArrayDeque<>();
    private long generation;
    private long dropped;
    synchronized void generation(long value) { generation = value; rows.clear(); dropped = 0; }
    synchronized void add(String kind, Object... values) {
        try {
            JSONObject row = new JSONObject().put("kind", kind).put("generation", generation)
                    .put("elapsedMs", SystemClock.elapsedRealtime());
            for (int i = 0; i < values.length; i += 2) row.put(values[i].toString(), values[i + 1]);
            if (rows.size() == 512) { rows.removeFirst(); dropped++; }
            rows.addLast(row.toString());
        } catch (Exception ignored) { dropped++; }
    }
    synchronized String snapshot() {
        return "{\"kind\":\"snapshot\",\"generation\":" + generation + ",\"elapsedMs\":" + SystemClock.elapsedRealtime() +
                ",\"dropped\":" + dropped + "}\n" + String.join("\n", rows) + "\n";
    }
}
