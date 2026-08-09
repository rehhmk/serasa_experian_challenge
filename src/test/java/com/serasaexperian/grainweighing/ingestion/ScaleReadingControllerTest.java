package com.serasaexperian.grainweighing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.serasaexperian.grainweighing.shared.BusinessRuleViolationException;
import com.serasaexperian.grainweighing.weighing.CompleteWeighingUseCase;
import com.serasaexperian.grainweighing.weighing.Weighing;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ScaleReadingControllerTest {

    private static final StabilizationProperties CONFIG = new StabilizationProperties(
            20, 0.8, 30.0, 100.0, 10.0, 3000L, 20.0, 200.0, 1000L, 20.0);

    /**
     * receive() gera timestamps via System.currentTimeMillis() (LOG: precisa ser o
     * relógio do servidor, não o do ESP32) — não há Clock injetável para controlar
     * isso em teste. Para exercitar o caminho STABLE de ponta a ponta sem depender
     * de 3s reais de sleep, usa thresholds bem mais permissivos/rápidos, análogo ao
     * que application-test.yml já faz para o teste de integração completo.
     */
    private static final StabilizationProperties FAST_CONFIG = new StabilizationProperties(
            3, 0.8, 1000.0, 1000.0, 1000.0, 20L, 1.0, 200.0, 200L, 1000.0);

    private static final String SCALE_ID = "scale-01";
    private static final String PLATE = "ABC1D23";

    @Mock
    private RawReadingBuffer rawReadingBuffer;
    @Mock
    private CompleteWeighingUseCase completeWeighingUseCase;

    private ScaleSessionManager sessionManager;
    private StabilizationEngine engine;

    @BeforeEach
    void setUp() {
        sessionManager = new ScaleSessionManager();
        engine = new StabilizationEngine();
    }

    private ScaleReadingController controllerWith(StabilizationProperties config) {
        return new ScaleReadingController(sessionManager, engine, config, rawReadingBuffer, completeWeighingUseCase);
    }

    @Test
    void unstableReadingReturnsAcceptedWithoutCompletingWeighing() {
        ScaleReadingController controller = controllerWith(CONFIG);

        ResponseEntity<Void> response = controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32010));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verifyNoInteractions(completeWeighingUseCase);
    }

    @Test
    void everyReadingIsOfferedToRawReadingBufferRegardlessOfStability() {
        ScaleReadingController controller = controllerWith(CONFIG);

        controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32010));
        controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32015));

        verify(rawReadingBuffer, times(2)).offer(any());
    }

    @Test
    void stableSequenceCompletesWeighingExactlyOnceAndMarksSessionRecorded() throws InterruptedException {
        ScaleReadingController controller = controllerWith(FAST_CONFIG);
        Weighing weighing = mock(Weighing.class);
        when(weighing.getTransportTransactionId()).thenReturn(UUID.randomUUID());
        when(weighing.getNetWeightKg()).thenReturn(BigDecimal.valueOf(24000));
        when(completeWeighingUseCase.complete(eq(SCALE_ID), eq(PLATE), any())).thenReturn(weighing);

        for (int i = 0; i < 3; i++) {
            controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32000));
        }
        Thread.sleep(40); // > FAST_CONFIG.stabilityDurationMs(20ms)
        controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32000));
        controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32000)); // pos-STABLE, nao deve reacionar

        verify(completeWeighingUseCase, times(1)).complete(eq(SCALE_ID), eq(PLATE), any());
        assertThat(sessionManager.sessionFor(SCALE_ID).state()).isEqualTo(SessionState.RECORDED);
    }

    @Test
    void completionFailureIsLoggedButStillReturnsAccepted() throws InterruptedException {
        ScaleReadingController controller = controllerWith(FAST_CONFIG);
        when(completeWeighingUseCase.complete(eq(SCALE_ID), eq(PLATE), any()))
                .thenThrow(new BusinessRuleViolationException("no OPEN transaction"));

        for (int i = 0; i < 3; i++) {
            controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32000));
        }
        Thread.sleep(40);
        ResponseEntity<Void> response = controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // sessao fica presa em STABLE (LOG-018) - markRecorded() nunca chamado apos falha
        assertThat(sessionManager.sessionFor(SCALE_ID).state()).isEqualTo(SessionState.STABLE);
    }

    @Test
    void plateChangeBeforeStableNeverReachesCompleteWeighingUseCase() {
        ScaleReadingController controller = controllerWith(FAST_CONFIG);

        controller.receive(new ScaleReadingRequest(SCALE_ID, PLATE, 32000));
        controller.receive(new ScaleReadingRequest(SCALE_ID, "ZZZ9Z99", 1000));

        verify(completeWeighingUseCase, never()).complete(any(), any(), any());
    }
}
