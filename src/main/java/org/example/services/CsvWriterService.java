// in src/main/java/org/example/services/CsvWriterService.java
package org.example.services;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.example.model.MethodMetrics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CsvWriterService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(CsvWriterService.class.getName());

    // --- MODIFICA 1: Rendi 'writer' e 'csvPrinter' campi della classe ---
    private final BufferedWriter writer;
    private final CSVPrinter csvPrinter;

    public CsvWriterService(String filePath) throws IOException {
        // Inizializza il writer come campo della classe
        this.writer = Files.newBufferedWriter(Paths.get(filePath));
        // Passa il writer al printer
        this.csvPrinter = new CSVPrinter(this.writer, CSVFormat.DEFAULT);
        LOGGER.log(Level.INFO, "CSV Writer inizializzato per: {0}", filePath);
    }

    public void writeHeader(String... headers) throws IOException {
        csvPrinter.printRecord((Object[]) headers);
        csvPrinter.flush();
    }

    public void writeDataRow(String projectName, String methodID, String releaseName,
                             MethodMetrics metrics, String bugginess) throws IOException {
        // La variabile 'record' è stata rinominata in 'dataRow'
        List<Object> dataRow = new ArrayList<>();
        dataRow.add(projectName);
        dataRow.add(methodID);
        dataRow.add(releaseName);

        List<Object> metricValues = metrics.toList();
        dataRow.addAll(metricValues);
        dataRow.add(bugginess);

        validateMetrics(methodID, releaseName, metricValues);

        csvPrinter.printRecord(dataRow);
    }

    private void validateMetrics(String methodID, String releaseName, List<Object> metricValues) {
        for (Object metricValue : metricValues) {
            if (metricValue instanceof Number numVal) {
                double value = numVal.doubleValue();
                if (value > 1_000_000 || Double.isInfinite(value) || Double.isNaN(value)) {
                    String errorMessage = String.format("Anomalia numerica rilevata per il metodo %s nella release %s", methodID, releaseName);
                    LOGGER.log(Level.SEVERE, "{0}. Valori metriche: {1}", new Object[]{errorMessage, metricValues});
                    throw new IllegalArgumentException(errorMessage);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            // Chiudi prima la risorsa che "avvolge" l'altra
            this.csvPrinter.flush();
            this.csvPrinter.close();
        } finally {
            // --- MODIFICA 2: Assicurati che anche il writer sia chiuso in un blocco finally ---
            // Questo garantisce la chiusura anche se csvPrinter.close() fallisce,
            // soddisfacendo pienamente Sonar.
            if (this.writer != null) {
                this.writer.close();
            }
        }
        LOGGER.info("CSV Writer chiuso correttamente.");
    }
}