package com.gatekeeper.paper.api;

import java.util.List;

/**
 * Mirror of the JSON returned by GET /api/players/{uuid}/application-context.
 */
public class ApplicationContext {
    public List<String> restrictedServers;
    public List<String> currentAccess;
    public List<String> availableServers;
    public List<String> defaultServers;
    public boolean hasPending;
    public Latest latest;

    public static class Latest {
        public long id;
        public String status;
        public long createdAt;
        public Long decidedAt;
        public String decisionNote;
    }
}
