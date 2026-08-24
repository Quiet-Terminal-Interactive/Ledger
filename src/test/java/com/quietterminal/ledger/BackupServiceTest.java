package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.quietterminal.ledger.backup.BackupGitPublisher;
import com.quietterminal.ledger.backup.BackupService;
import com.quietterminal.ledger.backup.PgDumpRunner;
import com.quietterminal.ledger.error.BackupFailedException;

class BackupServiceTest {

    @TempDir
    Path repoDir;

    @Test
    void runBackupDumpsIntoRepoDirBackupsFolderThenPublishes() throws Exception {
        PgDumpRunner pgDumpRunner = mock(PgDumpRunner.class);
        BackupGitPublisher gitPublisher = mock(BackupGitPublisher.class);
        BackupService service = new BackupService(pgDumpRunner, gitPublisher, repoDir.toString());

        BackupService.BackupResult result = service.runBackup();

        assertTrue(result.fileName().startsWith("ledger-") && result.fileName().endsWith(".sql"));
        Path expectedTarget = repoDir.resolve("backups").resolve(result.fileName());
        assertTrue(Files.isDirectory(repoDir.resolve("backups")));
        verify(pgDumpRunner).dump(eq(expectedTarget));
        verify(gitPublisher).publish(eq(expectedTarget), any());
    }

    @Test
    void missingRepoDirIsRejectedWithoutTouchingCollaborators() {
        PgDumpRunner pgDumpRunner = mock(PgDumpRunner.class);
        BackupGitPublisher gitPublisher = mock(BackupGitPublisher.class);
        BackupService service = new BackupService(pgDumpRunner, gitPublisher, "");

        assertThrows(BackupFailedException.class, service::runBackup);

        verifyNoInteractions(pgDumpRunner);
        verifyNoInteractions(gitPublisher);
    }
}
