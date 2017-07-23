package com.github.couchmove.utils;

import com.github.couchmove.exception.CouchmoveException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.io.IOUtils.toByteArray;

/**
 * @author ctayeb
 * Created on 02/06/2017
 */
public class FileUtils {

    /**
     * Returns Path of a resource in classpath no matter whether it is in a jar or in absolute or relative folder
     *
     * @param resource path
     * @return Path of a resource
     * @throws IOException if an I/O error occurs
     */
    public static Path getPathFromResource(String resource) throws IOException {
        File file = new File(resource);
        if (file.exists()) {
            return file.toPath();
        }
        URL resourceURL = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (resourceURL == null) {
            resourceURL = FileUtils.class.getResource(resource);
        }
        if (resourceURL == null) {
            throw new FileNotFoundException(resource);
        }
        URI uri;
        try {
            uri = resourceURL.toURI();
        } catch (URISyntaxException e) {
            // Can not happen normally
            throw new RuntimeException(e);
        }
        Path folder;
        if (uri.getScheme().equals("jar")) {
            FileSystem fileSystem;
            try {
                fileSystem = FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException e) {
                fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
            }
            folder = fileSystem.getPath(resource);
        } else {
            folder = Paths.get(uri);
        }
        return folder;
    }

    /**
     * If the file is a Directory, calculate the checksum of all files in this directory (one level)
     * Else, calculate the checksum of the file matching extensions
     *
     * @param file       file or folder
     * @param extensions of files to calculate checksum of
     * @return checksum
     */
    public static String calculateChecksum(@NotNull File file, String... extensions) {
        if (file == null || !file.exists()) {
            throw new CouchmoveException("File is null or doesn't exists");
        }
        if (file.isDirectory()) {
            //noinspection ConstantConditions
            return Arrays.stream(file.listFiles())
                    .filter(File::isFile)
                    .filter(f -> Arrays.stream(extensions)
                            .anyMatch(extension -> FilenameUtils
                                    .getExtension(f.getName()).toLowerCase()
                                    .equals(extension.toLowerCase())))
                    .sorted(Comparator.comparing(File::getName))
                    .map(FileUtils::calculateChecksum)
                    .reduce(String::concat)
                    .map(DigestUtils::sha256Hex)
                    .orElse(null);
        }
        try {
            return DigestUtils.sha256Hex(toByteArray(file.toURI()));
        } catch (IOException e) {
            throw new CouchmoveException("Unable to calculate file checksum '" + file.getName() + "'");
        }
    }

    /**
     * Read files content from a (@link File}
     *
     * @param file       The directory containing files to read
     * @param extensions The extensions of the files to read
     * @return {@link Map} which keys represents the name (with extension), and values the content of read files
     * @throws IOException if an I/O error occurs reading the files
     */
    public static Map<String, String> readFilesInDirectory(@NotNull File file, String... extensions) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File is null or doesn't exists");
        }
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("'" + file.getPath() + "' is not a directory");
        }
        try {
            //noinspection ConstantConditions
            return Arrays.stream(file.listFiles())
                    .filter(File::isFile)
                    .filter(f -> Arrays.stream(extensions)
                            .anyMatch(extension -> FilenameUtils
                                    .getExtension(f.getName()).toLowerCase()
                                    .equals(extension.toLowerCase())))
                    .collect(Collectors.toMap(File::getName, f -> {
                        try {
                            return new String(Files.readAllBytes(f.toPath()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }
}