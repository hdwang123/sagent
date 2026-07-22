package com.example.sagent.agent.agents.skill.tool;

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

@Component
public class DocumentTool {

    private static final String OUTPUT_DIR = "output";
    private static final String DOWNLOAD_BASE_URL = "/files/download/";

    @Tool(description = "生成Markdown文档，将内容写入指定文件")
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

    @Tool(description = "生成文本文件，将内容写入指定文件")
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

    @Tool(description = "列出output目录下的所有文件")
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