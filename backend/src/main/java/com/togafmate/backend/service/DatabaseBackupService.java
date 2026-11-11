package com.togafmate.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Creates, lists, and restores PostgreSQL custom-format backups. */
@Service
public class DatabaseBackupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyddMMHHmmss");
    private static final Pattern JDBC_URL = Pattern.compile("^jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^?]+).*$");

    private final Path backupDirectory;
    private final String databaseName;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String dumpCommand;
    private final String restoreCommand;
    private final Clock clock;
    private final Pattern backupNamePattern;

    /** Creates the service using Docker Compose database and backup settings. */
    @Autowired
    public DatabaseBackupService(
            @Value("${app.database.backup.directory:/var/lib/postgresql/backup}") String backupDirectory,
            @Value("${app.database.backup.jdbc-url:${spring.datasource.url}}") String jdbcUrl,
            @Value("${spring.datasource.username:${DB_USERNAME:${POSTGRES_USER:postgres}}}") String username,
            @Value("${spring.datasource.password:${DB_PASSWORD:${POSTGRES_PASSWORD:}}}") String password,
            @Value("${app.database.backup.dump-command:pg_dump}") String dumpCommand,
            @Value("${app.database.backup.restore-command:pg_restore}") String restoreCommand) {
        this(Path.of(backupDirectory), jdbcUrlDatabase(jdbcUrl), jdbcUrlHost(jdbcUrl), jdbcUrlPort(jdbcUrl), username,
                password, dumpCommand, restoreCommand, Clock.systemDefaultZone());
    }

    DatabaseBackupService(Path backupDirectory, String databaseName, String host, int port, String username,
            String password, String dumpCommand, String restoreCommand, Clock clock) {
        this.backupDirectory = backupDirectory.toAbsolutePath().normalize();
        if (!databaseName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Database name contains unsupported characters for backup files");
        }
        this.databaseName = databaseName;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.dumpCommand = dumpCommand;
        this.restoreCommand = restoreCommand;
        this.clock = clock;
        this.backupNamePattern = Pattern.compile("kbmt_" + Pattern.quote(databaseName) + "_\\d{14}\\.dump");
    }

    /** Lists valid backup files for the configured database. */
    public synchronized List<DatabaseBackup> listBackups() {
        if (Files.notExists(backupDirectory)) {
            LOGGER.info("No database backup directory exists yet at {}", backupDirectory);
            return List.of();
        }
        try {
            try (var files = Files.list(backupDirectory)) {
                List<DatabaseBackup> backups = files.filter(Files::isRegularFile)
                        .filter(path -> isValidBackupName(path.getFileName().toString()))
                        .map(this::toBackup)
                        .sorted(Comparator.comparing(DatabaseBackup::fileName).reversed())
                        .toList();
                if (backups.isEmpty()) {
                    LOGGER.info("No database backups were found");
                } else {
                    LOGGER.info("Listed {} database backups", backups.size());
                }
                return backups;
            }
        } catch (IOException exception) {
            LOGGER.error("Could not list database backups", exception);
            throw new IllegalStateException("Could not list database backups", exception);
        }
    }

    /** Creates and returns a custom-format dump of the configured database. */
    public synchronized DatabaseBackup createBackup() {
        try {
            Files.createDirectories(backupDirectory);
            Path destination = nextBackupPath();
            try {
                runCommand(dumpCommand, List.of("--format=custom", "--file=" + destination,
                        "--host=" + host, "--port=" + port, "--username=" + username, "--dbname=" + databaseName));
            } catch (IOException exception) {
                Files.deleteIfExists(destination);
                throw exception;
            }
            LOGGER.info("Created database backup {}", destination.getFileName());
            return toBackup(destination);
        } catch (IOException exception) {
            LOGGER.error("Could not create database backup", exception);
            throw new IllegalStateException("Could not create database backup", exception);
        }
    }

    /** Saves a safety backup, then restores the named backup into the configured database. */
    public synchronized DatabaseBackup restore(String fileName) {
        Path source = resolveBackup(fileName);
        if (!Files.isRegularFile(source)) {
            throw new DatabaseBackupNotFoundException("Database backup does not exist");
        }
        createBackup();
        try {
            runCommand(restoreCommand, List.of("--clean", "--if-exists", "--exit-on-error", "--single-transaction",
                    "--host=" + host, "--port=" + port, "--username=" + username,
                    "--dbname=" + databaseName, source.toString()));
            LOGGER.info("Restored database backup {}", fileName);
            return toBackup(source);
        } catch (IOException exception) {
            LOGGER.error("Could not restore database backup {}", fileName, exception);
            throw new IllegalStateException("Could not restore database backup", exception);
        }
    }

    private Path nextBackupPath() throws IOException {
        LocalDateTime timestamp = LocalDateTime.now(clock);
        for (int attempt = 0; attempt < 60; attempt++) {
            Path candidate = backupDirectory.resolve("kbmt_" + databaseName + "_"
                    + FILE_TIMESTAMP.format(timestamp.plusSeconds(attempt)) + ".dump");
            if (Files.notExists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Could not allocate a unique database backup name");
    }

    private Path resolveBackup(String fileName) {
        if (fileName == null || !isValidBackupName(fileName)) {
            throw new IllegalArgumentException("Invalid database backup name");
        }
        Path path = backupDirectory.resolve(fileName).normalize();
        if (!path.getParent().equals(backupDirectory)) {
            throw new IllegalArgumentException("Invalid database backup name");
        }
        return path;
    }

    private boolean isValidBackupName(String fileName) {
        return backupNamePattern.matcher(fileName).matches();
    }

    private DatabaseBackup toBackup(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            return new DatabaseBackup(path.getFileName().toString(), Files.size(path), modified.toMillis());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read database backup metadata", exception);
        }
    }

    private void runCommand(String command, List<String> arguments) throws IOException {
        var commandLine = new java.util.ArrayList<String>();
        commandLine.add(command);
        commandLine.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.environment().put("PGPASSWORD", password);
        Process process = processBuilder.start();
        try {
            process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("PostgreSQL backup command was interrupted", exception);
        }
        if (process.exitValue() != 0) {
            throw new IOException("PostgreSQL backup command failed");
        }
    }

    private static String jdbcUrlDatabase(String url) {
        return jdbcUrlParts(url).group(3);
    }

    private static String jdbcUrlHost(String url) {
        return jdbcUrlParts(url).group(1);
    }

    private static int jdbcUrlPort(String url) {
        String port = jdbcUrlParts(url).group(2);
        return port == null ? 5432 : Integer.parseInt(port);
    }

    private static Matcher jdbcUrlParts(String url) {
        Matcher matcher = JDBC_URL.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Database backup requires a PostgreSQL JDBC URL");
        }
        return matcher;
    }
}
