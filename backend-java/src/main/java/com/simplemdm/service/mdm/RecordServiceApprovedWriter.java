package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.service.workflow.ApprovalApplyService;
import org.springframework.stereotype.Component;

@Component
class RecordServiceApprovedWriter implements ApprovedRecordWriter {
    private final ApprovalApplyService applyService;
    private final CurrentUserProvider currentUser;

    RecordServiceApprovedWriter(ApprovalApplyService applyService, CurrentUserProvider currentUser) {
        this.applyService = applyService;
        this.currentUser = currentUser;
    }

    @Override
    public RecordView apply(Long requestId) {
        if (requestId == null) {
            throw new BusinessException(400, "Approval request ID is required");
        }
        Long actorId = currentUser.currentSystemUserId()
            .orElseThrow(() -> new BusinessException(401, "No authenticated system user"));
        return applyService.approve(requestId, actorId);
    }
}
