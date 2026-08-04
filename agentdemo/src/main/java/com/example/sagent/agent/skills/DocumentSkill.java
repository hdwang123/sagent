package com.example.sagent.agent.skills;

import com.example.sagent.agent.model.AgentResult;
import com.example.sagent.agent.storage.DownloadStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档生成技能
 * 提供生成、读取Markdown文档的能力，文档保存到下载目录，可通过 /files/download/ 下载
 */
@Component
public class DocumentSkill implements Skill {

    private static final String NAME = "document";
    private static final String DESCRIPTION = "生成Markdown文档、读取文档内容、生成文本文件";

    /** 读取文档内容的最大字符数，防止超长文本撑爆上下文 */
    private static final int MAX_READ_CHARS = 8000;

    private final DownloadStorage downloadStorage;
    private final ObjectMapper objectMapper;

    public DocumentSkill(DownloadStorage downloadStorage, ObjectMapper objectMapper) {
        this.downloadStorage = downloadStorage;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    private String validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("文件名不允许包含路径分隔符或上级目录引用");
        }
        return fileName;
    }

    private Path getValidatedOutputDir() {
        Path basePath = downloadStorage.getDownloadDir();
        if (!Files.exists(basePath)) {
            try {
                Files.createDirectories(basePath);
            } catch (IOException e) {
                throw new RuntimeException("创建下载目录失败: " + e.getMessage(), e);
            }
        }
        return basePath;
    }

    /**
     * 将业务结果序列化为 {@link AgentResult} JSON 字符串。
     * 所有 {@code @Tool(returnDirect = true)} 方法统一通过此方法返回，
     * 保证返回结构一致（{@code {"code":..., "content":...}}），
     * 由 Handler 层反序列化后提取 code 与 content。
     *
     * @param code    业务状态码（200=成功，4xx=业务失败，5xx=技术错误）
     * @param content 回答正文
     * @return AgentResult 的 JSON 字符串
     */
    private String toAgentResultJson(int code, String content) {
        try {
            return objectMapper.writeValueAsString(new AgentResult(code, content));
        } catch (JsonProcessingException e) {
            // 极端情况下序列化失败，回退为最简 JSON 字面量，保证返回结构仍是 {code, content}
            return "{\"code\":" + code + ",\"content\":\"序列化失败\"}";
        }
    }

    /**
     * 生成Markdown文档
     * 将用户提供的标题和正文内容保存为.md文件到output目录，返回下载链接
     */
    @Tool(returnDirect = true, description = "生成一份Markdown文档，根据提供的标题和正文内容创建.md文件并保存到output目录，返回文件的下载链接。适用于生成报告、说明文档、总结等文本文件")
    public String generateMarkdownDocument(
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "文档正文内容，支持Markdown格式，如标题、列表、表格等") String content,
            @ToolParam(description = "文件名（不含扩展名），会加上.md后缀") String fileName
    ) {
        String safeFileName = validateFileName(fileName);
        Path outputDir = getValidatedOutputDir();

        String markdown = """
                # %s

                ---

                %s

                ---

                *由 Sagent DocumentSkill 自动生成*
                """.formatted(title, content == null ? "" : content);

        Path filePath = outputDir.resolve(safeFileName + ".md");
        try {
            Files.writeString(filePath, markdown, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("生成文档失败: " + e.getMessage(), e);
        }

        String resultText = String.format("文档生成完成：\n" +
                "标题: %s\n" +
                "文件名: %s.md\n" +
                "下载链接: %s",
                title, safeFileName, downloadStorage.getDownloadBaseUrl() + safeFileName + ".md");
        return toAgentResultJson(AgentResult.CODE_SUCCESS, resultText);
    }

    /**
     * 读取已生成的文档内容
     * 从output目录读取指定文件的内容并返回，用于后续任务参考或汇总
     */
    @Tool(returnDirect = true, description = "读取output目录下已存在的文档或文本文件的内容，返回文件正文。适用于查看之前生成的文档内容、核对文档是否包含指定信息、基于已有文档继续加工")
    public String readDocument(
            @ToolParam(description = "要读取的文件名（包含.md扩展名），如：产品1-详细信息.md") String fileName
    ) {
        String safeFileName = validateFileName(fileName);
        Path outputDir = getValidatedOutputDir();
        Path filePath = outputDir.resolve(safeFileName).normalize();
        if (!filePath.startsWith(outputDir)) {
            throw new IllegalArgumentException("文件名不允许包含上级目录引用");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            String notFound = String.format(
                    "文件不存在: %s，请先使用generateMarkdownDocument生成该文档，或通过listFiles确认可用的文件列表",
                    safeFileName);
            // 资源不存在属于业务失败，使用 404 码便于编排层判断
            return toAgentResultJson(AgentResult.CODE_NOT_FOUND, notFound);
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (content.length() > MAX_READ_CHARS) {
                content = content.substring(0, MAX_READ_CHARS) + "\n...(内容过长，已截断)";
            }
            return toAgentResultJson(AgentResult.CODE_SUCCESS,
                    String.format("文件内容（%s）：\n%s", safeFileName, content));
        } catch (IOException e) {
            throw new RuntimeException("读取文档失败: " + e.getMessage(), e);
        }
    }
}
