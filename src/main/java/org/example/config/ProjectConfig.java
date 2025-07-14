package org.example.config;

public class ProjectConfig {
    private final String projectName;
    private final String repoPath;
    private final String outputCsvPath;

    public ProjectConfig(String projectName, String repoPath, String outputCsvPath) {
        this.projectName = projectName;
        this.repoPath = repoPath;
        this.outputCsvPath = outputCsvPath;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public String getOutputCsvPath() {
        return outputCsvPath;
    }
}
