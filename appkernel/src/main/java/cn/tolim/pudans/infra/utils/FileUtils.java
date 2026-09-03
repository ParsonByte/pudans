package cn.tolim.pudans.infra.utils;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.springframework.core.io.buffer.DataBufferUtils.subscriberInputStream;
import static org.springframework.util.FileSystemUtils.deleteRecursively;

import cn.tolim.pudans.infra.exception.AccessDeniedException;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;


@Slf4j
public abstract class FileUtils {

    private FileUtils() {}


    public static Mono<Void> unzip(Publisher<DataBuffer> content, Path targetPath) {
        return unzip(content, targetPath, null);
    }


    public static Mono<Void> unzip(Publisher<DataBuffer> content, Path targetPath, @Nullable Scheduler scheduler) {
        var unzip = Mono.fromCallable(() -> {
                    try (var is = subscriberInputStream(content, 1);
                         var zis = new ZipInputStream(is)) {
                        log.debug("Unzipping to target path: {}", targetPath);
                        unzip(zis, targetPath);
                        log.debug("Unzipped to target path: {}", targetPath);
                    }
                    return null;
                })
                .then();
        if (scheduler != null) {
            return unzip.subscribeOn(scheduler);
        }
        return unzip;
    }

    public static void unzip(ZipInputStream zis, Path targetPath) throws IOException {


        Assert.notNull(zis, "Zip input stream must not be null");
        Assert.notNull(targetPath, "Target path must not be null");


        createIfAbsent(targetPath);


        ensureEmpty(targetPath);

        ZipEntry zipEntry = zis.getNextEntry();

        while (zipEntry != null) {

            Path entryPath = targetPath.resolve(zipEntry.getName());

            checkDirectoryTraversal(targetPath, entryPath);

            if (Files.notExists(entryPath.getParent())) {
                Files.createDirectories(entryPath.getParent());
            }

            if (zipEntry.isDirectory()) {

                Files.createDirectory(entryPath);
            } else {

                Files.copy(zis, entryPath);
            }

            zipEntry = zis.getNextEntry();
        }
    }

    public static void zip(Path sourcePath, Path targetPath) throws IOException {
        try (var zos = new ZipOutputStream(Files.newOutputStream(targetPath))) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    checkDirectoryTraversal(sourcePath, file);
                    var relativePath = sourcePath.relativize(file);
                    var entry = new ZipEntry(relativePath.toString());
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return super.visitFile(file, attrs);
                }
            });
        }
    }

    public static void jar(Path sourcePath, Path targetPath) throws IOException {
        try (var jos = new JarOutputStream(Files.newOutputStream(targetPath))) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    checkDirectoryTraversal(sourcePath, file);
                    var relativePath = sourcePath.relativize(file);
                    var entry = new JarEntry(relativePath.toString());
                    jos.putNextEntry(entry);
                    Files.copy(file, jos);
                    jos.closeEntry();
                    return super.visitFile(file, attrs);
                }
            });
        }
    }


    public static void createIfAbsent(Path path) throws IOException {
        Assert.notNull(path, "Path must not be null");

        if (Files.notExists(path)) {

            Files.createDirectories(path);

            log.debug("Created directory: [{}]", path);
        }
    }


    public static void ensureEmpty(Path path) throws IOException {
        if (!isEmpty(path)) {
            throw new DirectoryNotEmptyException("Target directory: " + path + " was not empty");
        }
    }


    public static boolean isEmpty(Path path) throws IOException {
        Assert.notNull(path, "Path must not be null");

        if (!Files.isDirectory(path) || Files.notExists(path)) {
            return true;
        }

        try (Stream<Path> pathStream = Files.list(path)) {
            return pathStream.findAny().isEmpty();
        }
    }

    public static void closeQuietly(final Closeable closeable) {
        closeQuietly(closeable, null);
    }


    public static void closeQuietly(final Closeable closeable, final Consumer<IOException> consumer) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (consumer != null) {
                    consumer.accept(e);
                }
            }
        }
    }


    public static void checkDirectoryTraversal(Path parentPath, Path pathToCheck) {
        Assert.notNull(parentPath, "Parent path must not be null");
        Assert.notNull(pathToCheck, "Path to check must not be null");

        if (pathToCheck.normalize().startsWith(parentPath)) {
            return;
        }

        throw new AccessDeniedException(
                "Directory traversal detected: " + pathToCheck,
                "problemDetail.directoryTraversal",
                new Object[] {parentPath, pathToCheck});
    }


    public static void checkDirectoryTraversal(String parentPath, String pathToCheck) {
        checkDirectoryTraversal(Paths.get(parentPath), Paths.get(pathToCheck));
    }


    public static void checkDirectoryTraversal(Path parentPath, String pathToCheck) {
        checkDirectoryTraversal(parentPath, Paths.get(pathToCheck));
    }


    public static void deleteRecursivelyAndSilently(Path root) {
        try {
            var deleted = deleteRecursively(root);
            if (log.isDebugEnabled()) {
                log.debug("Delete {} result: {}", root, deleted);
            }
        } catch (IOException ignored) {

        }
    }

    public static Mono<Boolean> deleteRecursivelyAndSilently(Path root, @Nullable Scheduler scheduler) {
        var delete = Mono.fromSupplier(() -> {
            try {
                return deleteRecursively(root);
            } catch (IOException ignored) {
                return false;
            }
        });
        if (scheduler != null) {
            return delete.subscribeOn(scheduler);
        }
        return delete;
    }

    public static Mono<Boolean> deleteFileSilently(Path file) {
        return deleteFileSilently(file, Schedulers.boundedElastic());
    }

    public static Mono<Boolean> deleteFileSilently(Path file, Scheduler scheduler) {
        return Mono.fromSupplier(() -> {
                    if (file == null || !Files.isRegularFile(file)) {
                        return false;
                    }
                    try {
                        return Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                        return false;
                    }
                })
                .subscribeOn(scheduler);
    }

    public static void copyResource(Resource resource, Path path) {
        try (var inputStream = resource.getInputStream()) {
            Files.copy(inputStream, path, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copy(Path source, Path dest, CopyOption... options) {
        try {
            Files.copy(source, dest, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyRecursively(Path src, Path target, Set<String> excludes) throws IOException {
        var pathMatcher = new AntPathMatcher();
        Predicate<Path> shouldExclude =
                path -> excludes.stream().anyMatch(pattern -> pathMatcher.match(pattern, path.toString()));
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!shouldExclude.test(src.relativize(file))) {
                    Files.copy(file, target.resolve(src.relativize(file)), REPLACE_EXISTING);
                }
                return super.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (shouldExclude.test(src.relativize(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(src.relativize(dir)));
                return super.preVisitDirectory(dir, attrs);
            }
        });
    }

    public static Mono<Path> createTempDir(String prefix, @Nullable Scheduler scheduler) {
        var createTempDir = Mono.fromCallable(() -> Files.createTempDirectory(prefix));
        if (scheduler != null) {
            return createTempDir.subscribeOn(scheduler);
        }
        return createTempDir;
    }
}
