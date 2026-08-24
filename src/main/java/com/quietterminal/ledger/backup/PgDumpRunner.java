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
public class PgDumpRunner {

    private final ProcessRunner processRunner;
    private final String binary;
    private final String host;
    private final String port;
    private final String database;
    private final String user;
    private final String password;

    public PgDumpRunner(ProcessRunner processRunner,
            @Value("${ledger.backup.pg-dump-binary:pg_dump}") String binary,
            @Value("${POSTGRES_HOST:localhost}") String host,
            @Value("${POSTGRES_PORT:5432}") String port,
            @Value("${POSTGRES_DB:ledger}") String database,
            @Value("${POSTGRES_USER:ledger}") String user,
            @Value("${POSTGRES_PASSWORD:ledger}") String password) {
        this.processRunner = processRunner;
        this.binary = binary;
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    public void dump(Path targetFile) {
        File parentDir = targetFile.toAbsolutePath().getParent().toFile();
        List<String> command = List.of(binary,
                "-h", host,
                "-p", port,
                "-U", user,
                "--no-owner",
                "--no-privileges",
                "-f", targetFile.toAbsolutePath().toString(),
                database);

        ProcessResult result = processRunner.run(command, parentDir, Map.of("PGPASSWORD", password));
        if (!result.succeeded()) {
            throw new BackupFailedException("pg_dump exited with code " + result.exitCode() + ": " + result.stderr());
        }
    }
}
