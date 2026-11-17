package ch.stvbero.platform.core.error;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntSupplier;

/** Test helper: sends the same request several times at once, as with a double click. */
public final class Concurrently {

    private Concurrently() {
    }

    /** Returns the status codes sorted ascending. */
    public static List<Integer> statusCodes(int count, IntSupplier request) {
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
