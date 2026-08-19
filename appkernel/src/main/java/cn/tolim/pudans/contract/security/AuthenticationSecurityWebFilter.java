package cn.tolim.pudans.contract.security;

import org.pf4j.ExtensionPoint;
import org.springframework.web.server.WebFilter;


public interface AuthenticationSecurityWebFilter extends WebFilter, ExtensionPoint {}
