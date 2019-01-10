package me.devsaki.hentoid.enums;

import javax.annotation.Nullable;

import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 * TODO: deprecate {@link #allowParallelDownloads} on 1/10/2020 if not needed by that time
 */
public enum Site {

    XHAMSTER(0, "XHamster", "https://m.xhamster.com/photos/", "xhamster", R.drawable.ic_menu_xhamster, true),
    XNXX(1, "XNXX", "https://multi.xnxx.com/", "XNXX", R.drawable.ic_menu_xnxx, true),
    PORNPICS(2, "Pornpics", "https://www.pornpics.com/", "pornpics", R.drawable.ic_menu_pornpics, true),
    JPEGWORLD(3, "Jpegworld", "https://www.jpegworld.com/", "jpegworld", R.drawable.ic_menu_jpegworld, true);


    private final int code;
    private final String description;
    private final String uniqueKeyword;
    private final String url;
    private final int ico;
    private final boolean allowParallelDownloads;

    Site(int code, String description, String url, String uniqueKeyword, int ico, boolean allowParallelDownloads) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.uniqueKeyword = uniqueKeyword;
        this.ico = ico;
        this.allowParallelDownloads = allowParallelDownloads;
    }

    @Nullable
    public static Site searchByCode(int code) {
        if (code == -1) {
            Timber.w("Invalid site code!");
        }
        for (Site s : Site.values()) {
            if (s.getCode() == code)
                return s;
        }
        return null;
    }

    @Nullable
    public static Site searchByUrl(String url) {
        if (null == url || 0 == url.length()) {
            Timber.w("Invalid url");
            return null;
        }
        for (Site s : Site.values()) {
            if (url.contains(s.getUniqueKeyword()))
                return s;
        }
        return null;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getUniqueKeyword() {
        return uniqueKeyword;
    }

    public String getUrl() {
        return url;
    }

    public int getIco() {
        return ico;
    }

    public boolean isAllowParallelDownloads() {
        return allowParallelDownloads;
    }

    public String getFolder() {
        return '/' + description + '/';
    }
}
