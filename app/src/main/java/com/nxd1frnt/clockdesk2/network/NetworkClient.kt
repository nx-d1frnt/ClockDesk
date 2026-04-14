package com.nxd1frnt.clockdesk2.network

import android.content.Context
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

/**
 * Simple network client for making HTTP requests using Volley
 */
class NetworkClient private constructor(private val context: Context) {
    
    private val requestQueue = Volley.newRequestQueue(context.applicationContext)
    
    /**
     * Make a GET request and return the response as a String
     */
    fun getString(
        url: String,
        onSuccess: (String) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        val stringRequest = StringRequest(
            Request.Method.GET,
            url,
            Response.Listener { response ->
                onSuccess(response)
            },
            Response.ErrorListener { error ->
                val exception = Exception(error.message ?: "Unknown error", error.cause)
                onError?.invoke(exception)
            }
        )
        requestQueue.add(stringRequest)
    }
    
    companion object {
        @Volatile
        private var instance: NetworkClient? = null
        
        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = NetworkClient(context.applicationContext)
                    }
                }
            }
        }
        
        fun getInstance(): NetworkClient {
            return instance ?: throw IllegalStateException(
                "NetworkClient not initialized. Call NetworkClient.init(context) first."
            )
        }
    }
}
