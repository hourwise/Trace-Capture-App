package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedCaptureProcessorImpl @Inject constructor(
    private val intentParser: ShareIntentParser,
    private val urlExtractor: UrlExtractor,
    private val sourceResolver: SourceApplicationResolver
) : SharedCaptureProcessor {

    override fun process(intent: Intent): CaptureDraft? {
        val parseResult = intentParser.parse(intent) ?: return null
        val extraction = urlExtractor.extractUrls(parseResult.textContent)
        val sourceInfo = sourceResolver.resolve(parseResult.sourcePackageHint)

        return CaptureDraft(
            originalContent = parseResult.textContent,
            primaryUrl = extraction.primaryUrl,
            detectedUrls = extraction.urls,
            sourcePackageName = sourceInfo.packageName,
            sourceLabel = sourceInfo.displayLabel,
            captureType = extraction.captureType
        )
    }
}
