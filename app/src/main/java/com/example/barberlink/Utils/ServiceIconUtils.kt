package com.example.barberlink.Utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.barberlink.R

object ServiceIconUtils {
    /**
     * Loads a service icon into an ImageView.
     * Handles both cloud URLs and local drawable resource names.
     */
    fun loadServiceIcon(context: Context, iconValue: String?, imageView: ImageView) {
        if (iconValue.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.img_service_icon_placeholder)
            return
        }

        if (iconValue.startsWith("http")) {
            Glide.with(context)
                .load(iconValue)
                .placeholder(R.drawable.img_service_icon_placeholder)
                .error(R.drawable.img_service_icon_placeholder)
                .into(imageView)
        } else {
            // Assume it's a local resource name
            val resId = context.resources.getIdentifier(iconValue, "drawable", context.packageName)
            if (resId != 0) {
                imageView.setImageResource(resId)
            } else {
                imageView.setImageResource(R.drawable.img_service_icon_placeholder)
            }
        }
    }
}
