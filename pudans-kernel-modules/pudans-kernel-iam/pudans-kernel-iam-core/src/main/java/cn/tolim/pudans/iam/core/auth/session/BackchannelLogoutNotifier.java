package cn.tolim.pudans.iam.core.auth.session;

import cn.tolim.pudans.iam.communal.session.AppSessionEntity;

import java.util.List;

public interface BackchannelLogoutNotifier {

    // Notifies every application session bound to a revoked global login session
    // Parameter sid: String
    // Parameter userId: String
    // Parameter reason: String
    // Parameter appSessions: List
    // Returns: int number of applications successfully notified
    int notifyLogout(String sid, String userId, String reason, List<AppSessionEntity> appSessions);
}