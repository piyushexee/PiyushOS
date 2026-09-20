package com.piyushos.canary

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * PiyushOS Test app - ekdum minimal (zero dependencies, sirf Android framework).
 *
 * Diagnostic kaam:
 * - YEH app chale to → phone ka system theek hai, problem PiyushOS ki kisi component me hai
 * - YEH bhi crash kare to → problem phone ke system/install level pe hai
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E1424"))
        }
        root.addView(TextView(this).apply {
            text = "✅ Hello Piyush!"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        })
        root.addView(TextView(this).apply {
            text = "\nPhone ka system is test app ko theek se chalal raha hai.\n\n" +
                "Ab 'PiyushOS' app khol kar dekho:\n" +
                "- Khul jaye → theek ho gaya!\n" +
                "- Crash ho → drawer ka screenshot bhej dena (PiyushOS ka label version dikhata hai)"
            setTextColor(Color.parseColor("#8A97B5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(32, 24, 32, 24)
        })
        setContentView(root)
    }
}
