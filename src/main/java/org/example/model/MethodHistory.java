package org.example.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MethodHistory {
    public final String file;
    public final String signature;
    public final Set<String> authors = new HashSet<>();
    public final Map<String, MethodMetrics> metricsPerRelease = new HashMap<>(); // Key: Release Name

    public MethodHistory(String file, String signature) {
        this.file = file;
        this.signature = signature;
    }
}