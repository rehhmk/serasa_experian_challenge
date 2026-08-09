package com.serasaexperian.grainweighing.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Boundaries do LOG-013: estoque=0 -> 20%, estoque>=referencia -> 5%, ponto medio, clamp. */
class MarginCalculatorTest {

    private final MarginCalculator calculator = new MarginCalculator();

    private static final BigDecimal MIN_MARGIN = new BigDecimal("0.05");
    private static final BigDecimal MAX_MARGIN = new BigDecimal("0.20");
    private static final BigDecimal REFERENCE_STOCK = new BigDecimal("100000");

    @Test
    void zeroStockYieldsMaxMargin() {
        BigDecimal margin = calculator.calculate(BigDecimal.ZERO, REFERENCE_STOCK, MIN_MARGIN, MAX_MARGIN);
        assertThat(margin).isEqualByComparingTo("0.20");
    }

    @Test
    void stockAtReferenceYieldsMinMargin() {
        BigDecimal margin = calculator.calculate(REFERENCE_STOCK, REFERENCE_STOCK, MIN_MARGIN, MAX_MARGIN);
        assertThat(margin).isEqualByComparingTo("0.05");
    }

    @Test
    void stockAboveReferenceDoesNotExtrapolateBelowMinMargin() {
        BigDecimal aboveReference = REFERENCE_STOCK.multiply(BigDecimal.TEN);
        BigDecimal margin = calculator.calculate(aboveReference, REFERENCE_STOCK, MIN_MARGIN, MAX_MARGIN);
        assertThat(margin).isEqualByComparingTo("0.05");
    }

    @Test
    void halfReferenceStockYieldsMidpointMargin() {
        BigDecimal margin = calculator.calculate(
                new BigDecimal("50000"), REFERENCE_STOCK, MIN_MARGIN, MAX_MARGIN);
        assertThat(margin).isEqualByComparingTo("0.125");
    }

    @Test
    void negativeStockDoesNotExtrapolateBeyondMaxMargin() {
        BigDecimal margin = calculator.calculate(
                new BigDecimal("-1000"), REFERENCE_STOCK, MIN_MARGIN, MAX_MARGIN);
        assertThat(margin).isEqualByComparingTo("0.20");
    }

    @Test
    void suggestedSalePriceAppliesMarginOverPurchasePrice() {
        BigDecimal purchasePrice = new BigDecimal("1800.00");
        BigDecimal margin = new BigDecimal("0.10");
        BigDecimal salePrice = calculator.suggestedSalePricePerTon(purchasePrice, margin);
        assertThat(salePrice).isEqualByComparingTo("1980.00");
    }
}
