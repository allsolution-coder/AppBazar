package com.appbazaar.storage;

import static org.springframework.http.HttpStatus.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Stores uploaded product files under random names, so user input never decides the path.
 * With S3_BUCKET set it uses any S3-compatible storage (Supabase Storage, Cloudflare R2, Backblaze B2, AWS S3),
 * which survives restarts on free hosting. Without it, files go to a folder on disk (fine on your own computer).
 */
@Service
public class FileStorage {
    public record Saved(String storedName, String originalName, long size) {}

    private final Path root;          // disk mode
    private final S3Client s3;        // cloud mode, null in disk mode
    private final String bucket;
    private final Set<String> allowed;

    public FileStorage(@Value("${app.upload-dir}") String dir,
                       @Value("${app.allowed-extensions}") String exts,
                       @Value("${app.s3-bucket}") String bucket,
                       @Value("${app.s3-endpoint}") String endpoint,
                       @Value("${app.s3-region}") String region,
                       @Value("${app.s3-access-key}") String accessKey,
                       @Value("${app.s3-secret-key}") String secretKey) throws IOException {
        this.allowed = new HashSet<>();
        for (String e : exts.split(",")) allowed.add(e.trim().toLowerCase());
        this.bucket = bucket == null ? "" : bucket.trim();
        if (!this.bucket.isBlank()) {
            var b = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())))
                    .forcePathStyle(true);
            if (endpoint != null && !endpoint.isBlank()) b.endpointOverride(URI.create(endpoint.trim()));
            this.s3 = b.build();
            this.root = null;
        } else {
            this.s3 = null;
            this.root = Path.of(dir).toAbsolutePath().normalize();
            Files.createDirectories(root);
        }
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
            if (s3 != null) {
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(stored)
                        .contentType("application/octet-stream").build(),
                        RequestBody.fromInputStream(in, file.getSize()));
            } else {
                Files.copy(in, root.resolve(stored));
            }
        } catch (IOException | SdkException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Could not save the file");
        }
        return new Saved(stored, original, file.getSize());
    }

    public Resource load(String stored) {
        if (s3 != null) {
            try {
                ResponseInputStream<GetObjectResponse> in =
                        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(stored).build());
                long length = in.response().contentLength();
                return new InputStreamResource(in) {
                    @Override public long contentLength() { return length; }
                };
            } catch (NoSuchKeyException e) {
                throw new ResponseStatusException(NOT_FOUND, "File is missing on the server");
            } catch (SdkException e) {
                throw new ResponseStatusException(BAD_GATEWAY, "Could not read the file from storage");
            }
        }
        Path p = root.resolve(stored).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p))
            throw new ResponseStatusException(NOT_FOUND, "File is missing on the server");
        return new FileSystemResource(p);
    }

    public void delete(String stored) {
        if (stored == null) return;
        try {
            if (s3 != null) {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(stored).build());
            } else {
                Path p = root.resolve(stored).normalize();
                if (p.startsWith(root)) Files.deleteIfExists(p);
            }
        } catch (IOException | SdkException ignored) {
            // a leftover file is harmless; ignore
        }
    }
}
