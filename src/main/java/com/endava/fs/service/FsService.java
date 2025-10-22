package com.endava.fs.service;

import com.endava.fs.config.FsProperties;
import com.endava.fs.model.FileEntry;
import com.endava.fs.model.ReadRequest;
import com.endava.fs.model.ReadResult;
import com.endava.fs.model.WriteMode;
import com.endava.fs.model.WriteRequest;
import com.endava.fs.model.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class FsService {

    private static final Logger log = LoggerFactory.getLogger(FsService.class);
    private static final int MAX_CONTENT_BYTES = 1_048_576;

    private final Path root;
    private final List<PathMatcher> allowMatchers;
    private final FileSystem fileSystem = FileSystems.getDefault();

    public FsService(FsProperties properties) {
        this.root = Objects.requireNonNull(properties.root(), "Root directory is required");
        this.allowMatchers = properties.allow().stream()
                .map(this::compileMatcher)
                .toList();
        initialiseRoot();
    }

    public List<FileEntry> list(String glob) {
        String pattern = (glob == null || glob.isBlank()) ? "**/*" : glob;
        PathMatcher matcher = compileMatcher(pattern);
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !hasSymbolicLink(path))
                    .filter(path -> matcher.matches(root.relativize(path)))
                    .map(this::toEntry)
                    .sorted(Comparator.comparing(FileEntry::path))
                    .toList();
        } catch (IOException e) {
            throw new FsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list files", e);
        }
    }

    public ReadResult read(ReadRequest request) {
        Path target = resolvePath(request.path());
        ensureNoSymbolicLinks(target);
        if (!Files.exists(target)) {
            throw new FsException(HttpStatus.NOT_FOUND, "File not found");
        }
        if (!Files.isRegularFile(target)) {
            throw new FsException(HttpStatus.BAD_REQUEST, "Path is not a file");
        }
        try {
            byte[] data = Files.readAllBytes(target);
            boolean base64 = Boolean.TRUE.equals(request.base64());
            String content = base64
                    ? Base64.getEncoder().encodeToString(data)
                    : new String(data, StandardCharsets.UTF_8);
            return new ReadResult(relativePath(target), content, base64);
        } catch (IOException e) {
            throw new FsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file", e);
        }
    }

    public WriteResult write(WriteRequest request) {
        Path target = resolvePath(request.path());
        // ensureAllowed(target);
        Path parent = target.getParent();
        if (parent == null) {
            throw new FsException(HttpStatus.BAD_REQUEST, "Target path must be within the configured root");
        }
        ensureNoSymbolicLinks(parent);
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new FsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to prepare target directory", e);
        }
        ensureNoSymbolicLinks(parent);

        byte[] payload = decodeContent(request.content(), request.base64());
        if (payload.length > MAX_CONTENT_BYTES) {
            throw new FsException(HttpStatus.PAYLOAD_TOO_LARGE, "Content exceeds 1MB limit");
        }

        boolean targetExists = Files.exists(target);
        if (request.mode() == WriteMode.CREATE && targetExists) {
            throw new FsException(HttpStatus.CONFLICT, "File already exists");
        }
        ensureNoSymbolicLinks(target);

        Path tempFile = createTempFile(parent, target.getFileName().toString());
        try (OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING)) {
            if (request.mode() == WriteMode.APPEND && targetExists) {
                copyExistingContent(target, out);
            }
            out.write(payload);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new FsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write content", e);
        }

        moveIntoPlace(target, tempFile, request.mode(), targetExists);

        log.info("audit write user=\"{}\" path=\"{}\" bytes={} mode={}",
                "api-key", relativePath(target), payload.length, request.mode());

        return new WriteResult(relativePath(target), payload.length, request.mode().name());
    }

    private void moveIntoPlace(Path target, Path tempFile, WriteMode mode, boolean targetExists) {
        try {
            List<StandardCopyOption> options = new ArrayList<>();
            options.add(StandardCopyOption.ATOMIC_MOVE);
            if (mode != WriteMode.CREATE || targetExists) {
                options.add(StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile, target, options.toArray(StandardCopyOption[]::new));
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new FsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to finalise write operation", e);
        }
    }

    private void copyExistingContent(Path source, OutputStream out) throws IOException {
        try (InputStream in = Files.newInputStream(source, StandardOpenOption.READ)) {
            in.transferTo(out);
        }
    }

    private Path createTempFile(Path parent, String fileName) {
        String cleanedName = (fileName != null && fileName.length() >= 3)
                ? fileName
                : (fileName == null || fileName.isBlank() ? "tmp" : (fileName + "___"));
        try {
            return Files.createTempFile(parent, cleanedName.substring(0, 3), ".tmp");
        } catch (IOException e) {
            throw new FsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to allocate temporary file", e);
        }
    }

    private byte[] decodeContent(String content, boolean base64) {
        if (content == null) {
            throw new FsException(HttpStatus.BAD_REQUEST, "Content must be provided");
        }
        return base64 ? decodeBase64(content) : content.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeBase64(String content) {
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new FsException(HttpStatus.BAD_REQUEST, "Invalid base64 content", e);
        }
    }

    private FileEntry toEntry(Path path) {
        try {
            return new FileEntry(relativePath(path), Files.size(path));
        } catch (IOException e) {
            throw new FsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to inspect file", e);
        }
    }

    private boolean hasSymbolicLink(Path path) {
        Path current = root;
        Path relative = root.relativize(path);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current) && Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private void ensureAllowed(Path target) {
        Path relative = root.relativize(target);
        boolean match = allowMatchers.stream().anyMatch(matcher -> matcher.matches(relative));
        if (!match) {
            throw new FsException(HttpStatus.FORBIDDEN, "Path is not permitted by allow list");
        }
    }

    private void ensureNoSymbolicLinks(Path path) {
        Path current = root;
        Path relative = root.relativize(path);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current) && Files.isSymbolicLink(current)) {
                throw new FsException(HttpStatus.BAD_REQUEST, "Path contains a symbolic link segment");
            }
        }
    }

    private Path resolvePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new FsException(HttpStatus.BAD_REQUEST, "Path must be provided");
        }
        Path requested;
        try {
            requested = Path.of(rawPath);
        } catch (InvalidPathException e) {
            throw new FsException(HttpStatus.BAD_REQUEST, "Invalid path syntax", e);
        }
        Path resolved = root.resolve(requested).normalize();
        if (!resolved.startsWith(root)) {
            throw new FsException(HttpStatus.BAD_REQUEST, "Path escapes the configured root directory");
        }
        return resolved;
    }

    private PathMatcher compileMatcher(String pattern) {
        String adjusted = pattern.replace("/", fileSystem.getSeparator());
        return fileSystem.getPathMatcher("glob:" + adjusted);
    }

    private String relativePath(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private void initialiseRoot() {
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) {
                throw new IllegalStateException("Configured root directory must not be a symbolic link");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialise root directory", e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
