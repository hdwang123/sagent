package com.example.sagent.agent.skills;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class WebPageDownloadSkill implements Skill {

    private static final String NAME = "webPageDownload";
    private static final String DESCRIPTION = "下载网页内容、图片、视频、音频、文档，截取网页截图，文件压缩打包";

    private static final String OUTPUT_DIR = System.getProperty("java.io.tmpdir") + "/sagent-downloads";
    private static final String DOWNLOAD_BASE_URL = "/files/download/";
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    private void validateFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("文件夹名称不能为空");
        }
        if (folderName.contains("..") || folderName.contains("/") || folderName.contains("\\")) {
            throw new IllegalArgumentException("文件夹名称不允许包含路径分隔符或上级目录引用");
        }
    }

    private Path getValidatedFolderPath(String folderName) {
        validateFolderName(folderName);
        Path basePath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(basePath)) {
            try {
                Files.createDirectories(basePath);
            } catch (IOException e) {
                throw new RuntimeException("创建output目录失败: " + e.getMessage(), e);
            }
        }
        Path folderPath = basePath.resolve(folderName).normalize();
        if (!folderPath.startsWith(basePath)) {
            throw new IllegalArgumentException("不允许访问output目录外的路径");
        }
        if (!Files.exists(folderPath)) {
            try {
                Files.createDirectories(folderPath);
            } catch (IOException e) {
                throw new RuntimeException("创建文件夹失败: " + e.getMessage(), e);
            }
        }
        return folderPath;
    }

    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRCSET_PATTERN = Pattern.compile("<img[^>]+srcset\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_SRC_PATTERN = Pattern.compile("<video[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUDIO_SRC_PATTERN = Pattern.compile("<audio[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_SRC_PATTERN = Pattern.compile("<source[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_HREF_PATTERN = Pattern.compile("<a[^>]+href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    @Tool(returnDirect = true, description = "下载指定网页中的所有图片，解析HTML提取img标签的src和srcset属性，将图片保存到output目录，可选择是否压缩打包。返回下载的图片数量和下载链接。dynamic=true时使用浏览器渲染动态页面")
    public String downloadImagesFromWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否使用动态渲染（适用于SPA等JavaScript渲染的网站）") boolean dynamic,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = downloadMediaFromWebPage(url, folderName, this::extractImageUrls, "image_", ".jpg", "图片", dynamic);

        if (compress) {
            compressFiles(folderName, folderName + "_images");
            return result + "\n\n图片已压缩，下载链接: " + DOWNLOAD_BASE_URL + folderName + "_images.zip";
        }

        return result;
    }

    @Tool(returnDirect = true, description = "下载指定网页中的所有视频，解析HTML提取video、source标签和a标签中的视频链接，将视频保存到output目录，可选择是否压缩打包。返回下载的视频数量和下载链接。dynamic=true时使用浏览器渲染动态页面")
    public String downloadVideosFromWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否使用动态渲染（适用于SPA等JavaScript渲染的网站）") boolean dynamic,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = downloadMediaFromWebPage(url, folderName, this::extractVideoUrls, "video_", ".mp4", "视频", dynamic);

        if (compress) {
            compressFiles(folderName, folderName + "_videos");
            return result + "\n\n视频已压缩，下载链接: " + DOWNLOAD_BASE_URL + folderName + "_videos.zip";
        }

        return result;
    }

    @Tool(returnDirect = true, description = "下载指定网页中的所有音频，解析HTML提取audio、source标签和a标签中的音频链接，将音频保存到output目录，可选择是否压缩打包。返回下载的音频数量和下载链接。dynamic=true时使用浏览器渲染动态页面")
    public String downloadAudiosFromWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否使用动态渲染（适用于SPA等JavaScript渲染的网站）") boolean dynamic,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = downloadMediaFromWebPage(url, folderName, this::extractAudioUrls, "audio_", ".mp3", "音频", dynamic);

        if (compress) {
            compressFiles(folderName, folderName + "_audios");
            return result + "\n\n音频已压缩，下载链接: " + DOWNLOAD_BASE_URL + folderName + "_audios.zip";
        }

        return result;
    }

    @Tool(returnDirect = true, description = "下载指定网页中的所有文档，解析HTML提取a标签中的文档链接（.pdf/.doc/.docx/.xls/.xlsx/.ppt/.pptx/.txt/.csv等），将文档保存到output目录，可选择是否压缩打包。返回下载的文档数量和下载链接。dynamic=true时使用浏览器渲染动态页面")
    public String downloadDocumentsFromWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否使用动态渲染（适用于SPA等JavaScript渲染的网站）") boolean dynamic,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        String result = downloadMediaFromWebPage(url, folderName, this::extractDocumentUrls, "document_", ".pdf", "文档", dynamic);

        if (compress) {
            compressFiles(folderName, folderName + "_documents");
            return result + "\n\n文档已压缩，下载链接: " + DOWNLOAD_BASE_URL + folderName + "_documents.zip";
        }

        return result;
    }

    @Tool(returnDirect = true, description = "下载指定网页的HTML内容，保存为.html文件，并生成一篇Markdown介绍文档，将所有文件保存到output目录，可选择是否压缩打包。返回保存路径和文件列表。dynamic=true时使用浏览器渲染动态页面，适合SPA等JavaScript渲染的网站")
    public String downloadWebPageContent(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否使用动态渲染（适用于SPA等JavaScript渲染的网站）") boolean dynamic,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        Path folderPath = getValidatedFolderPath(folderName);

        try {
            String htmlContent;
            if (dynamic) {
                htmlContent = fetchDynamicHtml(url);
            } else {
                htmlContent = fetchStaticHtml(url);
            }

            String pageTitle = extractPageTitle(htmlContent);
            String htmlFileName = generateHtmlFileName(pageTitle);
            String mdFileName = generateMdFileName(pageTitle);

            Path htmlFilePath = folderPath.resolve(htmlFileName);
            Path mdFilePath = folderPath.resolve(mdFileName);

            Files.writeString(htmlFilePath, htmlContent, StandardCharsets.UTF_8);

            String mdContent = generateMarkdownIntroduction(url, pageTitle, htmlFileName, htmlContent.length(), dynamic);
            Files.writeString(mdFilePath, mdContent, StandardCharsets.UTF_8);

            String result = String.format("网页内容下载完成：\n" +
                    "网页标题: %s\n" +
                    "渲染方式: %s\n" +
                    "下载链接:\n" +
                    "- %s: %s\n" +
                    "- %s: %s",
                    pageTitle, dynamic ? "动态渲染(浏览器)" : "静态抓取(HTTP)",
                    htmlFileName, DOWNLOAD_BASE_URL + folderName + "/" + htmlFileName,
                    mdFileName, DOWNLOAD_BASE_URL + folderName + "/" + mdFileName);

            if (compress) {
                compressFiles(folderName, folderName + "_content");
                return result + "\n\n文件已压缩，下载链接: " + DOWNLOAD_BASE_URL + folderName + "_content.zip";
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException("下载网页内容失败: " + e.getMessage(), e);
        }
    }

    @Tool(returnDirect = true, description = "截取指定网页的完整截图，使用浏览器渲染页面后截取整张网页（包括滚动区域），保存为PNG图片到output目录，可选择是否压缩打包。返回截图路径")
    public String screenshotWebPage(
            @ToolParam(description = "网页URL地址") String url,
            @ToolParam(description = "保存的文件夹名称") String folderName,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        Path folderPath = getValidatedFolderPath(folderName);

        try (com.microsoft.playwright.Playwright playwright = com.microsoft.playwright.Playwright.create();
             com.microsoft.playwright.Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
                     .setHeadless(true)
                     .setTimeout(60000))) {

            com.microsoft.playwright.BrowserContext context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

            try (com.microsoft.playwright.Page page = context.newPage()) {
                page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(30000.0));

                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                TimeUnit.SECONDS.sleep(2);

                String pageTitle = page.title();
                String fileName = "screenshot_" + System.currentTimeMillis() + ".png";
                Path screenshotPath = folderPath.resolve(fileName);

                page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                        .setFullPage(true)
                        .setPath(screenshotPath)
                        .setType(com.microsoft.playwright.options.ScreenshotType.PNG));

                String result = String.format("网页：%s\n网站：%s\n\n截图：%s",
                        pageTitle, url, DOWNLOAD_BASE_URL + folderName + "/" + fileName);

                if (compress) {
                    compressFiles(folderName, folderName + "_screenshot");
                    return result + "\n\n截图已压缩，下载链接: " + DOWNLOAD_BASE_URL + folderName + "_screenshot.zip";
                }

                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("截图失败: " + e.getMessage(), e);
        }
    }

    @Tool(returnDirect = true, description = "将指定文件夹中的所有文件压缩成ZIP文件，保存到output目录，返回下载链接")
    public String compressFiles(
            @ToolParam(description = "要压缩的源文件夹名称") String sourceFolderName,
            @ToolParam(description = "压缩后的文件名（不含扩展名）") String zipFileName
    ) {
        validateFolderName(sourceFolderName);
        validateFolderName(zipFileName);

        Path sourcePath = Paths.get(OUTPUT_DIR).resolve(sourceFolderName).normalize();
        Path zipPath = Paths.get(OUTPUT_DIR).resolve(zipFileName + ".zip").normalize();

        if (!sourcePath.startsWith(Paths.get(OUTPUT_DIR))) {
            throw new IllegalArgumentException("不允许访问output目录外的路径");
        }

        if (!Files.exists(sourcePath)) {
            return "源文件夹不存在: " + sourceFolderName;
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Files.walk(sourcePath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Path relativePath = sourcePath.relativize(file);
                            ZipEntry zipEntry = new ZipEntry(relativePath.toString());
                            zos.putNextEntry(zipEntry);
                            Files.copy(file, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("压缩文件失败: " + e.getMessage(), e);
                        }
                    });

            return String.format("压缩完成：\n" +
                    "源文件夹: %s\n" +
                    "压缩文件: %s\n" +
                    "下载链接: %s",
                    sourceFolderName, zipFileName + ".zip", DOWNLOAD_BASE_URL + zipFileName + ".zip");
        } catch (IOException e) {
            throw new RuntimeException("创建压缩文件失败: " + e.getMessage(), e);
        }
    }

    private String fetchStaticHtml(String url) throws IOException {
        URL webpageUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) webpageUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP状态码: " + responseCode);
            }

            StringBuilder htmlBuilder = new StringBuilder();
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    htmlBuilder.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                }
            }

            return htmlBuilder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private String fetchDynamicHtml(String url) throws IOException {
        try (com.microsoft.playwright.Playwright playwright = com.microsoft.playwright.Playwright.create();
             com.microsoft.playwright.Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
                     .setHeadless(true)
                     .setTimeout(60000))) {

            com.microsoft.playwright.BrowserContext context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

            try (com.microsoft.playwright.Page page = context.newPage()) {
                page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(30000.0));

                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                TimeUnit.SECONDS.sleep(2);

                return page.content();
            }
        } catch (Exception e) {
            throw new IOException("动态渲染失败: " + e.getMessage(), e);
        }
    }

    private String extractPageTitle(String html) {
        Pattern titlePattern = Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = titlePattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Untitled Page";
    }

    private String generateHtmlFileName(String pageTitle) {
        String safeTitle = pageTitle.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeTitle.isEmpty() || safeTitle.length() > 100) {
            safeTitle = "page_" + System.currentTimeMillis();
        }
        return safeTitle + ".html";
    }

    private String generateMdFileName(String pageTitle) {
        String safeTitle = pageTitle.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeTitle.isEmpty() || safeTitle.length() > 100) {
            safeTitle = "page_" + System.currentTimeMillis();
        }
        return safeTitle + "_介绍文档.md";
    }

    private String generateMarkdownIntroduction(String url, String pageTitle, String htmlFileName, int contentLength, boolean dynamic) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return """
                # %s
                
                ## 基本信息
                
                | 属性 | 值 |
                |------|-----|
                | 源URL | %s |
                | 下载时间 | %s |
                | 渲染方式 | %s |
                | HTML文件名 | %s |
                | HTML内容大小 | %d 字节 |
                
                ## 文档说明
                
                此文档包含从网页下载的完整HTML内容，用于离线浏览或分析。
                
                ### 包含文件
                
                1. **%s** - 网页完整HTML内容
                2. **此文件** - 网页介绍文档
                
                ### 下载说明
                
                - 网页内容已完整保存，可直接用浏览器打开HTML文件查看
                - 图片、视频等外部资源可能需要网络连接才能正常显示
                - 如需下载网页中的媒体资源，请使用其他下载工具
                
                ### 渲染方式说明
                
                %s
                
                ---
                
                *由 Sagent WebPageDownloadSkill 自动生成*
                """.formatted(pageTitle, url, now.format(formatter),
                dynamic ? "动态渲染(浏览器)" : "静态抓取(HTTP)",
                htmlFileName, contentLength, htmlFileName,
                dynamic ? "使用浏览器渲染模式，适用于SPA等JavaScript动态渲染的网站，可获取完整的渲染后内容。" : "使用HTTP静态抓取模式，适用于传统服务端渲染的网站，速度较快。");
    }

    private String downloadMediaFromWebPage(String url, String folderName,
                                            BiFunction<String, String, Set<String>> urlExtractor,
                                            String defaultPrefix, String defaultExt, String mediaTypeLabel,
                                            boolean dynamic) {
        Path folderPath = getValidatedFolderPath(folderName);

        try {
            String htmlContent;
            if (dynamic) {
                htmlContent = fetchDynamicHtml(url);
            } else {
                htmlContent = fetchStaticHtml(url);
            }

            Set<String> mediaUrls = urlExtractor.apply(htmlContent, url);
            if (mediaUrls.isEmpty()) {
                return "网页中未找到" + mediaTypeLabel;
            }

            List<String> downloadedFiles = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (String mediaUrl : mediaUrls) {
                try {
                    String fileName = extractFileName(mediaUrl);
                    if (fileName.isEmpty() || fileName.length() > 200) {
                        fileName = defaultPrefix + System.currentTimeMillis() + defaultExt;
                    }

                    Path filePath = folderPath.resolve(fileName);
                    downloadFile(mediaUrl, filePath);
                    downloadedFiles.add(fileName);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                }
            }

            String result = String.format("从网页下载%s完成：成功 %d 个，失败 %d 个\n", mediaTypeLabel, successCount, failCount);
            if (successCount > 0) {
                result += "下载链接:\n";
                for (String f : downloadedFiles) {
                    result += "- " + f + ": " + DOWNLOAD_BASE_URL + folderName + "/" + f + ";";
                }
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException("下载网页" + mediaTypeLabel + "失败: " + e.getMessage(), e);
        }
    }

    private Set<String> extractImageUrls(String html, String baseUrl) {
        Set<String> imageUrls = new HashSet<>();

        Matcher imgMatcher = IMG_SRC_PATTERN.matcher(html);
        while (imgMatcher.find()) {
            String src = imgMatcher.group(1).trim();
            if (isValidImageUrl(src)) {
                imageUrls.add(resolveUrl(src, baseUrl));
            }
        }

        Matcher srcsetMatcher = SRCSET_PATTERN.matcher(html);
        while (srcsetMatcher.find()) {
            String srcset = srcsetMatcher.group(1);
            String[] entries = srcset.split(",");
            for (String entry : entries) {
                String url = entry.trim().split("\\s+")[0];
                if (isValidImageUrl(url)) {
                    imageUrls.add(resolveUrl(url, baseUrl));
                }
            }
        }

        return imageUrls;
    }

    private Set<String> extractVideoUrls(String html, String baseUrl) {
        Set<String> videoUrls = new HashSet<>();

        Matcher videoMatcher = VIDEO_SRC_PATTERN.matcher(html);
        while (videoMatcher.find()) {
            String src = videoMatcher.group(1).trim();
            if (isValidVideoUrl(src)) {
                videoUrls.add(resolveUrl(src, baseUrl));
            }
        }

        Matcher sourceMatcher = SOURCE_SRC_PATTERN.matcher(html);
        while (sourceMatcher.find()) {
            String src = sourceMatcher.group(1).trim();
            if (isValidVideoUrl(src)) {
                videoUrls.add(resolveUrl(src, baseUrl));
            }
        }

        Matcher linkMatcher = LINK_HREF_PATTERN.matcher(html);
        while (linkMatcher.find()) {
            String href = linkMatcher.group(1).trim();
            if (isValidVideoUrl(href)) {
                videoUrls.add(resolveUrl(href, baseUrl));
            }
        }

        return videoUrls;
    }

    private Set<String> extractAudioUrls(String html, String baseUrl) {
        Set<String> audioUrls = new HashSet<>();

        Matcher audioMatcher = AUDIO_SRC_PATTERN.matcher(html);
        while (audioMatcher.find()) {
            String src = audioMatcher.group(1).trim();
            if (isValidAudioUrl(src)) {
                audioUrls.add(resolveUrl(src, baseUrl));
            }
        }

        Matcher sourceMatcher = SOURCE_SRC_PATTERN.matcher(html);
        while (sourceMatcher.find()) {
            String src = sourceMatcher.group(1).trim();
            if (isValidAudioUrl(src)) {
                audioUrls.add(resolveUrl(src, baseUrl));
            }
        }

        Matcher linkMatcher = LINK_HREF_PATTERN.matcher(html);
        while (linkMatcher.find()) {
            String href = linkMatcher.group(1).trim();
            if (isValidAudioUrl(href)) {
                audioUrls.add(resolveUrl(href, baseUrl));
            }
        }

        return audioUrls;
    }

    private Set<String> extractDocumentUrls(String html, String baseUrl) {
        Set<String> documentUrls = new HashSet<>();

        Matcher linkMatcher = LINK_HREF_PATTERN.matcher(html);
        while (linkMatcher.find()) {
            String href = linkMatcher.group(1).trim();
            if (isValidDocumentUrl(href)) {
                documentUrls.add(resolveUrl(href, baseUrl));
            }
        }

        return documentUrls;
    }

    private boolean isValidImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        url = url.toLowerCase();
        return url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png")
                || url.endsWith(".gif") || url.endsWith(".webp") || url.endsWith(".svg")
                || url.startsWith("data:image");
    }

    private boolean isValidVideoUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        url = url.toLowerCase();
        return url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".mkv")
                || url.endsWith(".avi") || url.endsWith(".mov") || url.endsWith(".flv");
    }

    private boolean isValidAudioUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        url = url.toLowerCase();
        return url.endsWith(".mp3") || url.endsWith(".wav") || url.endsWith(".ogg")
                || url.endsWith(".flac") || url.endsWith(".aac") || url.endsWith(".m4a");
    }

    private boolean isValidDocumentUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        url = url.toLowerCase();
        return url.endsWith(".pdf") || url.endsWith(".doc") || url.endsWith(".docx")
                || url.endsWith(".xls") || url.endsWith(".xlsx") || url.endsWith(".ppt")
                || url.endsWith(".pptx") || url.endsWith(".txt") || url.endsWith(".csv")
                || url.endsWith(".rtf") || url.endsWith(".zip") || url.endsWith(".rar");
    }

    private String resolveUrl(String src, String baseUrl) {
        if (src.startsWith("http://") || src.startsWith("https://")) {
            return src;
        }
        if (src.startsWith("//")) {
            return "https:" + src;
        }
        try {
            URL base = new URL(baseUrl);
            return new URL(base, src).toString();
        } catch (Exception e) {
            return src;
        }
    }

    private String extractFileName(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
            int lastSlash = decoded.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < decoded.length() - 1) {
                String fileName = decoded.substring(lastSlash + 1);
                int queryIndex = fileName.indexOf('?');
                if (queryIndex > 0) {
                    fileName = fileName.substring(0, queryIndex);
                }
                return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            }
        } catch (Exception e) {
        }
        return "";
    }

    private void downloadFile(String fileUrl, Path filePath) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(60000);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP状态码: " + responseCode);
            }

            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_FILE_SIZE) {
                throw new IOException("文件大小超过限制: " + contentLength / (1024 * 1024) + "MB");
            }

            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                    if (totalBytesRead > MAX_FILE_SIZE) {
                        throw new IOException("文件大小超过限制");
                    }
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    @Tool(description = "列出output目录下的所有文件，返回output目录中所有文件名称列表（包含子目录）")
    public List<String> listOutputFiles() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                return List.of("output目录不存在");
            }
            return Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .map(outputPath::relativize)
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("列出文件失败: " + e.getMessage(), e);
        }
    }
}