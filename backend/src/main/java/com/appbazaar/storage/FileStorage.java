package com.appbazaar.storage;

import static org.springframework.http.HttpStatus.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Saves uploaded product files on disk under random names, so user input never decides the path. */
@Service
public class FileStorage {
    public record Saved(String storedName, String originalName, long size) {}

    private final Path root;
    private final Set<String> allowed;

    public FileStorage(@Value("${app.upload-dir}") String dir,
                       @Value("${app.allowed-extensions}") String exts) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        this.allowed = new HashSet<>();
        for (String e : exts.split(",")) allowed.add(e.trim().toLowerCase());
    }

    public Saved save(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(BAD_REQUEST, "Please attach the product file");
        String original = Path.of(Objects.requireNonNullElse(file.getOriginalFilename(), "file")).getFileName().toString();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase() : "";
        if (!allowed.contains(ext))
            throw new ResponseStatusException(BAD_REQUEST,
                    "File type ." + ext + " is not allowed. Allowed: " + String.join(", ", new TreeSet<>(allowed)) + ". Zip your files first.");
        String stored = UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(stored));
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Could not save the file");
        }
        return new Saved(stored, original, file.getSize());
    }

    public Resource load(String stored) {
        Path p = root.resolve(stored).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p))
            throw new ResponseStatusException(NOT_FOUND, "File is missing on the server");
        return new FileSystemResource(p);
    }

    public void delete(String stored) {
        if (stored == null) return;
        try {
            Path p = root.resolve(stored).normalize();
            if (p.startsWith(root)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // leftover file is harmless; ignore
        }
    }
}
