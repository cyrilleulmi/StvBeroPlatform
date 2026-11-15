package ch.stvbero.platform.kern.fehler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntSupplier;

/** Testhilfe: schickt dieselbe Anfrage mehrfach gleichzeitig ab, wie bei einem Doppelklick. */
public final class Gleichzeitig {

    private Gleichzeitig() {
    }

    /** Liefert die Statuscodes aufsteigend sortiert. */
    public static List<Integer> statuscodes(int anzahl, IntSupplier anfrage) {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> laeufe = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            laeufe.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return anfrage.getAsInt();
            }));
        }
        start.countDown();
        return laeufe.stream().map(CompletableFuture::join).sorted().toList();
    }
}
