package com.idee.controlescolar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public String storeAlumnoFile(Long alumnoId, MultipartFile file, String prefix) throws IOException {
        String originalName = safeOriginalFilename(file.getOriginalFilename(), "archivo");

        String safePrefix = safePathSegment(prefix, "documento");
        String filename = originalName;

        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("alumnos")
                .resolve(String.valueOf(alumnoId))
                .resolve(safePrefix);
        Files.createDirectories(baseDir);

        Path destino = baseDir.resolve(filename);
        Files.copy(file.getInputStream(), destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        log.info("Archivo almacenado en: {}", destino);
        return destino.toString();
    }

    public String storeAlumnoBytes(Long alumnoId, byte[] bytes, String prefix, String originalFilename) throws IOException {
        String originalName = safeOriginalFilename(originalFilename, "archivo");
        String safePrefix = safePathSegment(prefix, "documento");
        String filename = originalName;

        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("alumnos")
                .resolve(String.valueOf(alumnoId))
                .resolve(safePrefix);
        Files.createDirectories(baseDir);

        Path destino = baseDir.resolve(filename);
        try (InputStream in = new java.io.ByteArrayInputStream(bytes != null ? bytes : new byte[0])) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Archivo (bytes) almacenado en: {}", destino);
        return destino.toString();
    }

    public String storePersonalFile(Long personalId, MultipartFile file, String prefix) throws IOException {
        String originalName = safeOriginalFilename(file.getOriginalFilename(), "archivo");

        String safePrefix = safePathSegment(prefix, "documento");
        String filename = originalName;

        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("personal")
                .resolve(String.valueOf(personalId))
                .resolve(safePrefix);
        Files.createDirectories(baseDir);

        Path destino = baseDir.resolve(filename);
        Files.copy(file.getInputStream(), destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        log.info("Archivo almacenado en: {}", destino);
        return destino.toString();
    }

    public String storePersonalBytes(Long personalId, byte[] bytes, String prefix, String originalFilename) throws IOException {
        String originalName = safeOriginalFilename(originalFilename, "archivo");
        String safePrefix = safePathSegment(prefix, "documento");
        String filename = originalName;
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("personal")
                .resolve(String.valueOf(personalId))
                .resolve(safePrefix);
        Files.createDirectories(baseDir);
        Path destino = baseDir.resolve(filename);
        try (InputStream in = new java.io.ByteArrayInputStream(bytes != null ? bytes : new byte[0])) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Archivo personal (bytes) almacenado en: {}", destino);
        return destino.toString();
    }

    public String storeMaestroFile(Long maestroId, MultipartFile file, String prefix) throws IOException {
        String originalName = safeOriginalFilename(file.getOriginalFilename(), "archivo");
        String safePrefix = safePathSegment(prefix, "documento");
        String filename = originalName;
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("maestros")
                .resolve(String.valueOf(maestroId))
                .resolve(safePrefix);
        Files.createDirectories(baseDir);
        Path destino = baseDir.resolve(filename);
        Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        log.info("Archivo maestro almacenado en: {}", destino);
        return destino.toString();
    }

    public String storeMaestroBytes(Long maestroId, byte[] bytes, String prefix, String originalFilename) throws IOException {
        String originalName = safeOriginalFilename(originalFilename, "archivo");
        String safePrefix = safePathSegment(prefix, "documento");
        String filename = originalName;
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("maestros")
                .resolve(String.valueOf(maestroId))
                .resolve(safePrefix);
        Files.createDirectories(baseDir);
        Path destino = baseDir.resolve(filename);
        try (InputStream in = new java.io.ByteArrayInputStream(bytes != null ? bytes : new byte[0])) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Archivo maestro (bytes) almacenado en: {}", destino);
        return destino.toString();
    }

    private static String safeOriginalFilename(String originalFilename, String fallback) {
        String value = originalFilename == null ? "" : originalFilename.trim();
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[^A-Za-z0-9._ -]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            normalized = fallback;
        }
        return normalized.length() > 180 ? normalized.substring(0, 180).trim() : normalized;
    }

    private static String safePathSegment(String value, String fallback) {
        String safe = safeOriginalFilename(value, fallback)
                .replaceAll("[ .]+$", "")
                .toLowerCase(Locale.ROOT);
        return safe.isBlank() ? fallback : safe;
    }
}
