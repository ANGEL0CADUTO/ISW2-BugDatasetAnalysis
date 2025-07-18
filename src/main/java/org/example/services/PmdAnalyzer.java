// in src/main/java/org/example/services/PmdAnalyzer.java
package org.example.services;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PmdAnalyzer {
    private final PMDConfiguration config;
    private static final Logger LOGGER = LoggerFactory.getLogger(PmdAnalyzer.class);

    public PmdAnalyzer() {
        config = new PMDConfiguration();
        config.addRuleSet("category/java/bestpractices.xml");
        config.addRuleSet("category/java/design.xml");

        int numThreads = Runtime.getRuntime().availableProcessors();
        LOGGER.info("Configurazione PMD per usare {} thread.", numThreads);
        config.setThreads(numThreads);

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

            Map<Path, List<RuleViolation>> violationsByFile = report.getViolations().stream()
                    .collect(Collectors.groupingBy(v -> Paths.get(v.getFileId().getOriginalPath())));

            // Il ciclo principale ora è più pulito e delega il lavoro.
            for (Map.Entry<Path, List<RuleViolation>> entry : violationsByFile.entrySet()) {
                mapViolationsForFile(entry.getKey(), entry.getValue(), smellsPerMethod, releaseDir);
            }

        } catch (Exception e) {
            LOGGER.error("Errore critico durante l'analisi PMD", e);
        }

        LOGGER.info("Smells mappati a {} metodi unici.", smellsPerMethod.size());
        return smellsPerMethod;
    }

    /**
     * Metodo privato estratto per gestire il parsing di un singolo file e la mappatura
     * delle sue violazioni, risolvendo lo smell del blocco try annidato.
     */
    private void mapViolationsForFile(Path filePath, List<RuleViolation> violationsInFile,
                                      Map<String, Integer> smellsPerMethod, Path releaseDir) {
        try {
            // Parsa il file UNA SOLA VOLTA
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            List<MethodDeclaration> methodsInFile = cu.findAll(MethodDeclaration.class);

            // Per ogni violazione in questo file, trova il metodo corrispondente
            for (RuleViolation violation : violationsInFile) {
                int beginLine = violation.getBeginLine();
                int endLine = violation.getEndLine();

                Optional<MethodDeclaration> foundMethod = methodsInFile.stream()
                        .filter(md -> md.getRange().isPresent() &&
                                md.getRange().get().begin.line <= beginLine &&
                                md.getRange().get().end.line >= endLine)
                        .findFirst();

                if (foundMethod.isPresent()) {
                    String signature = foundMethod.get().getSignature().asString();
                    String relativePath = releaseDir.relativize(filePath).toString().replace("\\", "/");
                    String methodID = relativePath + "/" + signature;
                    smellsPerMethod.put(methodID, smellsPerMethod.getOrDefault(methodID, 0) + 1);
                }
            }
        } catch (IOException | StackOverflowError e) {
            LOGGER.warn("Impossibile parsare il file {} per la mappatura dello smell", filePath, e);
        } catch (Exception e) {
            LOGGER.warn("Errore generico di parsing per il file {}", filePath, e);
        }
    }

    // Il vecchio metodo findMethodIDForViolation non è più necessario e può essere rimosso
    // per mantenere la classe pulita. Se ti serve, puoi lasciarlo, ma non viene chiamato.
}