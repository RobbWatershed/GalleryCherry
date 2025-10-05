package me.devsaki.hentoid.util

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.file.getOutputStream
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Manager class for Oauth2.0 authentication flow
 */
object OAuthSessionManager {
    private val activeSessions: MutableMap<Site, OauthSession> = HashMap()

    fun addSession(site: Site): OauthSession {
        val session = OauthSession(site.name)
        activeSessions.put(site, session)
        return session
    }

    fun getSessionByState(state: String?): OauthSession? {
        for (session in activeSessions.values) {
            if (session.state == state) return session
        }
        return null
    }

    fun getSessionBySite(site: Site?): OauthSession? {
        return activeSessions[site]
    }

    private fun getSessionFile(context: Context, host: String?): DocumentFile {
        val dir = context.filesDir
        return DocumentFile.fromFile(File(dir, "$host.json"))
    }

    /**
     * Save the Oauth session to the app's internal storage
     *
     * @param context Context to be used
     * @param session Session to be saved
     */
    fun saveSession(context: Context, session: OauthSession) {
        val file = getSessionFile(context, session.host)
        try {
            getOutputStream(context, file)?.use { output ->
                updateJson(
                    session,
                    OauthSession::class.java,
                    output
                )
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    /**
     * Get the Oauth session from the app's internal storage
     *
     * @param context Context to be used
     * @param host    Host the session belongs to
     * @return Oauth session from the given host; null if no such session exists
     */
    fun loadSession(context: Context, host: String): OauthSession? {
        val file = getSessionFile(context, host)
        if (!file.exists()) return null

        try {
            return jsonToObject(context, file, OauthSession::class.java)
        } catch (e: IOException) {
            Timber.e(e)
        }
        return null
    }

    class OauthSession internal constructor(val host: String) {
        var redirectUri: String = ""
        var clientId: String = ""
        var state: String = ""
        var accessToken: String = ""
        var refreshToken: String = ""
        var expiry: Instant = Instant.MIN

        var targetUrl: String = ""
        var userName: String = ""
    }
}