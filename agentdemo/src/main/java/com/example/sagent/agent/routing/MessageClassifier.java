package com.example.sagent.agent.routing;

import com.example.sagent.agent.handlers.McpHandler;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.RouteDecision;
import com.example.sagent.agent.cost.CostMonitorService;
import com.example.sagent.agent.cost.TokenUsageCostAdvisor;
import com.example.sagent.agent.skills.ASkill;
import com.example.sagent.agent.skills.GSkill;
import com.example.sagent.agent.skills.Skill;
import com.example.sagent.agent.skills.ToolDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息分类器
 * <p>
 * 调用 LLM 对用户消息进行分类，输出 {@link RouteDecision}（类型 + 分类理由）。
 * 分类优先级：SKILL > GSKILL > ASKILL > RAG > MCP > CHAT。
 * <p>
 * 工具清单由容器中实际注册的 Skill/GSkill/ASkill Bean 动态生成，保证与真实工具一致。
 * 分类器读取历史消息理解上下文，但不使用会写入消息的记忆 Advisor，避免 RouteDecision 污染正式聊天记录。
 * 分类失败或返回空时降级为 CHAT，保证可用性。
 */
@Service
public class MessageClassifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageClassifier.class);

    /**
     * 分类提示词模板
     * SKILL/GSKILL/ASKILL/MCP 四个分类的工具清单由 {skillTools}/{gskillTools}/{askillTools}/{mcpTools} 占位符动态生成，
     * 保证与容器中实际注册的 Skill Bean 始终一致，避免人工维护描述导致与真实工具脱节
     */
    private static final String CLASSIFICATION_PROMPT_TEMPLATE = """
            你是专业的消息分类器，必须严格按照以下规则分类：

            === 各分类对应的工具功能 ===

            【SKILL】组合技能工具（单次调用）
            {skillTools}
            场景：下载网页图片、下载网页视频、下载网页音频、下载网页文档、下载网页内容、保存网页HTML、网页截图、压缩文件、生成Markdown文档、保存文件、文档操作等需要调用工具的任务，仅调用一个工具

            【GSKILL】通用技能工具（循环调用）
            {gskillTools}
            场景：产品查询、价格、库存、数量、统计等业务数据查询，查询时间、设置闹钟等，支持工具组合调用

            【RAG】知识库检索工具
            - VectorKnowledgeRetriever: 基于向量相似度检索内部知识库文档
            场景：查询Sagent介绍、项目说明、路由规则、使用手册、知识库文档等内部资料

            【MCP】外部服务工具（通过MCP协议调用）
            {mcpTools}
            场景：数学计算、天气查询、股票查询、系统信息获取、外部API调用等

            【ASKILL】审批技能工具（需要人工审批）
            {askillTools}
            场景：删除产品、修改产品价格、修改产品库存等敏感数据库操作；查询审批记录、审批状态、审批列表等审批管理操作，每次敏感操作先提交审批，人工审核通过后自动执行

            【CHAT】普通聊天
            场景：闲聊、写作、翻译、通用知识问答等不需要调用工具的情况

            === 分类判断规则 ===
            1. 严格按照优先级判断：SKILL > GSKILL > ASKILL > RAG > MCP > CHAT
            2. 如果消息需要生成文件/多步骤处理，归类SKILL
            3. 如果消息涉及删除/修改产品等敏感操作或查询审批记录/审批状态，归类ASKILL
            4. 如果消息涉及产品数据查询或需要工具组合调用，归类GSKILL
            5. 如果消息查询内部文档/项目说明，归类RAG
            6. 如果消息需要计算、查天气、查股票等外部服务，归类MCP
            7. 其他情况归类CHAT

            必须在type字段返回CHAT/RAG/SKILL/GSKILL/ASKILL/MCP之一，reason字段简要说明分类理由。
            """.trim();

    private final ChatClient chatClient;
    private final ConversationHistory conversationHistory;
    private final List<Skill> skills;
    private final List<GSkill> gSkills;
    private final List<ASkill> aSkills;
    private final McpHandler mcpHandler;
    private final CostMonitorService costMonitorService;

    /**
     * 构造函数
     *
     * @param chatClientBuilder   ChatClient构建器
     * @param conversationHistory 会话历史管理（读取历史消息理解上下文指代）
     * @param skills              SKILL 技能 Bean 列表（动态生成工具清单）
     * @param gSkills             GSKILL 技能 Bean 列表（动态生成工具清单）
     * @param aSkills             ASKILL 技能 Bean 列表（动态生成工具清单）
     * @param mcpHandler          MCP 处理器（提供动态 MCP 工具清单）
     * @param costMonitorService  成本监控服务
     */
    public MessageClassifier(
            ChatClient.Builder chatClientBuilder,
            ConversationHistory conversationHistory,
            List<Skill> skills,
            List<GSkill> gSkills,
            List<ASkill> aSkills,
            McpHandler mcpHandler,
            CostMonitorService costMonitorService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.conversationHistory = conversationHistory;
        this.skills = skills;
        this.gSkills = gSkills;
        this.aSkills = aSkills;
        this.mcpHandler = mcpHandler;
        this.costMonitorService = costMonitorService;
    }

    /**
     * 对用户消息进行分类
     * <p>
     * 读取会话历史拼入分类输入，让 LLM 理解"它/这个"等指代；
     * 分类失败或返回空时降级为 CHAT，保证可用性。
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return 路由决策（类型 + 分类理由），失败时返回 CHAT 兜底决策
     */
    public RouteDecision classify(String conversationId, String message) {
        long start = System.nanoTime();
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

            var callResponse = chatClient.prompt()
                    .system(buildClassificationPrompt())
                    .user(classificationInput)
                    .advisors(advisor -> advisor
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                            .param("operationType", "routing/classifier"))
                    .advisors(new TokenUsageCostAdvisor(costMonitorService))
                    .call();

            RouteDecision decision = callResponse.entity(RouteDecision.class, spec -> spec.validateSchema());

            if (decision == null || decision.type() == null) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                LOGGER.warn("消息分类返回空, 耗时={}ms, 降级为CHAT", ms);
                return fallbackDecision();
            }

            long ms = (System.nanoTime() - start) / 1_000_000;
            LOGGER.info("消息分类耗时: {}ms, type={}, reason={}", ms, decision.type(), decision.reason());
            return decision;
        } catch (RuntimeException exception) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            LOGGER.warn("消息分类异常, 耗时={}ms, 降级为CHAT: {}", ms, exception.getMessage());
            return fallbackDecision();
        }
    }

    /**
     * 构建分类提示词：用容器中实际注册的技能 Bean 动态填充工具清单
     */
    private String buildClassificationPrompt() {
        String mcpTools = "";
        try {
            List<ToolDescriptor> mcpDescriptors = mcpHandler.getToolDescriptors();
            mcpTools = formatToolList(mcpDescriptors);
        } catch (Exception e) {
            LOGGER.warn("获取MCP工具列表失败，使用默认描述", e);
            mcpTools = "- MCP工具列表获取失败，请检查MCP Server连接状态";
        }
        return CLASSIFICATION_PROMPT_TEMPLATE
                .replace("{skillTools}", formatToolList(skills))
                .replace("{gskillTools}", formatToolList(gSkills))
                .replace("{askillTools}", formatToolList(aSkills))
                .replace("{mcpTools}", mcpTools);
    }

    /**
     * 将技能列表格式化为 "名称: 描述" 的工具清单行
     */
    private String formatToolList(List<? extends ToolDescriptor> toolList) {
        if (toolList == null || toolList.isEmpty()) {
            return "（无）";
        }
        return toolList.stream()
                .map(tool -> "- " + tool.getName() + ": " + tool.getDescription())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 兜底决策：分类模型未返回有效结果时，降级为普通聊天
     */
    private RouteDecision fallbackDecision() {
        return new RouteDecision(AgentType.CHAT, "分类模型未返回有效结果，已使用普通聊天兜底");
    }
}
