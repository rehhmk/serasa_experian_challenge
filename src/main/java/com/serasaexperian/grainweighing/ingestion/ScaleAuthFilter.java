package com.serasaexperian.grainweighing.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Valida X-Scale-Key antes do hot path (LOG-014). 401 genérico sem revelar se o
 * scaleId existe. A chave é comparada por SHA-256 contra {@link ScaleCredentialsCache}
 * — nunca contra o banco, para não violar o hot path (CLAUDE.md secao 4).
 */
public class ScaleAuthFilter extends OncePerRequestFilter {

    public static final String SCALE_KEY_HEADER = "X-Scale-Key";

    private final ScaleCredentialsCache credentialsCache;
    private final ObjectMapper objectMapper;

    public ScaleAuthFilter(ScaleCredentialsCache credentialsCache, ObjectMapper objectMapper) {
        this.credentialsCache = credentialsCache;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(SCALE_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request);
        cachingRequest.getInputStream().readAllBytes();
        byte[] body = cachingRequest.getContentAsByteArray();

        String scaleId = extractScaleId(body);
        if (scaleId == null || scaleId.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (!credentialsCache.matches(scaleId, sha256Hex(apiKey))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(new ReplayableBodyRequestWrapper(request, body), response);
    }

    private String extractScaleId(byte[] body) {
        try {
            return objectMapper.readValue(body, ScaleReadingRequest.class).id();
        } catch (IOException e) {
            return null;
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    /**
     * O corpo já foi integralmente lido (via {@link ContentCachingRequestWrapper}) para
     * extrair o {@code id} — o InputStream original está exaurido. Esse wrapper serve os
     * bytes já lidos de volta, para que o controller consiga desserializar o corpo
     * normalmente depois do filtro.
     */
    private static final class ReplayableBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        ReplayableBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ReplayableServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }
    }

    private static final class ReplayableServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        ReplayableServletInputStream(byte[] body) {
            this.buffer = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("async read not supported");
        }
    }
}
