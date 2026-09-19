package me.devsaki.hentoid.util.exception

import java.io.IOException

class NetworkingException(val statusCode: Int, message: String, cause: Throwable? = null) :
    IOException(message, cause)
