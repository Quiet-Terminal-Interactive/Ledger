package com.quietterminal.ledger.backup;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface ProcessRunner {

    ProcessResult run(List<String> command, File workingDir, Map<String, String> extraEnv);

    record ProcessResult(int exitCode, String stdout, String stderr) {
        public boolean succeeded() {
            return exitCode == 0;
        }
    }
}
