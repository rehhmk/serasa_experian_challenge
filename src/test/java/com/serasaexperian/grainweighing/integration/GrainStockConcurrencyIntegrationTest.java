package com.serasaexperian.grainweighing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.serasaexperian.grainweighing.stock.GrainStock;
import com.serasaexperian.grainweighing.stock.GrainStockRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prova de regressão para o lost update descrito na revisão: dois "CompleteWeighingUseCase.complete()"
 * concorrentes (cada um sua própria transação curta, como em produção) incrementando o mesmo
 * branch/grainType não podem perder incremento um do outro. Antes da correção, o incremento era
 * read-modify-write via entidade gerenciada (dirty checking) — cada transação lia o mesmo valor
 * inicial e a última a comitar sobrescrevia a soma da outra. Agora é um UPDATE atômico no
 * Postgres (GrainStockRepository.increaseAvailableQuantity).
 */
class GrainStockConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GrainStockRepository grainStockRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentIncrementsOfTheSameStockRowAreNotLost() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID grainTypeId = UUID.randomUUID();
        BigDecimal initialQuantityKg = new BigDecimal("1000.00");
        BigDecimal deltaKg = new BigDecimal("10.00");
        int concurrentFinalizations = 20;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status ->
                grainStockRepository.save(new GrainStock(UUID.randomUUID(), branchId, grainTypeId, initialQuantityKg)));

        ExecutorService pool = Executors.newFixedThreadPool(concurrentFinalizations);
        CountDownLatch allThreadsReady = new CountDownLatch(concurrentFinalizations);
        CountDownLatch releaseThreads = new CountDownLatch(1);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrentFinalizations; i++) {
            tasks.add(() -> {
                allThreadsReady.countDown();
                releaseThreads.await();
                // cada finalização de pesagem abre e comita a sua própria transação curta,
                // exatamente como CompleteWeighingUseCase.complete() faz em produção.
                transactionTemplate.executeWithoutResult(status ->
                        grainStockRepository.increaseAvailableQuantity(branchId, grainTypeId, deltaKg));
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        allThreadsReady.await(5, TimeUnit.SECONDS);
        releaseThreads.countDown();
        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        GrainStock stock = grainStockRepository.findByBranchIdAndGrainTypeId(branchId, grainTypeId).orElseThrow();
        BigDecimal expected = initialQuantityKg.add(deltaKg.multiply(BigDecimal.valueOf(concurrentFinalizations)));
        assertThat(stock.getAvailableQuantityKg()).isEqualByComparingTo(expected);
    }
}
