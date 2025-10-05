package me.devsaki.hentoid.parsers.content

import android.net.Uri
import androidx.core.net.toUri
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.serializeToJson

class JjgirlsContent : SmartContent() {
    override fun toContent(url: String): Content {
        val result = super.toContent(url)

        // JJgirls galleries randomly replace one of the images by an ad link
        // The URL for the original image that has been replaced can be retrieved
        // by removing the 's' character added to the 1st part of the image's relative path
        val downloadParams: MutableMap<String, String> = HashMap()
        for (img in result.imageFiles) {
            val uri = img.url.toUri()
            val pathSegments = uri.pathSegments

            val firstSegment = pathSegments[0]
            if (firstSegment.endsWith("s")) {
                val altSegment = firstSegment.substring(0, firstSegment.length - 1)
                val altUri = Uri.Builder()
                    .scheme(uri.scheme)
                    .authority(uri.authority)
                    .appendPath(altSegment)

                if (pathSegments.size > 1) for (i in 1..<pathSegments.size) altUri.appendPath(
                    pathSegments[i]
                )

                downloadParams.put("backupUrl", altUri.build().toString())
                val downloadParamsStr = serializeToJson(downloadParams, MAP_STRINGS)
                img.downloadParams = downloadParamsStr
            }
        }

        return result
    }
}