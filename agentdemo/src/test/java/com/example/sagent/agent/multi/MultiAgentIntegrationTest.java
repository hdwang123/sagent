package com.example.sagent.agent.multi;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.core.HandlerRegistry;
import com.example.sagent.agent.cost.CostMonitorService;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.Task;
import com.example.sagent.agent.model.TaskPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多Agent全链路集成测试
 * <p>
 * 不启动Spring上下文（避免依赖真实LLM API Key/数据库），手动装配真实组件打通完整编排链路：
 * 真实 {@link Planner} → 真实 {@link TaskExecutor}（真实线程池）→ 真实 {@link Aggregator} →
 * 真实 {@link HandlerRegistry}（测试桩子Agent）→ 真实 {@link MessageWindowChatMemory}。
 * 仅 mock ChatClient 链式调用，替身"LLM输出"：Planner 返回固定任务计划、Aggregator 返回固定汇总。
 * <p>
 * 验证的链路行为：并行/依赖分波次调度、依赖结果注入、复合会话ID(原会话#taskId)、
 * 失败传播（4xx不重试/依赖任务跳过/整轮降级）、空计划降级单任务、多轮记忆写入与读取。
 */
class MultiAgentIntegrationTest {

    private ChatClient.Builder mockBuilder;
    private ChatClient mockClient;
    private ChatClient.ChatClientRequestSpec mockSpec;
    private ChatClient.CallResponseSpec mockCallSpec;
    private ChatMemory multiAgentChatMemory;
    private Planner planner;
    private Aggregator aggregator;
    private TaskExecutor executor;

    @BeforeEach
    void setUp() {
        // mock LLM 链式调用：ChatClient → prompt() → system/user/call() → entity/content()
        mockBuilder = mock(ChatClient.Builder.class);
        mockClient = mock(ChatClient.class);
        mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        mockCallSpec = mock(ChatClient.CallResponseSpec.class);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.prompt()).thenReturn(mockSpec);
        when(mockSpec.system(anyString())).thenReturn(mockSpec);
        when(mockSpec.user(anyString())).thenReturn(mockSpec);
        // Aggregator 使用 user(Consumer<UserSpec>) 重载
        when(mockSpec.user(any(Consumer.class))).thenReturn(mockSpec);
        when(mockSpec.advisors(any(Advisor.class))).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockCallSpec);

        // 真实多Agent独立会话记忆
        multiAgentChatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();
        planner = new Planner(mockBuilder, multiAgentChatMemory, new ConversationHistory(multiAgentChatMemory, 2),
                mock(CostMonitorService.class));
        aggregator = new Aggregator(mockBuilder);
    }

    @AfterEach
    void tearDown() {
        // 关闭执行器线程池，避免非守护线程阻止 JVM 退出
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * 用例1 正常全链路：并行 + 多依赖合并 + 汇总 + 复合会话ID + 记忆写入
     * <p>
     * t1(GSKILL) 与 t2(RAG) 并行执行，t3(SKILL) 同时依赖 t1、t2。
     * 断言：整轮成功、汇总回答与来源合并、依赖结果注入 t3 goal、
     * 子Agent收到"原会话#taskId"复合会话ID、多Agent记忆写入用户+汇总两条消息。
     */
    @Test
    void fullChain_dependentAndParallelTasks_aggregatesAndWritesMemory() {
        TaskPlan plan = new TaskPlan(List.of(
                new Task("t1", AgentType.GSKILL, "查询所有产品的信息", List.of()),
                new Task("t2", AgentType.RAG, "查询Sagent项目介绍", List.of()),
                new Task("t3", AgentType.SKILL, "结合产品信息和项目介绍生成Markdown文档", List.of("t1", "t2"))
        ));
        when(mockCallSpec.entity(eq(TaskPlan.class), any(Consumer.class))).thenReturn(plan);
        when(mockCallSpec.content()).thenReturn("已汇总报告，下载链接：/files/download/doc/report.md");

        StubHandler gskill = new StubHandler(AgentType.GSKILL,
                g -> new HandlerResult("产品A:100元", List.of("gsource")));
        StubHandler rag = new StubHandler(AgentType.RAG,
                g -> new HandlerResult("Sagent是Java多Agent框架"));
        StubHandler skill = new StubHandler(AgentType.SKILL,
                g -> new HandlerResult("文档已生成 /files/download/doc/report.md"));
        MultiAgentService service = buildService(List.of(gskill, rag, skill));

        HandlerResult result = service.handle("conv-1", "结合产品信息生成报告");

        // 1. 整轮成功：返回汇总回答，且各子任务来源合并去重
        assertThat(result.success()).isTrue();
        assertThat(result.answer()).isEqualTo("已汇总报告，下载链接：/files/download/doc/report.md");
        assertThat(result.sources()).containsExactly("gsource");

        // 2. 三个子Agent各执行一次，且都收到复合会话ID（原会话#子任务id）
        assertThat(gskill.receivedConversationIds).containsExactly("conv-1#t1");
        assertThat(rag.receivedConversationIds).containsExactly("conv-1#t2");
        assertThat(skill.receivedConversationIds).containsExactly("conv-1#t3");

        // 3. 多依赖结果已注入 t3 的 goal（t1 产品结果 + t2 项目介绍）
        assertThat(skill.receivedGoals.get(0))
                .contains("结合产品信息和项目介绍生成Markdown文档")
                .contains("产品A:100元")
                .contains("Sagent是Java多Agent框架");

        // 4. 整轮编排写入多Agent独立会话记忆（用户消息 + 汇总回答）
        List<Message> memory = multiAgentChatMemory.get("conv-1");
        assertThat(memory).hasSize(2);
        assertThat(memory.get(0).getText()).isEqualTo("结合产品信息生成报告");
        assertThat(memory.get(1).getText()).isEqualTo("已汇总报告，下载链接：/files/download/doc/report.md");
    }

    /**
     * 用例2 失败传播：子任务4xx业务失败 → 不重试、依赖任务跳过、整轮降级为error
     * <p>
     * t1(GSKILL) 返回404，触发重新规划时LLM返回空计划（放弃剩余任务），
     * 依赖t1的t2(SKILL)被跳过。断言：GSKILL仅执行1次（4xx不重试）、
     * SKILL从未执行、整轮code为error但汇总仍返回内容（不抛异常/不返回null）。
     */
    @Test
    void fullChain_subTaskBusinessFailure_marksWholeRoundError() {
        TaskPlan failPlan = new TaskPlan(List.of(
                new Task("t1", AgentType.GSKILL, "查询不存在的产品", List.of()),
                new Task("t2", AgentType.SKILL, "基于产品信息生成文档", List.of("t1"))
        ));
        // 第一次调用返回原计划(plan)；重新规划时返回空计划(放弃剩余任务)
        when(mockCallSpec.entity(eq(TaskPlan.class), any(Consumer.class)))
                .thenReturn(failPlan, new TaskPlan(List.of()));
        when(mockCallSpec.content()).thenReturn("汇总：部分任务失败");

        StubHandler gskill = new StubHandler(AgentType.GSKILL,
                g -> new HandlerResult("产品不存在", List.of(), 404));
        StubHandler skill = new StubHandler(AgentType.SKILL,
                g -> new HandlerResult("文档"));
        MultiAgentService service = buildService(List.of(gskill, skill));

        HandlerResult result = service.handle("conv-2", "查询并生成文档");

        // 整轮降级为错误，但汇总仍返回内容
        assertThat(result.error()).isTrue();
        assertThat(result.answer()).isEqualTo("汇总：部分任务失败");
        // 4xx业务失败不重试：GSKILL只执行1次；依赖失败任务的t2被跳过
        assertThat(gskill.receivedGoals).hasSize(1);
        assertThat(skill.receivedGoals).isEmpty();
    }

    /**
     * 用例3 空计划降级：Planner返回null → 降级为单个CHAT子任务
     * <p>
     * 断言：整轮仍成功、CHAT子任务收到原始消息并使用复合会话ID、记忆正常写入。
     */
    @Test
    void fullChain_plannerReturnsNull_degradesToSingleChat() {
        when(mockCallSpec.entity(eq(TaskPlan.class), any(Consumer.class))).thenReturn(null);
        when(mockCallSpec.content()).thenReturn("单任务汇总");

        StubHandler chat = new StubHandler(AgentType.CHAT, g -> new HandlerResult("闲聊回复"));
        MultiAgentService service = buildService(List.of(chat));

        HandlerResult result = service.handle("conv-3", "你好");

        assertThat(result.success()).isTrue();
        // 降级后的单个CHAT子任务收到原始消息，且使用复合会话ID
        assertThat(chat.receivedGoals).containsExactly("你好");
        assertThat(chat.receivedConversationIds).containsExactly("conv-3#t1");
        assertThat(multiAgentChatMemory.get("conv-3")).hasSize(2);
    }

    /**
     * 用例4 多轮记忆闭环：第二轮Planner读取上一轮编排结果拼入prompt
     * <p>
     * 断言：首轮后记忆2条；第二轮 user() 收到"会话历史"前缀且包含首轮用户问题；记忆累计4条。
     */
    @Test
    void fullChain_secondRound_readsPreviousRoundMemory() {
        TaskPlan plan = new TaskPlan(List.of(
                new Task("t1", AgentType.GSKILL, "查询所有产品的信息", List.of())));
        when(mockCallSpec.entity(eq(TaskPlan.class), any(Consumer.class))).thenReturn(plan);
        when(mockCallSpec.content()).thenReturn("产品汇总");

        StubHandler gskill = new StubHandler(AgentType.GSKILL, g -> new HandlerResult("产品A"));
        MultiAgentService service = buildService(List.of(gskill));

        // 第一轮
        service.handle("conv-m", "请生成产品报告");
        assertThat(multiAgentChatMemory.get("conv-m")).hasSize(2);

        // 第二轮：Planner 读取上一轮记忆（用户问题+汇总回答）拼入 prompt
        service.handle("conv-m", "那价格呢");

        verify(mockSpec).user(contains("会话历史"));
        // 两轮 user() 都包含首轮消息：第一轮为原始消息，第二轮为历史中的"用户：请生成产品报告"
        verify(mockSpec, times(2)).user(contains("请生成产品报告"));
        assertThat(multiAgentChatMemory.get("conv-m")).hasSize(4);
    }

    /**
     * 组装多Agent编排服务：真实 HandlerRegistry 注册桩子Agent + 真实 TaskExecutor + 门面 MultiAgentService
     */
    private MultiAgentService buildService(List<AgentHandler> handlers) {
        HandlerRegistry registry = new HandlerRegistry(handlers);
        executor = new TaskExecutor(registry, planner, 60, 1, 2, 4);
        return new MultiAgentService(planner, executor, aggregator, multiAgentChatMemory);
    }

    /**
     * 测试桩子Agent：记录收到的会话ID与goal，按脚本函数返回结果，充当真实子Agent处理器
     */
    private static class StubHandler implements AgentHandler {

        private final AgentType type;
        private final Function<String, HandlerResult> responder;
        private final List<String> receivedConversationIds = new ArrayList<>();
        private final List<String> receivedGoals = new ArrayList<>();

        StubHandler(AgentType type, Function<String, HandlerResult> responder) {
            this.type = type;
            this.responder = responder;
        }

        @Override
        public AgentType type() {
            return type;
        }

        @Override
        public HandlerResult handle(String conversationId, String message) {
            receivedConversationIds.add(conversationId);
            receivedGoals.add(message);
            return responder.apply(message);
        }
    }
}
