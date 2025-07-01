// in src/main/java/org/example/services/GitService.java
package org.example.services;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.model.MethodData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitService {
    public final Repository repository; // Reso pubblico per accesso da Main
    private final Git git;
    private final JavaParserUtil javaParserUtil = new JavaParserUtil();


    public GitService(Path repositoryPath) throws IOException {
        this.git = Git.open(repositoryPath.toFile());
        this.repository = git.getRepository();
    }

    public Iterable<RevCommit> getAllCommits() throws GitAPIException, IOException {
        return git.log().all().call();
    }

    public List<Ref> getAllReleasesSortedByDate() throws GitAPIException, IOException {
        List<Ref> tags = git.tagList().call();
        Map<Ref, Integer> tagCommitTimes = new HashMap<>();
        try (RevWalk walk = new RevWalk(repository)) {
            for (Ref tag : tags) {
                try {
                    RevCommit commit = walk.parseCommit(tag.getObjectId());
                    tagCommitTimes.put(tag, commit.getCommitTime());
                } catch (Exception e) { /* Ignora tag non validi */ }
            }
        }
        tags.sort(Comparator.comparing(tagCommitTimes::get));
        return tags;
    }

    public List<DiffEntry> getChangedFilesInCommit(RevCommit commit) throws IOException {
        if (commit.getParentCount() == 0) return Collections.emptyList();

        RevCommit parent = new RevWalk(repository).parseCommit(commit.getParent(0).getId());
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {
            diffFormatter.setRepository(repository);
            CanonicalTreeParser oldTree = new CanonicalTreeParser(null, reader, parent.getTree());
            CanonicalTreeParser newTree = new CanonicalTreeParser(null, reader, commit.getTree());
            return diffFormatter.scan(oldTree, newTree);
        }
    }

    public String getFileContent(RevCommit commit, String filePath) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk != null) {
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                byte[] bytes = loader.getBytes();
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                return ""; // File non trovato in questo commit
            }
        }
    }

    public Map<String, RevCommit> linkBugsToCommits(Set<String> bugKeys, String projectKey) throws GitAPIException, IOException {
        Map<String, RevCommit> bugCommits = new HashMap<>();
        Pattern issuePattern = Pattern.compile(projectKey.toUpperCase() + "-\\d+");

        Iterable<RevCommit> allCommits = getAllCommits();
        for (RevCommit commit : allCommits) {
            Matcher matcher = issuePattern.matcher(commit.getFullMessage().toUpperCase());
            while (matcher.find()) {
                if (bugKeys.contains(matcher.group(0))) {
                    bugCommits.put(commit.getName(), commit);
                    break; // Trovato un ID, basta per questo commit
                }
            }
        }
        return bugCommits;
    }

    public Map<String, MethodData> getMethodsInRelease(RevCommit releaseCommit) throws IOException {
        Map<String, MethodData> methodsInRelease = new HashMap<>();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(releaseCommit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String pathString = treeWalk.getPathString();
                if (pathString.endsWith(".java")) {
                    String fileContent = getFileContent(releaseCommit, pathString);
                    List<JavaParserUtil.MethodParseResult> parsedMethods = javaParserUtil.extractMethodsWithDeclarations(fileContent);
                    for (JavaParserUtil.MethodParseResult parsed : parsedMethods) {
                        String uniqueID = pathString.replace("\\", "/") + "/" + parsed.methodInfo.getSignature();
                        methodsInRelease.put(uniqueID, new MethodData(uniqueID, releaseCommit, parsed.methodDeclaration, fileContent));
                    }
                }
            }
        }
        return methodsInRelease;
    }
    public Map<String, RevCommit> linkBugsToCommitsFromTickets(List<JiraTicket> tickets) throws GitAPIException, IOException {
        Map<String, RevCommit> bugCommits = new HashMap<>();
        Set<String> ticketKeys = tickets.stream().map(JiraTicket::getKey).collect(Collectors.toSet());

        // Crea un pattern regex efficiente per trovare una qualsiasi delle chiavi dei ticket
        String patternString = String.join("|", ticketKeys);
        Pattern issuePattern = Pattern.compile(patternString);

        System.out.println("Inizio linking dei commit ai ticket JIRA...");
        Iterable<RevCommit> allCommits = getAllCommits();
        for (RevCommit commit : allCommits) {
            Matcher matcher = issuePattern.matcher(commit.getFullMessage());
            if (matcher.find()) {
                // Trovato un commit che menziona almeno un ID di ticket
                bugCommits.put(commit.getName(), commit);
            }
        }
        System.out.println("Trovati " + bugCommits.size() + " commit che menzionano ticket di bug.");
        return bugCommits;
    }

    public void close() {
        git.close();
        repository.close();
    }
}