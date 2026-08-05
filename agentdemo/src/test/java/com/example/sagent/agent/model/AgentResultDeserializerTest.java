package com.example.sagent.agent.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentResultDeserializer 单元测试
 * <p>
 * 覆盖 LLM 结构化输出中 {@code code} 字段异常形态（缺失、null、字符串、非法文本）的容错解析，
 * 防止 Cannot map null into type int 导致技能执行失败。
 */
class AgentResultDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_codeNull_returnsSuccess() throws Exception {
        // 回归用例：修复前抛出 Cannot map null into type int
        AgentResult result = mapper.readValue("{\"content\":\"审批已提交\",\"code\":null}", AgentResult.class);
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.content()).isEqualTo("审批已提交");
        assertThat(result.success()).isTrue();
    }

    @Test
    void deserialize_codeMissing_returnsSuccess() throws Exception {
        AgentResult result = mapper.readValue("{\"content\":\"操作完成\"}", AgentResult.class);
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.content()).isEqualTo("操作完成");
    }

    @Test
    void deserialize_normalCode_keepsCode() throws Exception {
        AgentResult result = mapper.readValue("{\"code\":404,\"content\":\"未找到\"}", AgentResult.class);
        assertThat(result.code()).isEqualTo(404);
    }

    @Test
    void deserialize_stringCode_parsesAsInt() throws Exception {
        AgentResult result = mapper.readValue("{\"code\":\"500\",\"content\":\"出错了\"}", AgentResult.class);
        assertThat(result.code()).isEqualTo(500);
        assertThat(result.success()).isFalse();
    }

    @Test
    void deserialize_illegalCodeText_returnsSuccess() throws Exception {
        AgentResult result = mapper.readValue("{\"code\":\"abc\",\"content\":\"内容\"}", AgentResult.class);
        assertThat(result.code()).isEqualTo(200);
    }

    @Test
    void deserialize_contentNull_returnsNullContent() throws Exception {
        AgentResult result = mapper.readValue("{\"code\":200,\"content\":null}", AgentResult.class);
        assertThat(result.content()).isNull();
    }
}
