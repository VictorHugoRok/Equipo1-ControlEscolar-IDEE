package com.idee.controlescolar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public String storeAlumnoFile(Long alumnoId, MultipartFile file, String prefix) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "archivo";
        } else {
            originalName = Paths.get(originalName).getFileName().toString();
        }

        String safePrefix = prefix == null ? "documento" : prefix;
        String filename = safePrefix + "_" + System.currentTimeMillis() + "_" + originalName;

        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("alumnos")
                .resolve(String.valueOf(alumnoId));
        Files.createDirectories(baseDir);

        Path destino = baseDir.resolve(filename);
        Files.copy(file.getInputStream(), destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        log.info("Archivo almacenado en: {}", destino);
        return destino.toString();
    }
}
