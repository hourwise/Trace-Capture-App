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

    override fun process(intent: Intent): SharedCaptureResult {
        val parseResult = intentParser.parse(intent)
        val result = parseResult as? ShareIntentParseResult.Success
            ?: return SharedCaptureResult.Rejected(
                (parseResult as ShareIntentParseResult.Rejected).reason
            )

        val extraction = urlExtractor.extractUrls(result.content.textContent)
        val sourceInfo = sourceResolver.resolve(result.content.sourcePackageHint)

        return SharedCaptureResult.Ready(
            draft = CaptureDraft(
                originalContent = result.content.textContent,
                primaryUrl = extraction.primaryUrl,
                detectedUrls = extraction.urls,
                sourcePackageName = sourceInfo.packageName,
                sourceLabel = sourceInfo.displayLabel,
                captureType = extraction.captureType
            )
        )
    }
}
