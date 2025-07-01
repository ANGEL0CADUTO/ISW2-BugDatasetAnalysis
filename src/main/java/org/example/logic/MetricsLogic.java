// in src/main/java/org/example/logic/MetricsLogic.java
package org.example.logic;

import org.example.model.MethodData;
import org.example.model.MethodHistory;
import org.example.model.MethodMetrics;
import org.example.services.FeatureExtractor;
import java.util.List;

public class MetricsLogic {
    private final FeatureExtractor featureExtractor;

    public MetricsLogic() {
        this.featureExtractor = new FeatureExtractor();
    }

    /**
     * Calcola tutte le metriche (complessità + change) per un metodo.
     * @param methodData La versione specifica del metodo in una release.
     * @param history La storia completa del metodo.
     * @return Un oggetto MethodMetrics contenente tutte le feature calcolate.
     */
    public MethodMetrics calculateMetrics(MethodData methodData, MethodHistory history) {
        MethodMetrics metrics = new MethodMetrics();

        // 1. Calcola e imposta le 5 feature di complessità
        List<Object> complexityFeatures = featureExtractor.extractComplexityFeatures(methodData);
        metrics.setComplexityMetrics(complexityFeatures);

        // 2. Calcola e imposta le 5 feature di change dalla storia
        metrics.setChangeMetrics(history);

        return metrics;
    }
}