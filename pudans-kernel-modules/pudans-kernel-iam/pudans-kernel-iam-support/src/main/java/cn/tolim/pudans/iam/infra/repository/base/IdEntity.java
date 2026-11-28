package cn.tolim.pudans.iam.infra.repository.base;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serializable;

@Access(AccessType.FIELD)
@MappedSuperclass
public class IdEntity implements Serializable {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Access(AccessType.PROPERTY)
    @Column(name = "id_")
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "IdEntity(id=" + id + ")";
    }
}
