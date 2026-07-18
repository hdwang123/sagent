package com.example.sagent.agent.database;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class DatabaseAgentHandler implements AgentHandler {

    private static final String DATABASE_SYSTEM_PROMPT = """
            你是产品数据库查询助手。
            必须调用提供的只读数据库工具获取真实数据后再回答，不能自行编造产品数据。
            不得声称执行了新增、修改或删除操作。
            如果现有工具无法满足查询，请明确说明当前仅支持产品列表、名称、价格、ID和数量查询。
            使用中文简洁回答，并保留价格和库存等关键字段。
            """;

    private final ChatClient chatClient;
    private final ProductDatabaseTools databaseTools;

    public DatabaseAgentHandler(
            ChatClient.Builder chatClientBuilder,
            ProductDatabaseTools databaseTools
    ) {
        this.chatClient = chatClientBuilder.build();
        this.databaseTools = databaseTools;
    }

    @Override
    public AgentType type() {
        return AgentType.DATABASE;
    }

    @Override
    public HandlerResult handle(String message) {
        String answer = chatClient.prompt()
                .system(DATABASE_SYSTEM_PROMPT)
                .user(message)
                .tools(databaseTools)
                .call()
                .content();
        return new HandlerResult(answer);
    }
}
