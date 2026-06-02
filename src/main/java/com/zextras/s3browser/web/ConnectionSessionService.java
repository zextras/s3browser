package com.zextras.s3browser.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.zextras.s3browser.domain.ConnectionSettings;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConnectionSessionService {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final TypeReference<LinkedHashMap<String, ConnectionSettings>> CONNECTIONS_TYPE = new TypeReference<>() {
    };
    private static final String SESSION_CONNECTIONS_KEY = "connection-settings";
    private static final String SESSION_ACTIVE_CONNECTION_KEY = "active-connection-id";
    private static final String DEFAULT_STORE_PATH = System.getProperty("user.home") + "/.s3browser/connections.json";

    @Value("${app.connections.store-file:${user.home}/.s3browser/connections.json}")
    private String storeFilePath;

    ConnectionSessionService(String storeFilePath) {
        this.storeFilePath = storeFilePath;
    }

    public ConnectionSessionService() {
        this(null);
    }

    public String save(HttpSession session, ConnectionSettings settings) {
        Map<String, ConnectionSettings> connections = getConnections(session);
        String connectionId = UUID.randomUUID().toString();
        connections.put(connectionId, settings);
        session.setAttribute(SESSION_CONNECTIONS_KEY, connections);
        session.setAttribute(SESSION_ACTIVE_CONNECTION_KEY, connectionId);
        persistConnections(connections);
        return connectionId;
    }

    public List<ConnectionSummary> list(HttpSession session) {
        Map<String, ConnectionSettings> connections = getConnections(session);
        String activeId = getActiveConnectionId(session);
        List<ConnectionSummary> result = new ArrayList<>();
        for (Map.Entry<String, ConnectionSettings> entry : connections.entrySet()) {
            ConnectionSettings settings = entry.getValue();
            result.add(new ConnectionSummary(
                entry.getKey(),
                buildLabel(settings),
                settings.region(),
                settings.endpointOverride(),
                settings.defaultBucket(),
                entry.getKey().equals(activeId)
            ));
        }
        return result;
    }

    public ResolvedConnection getRequired(HttpSession session, String requestedConnectionId) {
        Map<String, ConnectionSettings> connections = getConnections(session);
        String activeConnectionId = getActiveConnectionId(session);
        String resolvedConnectionId = StringUtils.hasText(requestedConnectionId)
            ? requestedConnectionId
            : activeConnectionId;

        if (!StringUtils.hasText(resolvedConnectionId)) {
            throw new IllegalStateException("Once baglantisi kurmaniz gerekiyor.");
        }

        ConnectionSettings settings = connections.get(resolvedConnectionId);
        if (settings == null) {
            throw new IllegalStateException("Secilen baglanti bulunamadi.");
        }

        session.setAttribute(SESSION_ACTIVE_CONNECTION_KEY, resolvedConnectionId);
        return new ResolvedConnection(resolvedConnectionId, settings);
    }

    public ConnectionSettings getById(HttpSession session, String connectionId) {
        if (!StringUtils.hasText(connectionId)) {
            return null;
        }
        return getConnections(session).get(connectionId);
    }

    public String getActiveConnectionId(HttpSession session) {
        Object value = session.getAttribute(SESSION_ACTIVE_CONNECTION_KEY);
        if (value instanceof String id && StringUtils.hasText(id)) {
            return id;
        }
        return null;
    }

    public boolean select(HttpSession session, String connectionId) {
        if (!StringUtils.hasText(connectionId)) {
            return false;
        }
        Map<String, ConnectionSettings> connections = getConnections(session);
        if (!connections.containsKey(connectionId)) {
            return false;
        }
        session.setAttribute(SESSION_ACTIVE_CONNECTION_KEY, connectionId);
        return true;
    }

    public boolean remove(HttpSession session, String connectionId) {
        if (!StringUtils.hasText(connectionId)) {
            return false;
        }
        Map<String, ConnectionSettings> connections = getConnections(session);
        ConnectionSettings removed = connections.remove(connectionId);
        if (removed == null) {
            return false;
        }

        session.setAttribute(SESSION_CONNECTIONS_KEY, connections);
        String activeConnectionId = getActiveConnectionId(session);
        if (connectionId.equals(activeConnectionId)) {
            String newActive = connections.keySet().stream().findFirst().orElse(null);
            if (newActive == null) {
                session.removeAttribute(SESSION_ACTIVE_CONNECTION_KEY);
            } else {
                session.setAttribute(SESSION_ACTIVE_CONNECTION_KEY, newActive);
            }
        }
        persistConnections(connections);
        return true;
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_CONNECTIONS_KEY);
        session.removeAttribute(SESSION_ACTIVE_CONNECTION_KEY);
        persistConnections(new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private Map<String, ConnectionSettings> getConnections(HttpSession session) {
        Object value = session.getAttribute(SESSION_CONNECTIONS_KEY);
        if (value instanceof Map<?, ?> rawMap) {
            return (Map<String, ConnectionSettings>) rawMap;
        }
        if (value instanceof ConnectionSettings legacySingleConnection) {
            Map<String, ConnectionSettings> migrated = new LinkedHashMap<>();
            String connectionId = UUID.randomUUID().toString();
            migrated.put(connectionId, legacySingleConnection);
            session.setAttribute(SESSION_CONNECTIONS_KEY, migrated);
            session.setAttribute(SESSION_ACTIVE_CONNECTION_KEY, connectionId);
            persistConnections(migrated);
            return migrated;
        }
        Map<String, ConnectionSettings> loaded = loadConnections();
        session.setAttribute(SESSION_CONNECTIONS_KEY, loaded);
        if (!loaded.isEmpty() && !StringUtils.hasText(getActiveConnectionId(session))) {
            session.setAttribute(SESSION_ACTIVE_CONNECTION_KEY, loaded.keySet().iterator().next());
        }
        return loaded;
    }

    private synchronized Map<String, ConnectionSettings> loadConnections() {
        Path storePath = resolveStorePath();
        if (!Files.exists(storePath)) {
            return new LinkedHashMap<>();
        }

        try {
            String json = Files.readString(storePath);
            if (!StringUtils.hasText(json)) {
                return new LinkedHashMap<>();
            }
            LinkedHashMap<String, ConnectionSettings> loaded = OBJECT_MAPPER.readValue(json, CONNECTIONS_TYPE);
            return loaded == null ? new LinkedHashMap<>() : loaded;
        } catch (IOException ex) {
            throw new IllegalStateException("Baglanti dosyasi okunamadi: " + storePath, ex);
        }
    }

    private synchronized void persistConnections(Map<String, ConnectionSettings> connections) {
        Path storePath = resolveStorePath();
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(connections);
            Files.writeString(
                storePath,
                json,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Baglanti dosyasi yazilamadi: " + storePath, ex);
        }
    }

    private Path resolveStorePath() {
        String configured = StringUtils.hasText(storeFilePath) ? storeFilePath : DEFAULT_STORE_PATH;
        return Path.of(configured);
    }

    private String buildLabel(ConnectionSettings settings) {
        String endpoint = StringUtils.hasText(settings.endpointOverride()) ? settings.endpointOverride() : "AWS";
        return endpoint + " (" + settings.region() + ")";
    }

    public record ResolvedConnection(String connectionId, ConnectionSettings settings) {
    }

    public record ConnectionSummary(
        String connectionId,
        String label,
        String region,
        String endpointOverride,
        String defaultBucket,
        boolean active
    ) {
    }
}

