package com.simplemdm.dto;

public class ApprovalDTO {
    // Used for approve/reject comment
    public String comment;

    // Used for listing (transient)
    public Long id;
    public Long personnelId;
    public String personnelName;
    public String workflowType;
    public Long submitterId;
    public String submitterName;
    public Long approverId;
    public String approverName;
    public String status;
    public String changeData;
    public String submitTime;
    public String approveTime;
    public String approveComment;
}
