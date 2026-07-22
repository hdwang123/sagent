package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.tools.ProductDatabaseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * 数据库查询处理器
 * 处理产品数据查询请求
 */
@Component
public class DatabaseHandler implements AgentHandler {

    /**
     * 数据库系统提示词
     */
    private static final String DATABASE_SYSTEM_PROMPT = """
            你是产品数据库查询助手。
            必须调用提供的只读数据库工具获取真实数据后再回答，不能自行编造产品数据。
            不得声称执行了新增、修改或删除操作。
            如果现有工具无法满足查询，请明确说明当前仅支持产品列表、名称、价格、ID和数量查询。
            使用中文简洁回答，并保留价格和库存等关键字段。
            """;

    private final ChatClient chatClient;
    private final ProductDatabaseTools databaseTools;

    /**
     * 构造函数
     *
     * @param chatClientBuilder ChatClient构建器
     * @param memoryAdvisor     消息聊天记忆顾问
     * @param databaseTools     产品数据库工具
     */
    public DatabaseHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            ProductDatabaseTools databaseTools
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();
        this.databaseTools = databaseTools;
    }

    /**
     * 获取处理器类型
     *
     * @return AgentType.DATABASE
     */
    @Override
    public AgentType type() {
        return AgentType.DATABASE;
    }

    /**
     * 处理数据库查询消息
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果
     */
    @Override
    public HandlerResult handle(String conversationId, String message) {
        String answer = chatClient.prompt()
                .system(DATABASE_SYSTEM_PROMPT)
                .user(message)
                .tools(databaseTools)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();
        return new HandlerResult(answer);
    }
}