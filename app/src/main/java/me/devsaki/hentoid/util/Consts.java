package me.devsaki.hentoid.util;

import android.os.Build;

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

    public static final String DEFAULT_LOCAL_DIRECTORY_OLD = "Cherry";
    public static final String DEFAULT_LOCAL_DIRECTORY = ".Cherry";

    public static final String JSON_FILE_NAME_V2 = "contentV2.json";

    public static final String QUEUE_JSON_FILE_NAME = "queue.json";
    public static final String GROUPS_JSON_FILE_NAME = "groups.json";

    public static final String THUMB_FILE_NAME = "thumb";
    public static final String PICTURE_CACHE_FOLDER = "pictures";

    // Some security mechanisms do check if Android devices connect with an Android mobile agent
    public static final String USER_AGENT_NEUTRAL = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Mobile Safari/537.36";

    public static final String USER_AGENT = USER_AGENT_NEUTRAL + " Cherry/v" + BuildConfig.VERSION_NAME;


    public static final String URL_GITHUB = "https://github.com/RobbWatershed/GalleryCherry";
    public static final String URL_GITHUB_WIKI = "https://github.com/RobbWatershed/GalleryCherry/wiki";
    public static final String URL_DISCORD = "https://discord.gg/waTF8vw"; // If that value changes, change it in assets/about_mikan.html too
}
