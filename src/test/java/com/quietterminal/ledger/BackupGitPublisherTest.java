package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.quietterminal.ledger.backup.BackupGitPublisher;
import com.quietterminal.ledger.backup.ProcessRunner;
import com.quietterminal.ledger.backup.ProcessRunner.ProcessResult;
import com.quietterminal.ledger.error.BackupFailedException;

class BackupGitPublisherTest {

    @TempDir
    Path repoDir;

    @Test
    void addsCommitsAndPushesTheDumpFile() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        when(processRunner.run(any(), any(), any())).thenReturn(new ProcessResult(0, "", ""));
        BackupGitPublisher publisher = new BackupGitPublisher(processRunner, "git", repoDir.toString(), "origin",
                "main");

        Path dumpFile = repoDir.resolve("backups/ledger-123.sql");
        publisher.publish(dumpFile, "Backup ledger-123.sql");

        verify(processRunner).run(eq(List.of("git", "add", "backups/ledger-123.sql")), eq(repoDir.toFile()),
                eq(Map.of()));
        verify(processRunner).run(eq(List.of("git", "commit", "-m", "Backup ledger-123.sql")), eq(repoDir.toFile()),
                eq(Map.of()));
        verify(processRunner).run(eq(List.of("git", "push", "origin", "main")), eq(repoDir.toFile()), eq(Map.of()));
    }

    @Test
    void missingRepoDirIsRejectedWithoutRunningAnything() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        BackupGitPublisher publisher = new BackupGitPublisher(processRunner, "git", "", "origin", "main");

        assertThrows(BackupFailedException.class,
                () -> publisher.publish(Path.of("/tmp/dump.sql"), "message"));

        verifyNoInteractions(processRunner);
    }

    @Test
    void failedGitCommandIsSurfacedAsBackupFailure() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        when(processRunner.run(any(), any(), any())).thenReturn(new ProcessResult(128, "", "not a git repository"));
        BackupGitPublisher publisher = new BackupGitPublisher(processRunner, "git", repoDir.toString(), "origin",
                "main");

        BackupFailedException e = assertThrows(BackupFailedException.class,
                () -> publisher.publish(repoDir.resolve("backups/ledger-123.sql"), "message"));
        assertEquals(true, e.getMessage().contains("not a git repository"));
    }
}
