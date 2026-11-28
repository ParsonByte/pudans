package cn.tolim.pudans.iam.core.security.session;

import cn.tolim.pudans.iam.infra.security.authentication.WebAuthenticationDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;


@SuppressWarnings("unchecked")
public class ClusterSessionRegistryImpl<T extends org.springframework.session.Session>
        extends SpringSessionBackedSessionRegistry<T> {

    // The key in the hash representing org.springframework.session.Session#getCreationTime().
    static final String CREATION_TIME_KEY = "creationTime";
    // The key in the hash representing org.springframework.session.Session#getLastAccessedTime().
    static final String LAST_ACCESSED_TIME_KEY = "lastAccessedTime";
    // The key in the hash representing org.springframework.session.Session#getMaxInactiveInterval().
    static final String MAX_INACTIVE_INTERVAL_KEY = "maxInactiveInterval";
    // The prefix of the key in the hash used for session attributes. For example, if the
    // session contained an attribute named attributeName, then there would be an
    // entry in the hash named sessionAttr:attributeName that mapped to its value.
    static final String ATTRIBUTE_PREFIX = "sessionAttr:";
    private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";
    // Get namespace
    @Getter
    private final String namespace;
    // FindByIndexNameSessionRepository
    private final FindByIndexNameSessionRepository<?> sessionRepository;

    public ClusterSessionRegistryImpl(FindByIndexNameSessionRepository<T> sessionRepository,
                                      String namespace) {
        super(sessionRepository);
        this.sessionRepository = sessionRepository;
        this.namespace = namespace;
    }

    @NotNull
    private static Session getSession(MapSession session, UserDetails userDetails,
                                      Authentication authentication) {
        WebAuthenticationDetails details = (WebAuthenticationDetails) authentication.getDetails();
        Session sessionDetails = new Session(userDetails.getId(), userDetails.getUsername());
        // last request
        Instant instant = session.getLastAccessedTime();
        ZoneId zoneId = ZoneId.systemDefault();
        // Last request time
        LocalDateTime lastRequestTime = instant.atZone(zoneId).toLocalDateTime();
        sessionDetails.setLastRequestTime(lastRequestTime);
        // Login time
        sessionDetails.setAuthenticationTime(details.getAuthenticationTime());
        // Login time
        sessionDetails.setAuthenticationProvider(details.getAuthenticationProvider().getType());
        // User type
        sessionDetails.setUserType(userDetails.getUserType());
        // Geolocation
        sessionDetails.setGeoLocation(details.getGeoLocation());
        // User agent
        sessionDetails.setUserAgent(details.getUserAgent());
        // Session ID
        sessionDetails.setSessionId(session.getId());
        return sessionDetails;
    }

    // remove session
    // Parameter sessionId: String
    @Override
    public void removeSessionInformation(String sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    // get all principals
    // Returns: List
    @Override
    public List<Object> getAllPrincipals() {
        if (sessionRepository instanceof RedisIndexedSessionRepository) {
            // names
            Set<String> names = getAllPrincipalNames();
            // session infos
            List<String> sessionIds = new ArrayList<>();
            // Query sessions by username, including expired sessions
            for (String s : names) {
                sessionIds.addAll(getSessionIds(s));
            }
            // Get session details excluding expired sessions
            List<Session> sessions = getSessionList(sessionIds);
            return new ArrayList<>(sessions);
        }
        return super.getAllPrincipals();
    }

    // Get session details by session ID
    // Parameter sessionIds: List
    // Returns: List
    @NonNull
    public List<Session> getSessionList(List<String> sessionIds) {
        List<Session> list = new ArrayList<>();
        Map<String, MapSession> sessions;
        sessions = getSessions(sessionIds);
        for (MapSession session : sessions.values()) {
            // Skip when the session is empty or expired
            if (Objects.isNull(session) || session.isExpired()) {
                continue;
            }
            try {
                // Convert to security context
                SecurityContext securityContext = session.getAttribute(SPRING_SECURITY_CONTEXT);
                // Convert to entity
                Authentication authentication = securityContext.getAuthentication();
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails userDetails) {
                    Session sessionDetails = getSession(session, userDetails, authentication);
                    list.add(sessionDetails);
                }
            } catch (NullPointerException ignored) {
            }
        }
        // Process
        return list;
    }

    // Get session list by username
    // Returns: List
    public List<Object> getSessionList(String username) {
        Assert.notNull(username, "用户名不能为空");
        // names
        Boolean hasKey = sessionRedisOperations().hasKey(getPrincipalKey(username));
        if (Boolean.FALSE.equals(hasKey)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(getSessionList(getSessionIds(username).stream().toList()));
    }

    private Map<String, MapSession> getSessions(List<String> sessionIds) {
        Map<String, MapSession> result = new HashMap<>(16);
        List<Object> pipelineResult = sessionRedisOperations()
                .executePipelined((RedisCallback<Object>) connection -> {
                    connection.openPipeline();
                    for (Object sessionId : sessionIds) {
                        String key = getSessionKeyPrefix() + sessionId;
                        connection.hashCommands().hGetAll(key.getBytes());
                    }
                    return null;
                });
        for (int i = 0; i < pipelineResult.size(); i++) {
            Object obj = pipelineResult.get(i);
            String id = sessionIds.get(i);
            if (obj instanceof Map && ((Map<Object, Object>) obj).size() > 0) {
                MapSession session = loadSession(id, (Map<Object, Object>) obj);
                result.put(session.getId(), session);
            }
        }
        return result;
    }

    private MapSession loadSession(String id, Map<Object, Object> objects) {
        MapSession loaded = new MapSession(id);
        for (Map.Entry<Object, Object> entry : objects.entrySet()) {
            String key = (String) entry.getKey();
            if (CREATION_TIME_KEY.equals(key)) {
                loaded.setCreationTime(Instant.ofEpochMilli((long) entry.getValue()));
            } else if (MAX_INACTIVE_INTERVAL_KEY.equals(key)) {
                loaded.setMaxInactiveInterval(Duration.ofSeconds((int) entry.getValue()));
            } else if (LAST_ACCESSED_TIME_KEY.equals(key)) {
                loaded.setLastAccessedTime(Instant.ofEpochMilli((long) entry.getValue()));
            } else if (key.startsWith(ATTRIBUTE_PREFIX)) {
                loaded.setAttribute(key.substring(ATTRIBUTE_PREFIX.length()), entry.getValue());
            }
        }
        return loaded;
    }

    // Get principal key
    // Parameter principalName: String
    // Returns: String
    String getPrincipalKey(String principalName) {
        return getPrincipalKeyPrefix() + principalName;
    }

    // Get principal key
    // Returns: String
    String getPrincipalKeyPrefix() {
        return getNamespace() + COLON + "index" + COLON
                + FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + COLON;
    }

    // Get session key
    // Returns: String
    String getSessionKeyPrefix() {
        return getNamespace() + COLON + "sessions:";
    }

    // Get all principal names
    // Returns: Set
    Set<String> getAllPrincipalNames() {
        Set<String> keys = new HashSet<>();
        ((RedisIndexedSessionRepository) sessionRepository).getSessionRedisOperations()
                .execute((RedisCallback<Object>) connection -> {
                    Cursor<byte[]> cursor = connection.keyCommands().scan(
                            ScanOptions.scanOptions().match(getPrincipalKey("*")).count(1000).build());
                    while (cursor.hasNext()) {
                        String key = new String(cursor.next()).replaceAll(getPrincipalKeyPrefix(), "");
                        keys.add(key);
                    }
                    return keys;
                });
        return keys;
    }

    // Get Session IDs
    // Returns: Set
    Set<String> getSessionIds(String indexValue) {
        String principalKey = getPrincipalKey(indexValue);
        Set<Object> members = sessionRedisOperations().boundSetOps(principalKey).members();
        if (CollectionUtils.isNotEmpty(members)) {
            return members.stream().map(Object::toString).collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    private RedisOperations<String, Object> sessionRedisOperations() {
        return ((RedisIndexedSessionRepository) sessionRepository).getSessionRedisOperations();
    }
}
