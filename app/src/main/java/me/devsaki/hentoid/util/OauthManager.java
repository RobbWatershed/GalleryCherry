package me.devsaki.hentoid.util;

import androidx.annotation.Nullable;

import org.threeten.bp.Instant;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.enums.Site;

/**
 * Created by Robb on 2019/09
 * Manager class for Oauth2.0 authentication flow
 */
public class OauthManager {
    private static OauthManager mInstance;   // Instance of the singleton

    private final Map<Site, OauthSession> activeSessions;

    private OauthManager() {
        activeSessions = new HashMap<>();
    }

    public static synchronized OauthManager getInstance() {
        if (mInstance == null) {
            mInstance = new OauthManager();
        }
        return mInstance;
    }


    public OauthSession addSession(Site site) {
        OauthSession session = new OauthSession();
        activeSessions.put(site, session);
        return session;
    }

    @Nullable
    public OauthSession getSessionByState(String state) {
        for (OauthSession session : activeSessions.values()) {
            if (session.state.equals(state)) return session;
        }
        return null;
    }

    @Nullable
    public OauthSession getSessionBySite(Site site) {
        return activeSessions.get(site);
    }

    public class OauthSession {
        private String redirectUri = "";
        private String clientId = "";
        private String state = "";
        private String accessToken = "";
        private String refreshToken = "";
        private Instant expiry;

        private String targetUrl = "";

        public String getState() { return state; }

        public void setState(String state) {
            this.state = state;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTargetUrl() {
            return targetUrl;
        }

        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }

        public Instant getExpiry() {
            return expiry;
        }

        public void setExpiry(Instant expiry) {
            this.expiry = expiry;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }
}
