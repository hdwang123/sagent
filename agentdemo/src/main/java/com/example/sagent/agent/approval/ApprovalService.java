package com.example.sagent.agent.approval;

import com.example.sagent.agent.model.ApprovalRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApprovalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalService.class);

    private static final String COLS = "id, user_id, tool_name, args_json, status, result, create_time";
    private static final String TABLE = "approval_records";

    private final JdbcClient jdbcClient;

    public ApprovalService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ApprovalRecord createPending(String userId, String toolName, String argsJson) {
        String id = UUID.randomUUID().toString();
        var now = LocalDateTime.now();
        jdbcClient.sql("insert into %s (id, user_id, tool_name, args_json, status, create_time) values (?, ?, ?, ?, 'PENDING', ?)".formatted(TABLE))
                .param(id).param(userId).param(toolName).param(argsJson).param(now).update();
        LOGGER.info("create PENDING #{}: {} {} (user={})", id, toolName, argsJson, userId);
        return new ApprovalRecord(id, userId, toolName, argsJson, "PENDING", null, null, now, null);
    }

    public Optional<ApprovalRecord> findExisting(String userId, String toolName, String argsJson) {
        return jdbcClient.sql("select %s from %s where user_id = ? and tool_name = ? and args_json = ? order by create_time desc limit 1".formatted(COLS, TABLE))
                .param(userId).param(toolName).param(argsJson)
                .query(this::mapRecord).optional();
    }

    public List<ApprovalRecord> listPending() {
        return jdbcClient.sql("select %s from %s where status = 'PENDING' order by create_time desc".formatted(COLS, TABLE))
                .query(this::mapRecord).list();
    }

    public ApprovalRecord getRecord(String id) {
        return jdbcClient.sql("select %s from %s where id = ?".formatted(COLS, TABLE))
                .param(id).query(this::mapRecord)
                .optional().orElseThrow(() -> new IllegalArgumentException("approval not found: " + id));
    }

    public void approve(String id, String executionResult) {
        ApprovalRecord record = getRecord(id);
        if (!"PENDING".equals(record.status())) {
            throw new IllegalStateException("status is not PENDING: " + record.status());
        }
        jdbcClient.sql("update %s set status = 'APPROVED', result = ? where id = ?".formatted(TABLE))
                .param(executionResult).param(id).update();
        LOGGER.info("approve #{}: {}", id, executionResult);
    }

    public void reject(String id) {
        ApprovalRecord record = getRecord(id);
        if (!"PENDING".equals(record.status())) {
            throw new IllegalStateException("status is not PENDING: " + record.status());
        }
        jdbcClient.sql("update %s set status = 'REJECTED', result = 'rejected' where id = ?".formatted(TABLE))
                .param(id).update();
        LOGGER.info("reject #{}", id);
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
                null,
                rs.getTimestamp("create_time") != null ? rs.getTimestamp("create_time").toLocalDateTime() : null,
                null
        );
    }
}
