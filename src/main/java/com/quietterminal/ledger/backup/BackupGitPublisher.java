package com.quietterminal.ledger.backup;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.quietterminal.ledger.backup.ProcessRunner.ProcessResult;
import com.quietterminal.ledger.error.BackupFailedException;

@Component
public class BackupGitPublisher {

    private final ProcessRunner processRunner;
    private final String binary;
    private final File repoDir;
    private final String remote;
    private final String branch;

    public BackupGitPublisher(ProcessRunner processRunner,
            @Value("${ledger.backup.git-binary:git}") String binary,
            @Value("${ledger.backup.repo-dir:}") String repoDir,
            @Value("${ledger.backup.git-remote:origin}") String remote,
            @Value("${ledger.backup.git-branch:main}") String branch) {
        this.processRunner = processRunner;
        this.binary = binary;
        this.repoDir = repoDir == null || repoDir.isBlank() ? null : new File(repoDir);
        this.remote = remote;
        this.branch = branch;
    }

    public void publish(Path dumpFile, String commitMessage) {
        if (repoDir == null) {
            throw new BackupFailedException(
                    "ledger.backup.repo-dir is not configured; cannot commit the backup anywhere.");
        }

        String relativePath = repoDir.toPath().toAbsolutePath().relativize(dumpFile.toAbsolutePath()).toString();

        run(List.of(binary, "add", relativePath));
        run(List.of(binary, "commit", "-m", commitMessage));
        run(List.of(binary, "push", remote, branch));
    }

    private void run(List<String> command) {
        ProcessResult result = processRunner.run(command, repoDir, Map.of());
        if (!result.succeeded()) {
            throw new BackupFailedException(
                    "'" + String.join(" ", command) + "' exited with code " + result.exitCode() + ": "
                            + result.stderr());
        }
    }
}
