package com.example.sagent.agent.routing;

import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.RouteDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class MessageClassifier {

    private static final String CLASSIFICATION_PROMPT_TEMPLATE = """
            你是专业的消息分类器，必须严格按照以下规则分类：

            === 各分类对应的工具功能 ===

            【DATABASE】产品数据库查询工具
            - listProducts(): 查询全部产品
            - findProductsByName(): 按名称模糊查询产品
            - findProductsByMaxPrice(): 按最高价格查询产品
            - findProductById(): 按ID精确查询产品
            - countProducts(): 统计产品总数
            场景：产品查询、价格、库存、数量、分类、统计等业务数据查询，仅返回数据不生成文件

            【SKILL】组合技能工具
            - ProductReportSkill: 生成产品报告（查询数据库→生成Markdown文档→可选压缩）
            - WebPageDownloadSkill: 下载并处理网页（下载URL→转换格式→生成文档→可选压缩）
            - DocumentTool: 生成Markdown文档、生成文本文件、列出输出目录文件
            - CompressionTool: 压缩多个文件为ZIP包
            - WebPageTool: 下载网页、读取网页内容、列出已下载文件
            场景：生成报告、下载网页、压缩文件、保存文件、文档操作等需要多步骤的任务

            【RAG】知识库检索工具
            - VectorKnowledgeRetriever: 基于向量相似度检索内部知识库文档
            场景：查询Sagent介绍、项目说明、路由规则、使用手册、知识库文档等内部资料

            【GSKILL】通用技能工具
            - AlarmSkill: getCurrentDateTime()获取当前时间、setAlarm()设置闹钟
            场景：查询时间、设置闹钟、设置提醒等通用工具调用

            【MCP】外部服务工具（通过MCP协议调用）
            - calculator(num1, num2, operation): 计算器（支持加减乘除）
            - get_weather(city): 获取指定城市天气（北京/上海/广州/深圳/成都）
            - get_stock_price(symbol): 获取股票实时价格（AAPL/GOOGL/MSFT/TSLA/NVDA/BABA/JD）
            - get_system_info(): 获取系统信息（OS版本、Java版本、内存等）
            - echo(message): 回显消息（测试用）
            场景：数学计算、天气查询、股票查询、系统信息获取、外部API调用等

            【CHAT】普通聊天
            场景：闲聊、写作、翻译、通用知识问答等不需要调用工具的情况

            === 分类判断规则 ===
            1. 严格按照优先级判断：DATABASE > SKILL > RAG > GSKILL > MCP > CHAT
            2. 如果消息明确涉及产品数据查询，优先归类DATABASE
            3. 如果消息需要生成文件或多步骤处理，归类SKILL
            4. 如果消息查询内部文档/项目说明，归类RAG
            5. 如果消息需要设置闹钟或查询时间，归类GSKILL
            6. 如果消息需要计算、查天气、查股票等外部服务，归类MCP
            7. 其他情况归类CHAT

            必须在type字段返回CHAT/RAG/DATABASE/SKILL/GSKILL/MCP之一，reason字段简要说明分类理由。
            """.trim();

    private final ChatClient chatClient;
    private final ConversationHistory conversationHistory;

    public MessageClassifier(
            ChatClient.Builder chatClientBuilder,
            ConversationHistory conversationHistory
    ) {
        this.chatClient = chatClientBuilder.build();
        this.conversationHistory = conversationHistory;
    }

    public RouteDecision classify(String conversationId, String message) {
        try {
            String history = conversationHistory.format(conversationId);
            String classificationInput = history.isBlank()
                    ? message
                    : """
                    以下是此前的会话，可用于理解当前消息中的指代和上下文：
                    %s

                    当前用户消息：
                    %s
                    """.formatted(history, message);

            RouteDecision decision = chatClient.prompt()
                    .system(CLASSIFICATION_PROMPT_TEMPLATE)
                    .user(classificationInput)
                    .call()
                    .entity(RouteDecision.class, spec -> spec.validateSchema());

            if (decision == null || decision.type() == null) {
                return fallbackDecision();
            }

            return decision;
        } catch (RuntimeException exception) {
            return fallbackDecision();
        }
    }

    private RouteDecision fallbackDecision() {
        return new RouteDecision(AgentType.CHAT, "分类模型未返回有效结果，已使用普通聊天兜底");
    }
}