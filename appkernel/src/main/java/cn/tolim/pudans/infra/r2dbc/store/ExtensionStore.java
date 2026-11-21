package cn.tolim.pudans.infra.r2dbc.store;

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

    // 创建不带版本的新扩展存储条目。
    public ExtensionStore(String name, byte[] data) {
        this.name = name;
        this.data = data;
    }

    // 创建仅用于删除或版本校验的条目。
    public ExtensionStore(String name, Long version) {
        this.name = name;
        this.version = version;
    }

    // 创建带乐观锁版本的扩展存储条目。
    public ExtensionStore(String name, byte[] data, Long version) {
        this.name = name;
        this.data = data;
        this.version = version;
    }
}
