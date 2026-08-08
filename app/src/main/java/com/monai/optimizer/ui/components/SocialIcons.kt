package com.monai.optimizer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Logo Telegram & GitHub — dibikin manual sebagai ImageVector karena
 * androidx.compose.material.icons (Material Icons) bawaan Google TIDAK
 * menyediakan logo brand pihak ketiga seperti ini.
 */
object SocialIcons {

    val Telegram: ImageVector by lazy {
        ImageVector.Builder(
            name = "Telegram",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            // Bentuk pesawat kertas khas logo Telegram, disederhanakan jadi satu path tunggal.
            moveTo(21.85f, 3.15f)
            curveTo(21.55f, 2.88f, 21.1f, 2.8f, 20.65f, 2.98f)
            lineTo(2.4f, 10.02f)
            curveTo(1.9f, 10.21f, 1.9f, 10.6f, 2.02f, 10.83f)
            curveTo(2.13f, 11.05f, 2.4f, 11.24f, 2.9f, 11.29f)
            lineTo(7.3f, 12.65f)
            lineTo(9.0f, 18.1f)
            curveTo(9.13f, 18.5f, 9.4f, 18.68f, 9.72f, 18.68f)
            curveTo(9.93f, 18.68f, 10.13f, 18.6f, 10.3f, 18.44f)
            lineTo(12.7f, 16.2f)
            lineTo(16.9f, 19.35f)
            curveTo(17.1f, 19.5f, 17.35f, 19.58f, 17.6f, 19.58f)
            curveTo(17.75f, 19.58f, 17.9f, 19.55f, 18.03f, 19.5f)
            curveTo(18.35f, 19.36f, 18.58f, 19.06f, 18.63f, 18.71f)
            lineTo(21.98f, 3.98f)
            curveTo(22.05f, 3.65f, 21.98f, 3.34f, 21.85f, 3.15f)
            close()

            moveTo(17.35f, 6.15f)
            lineTo(9.3f, 12.35f)
            lineTo(8.6f, 11.9f)
            lineTo(17.35f, 6.15f)
            close()
        }.build()
    }

    val GitHub: ImageVector by lazy {
        ImageVector.Builder(
            name = "GitHub",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            // Logo kucing GitHub (Octocat mark) — path standar 24x24 (mengikuti spesifikasi resmi GitHub brand assets).
            moveTo(12f, 1.5f)
            curveTo(6.2f, 1.5f, 1.5f, 6.2f, 1.5f, 12f)
            curveTo(1.5f, 16.63f, 4.5f, 20.55f, 8.67f, 21.93f)
            curveTo(9.19f, 22.03f, 9.38f, 21.71f, 9.38f, 21.43f)
            curveTo(9.38f, 21.18f, 9.37f, 20.4f, 9.37f, 19.58f)
            curveTo(6.5f, 20.2f, 5.9f, 18.32f, 5.9f, 18.32f)
            curveTo(5.43f, 17.14f, 4.76f, 16.82f, 4.76f, 16.82f)
            curveTo(3.83f, 16.19f, 4.83f, 16.2f, 4.83f, 16.2f)
            curveTo(5.86f, 16.27f, 6.4f, 17.26f, 6.4f, 17.26f)
            curveTo(7.32f, 18.83f, 8.8f, 18.38f, 9.39f, 18.11f)
            curveTo(9.48f, 17.44f, 9.75f, 16.99f, 10.05f, 16.73f)
            curveTo(7.77f, 16.48f, 5.37f, 15.6f, 5.37f, 11.68f)
            curveTo(5.37f, 10.56f, 5.77f, 9.65f, 6.42f, 8.93f)
            curveTo(6.32f, 8.68f, 5.97f, 7.63f, 6.52f, 6.24f)
            curveTo(6.52f, 6.24f, 7.38f, 5.97f, 9.36f, 7.3f)
            curveTo(10.2f, 7.07f, 11.1f, 6.95f, 12f, 6.95f)
            curveTo(12.9f, 6.95f, 13.8f, 7.07f, 14.64f, 7.3f)
            curveTo(16.62f, 5.97f, 17.48f, 6.24f, 17.48f, 6.24f)
            curveTo(18.03f, 7.63f, 17.68f, 8.68f, 17.58f, 8.93f)
            curveTo(18.23f, 9.65f, 18.63f, 10.56f, 18.63f, 11.68f)
            curveTo(18.63f, 15.61f, 16.22f, 16.47f, 13.93f, 16.72f)
            curveTo(14.3f, 17.04f, 14.64f, 17.66f, 14.64f, 18.61f)
            curveTo(14.64f, 19.97f, 14.63f, 21.07f, 14.63f, 21.43f)
            curveTo(14.63f, 21.71f, 14.81f, 22.04f, 15.35f, 21.93f)
            curveTo(19.5f, 20.55f, 22.5f, 16.63f, 22.5f, 12f)
            curveTo(22.5f, 6.2f, 17.8f, 1.5f, 12f, 1.5f)
            close()
        }.build()
    }
}
