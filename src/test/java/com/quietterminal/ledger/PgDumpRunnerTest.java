package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.quietterminal.ledger.backup.PgDumpRunner;
import com.quietterminal.ledger.backup.ProcessRunner;
import com.quietterminal.ledger.backup.ProcessRunner.ProcessResult;
import com.quietterminal.ledger.error.BackupFailedException;

class PgDumpRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void dumpsWithPasswordPassedThroughEnvironmentNotArguments() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        when(processRunner.run(any(), any(), any())).thenReturn(new ProcessResult(0, "", ""));
        PgDumpRunner runner = new PgDumpRunner(processRunner, "pg_dump", "db-host", "5433", "ledger", "ledger-user",
                "super-secret");

        Path target = tempDir.resolve("dump.sql");
        runner.dump(target);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<String>> commandCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(commandCaptor.capture(), eq(tempDir.toFile()), eq(Map.of("PGPASSWORD", "super-secret")));

        List<String> command = commandCaptor.getValue();
        assertEquals("pg_dump", command.get(0));
        assertEquals(target.toAbsolutePath().toString(), command.get(command.indexOf("-f") + 1));
        assertEquals("ledger", command.get(command.size() - 1));
        org.junit.jupiter.api.Assertions.assertTrue(command.stream().noneMatch(arg -> arg.contains("super-secret")));
    }

    @Test
    void nonZeroExitIsSurfacedAsBackupFailure() {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        when(processRunner.run(any(), any(), any())).thenReturn(new ProcessResult(1, "", "connection refused"));
        PgDumpRunner runner = new PgDumpRunner(processRunner, "pg_dump", "host", "5432", "ledger", "user", "pw");

        BackupFailedException e = assertThrows(BackupFailedException.class,
                () -> runner.dump(tempDir.resolve("dump.sql")));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("connection refused"));
    }
}
