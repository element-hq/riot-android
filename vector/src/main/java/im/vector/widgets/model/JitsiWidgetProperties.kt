package im.vector.widgets.model

import android.net.Uri

class JitsiWidgetProperties(private val uriString: String) {
    val domain: String by lazy { configs["conferenceDomain"] ?: DEFAULT_JITSI_DOMAIN }
    val displayName: String? by lazy { configs["displayName"] }
    val avatarUrl: String? by lazy { configs["avatarUrl"] }

    private val configString: String? by lazy { Uri.parse(uriString).fragment }

    private val configs: Map<String, String?> by lazy {
        configString?.split("&")
                ?.map { it.split("=") }
                ?.map { (key, value) -> key to value }
                ?.toMap()
                ?: mapOf()
    }
}

private const val DEFAULT_JITSI_DOMAIN = "jitsi.riot.im"
