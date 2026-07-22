package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.work.domain.model.MaterialUsageStatus;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.domain.model.TaskType;
import com.agricore.work.infrastructure.persistence.MaterialUsageJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.MaterialUsageEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MaterialUsagePersistenceIntegrationTest {

    @Autowired
    private WorkTaskJpaRepository taskRepository;
    @Autowired
    private MaterialUsageJpaRepository materialUsageRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void persistsRetryableMaterialUsageAndPreventsDuplicateTaskItem() {
        WorkTaskEntity task = taskRepository.save(task());
        UUID inventoryItemId = UUID.randomUUID();
        MaterialUsageEntity usage = usage(task.getId(), inventoryItemId, "material-" + UUID.randomUUID());

        materialUsageRepository.saveAndFlush(usage);

        MaterialUsageEntity persisted = materialUsageRepository.findById(usage.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(MaterialUsageStatus.PENDING);
        assertThat(persisted.getQuantity()).isEqualByComparingTo("2.500");

        MaterialUsageEntity duplicate = usage(task.getId(), inventoryItemId, "material-" + UUID.randomUUID());
        assertThatThrownBy(() -> materialUsageRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static WorkTaskEntity task() {
        Instant now = Instant.now();
        WorkTaskEntity task = new WorkTaskEntity();
        task.setId(UUID.randomUUID());
        task.setCode("WT-" + UUID.randomUUID());
        task.setCropCycleId(UUID.randomUUID());
        task.setPlotId(UUID.randomUUID());
        task.setTaskType(TaskType.FERTILIZING);
        task.setTitle("Apply fertilizer");
        task.setPriority("HIGH");
        task.setStatus(TaskStatus.ASSIGNED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private static MaterialUsageEntity usage(UUID taskId, UUID inventoryItemId, String referenceId) {
        Instant now = Instant.now();
        MaterialUsageEntity usage = new MaterialUsageEntity();
        usage.setId(UUID.randomUUID());
        usage.setWorkTaskId(taskId);
        usage.setInventoryItemId(inventoryItemId);
        usage.setQuantity(new BigDecimal("2.500"));
        usage.setStatus(MaterialUsageStatus.PENDING);
        usage.setInventoryReferenceId(referenceId);
        usage.setCreatedAt(now);
        usage.setUpdatedAt(now);
        return usage;
    }
}
