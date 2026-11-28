package cn.tolim.pudans.iam.domain.entity;

import cn.tolim.pudans.iam.domain.enums.IamApplicationStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "pudans_iam_application")
public class IamApplication {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IamApplicationStatus status;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IamApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(IamApplicationStatus status) {
        this.status = status;
    }
}
