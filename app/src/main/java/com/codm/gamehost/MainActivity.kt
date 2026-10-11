package com.codm.gamehost

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import com.codm.gamehost.shizuku.ShizukuBridge
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var editor: EditText
    private lateinit var output: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val requestOpen = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuBridge.init()
        setContentView(buildUi())
        refreshStatus()
        handler.postDelayed(object : Runnable {
            override fun run() {
                refreshStatus()
                handler.postDelayed(this, 1500)
            }
        }, 1500)
    }

    private fun buildUi(): View {
        val bg = Color.rgb(7, 10, 15)
        val panel = Color.rgb(17, 24, 35)
        val cyan = Color.rgb(0, 229, 255)
        val white = Color.rgb(234, 244, 255)
        val muted = Color.rgb(125, 142, 163)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(14), dp(18), dp(14), dp(18))
        }

        val title = TextView(this).apply {
            text = "CODM GAME HOST v0.3"
            setTextColor(cyan)
            textSize = 23f
            setTypeface(typeface, 1)
        }
        root.addView(title)

        status = TextView(this).apply {
            text = "SHIZUKU: checking..."
            setTextColor(white)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(10))
        }
        root.addView(status)

        root.addView(button("GRANT / CONNECT SHIZUKU", cyan) {
            ShizukuBridge.requestPermission()
            ShizukuBridge.connect()
            refreshStatus()
        })

        root.addView(button("▶ PLAY CODM • MASTER90", Color.rgb(0, 230, 118)) {
            loadAsset("MASTER90.sh")
            runEditor()
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(button("LOAD MASTER90", panel) { loadAsset("MASTER90.sh") }, weight())
        row.addView(button("LOAD CANDY", panel) { loadAsset("POST_MASTER_CANDY.sh") }, weight())
        root.addView(row)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row2.addView(button("+ IMPORT .SH", panel) { openScript() }, weight())
        row2.addView(button("SAVE", panel) { saveEditor() }, weight())
        row2.addView(button("RUN", cyan) { runEditor() }, weight())
        root.addView(row2)

        val label = TextView(this).apply {
            text = "SCRIPT LAB"
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(10), 0, dp(5))
        }
        root.addView(label)

        editor = EditText(this).apply {
            setTextColor(white)
            setHintTextColor(muted)
            setBackgroundColor(panel)
            textSize = 12f
            gravity = Gravity.TOP or Gravity.START
            minLines = 13
            maxLines = 22
            setPadding(dp(10), dp(10), dp(10), dp(10))
            hint = "#!/system/bin/sh"
            setHorizontallyScrolling(true)
        }
        root.addView(editor, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(330)
        ))

        val outLabel = TextView(this).apply {
            text = "OUTPUT"
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(10), 0, dp(5))
        }
        root.addView(outLabel)

        val scroll = ScrollView(this)
        output = TextView(this).apply {
            setTextColor(white)
            setBackgroundColor(Color.rgb(5, 8, 12))
            textSize = 11f
            setPadding(dp(10), dp(10), dp(10), dp(10))
            text = "Ready."
        }
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        return root
    }

    private fun button(textValue: String, color: Int, action: () -> Unit): Button {
        return Button(this).apply {
            text = textValue
            isAllCaps = false
            setOnClickListener { action() }
            setBackgroundColor(color)
            setTextColor(if (color == Color.rgb(0, 229, 255)) Color.BLACK else Color.WHITE)
        }
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }

    private fun refreshStatus() {
        val binder = ShizukuBridge.binderAvailable
        val perm = ShizukuBridge.permissionGranted
        val ready = ShizukuBridge.isReady()
        status.text = "SHIZUKU binder=" + binder + " permission=" + perm + " service=" + ready
        status.setTextColor(
            if (ready) Color.rgb(0, 230, 118)
            else if (binder) Color.rgb(255, 193, 7)
            else Color.rgb(255, 61, 113)
        )
    }

    private fun loadAsset(name: String) {
        try {
            editor.setText(assets.open("scripts/" + name).bufferedReader().use { it.readText() })
            output.text = "Loaded " + name
        } catch (t: Throwable) {
            output.text = "LOAD ERROR: " + t.message
        }
    }

    private fun openScript() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, requestOpen)
    }

    @Deprecated("legacy result API used for minimum dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestOpen || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                editor.setText(input.bufferedReader().use { it.readText() })
            }
            output.text = "Imported: " + displayName(uri)
        } catch (t: Throwable) {
            output.text = "IMPORT ERROR: " + t.message
        }
    }

    private fun displayName(uri: Uri): String {
        var name = "script.sh"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        return name
    }

    private fun saveEditor() {
        try {
            val f = File(filesDir, "custom.sh")
            f.writeText(editor.text.toString())
            output.text = "Saved: " + f.absolutePath
        } catch (t: Throwable) {
            output.text = "SAVE ERROR: " + t.message
        }
    }

    private fun runEditor() {
        val script = editor.text.toString()
        if (script.isBlank()) {
            output.text = "Script is empty."
            return
        }
        if (!ShizukuBridge.isReady()) {
            ShizukuBridge.connect()
            output.text = "Shizuku service is not ready yet. Tap GRANT / CONNECT and retry."
            return
        }
        output.text = "RUNNING..."
        Thread {
            val result = ShizukuBridge.exec(script) ?: "NO RESULT / SERVICE DISCONNECTED"
            runOnUiThread {
                output.text = result
                refreshStatus()
            }
        }.start()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
