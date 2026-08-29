package com.daftari.ledger.widget

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.R
import com.daftari.ledger.domain.BookAlerts
import com.daftari.ledger.domain.BookWidgetSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ودجت دفتر الحسابات على جهاز حقيقي.
 *
 * `RemoteViews` من إطار Android، فلا يكفي اختبار JVM: هنا نتأكد أن التخطيط موجود
 * بمعرّفاته الصحيحة، وأن ما يبنيه المزوّد يُطبَّق فعلًا ويعرض الأعداد والنصوص المقصودة.
 */
@RunWith(AndroidJUnit4::class)
class AccountsBookWidgetTest {

    /** يطبّق `RemoteViews` الحقيقية على عرض فعلي ويقرأ نصوصه. */
    private fun render(summary: BookWidgetSummary?): Map<Int, String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = bookWidgetViews(context, WIDGET_ID, summary).apply(context, null)
        return mapOf(
            R.id.widget_book_title to view.findViewById<TextView>(R.id.widget_book_title).text.toString(),
            R.id.widget_book_debtors to view.findViewById<TextView>(R.id.widget_book_debtors).text.toString(),
            R.id.widget_book_stale to view.findViewById<TextView>(R.id.widget_book_stale).text.toString()
        )
    }

    @Test
    fun showsDebtorAndIdleCounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val texts = render(BookWidgetSummary(debtors = 4, staleDebtors = 2, staleBalances = 3))

        assertEquals(context.getString(R.string.widget_book_title), texts[R.id.widget_book_title])
        assertEquals(context.getString(R.string.widget_book_debtors, 4), texts[R.id.widget_book_debtors])
        assertEquals(
            context.getString(R.string.widget_book_stale, 2, BookAlerts.STALE_AFTER_DAYS),
            texts[R.id.widget_book_stale]
        )
    }

    @Test
    fun showsFriendlyTextWhenNobodyOwes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val texts = render(BookWidgetSummary())

        assertEquals(context.getString(R.string.widget_book_none), texts[R.id.widget_book_debtors])
        assertEquals(context.getString(R.string.widget_book_stale_none), texts[R.id.widget_book_stale])
    }

    @Test
    fun unreadableDataRendersZerosInsteadOfFailing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val texts = render(null)

        // قاعدة لم تُقرأ بعد: نص مطمئن لا رقم خاطئ ولا تعطّل.
        assertEquals(context.getString(R.string.widget_book_none), texts[R.id.widget_book_debtors])
        assertEquals(context.getString(R.string.widget_book_stale_none), texts[R.id.widget_book_stale])
    }

    private companion object {
        const val WIDGET_ID = 7
    }
}
