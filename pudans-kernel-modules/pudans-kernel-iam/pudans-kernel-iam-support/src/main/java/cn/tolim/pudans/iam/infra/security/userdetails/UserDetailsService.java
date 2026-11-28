package cn.tolim.pudans.iam.infra.security.userdetails;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface UserDetailsService
        extends
        org.springframework.security.core.userdetails.UserDetailsService {

    UserDetails loadUserByPhone(String phone) throws UsernameNotFoundException;

    UserDetails loadUserByEmail(String email) throws UsernameNotFoundException;

    void changePassword(String userId, String password);

    void changePassword(String userId, String password, String newPassword);
}
