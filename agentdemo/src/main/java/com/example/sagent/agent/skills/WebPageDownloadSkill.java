package com.example.sagent.agent.skills;

import com.example.sagent.agent.tools.CompressionTool;
import com.example.sagent.agent.tools.WebPageTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebPageDownloadSkill implements Skill {

    private static final String DESCRIPTION = "网页媒体下载处理：下载网页中的所有图片、视频、音频、生成文档、压缩下载";

    private final WebPageTool webPageTool;
    private final CompressionTool compressionTool;

    public WebPageDownloadSkill(
            WebPageTool webPageTool,
            CompressionTool compressionTool
    ) {
        this.webPageTool = webPageTool;
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

    @Tool(description = "下载指定网页中的所有图片，解析HTML提取img标签的src和srcset属性，将图片保存到output目录，可选择是否压缩打包。返回下载的图片数量和下载链接")
    public String downloadAndProcessWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = webPageTool.downloadImagesFromWebPage(url, folderName);

        if (compress) {
            compressionTool.compressFiles(folderName, folderName + "_images");
            return result + "\n\n图片已压缩，下载链接: /files/download/" + folderName + "_images.zip";
        }

        return result;
    }

    @Tool(description = "下载指定网页中的所有视频，解析HTML提取video、source标签和a标签中的视频链接，将视频保存到output目录，可选择是否压缩打包。返回下载的视频数量和下载链接")
    public String downloadVideosFromWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = webPageTool.downloadVideosFromWebPage(url, folderName);

        if (compress) {
            compressionTool.compressFiles(folderName, folderName + "_videos");
            return result + "\n\n视频已压缩，下载链接: /files/download/" + folderName + "_videos.zip";
        }

        return result;
    }

    @Tool(description = "下载指定网页中的所有音频，解析HTML提取audio、source标签和a标签中的音频链接，将音频保存到output目录，可选择是否压缩打包。返回下载的音频数量和下载链接")
    public String downloadAudiosFromWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = webPageTool.downloadAudiosFromWebPage(url, folderName);

        if (compress) {
            compressionTool.compressFiles(folderName, folderName + "_audios");
            return result + "\n\n音频已压缩，下载链接: /files/download/" + folderName + "_audios.zip";
        }

        return result;
    }

    @Tool(description = "下载指定网页中的所有文档，解析HTML提取a标签中的文档链接（.pdf/.doc/.docx/.xls/.xlsx/.ppt/.pptx/.txt/.csv等），将文档保存到output目录，可选择是否压缩打包。返回下载的文档数量和下载链接")
    public String downloadDocumentsFromWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = webPageTool.downloadDocumentsFromWebPage(url, folderName);

        if (compress) {
            compressionTool.compressFiles(folderName, folderName + "_documents");
            return result + "\n\n文档已压缩，下载链接: /files/download/" + folderName + "_documents.zip";
        }

        return result;
    }

    @Tool(description = "下载指定网页的HTML内容，保存为.html文件，并生成一篇Markdown介绍文档，将所有文件保存到output目录，可选择是否压缩打包。返回保存路径和文件列表。dynamic=true时使用浏览器渲染动态页面，适合SPA等JavaScript渲染的网站")
    public String downloadWebPageContent(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否使用动态渲染（适用于SPA等JavaScript渲染的网站）") boolean dynamic,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = webPageTool.downloadWebPageContent(url, folderName, dynamic);

        if (compress) {
            compressionTool.compressFiles(folderName, folderName + "_content");
            return result + "\n\n文件已压缩，下载链接: /files/download/" + folderName + "_content.zip";
        }

        return result;
    }

    @Tool(description = "截取指定网页的完整截图，使用浏览器渲染页面后截取整张网页（包括滚动区域），保存为PNG图片到output目录，可选择是否压缩打包。返回截图路径")
    public String screenshotWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = webPageTool.screenshotWebPage(url, folderName);

        if (compress) {
            compressionTool.compressFiles(folderName, folderName + "_screenshot");
            return result + "\n\n截图已压缩，下载链接: /files/download/" + folderName + "_screenshot.zip";
        }

        return result;
    }
}