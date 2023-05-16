package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.file.FileHelper;
import timber.log.Timber;

/**
 * Created by Robb on 2019/09
 * Manager class for Oauth2.0 authentication flow
 */
public class OauthSessionManager {
    private static OauthSessionManager mInstance;   // Instance of the singleton

    private final Map<Site, OauthSession> activeSessions;

    private OauthSessionManager() {
        activeSessions = new HashMap<>();
    }

    public static synchronized OauthSessionManager getInstance() {
        if (mInstance == null) {
            mInstance = new OauthSessionManager();
        }
        return mInstance;
    }


    public OauthSession addSession(Site site) {
        OauthSession session = new OauthSession(site.name());
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

    private File getSessionFile(Context context, String host) {
        File dir = context.getFilesDir();
        return new File(dir, host + ".json");
    }

    /**
     * Save the Oauth session to the app's internal storage
     *
     * @param context Context to be used
     * @param session Session to be saved
     */
    public void saveSession(Context context, OauthSession session) {
        File file = getSessionFile(context, session.getHost());
        try (OutputStream output = FileHelper.getOutputStream(file)) {
            JsonHelper.updateJson(session, OauthSession.class, output);
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    /**
     * Get the Oauth session from the app's internal storage
     *
     * @param context Context to be used
     * @param host    Host the session belongs to
     * @return Oauth session from the given host; null if no such session exists
     */
    @Nullable
    public OauthSession loadSession(Context context, String host) {
        File file = getSessionFile(context, host);
        if (!file.exists()) return null;

        try {
            return JsonHelper.jsonToObject(file, OauthSession.class);
        } catch (IOException e) {
            Timber.e(e);
        }
        return null;
    }

    public static class OauthSession {
        private final String host;

        private String redirectUri = "";
        private String clientId = "";
        private String state = "";
        private String accessToken = "";
        private String refreshToken = "";
        private Instant expiry;

        private String targetUrl = "";
        private String userName = "";


        OauthSession(String host) {
            this.host = host;
        }

        String getHost() {
            return host;
        }

        public String getState() {
            return state;
        }

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

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }
}
