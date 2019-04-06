package me.devsaki.hentoid.util;

import me.devsaki.hentoid.BuildConfig;

/**
 * Created by DevSaki on 10/05/2015.
 * Common app constants.
 */
public abstract class Consts {

    public static final String DATABASE_NAME = "cherry.db";

    public static final String INTENT_URL = "url";

    public static final String DEFAULT_LOCAL_DIRECTORY = ".Cherry";

    public static final String JSON_FILE_NAME_V2 = "contentV2.json";

    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76K)"
                    + " AppleWebKit/535.19 (KHTML, like Gecko)"
                    + " Chrome/18.0.1025.166 Mobile Safari/535.19"
                    + " Cherry/v" + BuildConfig.VERSION_NAME;

    public static final String USER_AGENT_NEUTRAL = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36";
}
