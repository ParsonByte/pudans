package cn.tolim.pudans.contract.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
@Schema(description = "Extension reference object. The name is mandatory")
public class Ref {

    @Schema(description = "Extension group", requiredMode = REQUIRED)
    private String group;

    @Schema(description = "Extension version")
    @Nullable
    private String version;

    @Schema(description = "Extension kind", requiredMode = REQUIRED)
    private String kind;

    @Schema(requiredMode = REQUIRED, description = "Extension name. This field is mandatory")
    private String name;

    public static Ref of(String name) {
        Ref ref = new Ref();
        ref.setName(name);
        return ref;
    }

    public static Ref of(String name, GroupVersionKind gvk) {
        Ref ref = new Ref();
        ref.setName(name);
        ref.setGroup(gvk.group());
        ref.setVersion(gvk.version());
        ref.setKind(gvk.kind());
        return ref;
    }

    public static Ref of(Extension extension) {
        var metadata = extension.getMetadata();
        var gvk = extension.groupVersionKind();
        var ref = new Ref();
        ref.setName(metadata.getName());
        ref.setGroup(gvk.group());
        ref.setVersion(gvk.version());
        ref.setKind(gvk.kind());
        return ref;
    }


    public static boolean groupKindEquals(Ref ref, GroupVersionKind gvk) {
        return Objects.equals(ref.getGroup(), gvk.group()) && Objects.equals(ref.getKind(), gvk.kind());
    }


    public static boolean equals(Ref ref, ExtensionOperator extension) {
        var gvk = extension.groupVersionKind();
        var name = extension.getMetadata().getName();
        return groupKindEquals(ref, gvk) && Objects.equals(ref.getName(), name);
    }


    public static String toIdentifier(Ref ref) {
        return ref.getGroup() + "/" + ref.getKind() + "/" + ref.getName();
    }
}
