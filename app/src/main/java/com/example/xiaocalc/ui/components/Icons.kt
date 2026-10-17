package com.example.xiaocalc.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object WatchVectorIcons {
    private const val BACKSPACE_PATH =
        "M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z" +
            "M19 15.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12z"

    val Backspace: ImageVector by lazy {
        vector(BACKSPACE_PATH)
    }

    private const val COPY_PATH =
        "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1z" +
            "M19 5H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2z" +
            "M19 21H8V7h11v14z"

    val Copy: ImageVector by lazy {
        vector(COPY_PATH)
    }

    private const val SWAP_PATH =
        "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3z" +
            "M21 9l-3.99-4v3H10v2h7.01v3L21 9z"

    val Swap: ImageVector by lazy {
        vector(SWAP_PATH)
    }

    private const val CHEVRON_DOWN_PATH = "M7 10l5 5 5-5z"

    val ChevronDown: ImageVector by lazy {
        vector(CHEVRON_DOWN_PATH)
    }

    private const val REFRESH_PATH =
        "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-8 8s3.57 8 8 8" +
            "c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6" +
            "s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"

    val Refresh: ImageVector by lazy {
        vector(REFRESH_PATH)
    }

    private const val APPS_PATH =
        "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4z" +
            "m6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z"

    val Apps: ImageVector by lazy {
        vector(APPS_PATH)
    }

    private const val ARROW_FORWARD_PATH = "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"

    val ArrowForward: ImageVector by lazy {
        vector(ARROW_FORWARD_PATH)
    }

    private const val PLUS_PATH = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"

    val Plus: ImageVector by lazy {
        vector(PLUS_PATH)
    }

    private const val REMOVE_PATH = "M19 13H5v-2h14v2z"

    val Remove: ImageVector by lazy {
        vector(REMOVE_PATH)
    }

    private const val CLOSE_PATH =
        "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"

    val Close: ImageVector by lazy {
        vector(CLOSE_PATH)
    }

    private fun vector(path: String): ImageVector =
        ImageVector.Builder(
            name = "WatchIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
}
