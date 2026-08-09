package com.serasaexperian.grainweighing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.serasaexperian.grainweighing.ingestion.ScaleReadingRequest;
import com.serasaexperian.grainweighing.registry.branch.BranchRequest;
import com.serasaexperian.grainweighing.registry.branch.BranchResponse;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeRequest;
import com.serasaexperian.grainweighing.registry.grain.GrainTypeResponse;
import com.serasaexperian.grainweighing.registry.scale.ScaleCreatedResponse;
import com.serasaexperian.grainweighing.registry.scale.ScaleRequest;
import com.serasaexperian.grainweighing.registry.transaction.OpenTransportTransactionRequest;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionResponse;
import com.serasaexperian.grainweighing.registry.transaction.TransportTransactionStatus;
import com.serasaexperian.grainweighing.registry.truck.TruckRequest;
import com.serasaexperian.grainweighing.registry.truck.TruckResponse;
import com.serasaexperian.grainweighing.reports.ReportEnvelope;
import com.serasaexperian.grainweighing.reports.weighingbook.WeighingBookItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fluxo completo de LOG-012: OPEN TransportTransaction -> sequência de readings
 * -> STABLE -> exactly one Weighing -> TransportTransaction COMPLETED ->
 * net/cost/stock corretos. Usa os thresholds reduzidos de application-test.yml
 * (min-samples=5, stability-duration-ms=200) para não depender de segundos reais.
 */
class WeighingFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Confere o status HTTP imediatamente após cada chamada de setup, com o corpo
     * da resposta na mensagem de falha. Sem isso, uma falha numa chamada cedo
     * (ex: 500 transitório de conexão com o banco) deixa os IDs seguintes nulos e
     * só se manifesta muitos passos depois, como um erro confuso de deserialização
     * Jackson sem relação óbvia com a causa raiz real.
     */
    private <T> T post(String path, Object body, Class<T> responseType) {
        ResponseEntity<T> response = restTemplate.postForEntity(path, body, responseType);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s returned %s, body=%s", path, response.getStatusCode(), response.getBody())
                .isTrue();
        return response.getBody();
    }

    @Test
    void openTransactionThenStableReadingsProducesExactlyOneCompletedWeighing() throws InterruptedException {
        BranchResponse branch = post("/api/branches",
                new BranchRequest("Goiânia", "Goiânia", "GO"), BranchResponse.class);

        TruckResponse truck = post("/api/trucks",
                new TruckRequest("FLW9000", new BigDecimal("9000")), TruckResponse.class);

        GrainTypeResponse grainType = post("/api/grain-types",
                new GrainTypeRequest("Soja-" + UUID.randomUUID(), new BigDecimal("1800.00"),
                        new BigDecimal("100000.00")),
                GrainTypeResponse.class);

        String scaleId = "scale-flow-" + UUID.randomUUID();
        ScaleCreatedResponse scale = post("/api/scales",
                new ScaleRequest(scaleId, branch.id()), ScaleCreatedResponse.class);

        TransportTransactionResponse transaction = post("/api/transport-transactions",
                new OpenTransportTransactionRequest(truck.id(), grainType.id(), branch.id()),
                TransportTransactionResponse.class);
        assertThat(transaction.status()).isEqualTo(TransportTransactionStatus.OPEN);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Scale-Key", scale.apiKey());
        double grossWeightKg = 32000.0; // multiplo de scale-resolution-kg=20 (application-test.yml)

        for (int i = 0; i < 40; i++) {
            HttpEntity<ScaleReadingRequest> request = new HttpEntity<>(
                    new ScaleReadingRequest(scaleId, truck.plate(), grossWeightKg), headers);
            ResponseEntity<Void> response = restTemplate.exchange("/api/readings", HttpMethod.POST, request, Void.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            Thread.sleep(15); // acumula > stability-duration-ms=200 real, mesma logica do ScaleReadingControllerTest
        }

        TransportTransactionResponse completedTransaction = restTemplate.getForObject(
                "/api/transport-transactions/" + transaction.id(), TransportTransactionResponse.class);
        assertThat(completedTransaction.status()).isEqualTo(TransportTransactionStatus.COMPLETED);

        String reportUrl = UriComponentsBuilder.fromPath("/api/reports/weighings")
                .queryParam("from", Instant.now().minus(1, ChronoUnit.HOURS))
                .queryParam("to", Instant.now().plus(1, ChronoUnit.HOURS))
                .queryParam("branchId", branch.id())
                .toUriString();
        ResponseEntity<ReportEnvelope<WeighingBookItem>> report = restTemplate.exchange(
                reportUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        assertThat(report.getBody().data()).hasSize(1);

        WeighingBookItem weighing = report.getBody().data().get(0);
        assertThat(weighing.plate()).isEqualTo(truck.plate());
        assertThat(weighing.grossWeightKg()).isEqualByComparingTo("32000.00");
        assertThat(weighing.tareWeightKg()).isEqualByComparingTo("9000");
        assertThat(weighing.netWeightKg()).isEqualByComparingTo("23000.00"); // 32000 - 9000
        assertThat(weighing.cost()).isEqualByComparingTo("41400.00"); // 23 ton * 1800/ton
    }
}
