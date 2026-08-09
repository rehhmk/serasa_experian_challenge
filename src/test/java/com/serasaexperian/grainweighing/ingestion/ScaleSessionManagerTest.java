package com.serasaexperian.grainweighing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ScaleSessionManagerTest {

    private static final StabilizationProperties CONFIG = new StabilizationProperties(
            20,     // minSamples
            0.8,    // minValidRatio
            30.0,   // maxStdDevKg
            100.0,  // maxRangeKg
            10.0,   // maxSlopeKgPerSec
            3000L,  // stabilityDurationMs
            20.0,   // scaleResolutionKg
            200.0,  // emptyThresholdKg
            1000L,  // emptyDurationMs
            20.0);  // outlierToleranceKg

    private final StabilizationEngine engine = new StabilizationEngine();

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
    void plateChangeMidWindowDiscardsWindowAndRestarts() {
        ScaleSessionManager manager = new ScaleSessionManager();
        ScaleSession session = manager.sessionFor("scale-42");

        for (int i = 0; i < 10; i++) {
            session.addReading("AAA0001", new WeightSample(i * 100L, 5000.0), CONFIG, engine);
        }
        assertThat(session.plate()).isEqualTo("AAA0001");

        WeightResult afterSwitch = session.addReading("BBB0002", new WeightSample(1000L, 1234.0), CONFIG, engine);
        assertThat(session.plate()).isEqualTo("BBB0002");
        assertThat(session.state()).isEqualTo(SessionState.COLLECTING);
        assertThat(afterSwitch.samplesUsed()).isEqualTo(1);

        WeightResult after10Bbb = null;
        for (int i = 1; i < 10; i++) {
            after10Bbb = session.addReading("BBB0002", new WeightSample(1000L + i * 100L, 1234.0), CONFIG, engine);
        }
        assertThat(after10Bbb.samplesUsed()).isEqualTo(10);
    }

    @Test
    void concurrentReadingsOnSameScaleAreSerializedBySessionLock() throws Exception {
        ScaleSession session = new ScaleSession("scale-99");
        int threadCount = 16;
        int readingsPerThread = 50;
        AtomicLong timestampSeq = new AtomicLong(0);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            futures.add(pool.submit(() -> {
                for (int r = 0; r < readingsPerThread; r++) {
                    long ts = timestampSeq.getAndAdd(100);
                    session.addReading("AAA0001", new WeightSample(ts, 5000.0), CONFIG, engine);
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(session.plate()).isEqualTo("AAA0001");
        assertThat(session.state()).isIn(SessionState.COLLECTING, SessionState.STABILIZING, SessionState.STABLE);
    }

    @Test
    void sessionResetsAfterWeightDropsNearZeroPostSave() {
        ScaleSession session = new ScaleSession("scale-01");

        for (int i = 0; i < 26; i++) {
            session.addReading("AAA0001", new WeightSample(i * 500L, 5000.0), CONFIG, engine);
        }
        assertThat(session.state()).isEqualTo(SessionState.STABLE);
        session.markRecorded();
        assertThat(session.state()).isEqualTo(SessionState.RECORDED);

        session.addReading("AAA0001", new WeightSample(13000L, 50.0), CONFIG, engine);
        assertThat(session.state()).isEqualTo(SessionState.RECORDED);

        session.addReading("AAA0001", new WeightSample(13500L, 50.0), CONFIG, engine);
        assertThat(session.state()).isEqualTo(SessionState.RECORDED);

        session.addReading("AAA0001", new WeightSample(14000L, 50.0), CONFIG, engine);
        assertThat(session.state()).isEqualTo(SessionState.COLLECTING);
        assertThat(session.plate()).isNull();
    }
}
