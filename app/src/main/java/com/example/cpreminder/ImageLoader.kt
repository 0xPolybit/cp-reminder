package com.example.cpreminder

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.net.HttpURLConnection
import java.net.URL

/** Minimal blocking image downloader; call off the main thread. */
object ImageLoader {
    fun download(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Sets a circular-cropped [bitmap] on [imageView], or the person-icon fallback if null. */
    fun loadCircularInto(imageView: ImageView, bitmap: Bitmap?, resources: Resources) {
        if (bitmap != null) {
            imageView.setImageDrawable(
                RoundedBitmapDrawableFactory.create(resources, bitmap).apply { isCircular = true }
            )
        } else {
            imageView.setImageResource(R.drawable.ic_person)
        }
    }
}
