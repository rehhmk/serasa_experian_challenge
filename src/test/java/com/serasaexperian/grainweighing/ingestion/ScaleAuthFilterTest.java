package com.serasaexperian.grainweighing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ScaleAuthFilterTest {

    @Test
    void sha256HexIsDeterministicAndMatchesKnownVector() {
        assertThat(ScaleAuthFilter.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256HexOfDifferentInputsNeverCollideTrivially() {
        assertThat(ScaleAuthFilter.sha256Hex("dev-scale-01-key"))
                .isNotEqualTo(ScaleAuthFilter.sha256Hex("dev-scale-02-key"));
    }

    @Test
    @Disabled("TODO LOG-014: implementar ScaleAuthFilter.doFilterInternal()")
    void missingHeaderReturns401() {
    }

    @Test
    @Disabled("TODO LOG-014: implementar ScaleAuthFilter.doFilterInternal()")
    void invalidKeyReturns401() {
    }

    @Test
    @Disabled("TODO LOG-014: implementar ScaleAuthFilter.doFilterInternal() - nao autenticar cruzado")
    void keyValidForAnotherScaleReturns401() {
    }

    @Test
    @Disabled("TODO LOG-014: implementar ScaleAuthFilter.doFilterInternal()")
    void unauthenticatedRequestNeverReachesScaleSessionManager() {
    }
}
