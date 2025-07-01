// in src/main/java/org/example/services/GitService.java
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
import org.example.model.JiraTicket;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitService {
    public final Repository repository;
    private final Git git;

    public GitService(Path repositoryPath) throws IOException {
        this.git = Git.open(repositoryPath.toFile());
        this.repository = git.getRepository();
    }

    public ObjectId getFileId(RevCommit commit, String filePath) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk != null) {
                return treeWalk.getObjectId(0);
            }
        }
        return null;
    }
    public Iterable<RevCommit> getAllCommits() throws GitAPIException, IOException {
        return git.log().all().call();
    }

    public List<Ref> getAllTagsSortedByDate() throws GitAPIException {
        List<Ref> tags = git.tagList().call();
        tags.sort((t1, t2) -> {
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit c1 = walk.parseCommit(t1.getObjectId());
                RevCommit c2 = walk.parseCommit(t2.getObjectId());
                return Integer.compare(c1.getCommitTime(), c2.getCommitTime());
            } catch (Exception e) {
                return 0;
            }
        });
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

    public String getFileContentAtCommit(RevCommit commit, String filePath) throws IOException {
        if ("/dev/null".equals(filePath)) return "";
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk != null) {
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                return new String(loader.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    public Map<String, RevCommit> linkBugsToCommits(Set<String> ticketKeys) throws GitAPIException, IOException {
        Map<String, RevCommit> bugCommits = new HashMap<>();
        if (ticketKeys.isEmpty()) return bugCommits;

        String patternString = String.join("|", ticketKeys);
        Pattern issuePattern = Pattern.compile(patternString);

        for (RevCommit commit : getAllCommits()) {
            Matcher matcher = issuePattern.matcher(commit.getFullMessage());
            if (matcher.find()) {
                bugCommits.put(commit.getName(), commit);
            }
        }
        return bugCommits;
    }

    public void close() {
        git.close();
        repository.close();
    }
}