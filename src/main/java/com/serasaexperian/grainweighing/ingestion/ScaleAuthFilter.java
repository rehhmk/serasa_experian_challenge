package com.serasaexperian.grainweighing.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida X-Scale-Key antes do hot path (LOG-014). 401 genérico sem revelar se o
 * scaleId existe. A chave é comparada por SHA-256 contra {@link ScaleCredentialsCache}
 * — nunca contra o banco, para não violar o hot path (CLAUDE.md secao 4).
 */
public class ScaleAuthFilter extends OncePerRequestFilter {

    public static final String SCALE_KEY_HEADER = "X-Scale-Key";

    private final ScaleCredentialsCache credentialsCache;

    public ScaleAuthFilter(ScaleCredentialsCache credentialsCache) {
        this.credentialsCache = credentialsCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // TODO LOG-014: envolver a request num ContentCachingRequestWrapper (spring-web)
        // para ler o campo "id" do corpo JSON sem consumir o InputStream que o
        // controller ainda vai desserializar; hashear o header X-Scale-Key com
        // sha256Hex(); comparar via credentialsCache.matches(scaleId, hash) — deve
        // rejeitar chave válida de OUTRA balança (não autenticar cruzado); sem
        // header ou hash inválido -> response.sendError(401) e retornar sem chamar
        // filterChain.doFilter (ScaleSessionManager nunca deve ser tocado).
        throw new UnsupportedOperationException("TODO LOG-014: see doFilterInternal comment");
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
}
