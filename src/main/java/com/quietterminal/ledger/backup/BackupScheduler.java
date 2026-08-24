package com.quietterminal.ledger.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ledger.backup.enabled", havingValue = "true")
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(cron = "${ledger.backup.cron:0 0 3 * * *}")
    public void runScheduledBackup() {
        try {
            BackupService.BackupResult result = backupService.runBackup();
            log.info("Backup completed: {}", result.fileName());
        } catch (Exception e) {
            log.error("Scheduled backup failed", e);
        }
    }
}
