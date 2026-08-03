package com.example.sagent.agent.approval;

import com.example.sagent.agent.model.ApprovalRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批记录数据访问层
 * 集中管理 approval_records 表的 SQL 语句与行映射，
 * 供 ApprovalService（审批流）与 ApprovalSqlSkill（查询工具）共用，
 * 消除表名/列名常量与 mapRecord 在多个类中的重复定义
 */
@Repository
public class ApprovalRepository {

    private static final String COLS = "id, user_id, tool_name, args_json, status, result, auto_response, create_time, update_time";
    private static final String TABLE = "approval_records";

    private final JdbcClient jdbcClient;

    public ApprovalRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ApprovalRecord createPending(String userId, String toolName, String argsJson) {
        String id = UUID.randomUUID().toString();
        var now = LocalDateTime.now();
        jdbcClient.sql("insert into %s (id, user_id, tool_name, args_json, status, create_time) values (?, ?, ?, ?, 'PENDING', ?)".formatted(TABLE))
                .param(id).param(userId).param(toolName).param(argsJson).param(now).update();
        return new ApprovalRecord(id, userId, toolName, argsJson, "PENDING", null, null, now, null);
    }

    public Optional<ApprovalRecord> findExisting(String userId, String toolName, String argsJson) {
        return jdbcClient.sql("select %s from %s where user_id = ? and tool_name = ? and args_json = ? and status = 'PENDING' order by create_time desc limit 1".formatted(COLS, TABLE))
                .param(userId).param(toolName).param(argsJson)
                .query(this::mapRecord).optional();
    }

    public List<ApprovalRecord> listAll() {
        return jdbcClient.sql("select %s from %s order by create_time desc".formatted(COLS, TABLE))
                .query(this::mapRecord).list();
    }

    public List<ApprovalRecord> listPending() {
        return jdbcClient.sql("select %s from %s where status = 'PENDING' order by create_time desc".formatted(COLS, TABLE))
                .query(this::mapRecord).list();
    }

    public Optional<ApprovalRecord> findById(String id) {
        return jdbcClient.sql("select %s from %s where id = ?".formatted(COLS, TABLE))
                .param(id).query(this::mapRecord).optional();
    }

    public void updateStatus(String id, String status, String result) {
        jdbcClient.sql("update %s set status = ?, result = ?, update_time = ? where id = ?".formatted(TABLE))
                .param(status).param(result).param(LocalDateTime.now()).param(id).update();
    }

    public Optional<String> getApprovedResult(String userId, String toolName, String argsJson) {
        return jdbcClient.sql("select %s from %s where user_id = ? and tool_name = ? and args_json = ? and status = 'APPROVED' order by create_time desc limit 1".formatted(COLS, TABLE))
                .param(userId).param(toolName).param(argsJson)
                .query(this::mapRecord)
                .optional()
                .map(ApprovalRecord::result);
    }

    private ApprovalRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalRecord(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("tool_name"),
                rs.getString("args_json"),
                rs.getString("status"),
                rs.getString("result"),
                rs.getString("auto_response"),
                rs.getTimestamp("create_time") != null ? rs.getTimestamp("create_time").toLocalDateTime() : null,
                rs.getTimestamp("update_time") != null ? rs.getTimestamp("update_time").toLocalDateTime() : null
        );
    }
}
