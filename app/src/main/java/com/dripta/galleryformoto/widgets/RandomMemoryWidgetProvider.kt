package com.dripta.galleryformoto.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.dripta.galleryformoto.MainActivity
import com.dripta.galleryformoto.R
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.BitmapUtils
import com.dripta.galleryformoto.data.MediaStoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ACTION_REFRESH = "com.dripta.galleryformoto.widgets.ACTION_REFRESH_RANDOM_MEMORY"

/** Home-screen widget showing a random photo (favorites preferred), refreshed periodically or on tap. */
class RandomMemoryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateWidget(context, appWidgetManager, id)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
            else android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val views = RemoteViews(context.packageName, R.layout.widget_random_memory)

        val openAppIntent = android.app.PendingIntent.getActivity(
            context, appWidgetId, Intent(context, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_photo, openAppIntent)

        val refreshIntent = Intent(context, RandomMemoryWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = android.app.PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

        if (!hasPermission) {
            views.setViewVisibility(R.id.widget_empty_text, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_photo, android.view.View.GONE)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
                val favoriteIds = db.favoriteDao().getAllIds().first().toSet()
                val allMedia = MediaStoreRepository.queryAllMedia(context)
                    .filterNot { it.isVideo || it.id in hiddenIds }

                val pool = allMedia.filter { it.id in favoriteIds }.ifEmpty { allMedia }
                val chosen = pool.randomOrNull()

                if (chosen == null) {
                    views.setViewVisibility(R.id.widget_empty_text, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_photo, android.view.View.GONE)
                } else {
                    val bitmap: Bitmap = BitmapUtils.loadBitmap(context, chosen.uri, maxDimension = 400)
                    views.setViewVisibility(R.id.widget_empty_text, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_photo, android.view.View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_photo, bitmap)
                }
            } catch (e: Exception) {
                views.setViewVisibility(R.id.widget_empty_text, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_photo, android.view.View.GONE)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
