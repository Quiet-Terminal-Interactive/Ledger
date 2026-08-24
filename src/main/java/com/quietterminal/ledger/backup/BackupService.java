package com.quietterminal.ledger.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.quietterminal.ledger.error.BackupFailedException;

@Service
public class BackupService {

    private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final PgDumpRunner pgDumpRunner;
    private final BackupGitPublisher gitPublisher;
    private final Path dumpDir;

    public BackupService(PgDumpRunner pgDumpRunner, BackupGitPublisher gitPublisher,
            @Value("${ledger.backup.repo-dir:}") String repoDir) {
        this.pgDumpRunner = pgDumpRunner;
        this.gitPublisher = gitPublisher;
        this.dumpDir = repoDir == null || repoDir.isBlank() ? null : Path.of(repoDir, "backups");
    }

    public BackupResult runBackup() {
        if (dumpDir == null) {
            throw new BackupFailedException(
                    "ledger.backup.repo-dir is not configured; cannot run a backup.");
        }

        try {
            Files.createDirectories(dumpDir);
        } catch (IOException e) {
            throw new BackupFailedException("Could not create backup directory " + dumpDir + ": " + e.getMessage());
        }

        Instant now = Instant.now();
        String fileName = "ledger-" + FILENAME_TIMESTAMP.format(now.atZone(ZoneOffset.UTC)) + ".sql";
        Path target = dumpDir.resolve(fileName);

        pgDumpRunner.dump(target);
        gitPublisher.publish(target, "Backup " + fileName);

        return new BackupResult(fileName, now);
    }

    public record BackupResult(String fileName, Instant createdAt) {
    }
}
