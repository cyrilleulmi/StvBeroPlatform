package ch.stvbero.platform.core.error;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntSupplier;

/** Testhilfe: schickt dieselbe Anfrage mehrfach gleichzeitig ab, wie bei einem Doppelklick. */
public final class Concurrently {

    private Concurrently() {
    }

    /** Liefert die Statuscodes aufsteigend sortiert. */
    public static List<Integer> statuscodes(int count, IntSupplier request) {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            runs.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return request.getAsInt();
            }));
        }
        start.countDown();
        return runs.stream().map(CompletableFuture::join).sorted().toList();
    }
}
