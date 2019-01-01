package me.devsaki.hentoid.parsers;

import android.webkit.CookieManager;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import timber.log.Timber;

import static me.devsaki.hentoid.enums.Site.TSUMINO;

/**
 * Created by Shiro on 1/22/2016.
 * Handles parsing of content from tsumino
 */
public class TsuminoParser extends BaseParser {

    @Override
    protected List<String> parseImages(Content content) throws Exception {
        Document doc = getOnlineDocument(content.getReaderUrl());
        if (null != doc) {
            Elements contents = doc.select("#image-container");
            String dataUrl, dataOpt, dataObj;

            dataUrl = contents.attr("data-url");
            dataOpt = contents.attr("data-opt");
            dataObj = contents.attr("data-obj");

            Timber.d("Data URL: %s%s, Data Opt: %s, Data Obj: %s",
                    TSUMINO.getUrl(), dataUrl, dataOpt, dataObj);

            String request = sendPostRequest(dataUrl, dataOpt);
            return buildImageUrls(dataObj, request);
        }
        return Collections.emptyList();
    }

    private static String sendPostRequest(String dataUrl, String dataOpt) {
        final CookieManager cookieManager = CookieManager.getInstance();
        String url = TSUMINO.getUrl() + dataUrl;
        HttpURLConnection http = null;
        Map<String, String> data = new HashMap<>();

        data.put("q", dataOpt);
        String dataJson = new GsonBuilder().create().toJson(data, Map.class);

        String cookie = cookieManager.getCookie(url);

        try {
            http = (HttpURLConnection) ((new URL(url).openConnection()));
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("Accept", "application/json");
            http.setRequestProperty("Cookie", cookie);
            http.setRequestMethod("POST");
            http.connect();

            OutputStream stream = http.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"));

            writer.write(dataJson);
            writer.close();
            stream.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    http.getInputStream(), "UTF-8"));

            String line;
            StringBuilder builder = new StringBuilder();

            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            br.close();

            return builder.toString();
        } catch (UnsupportedEncodingException e) {
            Timber.e(e, "Encoding option is not supported for this URL");
        } catch (IOException e) {
            Timber.e(e, "IO Exception while attempting request");
        } finally {
            if (http != null) {
                http.disconnect();
            }
        }

        return null;
    }

    private static List<String> buildImageUrls(String data, String request) {
        List<String> imgUrls = new ArrayList<>();
        String imgUrl = TSUMINO.getUrl() + data + "?name=";

        JsonElement parser = new JsonParser().parse(request);
        JsonElement urls = parser.getAsJsonObject().get("reader_page_urls");

        for (int i = 0; i < urls.getAsJsonArray().size(); i++) {
            try {
                imgUrls.add(imgUrl + URLEncoder.encode(
                        urls.getAsJsonArray().get(i).getAsString(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                Timber.e(e, "Failed to encode URL");
            }
        }

        return imgUrls;
    }
}
