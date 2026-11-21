package cn.tolim.pudans.extension.contract;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(exclude = "version")
public class Metadata implements MetadataOperator {


    private String name;


    private String generateName;


    private Map<String, String> labels;


    private Map<String, String> annotations;


    private Long version;


    private Instant creationTimestamp;


    private Instant deletionTimestamp;

    private Set<String> finalizers;
}
