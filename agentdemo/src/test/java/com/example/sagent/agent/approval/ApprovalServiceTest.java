package com.example.sagent.agent.approval;

import com.example.sagent.agent.model.ApprovalRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * ApprovalService 单元测试
 * <p>
 * 覆盖审批状态机核心逻辑：创建待审批、查询、审批通过/拒绝的状态校验，
 * 以及不存在记录的异常处理。
 */
class ApprovalServiceTest {

    private ApprovalRepository repository;
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApprovalRepository.class);
        service = new ApprovalService(repository);
    }

    // === createPending ===

    @Test
    void createPending_delegatesToRepository() {
        ApprovalRecord record = new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "PENDING", null, null, LocalDateTime.now(), null);
        when(repository.createPending("user-1", "deleteProduct", "{}")).thenReturn(record);

        ApprovalRecord result = service.createPending("user-1", "deleteProduct", "{}");

        assertThat(result.id()).isEqualTo("rec-1");
        assertThat(result.status()).isEqualTo("PENDING");
        verify(repository).createPending("user-1", "deleteProduct", "{}");
    }

    // === getRecord ===

    @Test
    void getRecord_found_returnsRecord() {
        ApprovalRecord record = new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "PENDING", null, null, LocalDateTime.now(), null);
        when(repository.findById("rec-1")).thenReturn(Optional.of(record));

        ApprovalRecord result = service.getRecord("rec-1");

        assertThat(result.id()).isEqualTo("rec-1");
    }

    @Test
    void getRecord_notFound_throwsIllegalArgument() {
        when(repository.findById("rec-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecord("rec-999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rec-999");
    }

    // === approve ===

    @Test
    void approve_pendingStatus_updatesToApproved() {
        ApprovalRecord pending = new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "PENDING", null, null, LocalDateTime.now(), null);
        when(repository.findById("rec-1")).thenReturn(Optional.of(pending));

        service.approve("rec-1", "删除成功");

        verify(repository).updateStatus("rec-1", "APPROVED", "删除成功");
    }

    @Test
    void approve_nonPendingStatus_throwsIllegalState() {
        ApprovalRecord approved = new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "APPROVED", "已执行", null, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findById("rec-1")).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approve("rec-1", "删除成功"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
        verify(repository, never()).updateStatus(anyString(), anyString(), anyString());
    }

    // === reject ===

    @Test
    void reject_pendingStatus_updatesToRejected() {
        ApprovalRecord pending = new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "PENDING", null, null, LocalDateTime.now(), null);
        when(repository.findById("rec-1")).thenReturn(Optional.of(pending));

        service.reject("rec-1");

        verify(repository).updateStatus("rec-1", "REJECTED", "rejected");
    }

    @Test
    void reject_nonPendingStatus_throwsIllegalState() {
        ApprovalRecord rejected = new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "REJECTED", "rejected", null, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findById("rec-1")).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.reject("rec-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED");
        verify(repository, never()).updateStatus(anyString(), anyString(), anyString());
    }

    // === 查询方法 ===

    @Test
    void listAll_delegatesToRepository() {
        List<ApprovalRecord> records = List.of(
                new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "PENDING", null, null, LocalDateTime.now(), null)
        );
        when(repository.listAll()).thenReturn(records);

        List<ApprovalRecord> result = service.listAll();

        assertThat(result).hasSize(1);
        verify(repository).listAll();
    }

    @Test
    void listPending_delegatesToRepository() {
        when(repository.listPending()).thenReturn(List.of());

        List<ApprovalRecord> result = service.listPending();

        assertThat(result).isEmpty();
        verify(repository).listPending();
    }

    @Test
    void findExisting_delegatesToRepository() {
        ApprovalRecord record = new ApprovalRecord("rec-1", "user-1", "deleteProduct", "{}", "PENDING", null, null, LocalDateTime.now(), null);
        when(repository.findExisting("user-1", "deleteProduct", "{}")).thenReturn(Optional.of(record));

        Optional<ApprovalRecord> result = service.findExisting("user-1", "deleteProduct", "{}");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("rec-1");
    }

    @Test
    void getApprovedResult_delegatesToRepository() {
        when(repository.getApprovedResult("user-1", "deleteProduct", "{}")).thenReturn(Optional.of("删除成功"));

        Optional<String> result = service.getApprovedResult("user-1", "deleteProduct", "{}");

        assertThat(result).contains("删除成功");
    }
}
