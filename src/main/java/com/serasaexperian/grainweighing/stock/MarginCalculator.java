package com.serasaexperian.grainweighing.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Fórmula da margem de lucro — LOG-013. Calculada sob demanda nos relatórios
 * (não persistida na Weighing), porque depende do estoque atual, que muda a
 * cada transação.
 *
 * margin = maxMargin - (maxMargin - minMargin) * clamp(stock / referenceStock, 0, 1)
 */
@Component
public class MarginCalculator {

    private static final int SCALE = 10;

    public BigDecimal calculate(BigDecimal currentStockKg, BigDecimal referenceStockKg,
                                 BigDecimal minMargin, BigDecimal maxMargin) {
        BigDecimal stockRatio = referenceStockKg.signum() == 0
                ? BigDecimal.ONE
                : currentStockKg.divide(referenceStockKg, SCALE, RoundingMode.HALF_UP);
        BigDecimal clamped = clamp(stockRatio, BigDecimal.ZERO, BigDecimal.ONE);
        return maxMargin.subtract(maxMargin.subtract(minMargin).multiply(clamped));
    }

    public BigDecimal suggestedSalePricePerTon(BigDecimal purchasePricePerTon, BigDecimal margin) {
        return purchasePricePerTon.multiply(BigDecimal.ONE.add(margin));
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
