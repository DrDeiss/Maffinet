package io.maffinet.lab.transport;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class StartTicketsContract {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        LabStartTickets gate = new LabStartTickets();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch blocked = new CountDownLatch(1), entered = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        try {
            worker.submit(() -> { entered.countDown(); try { blocked.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            check(entered.await(2, TimeUnit.SECONDS), "worker blocked before admission");
            long first = gate.admitStart(), duplicate = gate.admitStart();
            worker.submit(() -> { if (gate.current(first)) starts.incrementAndGet(); });
            worker.submit(() -> { if (gate.current(duplicate)) starts.incrementAndGet(); });
            gate.cancelStarts(); // STOP is accepted while both old STARTs are queued.
            Future<?> cleaned = worker.submit(() -> check(!gate.current(first) && !gate.current(duplicate), "cleanup must not resurrect tickets"));
            long restart = gate.admitStart();
            Future<?> restarted = worker.submit(() -> { if (gate.current(restart)) starts.incrementAndGet(); });
            blocked.countDown(); cleaned.get(2, TimeUnit.SECONDS); restarted.get(2, TimeUnit.SECONDS);
            check(starts.get() == 1, "only START admitted after STOP executes");
            gate.cancelStarts(); // Same invalidation contract is used for revoke.
            check(!gate.current(restart), "active startup observes cancellation");
            long beforeDestroy = gate.admitStart(); gate.destroy();
            check(!gate.current(beforeDestroy) && !gate.current(gate.admitStart()), "destroy is terminal");
        } finally { blocked.countDown(); worker.shutdownNow(); check(worker.awaitTermination(2, TimeUnit.SECONDS), "worker reaped"); }
        System.out.println("{\"status\":\"passed\",\"queuedStartStopRestart\":true,\"revokeAndDestroy\":true,\"scope\":\"host-admission-only\",\"nativeTun\":\"not-tested\"}");
    }
}
