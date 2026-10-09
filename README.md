# Bug Dataset Analysis

A Java pipeline developed for ISW2 coursework to build method-level defect datasets from Git history and Jira bug reports. It extracts source and change metrics from Apache Avro and BookKeeper, links fixes to reported bugs, and exports one CSV row per method and release.

The project covers repository mining, static analysis and defect labelling. It produces data for subsequent analysis; it does not include a trained defect-prediction model.

## Analysis pipeline

1. Read Git commits and release tags with JGit, ordering releases by commit date.
2. Retrieve resolved or closed Jira issues whose type is Bug and resolution is Fixed.
3. Link issue keys to commit messages and reconstruct method and file histories.
4. Derive opening, fixing and injected releases. When the injected release is missing, estimate it with the Proportion method, using the median of known proportions or a cold-start value of 1.0.
5. Parse Java methods with JavaParser and run PMD's Java design and best-practice rules.
6. Analyse the first 34% of releases, rounded up, and export metrics and the `Bugginess` label.

Tests, generated sources and several project-specific paths are excluded. The filtering rules are in `Main.isFileExcluded`. Rename changes are skipped by the history analysis.

## Build

Requirements: JDK 17, Maven 3.9 and Git.

```bash
mvn verify
mvn exec:java -Dexec.args="--help"
```

`mvn verify` compiles and packages the project. There is currently no automated test suite in the repository.

## Generate a dataset

Clone the source projects with their complete history and tags. Shallow clones are unsuitable for this analysis.

```bash
mkdir -p repositories results
git clone https://github.com/apache/avro.git repositories/avro
git clone https://github.com/apache/bookkeeper.git repositories/bookkeeper

mvn exec:java -Dexec.args="AVRO ./repositories/avro ./results/avro_dataset.csv"
mvn exec:java -Dexec.args="BOOKKEEPER ./repositories/bookkeeper ./results/bookkeeper_dataset.csv"
```

The three arguments are the Jira project key, local repository path and destination CSV. Run these commands from this repository's root. Create the destination directory first; use quotes around paths containing spaces.

Each invocation analyses one project. Invalid arguments exit with status 2; pipeline failures exit with status 1. A failed run can leave a partial CSV, so check the log before using the output. Running without arguments prints usage information. Output paths are explicit so that new runs can be kept separately from the committed exports.

Dataset generation requires access to Apache Jira and can take a long time: it traverses the repository history and runs static analysis for the selected releases. A successful build alone does not verify access to Jira or reproduce an earlier dataset.

## CSV fields

| Fields | Meaning |
| --- | --- |
| `ProjectName`, `MethodID`, `ReleaseID` | Project, source path plus method signature, and release tag |
| `LOC`, `CC`, `ParamCount`, `NestingDepth` | Method size, cyclomatic complexity, parameter count and nesting |
| `NSmells` | PMD violations mapped to the method |
| `NR`, `NAuth`, `NFix` | Changes, distinct authors and linked bug-fix changes |
| `Churn`, `MaxChurn`, `AvgChurn` | Change volume; cumulative and average churn use release-age weighting |
| `ClassNR`, `ClassNAuth`, `ClassChurn`, `AvgClassChurn` | Corresponding file-level history metrics |
| `Bugginess` | `yes` when a linked bug is considered present in that release, otherwise `no` |

Labels depend on the available Jira metadata, issue references in commit messages and the injected-release estimate. They are derived labels, not a manual review of every method.

## Included exports

| File | Method-release rows | Releases | Labels |
| --- | ---: | ---: | --- |
| [avro_dataset.csv](avro_dataset.csv) | 42,302 | 37 | 7,791 `yes`, 34,511 `no` |
| [bookkeeper_dataset.csv](bookkeeper_dataset.csv) | 0 | 0 | No data rows |

These are the exports already present in the repository. The BookKeeper export is incomplete. The Avro counts describe the stored file and are not prediction-performance results.

Regeneration can change the dataset as Git history and Jira metadata evolve. For a reproducible study, record the source commit, available tags and Jira retrieval date alongside each generated CSV.

## Code layout

- `config/`: project key, repository path and output path.
- `services/`: Git, Jira, PMD and CSV integration.
- `logic/`: history reconstruction, metrics and bug-lifecycle analysis.
- `model/`: releases, tickets, methods and their histories.
- `Main.java`: pipeline orchestration, source filtering and command-line entry point.
