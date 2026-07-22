package com.example.sagent.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档工具
 * 提供生成Markdown文档和文本文件功能
 */
@Component
public class DocumentTool {

    /**
     * 输出目录
     */
    private static final String OUTPUT_DIR = "output";

    /**
     * 下载基础URL
     */
    private static final String DOWNLOAD_BASE_URL = "/files/download/";

    /**
     * 生成Markdown文档
     * 将内容写入指定的Markdown文件
     *
     * @param fileName 文件名称，不含扩展名
     * @param content  文档内容
     * @return 文档下载链接
     */
    @Tool(description = "生成Markdown文档，将内容写入指定文件。该工具会创建一个.md文件，将提供的文本内容保存到output目录下，并返回可下载的链接。适用于生成报告、文档、笔记等格式化文本内容。")
    public String generateMarkdownDocument(
            @ToolParam(description = "文件名，不含扩展名") String fileName,
            @ToolParam(description = "文档内容") String content
    ) {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            String fullFileName = fileName + ".md";
            Path filePath = outputPath.resolve(fullFileName);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(filePath.toFile()), "UTF-8")) {
                writer.write(content);
            }
            String downloadUrl = DOWNLOAD_BASE_URL + fullFileName;
            return "Markdown文档已生成，下载链接: " + downloadUrl;
        } catch (IOException e) {
            throw new RuntimeException("生成文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成文本文件
     * 将内容写入指定的文本文件
     *
     * @param fileName 文件名称，不含扩展名
     * @param content  文件内容
     * @return 文件下载链接
     */
    @Tool(description = "生成文本文件，将内容写入指定文件。该工具会创建一个.txt文件，将提供的文本内容保存到output目录下，并返回可下载的链接。适用于生成纯文本内容、日志文件、配置信息等。")
    public String generateTextFile(
            @ToolParam(description = "文件名，不含扩展名") String fileName,
            @ToolParam(description = "文件内容") String content
    ) {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            String fullFileName = fileName + ".txt";
            Path filePath = outputPath.resolve(fullFileName);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(filePath.toFile()), "UTF-8")) {
                writer.write(content);
            }
            String downloadUrl = DOWNLOAD_BASE_URL + fullFileName;
            return "文本文件已生成，下载链接: " + downloadUrl;
        } catch (IOException e) {
            throw new RuntimeException("生成文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出输出目录下的文件
     *
     * @return 文件名称列表
     */
    @Tool(description = "列出output目录下的所有文件。该工具会扫描output目录，返回该目录下所有文件的名称列表。适用于查看已生成的文档和文件，方便用户了解当前可用的下载文件。")
    public List<String> listOutputFiles() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                return List.of("output目录不存在");
            }
            return Files.list(outputPath)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("列出文件失败: " + e.getMessage(), e);
        }
    }
}