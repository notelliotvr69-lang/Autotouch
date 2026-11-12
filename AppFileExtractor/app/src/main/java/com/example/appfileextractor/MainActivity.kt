package com.example.appfileextractor

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class InstalledApp(val label: String, val packageName: String, val apkPath: String)

class MainActivity : Activity() {
    private lateinit var listView: ListView
    private lateinit var status: TextView
    private lateinit var apps: List<InstalledApp>
    private var selected: InstalledApp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadApps()
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 24)
            setBackgroundColor(0xFF101318.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "App File Extractor"
            textSize = 25f
            setTextColor(0xFFFFFFFF.toInt())
        })
        layout.addView(TextView(this).apply {
            text = "Choose an installed app to export the files packaged inside its APK."
            textSize = 14f
            setTextColor(0xFFB8C0CC.toInt())
            setPadding(0, 10, 0, 20)
        })
        layout.addView(Button(this).apply {
            text = "Refresh app list"
            setOnClickListener { loadApps() }
        })
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFB8C0CC.toInt())
            setPadding(0, 12, 0, 8)
        }
        listView = ListView(this).apply {
            dividerHeight = 1
            setOnItemClickListener { _, _, position, _ ->
                selected = apps[position]
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, safeFileName(selected!!.label) + "-apk-contents.zip")
                }
                startActivityForResult(intent, 1001)
            }
        }
        layout.addView(status)
        layout.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(launcher, 0)
        }
        apps = resolved.mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            try {
                InstalledApp(packageManager.getApplicationLabel(ai).toString(), ai.packageName, ai.sourceDir)
            } catch (_: Exception) { null }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

        listView.adapter = object : ArrayAdapter<InstalledApp>(this, android.R.layout.simple_list_item_2, android.R.id.text1, apps) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                val app = getItem(position)!!
                view.findViewById<TextView>(android.R.id.text1).apply {
                    text = app.label
                    setTextColor(0xFFFFFFFF.toInt())
                }
                view.findViewById<TextView>(android.R.id.text2).apply {
                    text = app.packageName
                    setTextColor(0xFFB8C0CC.toInt())
                }
                return view
            }
        }
        status.text = "${apps.size} launchable apps found. Tap one to export its APK contents."
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selected?.let { exportApkContents(it, uri) }
        }
    }

    private fun exportApkContents(app: InstalledApp, destination: Uri) {
        status.text = "Exporting ${app.label}…"
        Thread {
            var count = 0
            var error: String? = null
            try {
                contentResolver.openOutputStream(destination)?.use { rawOut ->
                    ZipOutputStream(BufferedOutputStream(rawOut)).use { out ->
                        ZipInputStream(BufferedInputStream(FileInputStream(app.apkPath))).use { input ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val entry = input.nextEntry ?: break
                                if (!entry.isDirectory) {
                                    out.putNextEntry(ZipEntry(entry.name))
                                    while (true) {
                                        val n = input.read(buffer)
                                        if (n <= 0) break
                                        out.write(buffer, 0, n)
                                    }
                                    out.closeEntry()
                                    count++
                                }
                                input.closeEntry()
                            }
                        }
                    }
                } ?: throw IllegalStateException("Could not open the selected save location.")
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Unknown export error"
            }
            runOnUiThread {
                status.text = if (error == null) {
                    "Export complete: $count files saved. Some Android resources are compiled and won't be editable as plain text."
                } else "Export failed: $error"
            }
        }.start()
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "app" }
}
