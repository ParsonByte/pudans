package cn.tolim.pudans.iam.domain.entity;

import cn.tolim.pudans.iam.domain.enums.IamOrganizationStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "pudans_iam_organization")
public class IamOrganization {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IamOrganizationStatus status;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IamOrganizationStatus getStatus() {
        return status;
    }

    public void setStatus(IamOrganizationStatus status) {
        this.status = status;
    }
}
