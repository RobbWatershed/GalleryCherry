package me.devsaki.hentoid.model;

import com.google.gson.annotations.SerializedName;

public class RedditUser {

    @SerializedName("name")
    private String name;

    
    public String getName() {
        return name;
    }
}
