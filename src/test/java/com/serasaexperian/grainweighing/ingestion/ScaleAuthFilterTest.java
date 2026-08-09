package com.serasaexperian.grainweighing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ScaleAuthFilterTest {

    private static final String SCALE_ID = "scale-01";
    private static final String VALID_KEY = "dev-scale-01-key";
    private static final String OTHER_SCALE_ID = "scale-02";
    private static final String OTHER_SCALE_KEY = "dev-scale-02-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScaleCredentialsCache credentialsCache = new ScaleCredentialsCache();
    private final ScaleAuthFilter filter = new ScaleAuthFilter(credentialsCache, objectMapper);

    ScaleAuthFilterTest() {
        credentialsCache.put(SCALE_ID, ScaleAuthFilter.sha256Hex(VALID_KEY));
        credentialsCache.put(OTHER_SCALE_ID, ScaleAuthFilter.sha256Hex(OTHER_SCALE_KEY));
    }

    private MockHttpServletRequest readingRequest(String scaleId, String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/readings");
        request.setContentType("application/json");
        request.setContent(
                ("{\"id\":\"" + scaleId + "\",\"plate\":\"ABC1D23\",\"weight\":32010}").getBytes());
        if (apiKey != null) {
            request.addHeader(ScaleAuthFilter.SCALE_KEY_HEADER, apiKey);
        }
        return request;
    }

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
    void missingHeaderReturns401() throws Exception {
        MockHttpServletRequest request = readingRequest(SCALE_ID, null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void invalidKeyReturns401() throws Exception {
        MockHttpServletRequest request = readingRequest(SCALE_ID, "not-the-real-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void keyValidForAnotherScaleReturns401() throws Exception {
        MockHttpServletRequest request = readingRequest(SCALE_ID, OTHER_SCALE_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void unauthenticatedRequestNeverReachesScaleSessionManager() throws Exception {
        MockHttpServletRequest request = readingRequest(SCALE_ID, "not-the-real-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void validKeyReachesFilterChainWithReplayableBody() throws Exception {
        MockHttpServletRequest request = readingRequest(SCALE_ID, VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        ScaleReadingRequest forwarded = objectMapper.readValue(
                chain.getRequest().getInputStream().readAllBytes(), ScaleReadingRequest.class);
        assertThat(forwarded.id()).isEqualTo(SCALE_ID);
        assertThat(forwarded.plate()).isEqualTo("ABC1D23");
    }
}
