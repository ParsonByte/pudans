package cn.tolim.pudans.core.infra.r2dbc.store;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;


@Data
@Table(name = "extensions")
public class ExtensionStore {


    @Id
    private String name;


    private byte[] data;


    @Version
    private Long version;

    public ExtensionStore() {}

    public ExtensionStore(String name, byte[] data) {
        this.name = name;
        this.data = data;
    }

    public ExtensionStore(String name, Long version) {
        this.name = name;
        this.version = version;
    }

    public ExtensionStore(String name, byte[] data, Long version) {
        this.name = name;
        this.data = data;
        this.version = version;
    }
}
