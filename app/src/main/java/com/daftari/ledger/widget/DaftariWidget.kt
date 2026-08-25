package com.daftari.ledger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.daftari.ledger.MainActivity
import com.daftari.ledger.R
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.domain.Money
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DaftariWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDb.get(context)
                val repo = LedgerRepository(db)
                val activeShops = repo.shops.listActive()
                val activeId = activeShopId(context)
                val shop = activeShops.firstOrNull { it.id == activeId } ?: activeShops.firstOrNull()
                val settings = repo.settings.get()
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val total = shop?.let { repo.totals(it.id, start, System.currentTimeMillis()).cashNet } ?: 0L
                val amount = if (settings?.hideBalances == true) {
                    "••••"
                } else {
                    Money(total, shop?.fractionDigits ?: 2).format(
                        if (settings?.latinDigits != false) Locale.US else Locale.getDefault(),
                        shop?.currencyCode,
                        includeCurrency = shop != null
                    )
                }
                ids.forEach { id -> updateWidget(context, manager, id, shop?.name.orEmpty(), amount) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        shopName: String,
        amount: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_daftari)
        views.setTextViewText(R.id.widget_title, shopName.ifBlank { context.getString(R.string.app_name) })
        views.setTextViewText(R.id.widget_total, amount)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_QUICK_SALE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_quick_sale,
            PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        manager.updateAppWidget(id, views)
    }

    companion object {
        private const val ACTIVE_SHOP_PREFS = "daftari_active_shop"

        /** يحفظ المحل النشط حتى يعرضه الويدجت (بدل أول محل في القائمة). */
        fun saveActiveShop(context: Context, shopId: Long) {
            context.getSharedPreferences(ACTIVE_SHOP_PREFS, Context.MODE_PRIVATE)
                .edit().putLong("shopId", shopId).apply()
        }

        fun activeShopId(context: Context): Long =
            context.getSharedPreferences(ACTIVE_SHOP_PREFS, Context.MODE_PRIVATE).getLong("shopId", -1L)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DaftariWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, DaftariWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }
    }
}
