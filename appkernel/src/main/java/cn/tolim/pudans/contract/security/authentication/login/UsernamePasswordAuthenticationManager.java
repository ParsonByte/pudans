package cn.tolim.pudans.contract.security.authentication.login;

import org.pf4j.ExtensionPoint;
import org.springframework.security.authentication.ReactiveAuthenticationManager;


public interface UsernamePasswordAuthenticationManager extends ReactiveAuthenticationManager, ExtensionPoint {}
