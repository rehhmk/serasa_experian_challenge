package com.serasaexperian.grainweighing.reports;

import java.util.List;

/** Contrato de resposta uniforme dos 4 relatórios MUST — LOG-015. */
public record ReportEnvelope<T>(ReportPeriod period, Object filters, List<T> data) {
}
