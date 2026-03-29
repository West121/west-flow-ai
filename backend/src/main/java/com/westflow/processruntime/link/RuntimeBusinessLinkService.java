package com.westflow.processruntime.link;

import com.westflow.processbinding.mapper.BusinessProcessLinkMapper;
import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeBusinessLinkService {

    private final BusinessProcessLinkMapper businessProcessLinkMapper;

    public Optional<BusinessLinkSnapshot> findByBusiness(String businessType, String businessId) {
        return Optional.ofNullable(businessProcessLinkMapper.selectByBusiness(businessType, businessId))
                .map(this::toSnapshot);
    }

    public Optional<BusinessLinkSnapshot> findByInstanceId(String processInstanceId) {
        return Optional.ofNullable(businessProcessLinkMapper.selectByProcessInstanceId(processInstanceId))
                .map(this::toSnapshot);
    }

    public List<BusinessLinkSnapshot> listByStartUser(String startUserId) {
        return businessProcessLinkMapper.selectByStartUser(startUserId).stream()
                .map(this::toSnapshot)
                .toList();
    }

    public void insertLink(
            String businessType,
            String businessId,
            String processInstanceId,
            String processDefinitionId,
            String startUserId,
            String status
    ) {
        businessProcessLinkMapper.insertLink(new BusinessProcessLinkRecord(
                "bpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                businessType,
                businessId,
                processInstanceId,
                processDefinitionId,
                startUserId,
                status
        ));
    }

    public void updateStatus(String processInstanceId, String status) {
        businessProcessLinkMapper.updateStatusByProcessInstanceId(processInstanceId, status);
    }

    private BusinessLinkSnapshot toSnapshot(BusinessProcessLinkRecord record) {
        return new BusinessLinkSnapshot(
                record.businessType(),
                record.businessId(),
                record.processInstanceId(),
                record.processDefinitionId(),
                record.startUserId(),
                record.status()
        );
    }
}
