package com.example.sagent.agent.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 下载文件存储配置
 * 统一管理下载文件的输出目录与下载URL前缀，
 * 供 DocumentSkill、WebPageDownloadSkill、FileController 共用，
 * 避免同一目录在多处被重复定义
 */
@Component
public class DownloadStorage {

    private static final String DEFAULT_DOWNLOAD_BASE_URL = "/files/download/";

    private final Path downloadDir;

    public DownloadStorage(@Value("${app.download-dir:}") String configuredDir) {
        String dir = (configuredDir == null || configuredDir.isBlank())
                ? Paths.get(System.getProperty("java.io.tmpdir"), "sagent-downloads").toString()
                : configuredDir;
        this.downloadDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    /**
     * 获取下载文件输出目录（绝对路径）
     */
    public Path getDownloadDir() {
        return downloadDir;
    }

    /**
     * 获取下载URL前缀
     */
    public String getDownloadBaseUrl() {
        return DEFAULT_DOWNLOAD_BASE_URL;
    }
}
