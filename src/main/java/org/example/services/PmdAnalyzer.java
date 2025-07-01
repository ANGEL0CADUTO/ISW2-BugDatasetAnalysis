// in src/main/java/org/example/services/PmdAnalyzer.java
package org.example.services;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths; // <-- IMPORT AGGIUNTO
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PmdAnalyzer {
    private final PMDConfiguration config;
    private static final Logger LOGGER = LoggerFactory.getLogger(PmdAnalyzer.class);

    public PmdAnalyzer() {
        config = new PMDConfiguration();
        config.addRuleSet("category/java/bestpractices.xml");
        config.addRuleSet("category/java/design.xml");
        config.addRuleSet("category/java/codestyle.xml");

        config.setDefaultLanguageVersion(
                LanguageRegistry.PMD.getLanguageVersionById("java", "17"));
    }

    public Map<String, Integer> countSmellsPerMethod(Path releaseDir) {
        LOGGER.info("Avvio analisi PMD sulla directory: {}", releaseDir);
        Map<String, Integer> smellsPerMethod = new HashMap<>();

        PMDConfiguration releaseConfig = new PMDConfiguration();
        releaseConfig.setRuleSets(new ArrayList<>(config.getRuleSetPaths()));
        releaseConfig.setMinimumPriority(RulePriority.LOW);
        releaseConfig.setDefaultLanguageVersion(
                LanguageRegistry.PMD.getLanguageVersionById("java", "17"));
        releaseConfig.setInputPathList(List.of(releaseDir));

        try (PmdAnalysis analysis = PmdAnalysis.create(releaseConfig)) {
            Report report = analysis.performAnalysisAndCollectReport();

            LOGGER.info("Analisi PMD completata. Trovate {} violazioni.", report.getViolations().size());

            for (RuleViolation violation : report.getViolations()) {
                // --- QUESTA È LA RIGA CORRETTA ---
                Path absoluteFilePath = Paths.get(violation.getFileId().getOriginalPath());

                int beginLine = violation.getBeginLine();
                int endLine = violation.getEndLine();

                String methodID = findMethodIDForViolation(absoluteFilePath, beginLine, endLine, releaseDir);
                if (methodID != null) {
                    smellsPerMethod.put(methodID, smellsPerMethod.getOrDefault(methodID, 0) + 1);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Errore critico durante l'analisi PMD", e);
        }

        LOGGER.info("Smells mappati a {} metodi unici.", smellsPerMethod.size());
        return smellsPerMethod;
    }

    private String findMethodIDForViolation(Path absoluteFilePath, int beginLine, int endLine, Path releaseDir) {
        try {
            String fileContent = new String(Files.readAllBytes(absoluteFilePath));
            Optional<MethodDeclaration> foundMethod = StaticJavaParser.parse(fileContent)
                    .findAll(MethodDeclaration.class).stream()
                    .filter(md -> md.getRange().isPresent() &&
                            md.getRange().get().begin.line <= beginLine &&
                            md.getRange().get().end.line >= endLine)
                    .findFirst();

            if (foundMethod.isPresent()) {
                String signature = foundMethod.get().getSignature().asString();
                String relativePath = releaseDir.relativize(absoluteFilePath).toString().replace("\\", "/");
                return relativePath + "/" + signature;
            }
        } catch (IOException | StackOverflowError e) {
            LOGGER.warn("Impossibile parsare il file {} per la mappatura dello smell", absoluteFilePath);
        } catch (Exception e) {
            LOGGER.warn("Errore generico di parsing per il file {}", absoluteFilePath);
        }
        return null;
    }
}