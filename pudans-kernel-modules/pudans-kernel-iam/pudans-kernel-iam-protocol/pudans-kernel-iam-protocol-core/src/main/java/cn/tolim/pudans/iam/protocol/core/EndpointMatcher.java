package cn.tolim.pudans.iam.protocol.core;

import lombok.Data;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.Serializable;


@Data
public class EndpointMatcher implements Serializable {

    // Request matcher
    private RequestMatcher requestMatcher;

    // access
    private Boolean access;

    public EndpointMatcher(RequestMatcher requestMatcher, Boolean access) {
        this.requestMatcher = requestMatcher;
        this.access = access;
    }

    public EndpointMatcher() {
    }
}
