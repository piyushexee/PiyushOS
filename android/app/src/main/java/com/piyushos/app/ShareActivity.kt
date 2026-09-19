package com.piyushos.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriStr = intent?.getStringExtra("uri")
        val mime = intent?.getStringExtra("mime") ?: "application/octet-stream"
        val name = intent?.getStringExtra("name") ?: "file"
        if (uriStr == null) {
            finish()
            return
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Share $name"))
        finish()
    }
}
