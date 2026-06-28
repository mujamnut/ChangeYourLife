package com.changeyourlife.cyl.core.network

import java.io.IOException
import java.net.UnknownHostException

fun Throwable.isDnsResolutionFailure(): Boolean {
    return generateSequence(this as Throwable?) { error -> error.cause }
        .any { error -> error is UnknownHostException }
}

fun Throwable.toBackendConnectionMessage(): String {
    return when {
        isDnsResolutionFailure() -> {
            "Cannot resolve CYL backend host. Check phone internet/DNS, disable Private DNS if enabled, then try again."
        }
        this is IOException -> {
            "Cannot reach CYL backend. Check your connection and make sure the backend URL is correct."
        }
        else -> localizedMessage ?: "Check your backend URL and make sure the server is running."
    }
}
