package com.example.sagent.agent.skills;

import com.example.sagent.agent.approval.Approval;
import com.example.sagent.agent.model.ApprovalRecord;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审批 SQL 技能 - 纯业务逻辑
 * 审批拦截由 ApprovalMethodInterceptor 统一处理
 */
@Component
public class ApprovalSqlSkill implements ASkill {

    private static final String COLS = "id, user_id, tool_name, args_json, status, result, create_time";

    private final JdbcClient jdbcClient;

    public ApprovalSqlSkill(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public String getName() { return "ApprovalSqlSkill"; }

    @Override
    public String getDescription() {
        return "数据库审批技能：支持查询审批状态、删除产品、修改产品价格和库存";
    }

    // ===== 查询方法（不加 @Approval，直接放行） =====

    @Tool(description = "查询所有审批记录的详细状态")
    public String getMyApprovals() {
        List<ApprovalRecord> records = jdbcClient.sql(
                "select %s from approval_records order by create_time desc"
                        .formatted(COLS))
                .query(this::mapRecord)
                .list();
        if (records.isEmpty()) return "当前无审批记录";
        StringBuilder sb = new StringBuilder();
        for (ApprovalRecord r : records) {
            String s = switch (r.status()) {
                case "PENDING" -> "待审批";
                case "APPROVED" -> "已通过";
                case "REJECTED" -> "已拒绝";
                default -> r.status();
            };
            sb.append("#").append(r.id())
                    .append(" 操作:").append(r.toolName()).append(" 状态:").append(s)
                    .append(" 结果:").append(r.result() != null ? r.result() : "未执行")
                    .append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(description = "根据审批编号查询单个审批的详细状态")
    public String checkApprovalById(
            @ToolParam(description = "审批编号") String id
    ) {
        try {
            return jdbcClient.sql("select %s from approval_records where id = :id".formatted(COLS))
                    .param("id", id)
                    .query(this::mapRecord)
                    .optional()
                    .map(r -> {
                        String s = switch (r.status()) {
                            case "PENDING" -> "待审批";
                            case "APPROVED" -> "已通过";
                            case "REJECTED" -> "已拒绝";
                            default -> r.status();
                        };
                        return "#%d 操作:%s 状态:%s 结果:%s".formatted(r.id(), r.toolName(), s,
                                r.result() != null ? r.result() : "未执行");
                    })
                    .orElse("审批记录 #" + id + " 不存在");
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    // ===== 写入方法（加 @Approval(enable=true) 触发审批拦截） =====

    @Approval(enable = true)
    @Tool(description = "根据产品ID删除产品。注意：删除操作不可恢复")
    public String deleteProduct(
            @ToolParam(description = "要删除的产品ID") Long id
    ) {
        String name = jdbcClient.sql("select name from products where id = :id")
                .param("id", id).query(String.class).optional().orElse("未知产品");
        int rows = jdbcClient.sql("delete from products where id = :id")
                .param("id", id).update();
        return "已删除产品（ID: %d, 名称: %s），共删除 %d 条记录".formatted(id, name, rows);
    }

    @Approval(enable = true)
    @Tool(description = "修改产品价格")
    public String updateProductPrice(
            @ToolParam(description = "要修改的产品ID") Long id,
            @ToolParam(description = "新的产品价格") Double newPrice
    ) {
        int rows = jdbcClient.sql("update products set price = :price where id = :id")
                .param("id", id).param("price", newPrice).update();
        return "已修改产品价格（ID: %d -> %.2f），共更新 %d 条记录".formatted(id, newPrice, rows);
    }

    @Approval(enable = true)
    @Tool(description = "修改产品库存")
    public String updateProductStock(
            @ToolParam(description = "要修改的产品ID") Long id,
            @ToolParam(description = "新的库存数量") Integer newStock
    ) {
        int rows = jdbcClient.sql("update products set stock = :stock where id = :id")
                .param("id", id).param("stock", newStock).update();
        return "已修改产品库存（ID: %d -> %d），共更新 %d 条记录".formatted(id, newStock, rows);
    }

    private ApprovalRecord mapRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ApprovalRecord(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("tool_name"),
                rs.getString("args_json"),
                rs.getString("status"),
                rs.getString("result"),
                null,
                null,
                null
        );
    }
}
