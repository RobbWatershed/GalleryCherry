package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.enums.StatusContent;

/**
 * Helper class to transfer payload data to {@link me.devsaki.hentoid.viewholders.ContentItem}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class ContentItemBundle {
    private static final String KEY_DELETE_PROCESSING = "is_being_deleted";
    private static final String KEY_FAV_STATE = "favourite";
    private static final String KEY_READS = "reads";
    private static final String KEY_STATUS = "status";
    private static final String KEY_COVER_URI = "cover_uri";

    private ContentItemBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setIsBeingDeleted(boolean isBeingDeleted) {
            bundle.putBoolean(KEY_DELETE_PROCESSING, isBeingDeleted);
        }

        public void setIsFavourite(boolean isFavourite) {
            bundle.putBoolean(KEY_FAV_STATE, isFavourite);
        }

        public Builder setStatus(StatusContent status) {
            bundle.putInt(KEY_STATUS, status.getCode());
            return this;
        }

        public void setReads(long reads) {
            bundle.putLong(KEY_READS, reads);
        }

        public void setCoverUri(String uri) {
            bundle.putString(KEY_COVER_URI, uri);
        }

        public boolean isEmpty() {
            return bundle.isEmpty();
        }

        public Bundle getBundle() {
            return bundle;
        }
    }

    public static final class Parser {

        private final Bundle bundle;

        public Parser(@Nonnull Bundle bundle) {
            this.bundle = bundle;
        }

        @Nullable
        public Boolean isBeingDeleted() {
            if (bundle.containsKey(KEY_DELETE_PROCESSING))
                return bundle.getBoolean(KEY_DELETE_PROCESSING);
            else return null;
        }

        @Nullable
        public Boolean isFavourite() {
            if (bundle.containsKey(KEY_FAV_STATE)) return bundle.getBoolean(KEY_FAV_STATE);
            else return null;
        }

        @Nullable
        public Long getReads() {
            if (bundle.containsKey(KEY_READS)) return bundle.getLong(KEY_READS);
            else return null;
        }

        @Nullable
        public StatusContent getStatus() {
            if (bundle.containsKey(KEY_STATUS))
                return StatusContent.searchByCode(bundle.getInt(KEY_STATUS));
            else return null;
        }

        @Nullable
        public String getCoverUri() {
            if (bundle.containsKey(KEY_COVER_URI)) return bundle.getString(KEY_COVER_URI);
            else return null;
        }
    }
}
