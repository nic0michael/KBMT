package com.togafmate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseBackupServiceTest {
    @TempDir
    Path backupDirectory;

    @Test
    void should_listOnlyValidDatabaseBackupFiles() throws Exception {
        Files.createFile(backupDirectory.resolve("kbmt_testdb_20261111083045.dump"));
        Files.createFile(backupDirectory.resolve("other.txt"));

        DatabaseBackupService service = service();

        assertThat(service.listBackups()).extracting(DatabaseBackup::fileName)
                .containsExactly("kbmt_testdb_20261111083045.dump");
    }

    @Test
    void should_returnEmptyList_withoutCreatingDirectory_whenBackupDirectoryDoesNotExist() {
        Path missingDirectory = backupDirectory.resolve("not-created-yet");
        DatabaseBackupService service = service(missingDirectory);

        assertThat(service.listBackups()).isEmpty();
        assertThat(missingDirectory).doesNotExist();
    }

    @Test
    void should_returnEmptyList_whenBackupDirectoryContainsNoBackups() {
        DatabaseBackupService service = service(backupDirectory);

        assertThat(service.listBackups()).isEmpty();
    }

    @Test
    void should_rejectBackupNameOutsideConfiguredDatabase() {
        DatabaseBackupService service = service();

        assertThatThrownBy(() -> service.restore("../kbmt_testdb_20261111083045.dump"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_createDumpWithAgreedFilename_whenBackupRequested() throws Exception {
        Path dumpCommand = backupDirectory.resolve("fake-pg-dump");
        Files.writeString(dumpCommand, "#!/bin/sh\nfor arg do case $arg in --file=*) touch \"${arg#--file=}\";; esac; done\n");
        Files.setPosixFilePermissions(dumpCommand, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        DatabaseBackupService service = service(dumpCommand.toString(), "/bin/true");

        DatabaseBackup created = service.createBackup();

        assertThat(created.fileName()).isEqualTo("kbmt_testdb_20261111083045.dump");
        assertThat(backupDirectory.resolve(created.fileName())).exists();
    }

    @Test
    void should_createSafetyDumpBeforeRestoringSelectedBackup() throws Exception {
        Path source = backupDirectory.resolve("kbmt_testdb_20261111083045.dump");
        Files.createFile(source);
        Path dumpCommand = backupDirectory.resolve("fake-pg-dump");
        Files.writeString(dumpCommand, "#!/bin/sh\nfor arg do case $arg in --file=*) touch \"${arg#--file=}\";; esac; done\n");
        Files.setPosixFilePermissions(dumpCommand, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));

        DatabaseBackup restored = service(dumpCommand.toString(), "/bin/true").restore(source.getFileName().toString());

        assertThat(restored.fileName()).isEqualTo(source.getFileName().toString());
        assertThat(backupDirectory.resolve("kbmt_testdb_20261111083046.dump")).exists();
    }

    private DatabaseBackupService service() {
        return service(backupDirectory, "pg_dump", "pg_restore");
    }

    private DatabaseBackupService service(String dumpCommand, String restoreCommand) {
        return service(backupDirectory, dumpCommand, restoreCommand);
    }

    private DatabaseBackupService service(Path directory) {
        return service(directory, "pg_dump", "pg_restore");
    }

    private DatabaseBackupService service(Path directory, String dumpCommand, String restoreCommand) {
        return new DatabaseBackupService(directory, "testdb", "localhost", 5432,
                "testuser", "testpassword", dumpCommand, restoreCommand,
                Clock.fixed(Instant.parse("2026-11-11T08:30:45Z"), ZoneOffset.UTC));
    }
}
