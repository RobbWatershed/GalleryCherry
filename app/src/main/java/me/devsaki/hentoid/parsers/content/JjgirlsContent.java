package me.devsaki.hentoid.parsers.content;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.JsonHelper;

public class JjgirlsContent extends SmartContent {

    @Override
    public Content toContent(@NonNull String url) {
        Content result = super.toContent(url);

        if (null == result.getImageFiles()) return result;

        // JJgirls galleries randomly replace one of the images by an ad link
        // The URL for the original image that has been replaced can be retrieved
        // by removing the 's' character added to the 1st part of the image's relative path
        Map<String, String> downloadParams = new HashMap<>();
        for (ImageFile img : result.getImageFiles()) {
            Uri uri = Uri.parse(img.getUrl());
            List<String> pathSegments = uri.getPathSegments();

            String firstSegment = pathSegments.get(0);
            if (firstSegment.endsWith("s")) {
                String altSegment = firstSegment.substring(0, firstSegment.length() - 1);
                Uri.Builder altUri = new Uri.Builder()
                        .scheme(uri.getScheme())
                        .authority(uri.getAuthority())
                        .appendPath(altSegment);

                if (pathSegments.size() > 1)
                    for (int i = 1; i < pathSegments.size(); i++)
                        altUri.appendPath(pathSegments.get(i));

                downloadParams.put("backupUrl", altUri.build().toString());
                String downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);
                img.setDownloadParams(downloadParamsStr);
            }
        }

        return result;
    }
}
