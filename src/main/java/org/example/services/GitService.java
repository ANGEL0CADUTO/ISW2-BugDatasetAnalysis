package org.example.services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
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

    // Classe interna (record) per contenere i risultati di un diff.
    // Dichiarata public static per essere accessibile da altre classi come Main.
    public static record CommitDiffInfo(int linesAdded, int linesDeleted, List<Edit> edits) {}

    public GitService(Path repositoryPath) throws IOException {
        this.git = Git.open(repositoryPath.toFile());
        this.repository = git.getRepository();
    }

    public Git getGit() {
        return git;
    }

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
                    // Ignora tag non validi o che non puntano a commit
                }
            }
        }

        Collections.sort(releases);
        System.out.println("Trovate e ordinate " + releases.size() + " release.");
        return releases;
    }

    public List<RevCommit> getCommitsFromTickets(Set<String> ticketKeys, String projectKey) throws GitAPIException, IOException {
        List<RevCommit> bugCommits = new ArrayList<>();
        Pattern issuePattern = Pattern.compile(projectKey.toUpperCase() + "-\\d+");

        Iterable<RevCommit> allCommits = git.log().all().call();
        for (RevCommit commit : allCommits) {
            Matcher matcher = issuePattern.matcher(commit.getFullMessage().toUpperCase());
            if (matcher.find() && ticketKeys.contains(matcher.group(0))) {
                bugCommits.add(commit);
            }
        }
        return bugCommits;
    }

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

    public List<RevCommit> getCommitsTouchingFile(String filePath, RevCommit releaseCommit) throws GitAPIException, IOException {
        List<RevCommit> commits = new ArrayList<>();
        git.log().add(releaseCommit.getId()).addPath(filePath).call().forEach(commits::add);
        Collections.reverse(commits);
        return commits;
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
        } catch (Exception e) {
            System.err.println("Impossibile calcolare il diff per il commit: " + commit.getName());
            return Collections.emptyList();
        }
        return touchedFiles;
    }

    public CommitDiffInfo getCommitDiff(RevCommit commit, String filePath) throws IOException {
        List<Edit> edits = new ArrayList<>();
        if (commit.getParentCount() == 0) return new CommitDiffInfo(0, 0, edits);

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {

            RevCommit parent = new RevWalk(repository).parseCommit(commit.getParent(0).getId());
            diffFormatter.setRepository(repository);
            List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());

            for (DiffEntry diff : diffs) {
                if (diff.getNewPath().equals(filePath) || diff.getOldPath().equals(filePath)) {
                    edits.addAll(diffFormatter.toFileHeader(diff).toEditList());
                    break;
                }
            }
        }
        // Il calcolo di linesAdded/Deleted non è necessario qui, lo facciamo nel chiamante
        return new CommitDiffInfo(0, 0, edits);
    }

    @Override
    public void close() {
        if (this.git != null) {
            this.git.close();
        }
    }
}