package uk.co.pcgsoft.tracecapture.capture

import android.content.pm.PackageManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceApplicationResolverImpl @Inject constructor(
    private val packageManager: PackageManager
) : SourceApplicationResolver {

    companion object {
        private val KNOWN_APPS = mapOf(
            "com.android.chrome" to "Chrome",
            "com.google.android.apps.messaging" to "Messages",
            "com.google.android.gm" to "Gmail",
            "com.google.android.apps.docs" to "Google Docs",
            "com.twitter.android" to "X (Twitter)",
            "com.facebook.katana" to "Facebook",
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram",
            "com.slack" to "Slack",
            "com.google.android.youtube" to "YouTube",
            "com.instagram.android" to "Instagram",
            "com.linkedin.android" to "LinkedIn",
            "com.reddit.frontpage" to "Reddit",
            "com.discord" to "Discord",
            "org.mozilla.firefox" to "Firefox",
            "com.microsoft.emmx" to "Edge",
            "com.apple.android.mobilesafari" to "Safari",
            "com.brave.browser" to "Brave",
            "com.opera.browser" to "Opera",
        )
    }

    override fun resolve(packageName: String?): SourceApplicationInfo {
        if (packageName == null) return SourceApplicationInfo(null, null)

        val displayLabel = try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            KNOWN_APPS[packageName]
        }

        return SourceApplicationInfo(packageName, displayLabel)
    }
}
