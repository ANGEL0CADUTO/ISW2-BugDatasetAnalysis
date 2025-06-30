package org.example.services;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.example.model.DataRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class CsvWriterService implements AutoCloseable {

    private final CSVPrinter csvPrinter;

    public CsvWriterService(String filePath) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
        // Intestazione basata sulle metriche di processo della slide 9 + identificatori
        List<String> headers = Arrays.asList(
                "Project", "Release", "Method", "Bugginess",
                "LOC", "LOC_added", "AVG_LOC_added", "MAX_LOC_added",
                "Churn", "AVG_Churn", "MAX_Churn",
                "NR", "NAuth", "NFix" // NumRevisions, NumAuthors, NumFixes
        );
        this.csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])));
    }

    public void writeDataRow(DataRow dataRow, String projectName) throws IOException {
        // Il metodo toCsvRow in DataRow prepara la lista nell'ordine corretto
        csvPrinter.printRecord(dataRow.toCsvRow(projectName));
    }

    @Override
    public void close() throws IOException {
        if (csvPrinter != null) {
            csvPrinter.flush();
            csvPrinter.close();
        }
    }
}