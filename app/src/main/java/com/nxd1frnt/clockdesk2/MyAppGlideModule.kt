package com.nxd1frnt.clockdesk2

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import java.io.InputStream
import java.util.concurrent.TimeUnit

@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Set a custom timeout for Glide image requests
        val timeoutMs = 15000
        val requestOptions = RequestOptions().timeout(timeoutMs)
        builder.setDefaultRequestOptions(requestOptions)
        Log.d("MyAppGlideModule", "Glide timeout set to $timeoutMs ms")
    }
}