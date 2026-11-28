package cn.tolim.pudans.iam.identitysource.core.event;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


@Data
@RequiredArgsConstructor
public class IdentitySourceEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1099740917667842614L;
    // ID
    private final String id;
    // Event type
    private final IdentitySourceEventType identitySourceEventType;
}
