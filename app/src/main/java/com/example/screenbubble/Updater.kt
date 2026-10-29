package com.example.screenbubble

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Self-update from the GitHub release: version.txt holds the newest build number, ScreenBubble.apk is the app. */
object Updater {
    private const val BASE = "https://github.com/Santed3/Ai-live/releases/download/latest-build/"
    private val http = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun current(c: Context): Long = try {
        c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode
    } catch (e: Exception) {
        0L
    }

    /** Newest published build number, or null when it can't be reached. Blocking. */
    fun latest(): Long? = try {
        http.newCall(Request.Builder().url(BASE + "version.txt").build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()?.trim()?.toLongOrNull()
        }
    } catch (e: Exception) {
        null
    }

    /** Throttled background check from the service; returns the new build number if one exists. */
    fun checkIfDue(c: Context): Long? {
        val prefs = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("upd_checked", 0L) < 6 * 3600 * 1000L) return null
        prefs.edit().putLong("upd_checked", now).apply()
        val l = latest() ?: return null
        return if (l > current(c)) l else null
    }

    fun canInstall(c: Context): Boolean = Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    /** Downloads the APK and hands it to the system installer (the user confirms). Blocking. */
    fun downloadAndInstall(c: Context, onProgress: (Int) -> Unit) {
        val file = File(c.cacheDir, "update.apk")
        http.newCall(Request.Builder().url(BASE + "ScreenBubble.apk").build()).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("Hämtningen misslyckades (${r.code})")
            val body = r.body ?: throw RuntimeException("Tomt svar")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress(pct) }
                        }
                    }
                }
            }
        }
        val installer = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(c, InstallReceiver::class.java).setPackage(c.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(c, id, intent, flags).intentSender)
        }
    }
}

/** Receives the installer's status; when the system needs the user's confirmation it shows its dialog. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
