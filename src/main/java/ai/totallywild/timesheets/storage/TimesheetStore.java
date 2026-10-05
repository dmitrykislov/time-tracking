package ai.totallywild.timesheets.storage;

import ai.totallywild.timesheets.domain.Timesheet;
import ai.totallywild.timesheets.domain.TimesheetException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes the single JSON file. Every read hits the disk so edits made by hand in an editor
 * show up on the next request; every write goes through a temp file and an atomic rename so a crash
 * mid-write never leaves a half file behind.
 */
public class TimesheetStore {

    private final Path file;
    private final ObjectMapper mapper;

    public TimesheetStore(Path file) {
        this(file, defaultMapper());
    }

    public TimesheetStore(Path file, ObjectMapper mapper) {
        this.file = file.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public static ObjectMapper defaultMapper() {
        return JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public Path file() {
        return file;
    }

    public boolean exists() {
        return Files.exists(file);
    }

    /** The timesheet on disk, or an empty one with default config when the file does not exist yet. */
    public Timesheet load() {
        if (!Files.exists(file)) {
            return Timesheet.empty();
        }
        try {
            var text = Files.readString(file);
            if (text.isBlank()) {
                return Timesheet.empty();
            }
            return mapper.readValue(text, Timesheet.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        } catch (tools.jackson.core.JacksonException e) {
            var cause = e.getCause() instanceof TimesheetException te ? te.getMessage() : e.getOriginalMessage();
            throw new TimesheetException("The file " + file + " could not be parsed: " + cause);
        }
    }

    public void save(Timesheet timesheet) {
        try {
            Files.createDirectories(file.getParent());
            var temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temp, mapper.writeValueAsString(timesheet) + System.lineSeparator());
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }
}
