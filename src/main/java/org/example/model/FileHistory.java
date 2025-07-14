package org.example.model;


import org.eclipse.jgit.revwalk.RevCommit;
import java.util.ArrayList;
import java.util.List;

public class FileHistory {
    private final String filePath;
    private final List<MethodHistory.Change> changes = new ArrayList<>();

    public FileHistory(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<MethodHistory.Change> getChanges() {
        return changes;
    }

    public void addChange(RevCommit commit, int addedLines, int deletedLines) {
        int currentChurn = addedLines + deletedLines;
        if (currentChurn > 0) {
            this.changes.add(new MethodHistory.Change(commit, currentChurn));
        }
    }
}