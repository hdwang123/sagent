package com.example.sagent.agent.skill.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class CompressionTool {

    private static final String OUTPUT_DIR = "output";
    private static final String DOWNLOAD_BASE_URL = "/files/download/";

    @Tool(description = "压缩output目录下的文件生成ZIP压缩包")
    public String compressFiles(
            @ToolParam(description = "要压缩的文件列表，用逗号分隔，仅支持output目录下的文件") String fileNames,
            @ToolParam(description = "压缩包文件名，不含扩展名") String zipFileName
    ) {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                return "错误: output目录不存在";
            }
            String fullZipFileName = zipFileName + ".zip";
            Path zipFilePath = outputPath.resolve(fullZipFileName);
            String[] files = fileNames.split(",");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
                for (String fileName : files) {
                    fileName = fileName.trim();
                    Path filePath = outputPath.resolve(fileName).normalize();
                    if (!filePath.startsWith(outputPath)) {
                        return "错误: 不允许访问output目录外的文件";
                    }
                    if (!Files.exists(filePath)) {
                        return "错误: 文件不存在 - " + fileName;
                    }
                    if (Files.isDirectory(filePath)) {
                        continue;
                    }
                    ZipEntry entry = new ZipEntry(fileName);
                    zos.putNextEntry(entry);
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
            String downloadUrl = DOWNLOAD_BASE_URL + fullZipFileName;
            return "压缩包已生成，下载链接: " + downloadUrl;
        } catch (IOException e) {
            throw new RuntimeException("生成压缩包失败: " + e.getMessage(), e);
        }
    }
}