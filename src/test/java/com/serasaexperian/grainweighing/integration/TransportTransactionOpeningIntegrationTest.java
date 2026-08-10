package com.serasaexperian.grainweighing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.serasaexperian.grainweighing.registry.branch.BranchRequest;
import com.serasaexperian.grainweighing.registry.branch.BranchResponse;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeRequest;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeResponse;
import com.serasaexperian.grainweighing.registry.transaction.OpenTransportTransactionRequest;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionResponse;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionStatus;
import com.serasaexperian.grainweighing.registry.truck.TruckRequest;
import com.serasaexperian.grainweighing.registry.truck.TruckResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * LOG-020: uma TransportTransaction OPEN por truck, provado contra Postgres
 * real (não mockado) — o índice único parcial (V10) é a garantia de verdade,
 * OpenTransportTransactionUseCaseTest cobre a lógica de decisão isolada.
 */
class TransportTransactionOpeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private <T> T post(String path, Object body, Class<T> responseType) {
        ResponseEntity<T> response = restTemplate.postForEntity(path, body, responseType);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s returned %s, body=%s", path, response.getStatusCode(), response.getBody())
                .isTrue();
        return response.getBody();
    }

    private record Fixture(BranchResponse branch, TruckResponse truck, GrainTypeResponse grainType) {
    }

    private Fixture newFixture() {
        BranchResponse branch = post("/api/branches",
                new BranchRequest("Sorriso", "Sorriso", "MT"), BranchResponse.class);
        TruckResponse truck = post("/api/trucks",
                new TruckRequest("CNC" + UUID.randomUUID().toString().substring(0, 4), new BigDecimal("9000")),
                TruckResponse.class);
        GrainTypeResponse grainType = post("/api/grain-types",
                new GrainTypeRequest("Milho-" + UUID.randomUUID(), new BigDecimal("1200.00"),
                        new BigDecimal("50000.00")),
                GrainTypeResponse.class);
        return new Fixture(branch, truck, grainType);
    }

    private OpenTransportTransactionRequest openRequestFor(Fixture fixture) {
        return new OpenTransportTransactionRequest(fixture.truck().id(), fixture.grainType().id(), fixture.branch().id());
    }

    @Test
    void secondSequentialOpenAttemptWhileFirstIsStillOpenIsRejectedWith409() {
        Fixture fixture = newFixture();

        ResponseEntity<TransportTransactionResponse> first = restTemplate.postForEntity(
                "/api/transport-transactions", openRequestFor(fixture), TransportTransactionResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/transport-transactions", openRequestFor(fixture), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains(fixture.truck().id().toString());
    }

    @Test
    void newOpenTransactionSucceedsAfterThePreviousOneWasCancelled() {
        Fixture fixture = newFixture();

        TransportTransactionResponse first = post(
                "/api/transport-transactions", openRequestFor(fixture), TransportTransactionResponse.class);

        ResponseEntity<TransportTransactionResponse> cancelled = restTemplate.postForEntity(
                "/api/transport-transactions/" + first.id() + "/cancel", null, TransportTransactionResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(TransportTransactionStatus.CANCELLED);

        // A mesma restrição que bloqueou a 2ª tentativa OPEN no teste acima não
        // se aplica mais — o índice único parcial só enxerga linhas OPEN, e a
        // 1ª já é CANCELLED.
        ResponseEntity<TransportTransactionResponse> second = restTemplate.postForEntity(
                "/api/transport-transactions", openRequestFor(fixture), TransportTransactionResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String listUrl = UriComponentsBuilder.fromPath("/api/transport-transactions")
                .queryParam("truckId", fixture.truck().id())
                .queryParam("status", TransportTransactionStatus.OPEN)
                .toUriString();
        ResponseEntity<TransportTransactionResponse[]> openOnes =
                restTemplate.getForEntity(listUrl, TransportTransactionResponse[].class);
        assertThat(openOnes.getBody()).hasSize(1);
        assertThat(openOnes.getBody()[0].id()).isEqualTo(second.getBody().id());
    }

    @Test
    void concurrentOpenAttemptsForTheSameTruckProduceExactlyOneOpenTransaction() throws Exception {
        Fixture fixture = newFixture();

        int concurrentAttempts = 15;
        ExecutorService pool = Executors.newFixedThreadPool(concurrentAttempts);
        CountDownLatch ready = new CountDownLatch(concurrentAttempts);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Callable<Void>> tasks = IntStream.range(0, concurrentAttempts)
                .<Callable<Void>>mapToObj(i -> () -> {
                    ready.countDown();
                    release.await();
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "/api/transport-transactions", openRequestFor(fixture), String.class);
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        created.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflicts.incrementAndGet();
                    }
                    return null;
                })
                .collect(Collectors.toList());

        List<Future<Void>> futures = pool.invokeAll(tasks);
        ready.await(5, TimeUnit.SECONDS);
        release.countDown();
        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(created.get()).as("exactly one attempt should succeed").isEqualTo(1);
        assertThat(conflicts.get()).as("every other attempt should be rejected as 409").isEqualTo(concurrentAttempts - 1);

        String listUrl = UriComponentsBuilder.fromPath("/api/transport-transactions")
                .queryParam("truckId", fixture.truck().id())
                .queryParam("status", TransportTransactionStatus.OPEN)
                .toUriString();
        ResponseEntity<Object[]> openTransactions = restTemplate.getForEntity(listUrl, Object[].class);
        assertThat(openTransactions.getBody()).hasSize(1);
    }
}
