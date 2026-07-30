package uk.co.pcgsoft.tracecapture

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRoutesTest {

    @Test
    fun detailRoutes_preserveCaptureIdInSavedStateHandle() {
        val captureIds = listOf(
            "capture-123-abc",
            "capture with spaces",
            "folder/item",
            "percent%value",
            "café-☕"
        )

        captureIds.forEach { captureId ->
            val encodedArgument = AppRoutes.detail(captureId).removePrefix("detail/")
            val savedStateHandle = SavedStateHandle(
                mapOf("captureId" to Uri.decode(encodedArgument))
            )

            assertEquals(captureId, savedStateHandle.get<String>("captureId"))
        }
    }
}
