package com.example.sagent.agent.skill.skills;

import com.example.sagent.agent.skill.tool.CompressionTool;
import com.example.sagent.agent.skill.tool.DocumentTool;
import com.example.sagent.agent.skill.tool.WebPageTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebPageDownloadSkill implements Skill {

    private static final String DESCRIPTION = "网页下载处理：下载网页内容、生成文档、压缩下载";

    private final WebPageTool webPageTool;
    private final DocumentTool documentTool;
    private final CompressionTool compressionTool;

    public WebPageDownloadSkill(
            WebPageTool webPageTool,
            DocumentTool documentTool,
            CompressionTool compressionTool
    ) {
        this.webPageTool = webPageTool;
        this.documentTool = documentTool;
        this.compressionTool = compressionTool;
    }

    @Override
    public String getName() {
        return "WebPageDownload";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Tool(description = "下载网页并生成文档，提供下载链接")
    public String downloadAndProcessWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "文件名称") String fileName,
            @ToolParam(description = "文档格式，md或txt") String format,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        webPageTool.downloadWebPage(url, fileName);
        String htmlFileName = fileName + ".html";
        String htmlContent = webPageTool.readWebPageContent(htmlFileName);

        String docFileName;
        if ("md".equalsIgnoreCase(format)) {
            String mdContent = convertHtmlToMarkdown(htmlContent);
            documentTool.generateMarkdownDocument(fileName, mdContent);
            docFileName = fileName + ".md";
        } else {
            documentTool.generateTextFile(fileName, htmlContent);
            docFileName = fileName + ".txt";
        }

        if (compress) {
            compressionTool.compressFiles(htmlFileName + "," + docFileName, fileName + "_webpage");
            return "网页已下载处理并压缩，下载链接: /files/download/" + fileName + "_webpage.zip";
        }

        return "网页已下载处理，文档下载链接: /files/download/" + docFileName;
    }

    private String convertHtmlToMarkdown(String html) {
        String markdown = html
                .replaceAll("<h1[^>]*>(.*?)</h1>", "# $1\n\n")
                .replaceAll("<h2[^>]*>(.*?)</h2>", "## $1\n\n")
                .replaceAll("<h3[^>]*>(.*?)</h3>", "### $1\n\n")
                .replaceAll("<p[^>]*>(.*?)</p>", "$1\n\n")
                .replaceAll("<a[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "[$2]($1)")
                .replaceAll("<b[^>]*>(.*?)</b>", "**$1**")
                .replaceAll("<strong[^>]*>(.*?)</strong>", "**$1**")
                .replaceAll("<ul[^>]*>(.*?)</ul>", "\n$1\n")
                .replaceAll("<li[^>]*>(.*?)</li>", "- $1\n")
                .replaceAll("<ol[^>]*>(.*?)</ol>", "\n$1\n")
                .replaceAll("<br[^>]*>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\n\\s+\n", "\n\n");
        return markdown.trim();
    }
}