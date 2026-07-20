package com.example.sagent.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/files")
public class FileController {

    private static final String OUTPUT_DIR = "output";

    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        validateFileName(fileName);
        
        Path filePath = Paths.get(OUTPUT_DIR).resolve(fileName).normalize();
        
        if (!filePath.startsWith(Paths.get(OUTPUT_DIR))) {
            throw new ResponseStatusException(NOT_FOUND, "文件不存在");
        }
        
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new ResponseStatusException(NOT_FOUND, "文件不存在");
        }
        
        Resource resource = new FileSystemResource(file);
        
        MediaType contentType = resolveContentType(filePath);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.getName(), StandardCharsets.UTF_8)
                .build();
        
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    @GetMapping("/list")
    public java.util.List<String> listFiles() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                return java.util.List.of();
            }
            return Files.list(outputPath)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ResponseStatusException(NOT_FOUND, "无效的文件名");
        }
    }

    private MediaType resolveContentType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".zip")) {
            return MediaType.parseMediaType("application/zip");
        }
        try {
            String detectedType = Files.probeContentType(filePath);
            return detectedType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(detectedType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
