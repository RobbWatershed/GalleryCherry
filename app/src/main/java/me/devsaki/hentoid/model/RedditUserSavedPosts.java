package me.devsaki.hentoid.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class RedditUserSavedPosts {

    @SerializedName("data")
    private SavedPostsDataContainer container;


    class SavedPostsDataContainer {
        @SerializedName("children")
        List<SavedPostsDataRoot> roots;
    }

    class SavedPostsDataRoot {
        @SerializedName("data")
        SavedPostsData post;
    }

    class SavedPostsData {
        @SerializedName("url")
        String url;
    }

    public List<String> toImageList() {
        List<String> result = new ArrayList<>();

        if (container != null && container.roots != null)
            for (SavedPostsDataRoot root : container.roots)
                if (root.post != null)
                    result.add(root.post.url);

        return result;
    }
}
