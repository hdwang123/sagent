package com.example.sagent.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 网页工具
 * 提供下载网页和读取网页内容功能
 */
@Component
public class WebPageTool {

    /**
     * 输出目录
     */
    private static final String OUTPUT_DIR = "output";

    /**
     * 下载基础URL
     */
    private static final String DOWNLOAD_BASE_URL = "/files/download/";

    /**
     * 下载网页
     * 下载指定URL的网页内容，保存为HTML文件
     *
     * @param url      网页URL地址
     * @param fileName 保存的文件名，不含扩展名
     * @return 文件下载链接或错误信息
     */
    @Tool(description = "下载指定URL的网页内容，将HTML内容保存到output目录中，返回文件的下载链接")
    public String downloadWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件名，不含扩展名") String fileName
    ) {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }

            URL webpageUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) webpageUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return "下载失败，HTTP状态码: " + responseCode;
            }

            String fullFileName = fileName + ".html";
            Path filePath = outputPath.resolve(fullFileName);
            try (InputStream inputStream = connection.getInputStream();
                 Writer writer = new OutputStreamWriter(new FileOutputStream(filePath.toFile()), "UTF-8")) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    writer.write(new String(buffer, 0, bytesRead));
                }
            }

            connection.disconnect();
            String downloadUrl = DOWNLOAD_BASE_URL + fullFileName;
            return "网页已下载，下载链接: " + downloadUrl;
        } catch (IOException e) {
            throw new RuntimeException("下载网页失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取网页内容
     * 从output目录中读取指定的HTML文件内容
     *
     * @param fileName HTML文件名，含扩展名
     * @return HTML文件内容或错误信息
     */
    @Tool(description = "读取已下载的网页HTML文件内容")
    public String readWebPageContent(
            @ToolParam(description = "HTML文件名，含扩展名") String fileName
    ) {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            Path filePath = outputPath.resolve(fileName).normalize();
            
            if (!filePath.startsWith(outputPath)) {
                return "错误: 不允许访问output目录外的文件";
            }
            if (!Files.exists(filePath)) {
                return "错误: 文件不存在 - " + fileName;
            }
            
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出输出目录下的文件
     *
     * @return 文件名称列表
     */
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