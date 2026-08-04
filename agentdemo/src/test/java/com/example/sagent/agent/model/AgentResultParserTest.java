package com.example.sagent.agent.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentResultParser 单元测试
 * <p>
 * 覆盖 returnDirect 透传场景下的 JSON 解析、自然语言兜底、序列化等纯逻辑路径。
 */
class AgentResultParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // === tryParse ===

    @Test
    void tryParse_validJson_returnsAgentResult() {
        AgentResult result = AgentResultParser.tryParse(mapper, "{\"code\":200,\"content\":\"成功\"}");
        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.content()).isEqualTo("成功");
    }

    @Test
    void tryParse_errorCode_returnsAgentResult() {
        AgentResult result = AgentResultParser.tryParse(mapper, "{\"code\":404,\"content\":\"未找到\"}");
        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo(404);
    }

    @Test
    void tryParse_naturalLanguage_returnsNull() {
        assertThat(AgentResultParser.tryParse(mapper, "这是自然语言回复")).isNull();
    }

    @Test
    void tryParse_null_returnsNull() {
        assertThat(AgentResultParser.tryParse(mapper, null)).isNull();
    }

    @Test
    void tryParse_blank_returnsNull() {
        assertThat(AgentResultParser.tryParse(mapper, "   ")).isNull();
    }

    @Test
    void tryParse_arrayJson_returnsNull() {
        // 不以 { 开头 / 不以 } 结尾，不当作 JSON 对象处理
        assertThat(AgentResultParser.tryParse(mapper, "[1,2,3]")).isNull();
    }

    @Test
    void tryParse_nullContent_returnsNull() {
        assertThat(AgentResultParser.tryParse(mapper, "{\"code\":200,\"content\":null}")).isNull();
    }

    @Test
    void tryParse_brokenJson_returnsNull() {
        assertThat(AgentResultParser.tryParse(mapper, "{broken")).isNull();
    }

    // === toHandlerResult ===

    @Test
    void toHandlerResult_json_returnsHandlerResultWithCode() {
        HandlerResult result = AgentResultParser.toHandlerResult(mapper, "{\"code\":500,\"content\":\"出错了\"}");
        assertThat(result.answer()).isEqualTo("出错了");
        assertThat(result.code()).isEqualTo(500);
        assertThat(result.error()).isTrue();
    }

    @Test
    void toHandlerResult_naturalLanguage_returnsSuccessCode() {
        HandlerResult result = AgentResultParser.toHandlerResult(mapper, "这是自然语言");
        assertThat(result.answer()).isEqualTo("这是自然语言");
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.success()).isTrue();
    }

    @Test
    void toHandlerResult_null_returnsEmptyAnswer() {
        HandlerResult result = AgentResultParser.toHandlerResult(mapper, null);
        assertThat(result.answer()).isEqualTo("");
        assertThat(result.code()).isEqualTo(200);
    }

    // === toJson ===

    @Test
    void toJson_normal_roundTripsThroughTryParse() {
        String json = AgentResultParser.toJson(mapper, 200, "测试内容");
        AgentResult parsed = AgentResultParser.tryParse(mapper, json);
        assertThat(parsed).isNotNull();
        assertThat(parsed.code()).isEqualTo(200);
        assertThat(parsed.content()).isEqualTo("测试内容");
    }

    @Test
    void toJson_errorCode_containsCodeAndContent() {
        String json = AgentResultParser.toJson(mapper, 404, "未找到");
        assertThat(json).contains("\"code\":404");
        assertThat(json).contains("未找到");
    }
}
