package com.serasaexperian.grainweighing.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container Postgres em modo "singleton" (padrão oficial do Testcontainers para
 * reuso entre múltiplas classes de teste): iniciado uma única vez no static
 * initializer, sem {@code @Testcontainers}/{@code @Container}. Essas anotações
 * escopam o ciclo de vida do container por CLASSE de teste mesmo quando o campo é
 * static numa superclasse comum — a primeira classe a terminar (ex:
 * ReportsIntegrationTest) para o container, deixando as próximas (ex:
 * WeighingFlowIntegrationTest, que reusa o mesmo ApplicationContext em cache)
 * com a URL do datasource apontando para uma porta morta. Descoberto via CI real
 * (connection refused permanente entre as duas classes, não um blip).
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
