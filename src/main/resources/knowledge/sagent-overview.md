# Sagent 项目说明

Sagent 是一个基于 JDK 21、Spring Boot 4.1 和 Spring AI 2.0 构建的 Agent 实验项目。

项目使用 OpenRouter 的 OpenAI 兼容接口调用大模型。API Key 从环境变量
`OPENROUTER_API_KEY` 读取，模型可通过 `OPENROUTER_MODEL` 环境变量切换。

聊天测试页面地址为 `/chat.html`。
