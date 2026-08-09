package com.serasaexperian.grainweighing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ScaleSessionManagerTest {

    @Test
    void sessionForCreatesOneSessionPerScaleIdAndReusesIt() {
        ScaleSessionManager manager = new ScaleSessionManager();
        ScaleSession sessionA = manager.sessionFor("scale-01");
        ScaleSession sessionB = manager.sessionFor("scale-02");

        assertThat(sessionA).isNotSameAs(sessionB);
        assertThat(manager.sessionFor("scale-01")).isSameAs(sessionA);
    }

    @Test
    void resetOnUnknownScaleIdIsNoop() {
        ScaleSessionManager manager = new ScaleSessionManager();
        manager.reset("scale-never-seen");
    }

    @Test
    void concurrentSessionForOnDifferentScalesNeverCollide() throws InterruptedException {
        ScaleSessionManager manager = new ScaleSessionManager();
        int scaleCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(scaleCount);
        Set<ScaleSession> seen = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < scaleCount; i++) {
            String scaleId = "scale-" + i;
            pool.submit(() -> {
                seen.add(manager.sessionFor(scaleId));
                ready.countDown();
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(seen).hasSize(scaleCount);
    }

    @Test
    @Disabled("TODO LOG-016: implementar ScaleSession.addReading() - troca de plate mid-window")
    void plateChangeMidWindowDiscardsWindowAndRestarts() {
    }

    @Test
    @Disabled("TODO LOG-007: implementar ScaleSession.addReading()")
    void concurrentReadingsOnSameScaleAreSerializedBySessionLock() {
    }

    @Test
    @Disabled("TODO LOG-016: implementar ScaleSession.addReading() - reset apos peso cair perto de zero pos-SAVE")
    void sessionResetsAfterWeightDropsNearZeroPostSave() {
    }
}
