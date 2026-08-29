package com.daftari.ledger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.daftari.ledger.MainActivity
import com.daftari.ledger.R
import com.daftari.ledger.data.AccountsBookRepository
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.domain.BookAlerts
import com.daftari.ledger.domain.BookDebtor
import com.daftari.ledger.domain.BookWidgetSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ودجت «دفتر الحسابات» على الشاشة الرئيسية.
 *
 * يعرض **أعدادًا لا مبالغ**: كم شخصًا عليه دين، وكم منهم له دين متوقف منذ
 * [BookAlerts.STALE_AFTER_DAYS] يومًا. فلا يُكشف أي رصيد، ولا حاجة لجمع عملات مختلفة،
 * ويبقى الودجت صحيحًا مع تعدد العملات. الزر يفتح التطبيق داخل الدفتر مباشرة.
 *
 * المحل المقروء هو المحل النشط المحفوظ (نفس مصدر [DaftariWidget])، وإلا فأول محل نشط.
 */
class AccountsBookWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val summary = try {
                readSummary(context)
            } catch (error: Exception) {
                // قاعدة لم تُنشأ بعد أو محل محذوف: نعرض أصفارًا بدل تعطيل الودجت.
                Log.w(TAG, "تعذّرت قراءة بيانات الدفتر", error)
                null
            }
            try {
                ids.forEach { id -> manager.updateAppWidget(id, bookWidgetViews(context, id, summary)) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun readSummary(context: Context): BookWidgetSummary {
        val db = AppDb.get(context)
        val shops = LedgerRepository(db).shops.listActive()
        val activeId = DaftariWidget.activeShopId(context)
        val shop = shops.firstOrNull { it.id == activeId } ?: shops.firstOrNull() ?: return BookWidgetSummary()
        val book = AccountsBookRepository(db)
        val activity = book.observeLastActivity().first()
            .filter { it.lastAt != null }
            .associate { it.personId to it.lastAt!! }
        val debtors = book.observeBalances(shop.id).first().map { balance ->
            BookDebtor(balance.personId, balance.currencyId, balance.netMinor, activity[balance.personId])
        }
        return BookAlerts.summary(debtors, System.currentTimeMillis())
    }

    companion object {
        private const val TAG = "AccountsBookWidget"

        /** يطلب تحديث الودجت بعد أي تغيير في الأشخاص أو العمليات. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AccountsBookWidget::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, AccountsBookWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }
}

/**
 * يبني مظهر الودجت من [summary]؛ `null` تعني أن البيانات لم تُقرأ بعد فتظهر أصفار
 * بدل أرقام خاطئة.
 *
 * عامة (لا `private`) حتى يختبرها `AccountsBookWidgetTest` على جهاز حقيقي عبر
 * `RemoteViews.apply` دون حاجة إلى `AppWidgetManager`.
 */
fun bookWidgetViews(context: Context, widgetId: Int, summary: BookWidgetSummary?): RemoteViews {
    val debtors = summary?.debtors ?: 0
    val stale = summary?.staleDebtors ?: 0
    val views = RemoteViews(context.packageName, R.layout.widget_accounts_book)
    views.setTextViewText(R.id.widget_book_title, context.getString(R.string.widget_book_title))
    views.setTextViewText(
        R.id.widget_book_debtors,
        if (debtors == 0) {
            context.getString(R.string.widget_book_none)
        } else {
            context.getString(R.string.widget_book_debtors, debtors)
        }
    )
    views.setTextViewText(
        R.id.widget_book_stale,
        if (stale == 0) {
            context.getString(R.string.widget_book_stale_none)
        } else {
            context.getString(R.string.widget_book_stale, stale, BookAlerts.STALE_AFTER_DAYS)
        }
    )
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_OPEN_BOOK, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    views.setOnClickPendingIntent(
        R.id.widget_book_open,
        PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
    return views
}
