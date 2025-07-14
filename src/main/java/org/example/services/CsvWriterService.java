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

public class CsvWriterService {
    private final CSVPrinter csvPrinter;

    public CsvWriterService(String filePath) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
        this.csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
        System.out.println("CSV Writer inizializzato per: " + filePath);
    }

    public void writeHeader(String... headers) throws IOException {
        csvPrinter.printRecord((Object[]) headers);
        csvPrinter.flush();
    }


    public void writeDataRow(String projectName, String methodID, String releaseName,
                             MethodMetrics metrics, String bugginess) throws IOException {
        List<Object> record = new ArrayList<>();
        record.add(projectName);
        record.add(methodID);
        record.add(releaseName);

        List<Object> metricValues = metrics.toList();
        record.addAll(metricValues);
        record.add(bugginess);

        // --- DEBUG ---
        boolean hasAnomaly = false;
        for (Object val : metricValues) {
            if (val instanceof Number) {
                double numVal = ((Number) val).doubleValue();
                // Controlla per valori enormi o non validi
                if (numVal > 1000000 || Double.isInfinite(numVal) || Double.isNaN(numVal)) {
                    hasAnomaly = true;
                    break;
                }
            }
        }

        if (hasAnomaly) {
            System.err.println("!!! ANOMALIA NUMERICA RILEVATA !!!");
            System.err.println("MethodID: " + methodID);
            System.err.println("ReleaseID: " + releaseName);
            System.err.println("Valori metriche grezzi: " + metricValues);
            throw new RuntimeException("Anomalia numerica rilevata per il metodo " + methodID);
        }
        // ---------------------------------------------

        csvPrinter.printRecord(record);
    }

    public void close() throws IOException {
        csvPrinter.flush();
        csvPrinter.close();
    }
}