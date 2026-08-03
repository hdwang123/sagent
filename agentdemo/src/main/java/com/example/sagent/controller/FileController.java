package com.example.sagent.controller;

import com.example.sagent.agent.storage.DownloadStorage;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件控制器
 * 提供文件下载和列出文件的RESTful API接口
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final DownloadStorage downloadStorage;

    public FileController(DownloadStorage downloadStorage) {
        this.downloadStorage = downloadStorage;
    }

    /**
     * 下载文件
     * 根据文件名从下载目录下载文件
     *
     * @param filename 文件名
     * @return 文件资源
     */
    @GetMapping("/download/{*filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        return buildFileResponse(filename, true);
    }

    /**
     * 预览文件
     * 根据文件名从下载目录预览文件（内联展示，不触发下载）
     *
     * @param filename 文件名
     * @return 文件资源
     */
    @GetMapping("/preview/{*filename}")
    public ResponseEntity<Resource> previewFile(@PathVariable String filename) {
        return buildFileResponse(filename, false);
    }

    /**
     * 列出文件
     * 获取下载目录下所有文件列表
     *
     * @return 文件名称列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<String>> listFiles() {
        try {
            Path outputPath = downloadStorage.getDownloadDir();
            if (!Files.exists(outputPath)) {
                return ResponseEntity.ok(List.of());
            }

            List<String> files = Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .map(outputPath::relativize)
                    .map(Path::toString)
                    .toList();

            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 构建文件响应，downloadFile 与 previewFile 共用
     * 统一处理路径穿越校验、Resource 创建、Content-Type 解析与下载头
     */
    private ResponseEntity<Resource> buildFileResponse(String filename, boolean asAttachment) {
        try {
            Path filePath = resolveFilePath(filename);
            if (filePath == null) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(resolveMediaType(contentType));
            if (asAttachment) {
                builder.header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(resource.getFilename()));
            }
            return builder.body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 校验并解析文件名对应的文件路径，防止路径穿越
     *
     * @param filename 文件名（URL路径变量）
     * @return 规范化后的文件路径；不合法或不存在时返回null
     */
    private Path resolveFilePath(String filename) {
        Path outputPath = downloadStorage.getDownloadDir();
        String cleanFilename = filename;
        if (cleanFilename.startsWith("/") || cleanFilename.startsWith("\\")) {
            cleanFilename = cleanFilename.substring(1);
        }
        Path filePath = outputPath.resolve(cleanFilename).normalize();
        if (!filePath.startsWith(outputPath)) {
            return null;
        }
        return filePath;
    }

    /**
     * 解析媒体类型，文本类类型补充UTF-8字符集，避免浏览器按默认编码解码导致中文乱码
     *
     * @param contentType 原始Content-Type
     * @return 带charset的MediaType
     */
    private MediaType resolveMediaType(String contentType) {
        MediaType mediaType = MediaType.parseMediaType(contentType);
        if (mediaType.getCharset() == null && "text".equals(mediaType.getType())) {
            return new MediaType(mediaType, StandardCharsets.UTF_8);
        }
        return mediaType;
    }

    /**
     * 构造Content-Disposition响应头（RFC 5987标准）
     * 中文文件名使用filename*（UTF-8百分号编码）传递，filename提供ASCII回退，
     * 避免Tomcat因非ASCII字符丢弃整个响应头
     *
     * @param filename 文件名
     * @return Content-Disposition头值
     */
    private String buildContentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"download\"; filename*=UTF-8''" + encoded;
    }
}
