package com.simplemdm.model.mdm;

import com.simplemdm.exception.BusinessException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_child_record")
public class ChildRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "record_id", nullable = false) private Long recordId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "record_id", insertable = false, updatable = false) private MdmRecord parent;
    @Column(name = "child_type_id", nullable = false) private Long childTypeId;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    protected ChildRecord() { }
    private ChildRecord(MdmRecord parent, ChildType childType, int sortOrder, Long actorId) {
        if (!parent.getSystemId().equals(childType.getSystemId())) throw new BusinessException(400, "Child type must belong to the parent system");
        this.parent = parent; systemId = parent.getSystemId(); recordId = parent.getId(); childTypeId = childType.getId(); this.sortOrder = sortOrder;
        status = "active"; createdBy = actorId; updatedBy = actorId;
    }
    public static ChildRecord create(MdmRecord parent, ChildType childType, int sortOrder, Long actorId) { return new ChildRecord(parent, childType, sortOrder, actorId); }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getRecordId() { return recordId; }
    public Long getChildTypeId() { return childTypeId; }
    public Long getDepartmentId() { return parent == null ? null : parent.getDepartmentId(); }
    public long getVersion() { return version == null ? 0L : version; }
    public void touch(Long actorId) { updatedBy = actorId; updatedAt = LocalDateTime.now(); }
}
