package me.devsaki.hentoid.util;

import me.devsaki.hentoid.BuildConfig;

/**
 * Created by DevSaki on 10/05/2015.
 * Common app constants.
 */
public abstract class Consts {

    private Consts() {
        throw new IllegalStateException("Utility class");
    }

    public static final String DATABASE_NAME = "cherry.db";

    public static final String DEFAULT_LOCAL_DIRECTORY_OLD = "Hentoid";
    public static final String DEFAULT_LOCAL_DIRECTORY = ".Cherry";

    public static final String JSON_FILE_NAME_V2 = "contentV2.json";

    public static final String USER_AGENT_NEUTRAL = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.132 Safari/537.36";

    public static final String USER_AGENT = USER_AGENT_NEUTRAL + " Cherry/v" + BuildConfig.VERSION_NAME;


    public static final String URL_GITHUB = "https://github.com/RobbWatershed/GalleryCherry";
    public static final String URL_GITHUB_WIKI = "https://github.com/RobbWatershed/GalleryCherry/wiki";
    public static final String URL_DISCORD = "https://discord.gg/waTF8vw"; // If that value changes, change it in assets/about_mikan.html too
}
