package com.zextras.s3browser.web;

import com.zextras.s3browser.domain.ConnectionSettings;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class ConnectionSessionService {

    private static final String SESSION_KEY = "connection-settings";

    public void save(HttpSession session, ConnectionSettings settings) {
        session.setAttribute(SESSION_KEY, settings);
    }

    public ConnectionSettings get(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof ConnectionSettings settings) {
            return settings;
        }
        return null;
    }

    public ConnectionSettings getRequired(HttpSession session) {
        ConnectionSettings settings = get(session);
        if (settings == null) {
            throw new IllegalStateException("Once baglantisi kurmaniz gerekiyor.");
        }
        return settings;
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }
}

