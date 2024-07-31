package com.example.barberlink.Utils

import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object SvgUtils {
    suspend fun loadSVGFromResource(resources: Resources, resourceId: Int): String {
        return withContext(Dispatchers.IO) {
            val inputStream: InputStream = resources.openRawResource(resourceId)
            val svgContent = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            return@withContext """
            <html>
            <head>
                <style>
                    body, html {
                        margin: 0;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        opacity: 1;
                    }
                    svg {
                        width: 100%;
                        height: 100%;
                    }
                </style>
            </head>
            <body>
                $svgContent
                <script>
                    document.querySelector('svg').classList.add('animated');
                </script>
            </body>
            </html>
        """.trimIndent()
        }
    }
}