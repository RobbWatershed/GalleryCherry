package me.devsaki.hentoid.json.sources;

import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.List;

public class RedditUserSavedPosts {

    @Json(name = "data")
    private SavedPostsDataContainer container;


    static class SavedPostsDataContainer {
        @Json(name = "children")
        List<SavedPostsDataRoot> roots;
    }

    static class SavedPostsDataRoot {
        @Json(name = "data")
        SavedPostsData post;
    }

    static class SavedPostsData {
        String url;
    }

    public List<String> toImageList() {
        List<String> result = new ArrayList<>();

        if (container != null && container.roots != null)
            for (SavedPostsDataRoot root : container.roots)
                if (root.post != null && root.post.url != null)
                    result.add(root.post.url);

        return result;
    }
}
