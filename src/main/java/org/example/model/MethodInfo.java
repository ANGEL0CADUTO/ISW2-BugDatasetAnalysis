package org.example.model;

// package com.yourdomain.datasetgenerator.model;

public class MethodInfo {
    private String name;
    private String signature; // Es. "myMethod(int, String)"
    private int startLine;
    private int endLine;
    private String body; // Il corpo del metodo, se necessario per alcune features
    // Aggiungi altri campi se servono per le features (es. numero parametri, complessità ciclomatica calcolata qui)

    public MethodInfo(String name, String signature, int startLine, int endLine, String body) {
        this.name = name;
        this.signature = signature;
        this.startLine = startLine;
        this.endLine = endLine;
        this.body = body;
    }

    public String getName() { return name; }
    public String getSignature() { return signature; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    public String getBody() { return body; }

    @Override
    public String toString() {
        return "MethodInfo{" + "signature='" + signature + '\'' + ", lines=" + startLine + "-" + endLine + '}';
    }
}