package com.quietterminal.ledger.backup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.quietterminal.ledger.error.BackupFailedException;

@Component
public class DefaultProcessRunner implements ProcessRunner {

    @Override
    public ProcessResult run(List<String> command, File workingDir, Map<String, String> extraEnv) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDir);
        builder.environment().putAll(extraEnv);
        try {
            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, stdout, stderr);
        } catch (IOException e) {
            throw new BackupFailedException("Failed to run '" + command.get(0) + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupFailedException("Interrupted while running '" + command.get(0) + "'.");
        }
    }
}
