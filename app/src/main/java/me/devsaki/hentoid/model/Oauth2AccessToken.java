package me.devsaki.hentoid.model;

import com.google.gson.annotations.SerializedName;

import org.threeten.bp.Instant;

public class Oauth2AccessToken {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("token_type")
    private String tokenType;

    @SerializedName("expires_in")
    private long expiresIn;

    @SerializedName("refresh_token")
    private String refreshToken;


    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiryDelaySeconds() {
        return expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
