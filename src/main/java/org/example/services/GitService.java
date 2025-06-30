package org.example.services;

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
import org.example.model.Release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitService implements AutoCloseable {
    private final Repository repository;
    private final Git git;

    public GitService(Path repositoryPath) throws IOException {
        this.git = Git.open(repositoryPath.toFile());
        this.repository = git.getRepository();
    }

    public Git getGit() {
        return git;
    }

    public Repository getRepository() {
        return repository;
    }

    /**
     * Recupera tutte le release (tag) dal repository e le ordina cronologicamente.
     */
    public List<Release> getReleases() throws GitAPIException, IOException {
        System.out.println("Recupero e ordino le release da Git...");
        List<Ref> tags = git.tagList().call();
        List<Release> releases = new ArrayList<>();

        try (RevWalk walk = new RevWalk(repository)) {
            for (Ref tag : tags) {
                try {
                    RevCommit commit = walk.parseCommit(tag.getObjectId());
                    String releaseName = tag.getName().replace("refs/tags/", "");
                    releases.add(new Release(releaseName, commit, tag));
                } catch (Exception e) {
                    // Ignora tag non validi
                }
            }
        }

        Collections.sort(releases);
        System.out.println("Trovate e ordinate " + releases.size() + " release.");
        return releases;
    }

    /**
     * Dato un set di chiavi di ticket, trova tutti i commit che le menzionano.
     */
    public List<RevCommit> getCommitsFromTickets(Set<String> ticketKeys, String projectKey) throws GitAPIException, IOException {
        List<RevCommit> bugCommits = new ArrayList<>();
        Pattern issuePattern = Pattern.compile(projectKey.toUpperCase() + "-\\d+");

        Iterable<RevCommit> allCommits = git.log().all().call();
        for (RevCommit commit : allCommits) {
            Matcher matcher = issuePattern.matcher(commit.getFullMessage().toUpperCase());
            while (matcher.find()) {
                if (ticketKeys.contains(matcher.group(0))) {
                    bugCommits.add(commit);
                    break;
                }
            }
        }
        return bugCommits;
    }

    /**
     * Trova tutti i file .java nell'albero di commit di una data release.
     */
    public List<String> findJavaFilesInRelease(Release release) throws IOException {
        List<String> javaFiles = new ArrayList<>();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(release.getCommit().getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                if (treeWalk.getPathString().endsWith(".java")) {
                    javaFiles.add(treeWalk.getPathString());
                }
            }
        }
        return javaFiles;
    }

    /**
     * Legge il contenuto di un file da un commit specifico come una stringa.
     */
    public String getFileContent(RevCommit commit, String filePath) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk != null) {
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                byte[] bytes = loader.getBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            } else {
                throw new IOException("File non trovato nel commit " + commit.getName() + ": " + filePath);
            }
        }
    }

    /**
     * Per un dato commit, restituisce la lista dei path dei file modificati.
     */
    public List<String> getTouchedFiles(RevCommit commit) throws IOException {
        List<String> touchedFiles = new ArrayList<>();
        if (commit.getParentCount() == 0) return touchedFiles;

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {

            RevCommit parent = new RevWalk(repository).parseCommit(commit.getParent(0).getId());
            diffFormatter.setRepository(repository);

            List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diff : diffs) {
                if (diff.getChangeType() == DiffEntry.ChangeType.ADD || diff.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                    touchedFiles.add(diff.getNewPath());
                }
            }
        }
        return touchedFiles;
    }

    @Override
    public void close() {
        if (this.git != null) {
            this.git.close();
        }
    }
}