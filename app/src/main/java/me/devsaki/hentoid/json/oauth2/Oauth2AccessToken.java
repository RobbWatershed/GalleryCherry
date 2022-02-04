package me.devsaki.hentoid.json.oauth2;

public class Oauth2AccessToken {

    private String access_token;
    private String token_type;
    private long expires_in;
    private String refresh_token;


    public String getAccessToken() {
        return access_token;
    }

    public String getTokenType() {
        return token_type;
    }

    public long getExpiryDelaySeconds() {
        return expires_in;
    }

    public String getRefreshToken() {
        return refresh_token;
    }
}
