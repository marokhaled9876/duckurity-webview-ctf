package com.duckurity.webviewctf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Normal / "intended" path: open the bundled help page.
        // This is the ONLY path a benign user takes, and it is safe on
        // its own -- the bug only surfaces when HelpActivity is reached
        // with an attacker-controlled "url" extra (see HelpActivity.kt
        // and AndroidManifest.xml's duckurity://help deep link).
        findViewById<Button>(R.id.btnHelp).setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java)
            intent.data = Uri.parse("file:///android_asset/help.html")
            startActivity(intent)
        }
    }
}
