package me.devsaki.hentoid.services;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.util.Consts;

/**
 * Created by Robb_w on 2018/04
 * <p>
 * Specific Volley Request intended at transmitting :
 * - content as byte array
 * - raw HTTP response headers
 * <p>
 * to the download callback routine
 */
class InputStreamVolleyRequest extends Request<Object> {
    // Callback listener
    private final Response.Listener<Map.Entry<byte[], Map<String, String>>> mParseListener;
    private final Map<String, String> headers;
    private final boolean useHentoidAgent;


    InputStreamVolleyRequest(
            int method,
            String mUrl,
            Map<String, String> headers,
            boolean useHentoidAgent,
            Response.Listener<Map.Entry<byte[], Map<String, String>>> parseListener,
            Response.ErrorListener errorListener) {
        super(method, mUrl, errorListener);
        this.headers = headers;
        this.useHentoidAgent = useHentoidAgent;
        // this request would never use cache.
        setShouldCache(false);
        mParseListener = parseListener;
    }

    @Override
    protected void deliverResponse(Object response) {
        // Nothing; all the work is done in Volley's worker thread, since it is time consuming (picture saving + DB operations)
    }

    @Override
    protected Response<Object> parseNetworkResponse(NetworkResponse response) {
        //Initialise local responseHeaders map with response headers received
        Map<String, String> responseHeaders = response.headers;

        mParseListener.onResponse(new AbstractMap.SimpleEntry<>(response.data, responseHeaders));

        //Pass the response data here
        return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> params = new HashMap<>();
        params.put("User-Agent", useHentoidAgent ? Consts.USER_AGENT : Consts.USER_AGENT_NEUTRAL);
        params.putAll(headers);
        return params;
    }
}