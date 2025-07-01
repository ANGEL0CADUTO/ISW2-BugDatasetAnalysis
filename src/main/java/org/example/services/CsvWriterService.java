// in src/main/java/org/example/services/CsvWriterService.java
package org.example.services;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
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

    public void writeDataRow(String projectName, String methodID, String releaseId,
                             String bugginess, List<Object> features) throws IOException {
        List<Object> record = new ArrayList<>();
        record.add(projectName);
        record.add(methodID);
        record.add(releaseId);
        record.add(bugginess);
        record.addAll(features);
        csvPrinter.printRecord(record);
    }

    public void close() throws IOException {
        csvPrinter.flush();
        csvPrinter.close();
    }
}