package com.example.sagent.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文件控制器
 * 提供文件下载和列出文件的RESTful API接口
 */
@RestController
@RequestMapping("/files")
public class FileController {

    /**
     * 文件存储目录
     */
    private static final Path OUTPUT_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "sagent-downloads").toAbsolutePath().normalize();

    /**
     * 下载文件
     * 根据文件名从output目录下载文件
     *
     * @param filename 文件名
     * @return 文件资源
     */
    @GetMapping("/download/{*filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = OUTPUT_PATH.resolve(filename).normalize();
            if (!filePath.startsWith(OUTPUT_PATH)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 列出文件
     * 获取output目录下所有文件列表
     *
     * @return 文件名称列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<String>> listFiles() {
        try {
            if (!Files.exists(OUTPUT_PATH)) {
                return ResponseEntity.ok(List.of());
            }

            List<String> files = Files.walk(OUTPUT_PATH)
                    .filter(Files::isRegularFile)
                    .map(OUTPUT_PATH::relativize)
                    .map(Path::toString)
                    .toList();

            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}