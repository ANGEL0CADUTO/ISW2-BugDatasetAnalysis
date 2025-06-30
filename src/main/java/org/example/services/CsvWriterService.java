package org.example.services;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.example.model.MethodData;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CsvWriterService implements AutoCloseable {

    private final CSVPrinter csvPrinter;
    // Intestazione basata sulle metriche di processo della slide 9 + identificatori
    private static final String[] HEADERS = {
            "Project", "Release", "Method", "Bugginess",
            "LOC", "LOC_added", "AVG_LOC_added", "MAX_LOC_added",
            "Churn", "AVG_Churn", "MAX_Churn",
            "NR", "NAuth", "NFix"
    };
    public CsvWriterService(String filePath) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));

        this.csvPrinter = new CSVPrinter(writer, CSVFormat.EXCEL.withHeader(HEADERS));
    }

    public void writeDataRow(MethodData dataRow, String projectName) throws IOException {
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