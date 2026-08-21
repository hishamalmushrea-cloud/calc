package com.daftari.ledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.daftari.ledger.data.AgingRow
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind
import com.daftari.ledger.security.AppLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftariRoot(s: UiState, vm: MainViewModel, activity: FragmentActivity? = null) {
    var tab by remember { mutableIntStateOf(0) }
    var addType by remember { mutableStateOf<DocType?>(null) }
    var quickParty by remember { mutableStateOf<PartyEntity?>(null) }
    var quickType by remember { mutableStateOf<DocType?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) {
        s.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("دفتري", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(s.shop?.name ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (s.agingAlert > 0) {
                        BadgedBox(badge = { Badge { Text("${s.agingAlert}") } }) {
                            IconButton(onClick = { tab = 3 }) {
                                Icon(Icons.Default.Notifications, "تنبيهات")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (tab <= 3) FloatingActionButton(
                onClick = { addType = DocType.SALE },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val items = listOf("الرئيسية", "الحسابات", "العمليات", "التقارير", "المزيد")
                val icons = listOf(Icons.Default.Home, Icons.Default.People, Icons.Default.ReceiptLong, Icons.Default.Assessment, Icons.Default.MoreHoriz)
                items.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], null) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { pad ->
        when (tab) {
            0 -> Dashboard(s, vm, pad) { addType = it }
            1 -> PartiesScreen(s, vm, pad)
            2 -> DocsScreen(s, vm, pad)
            3 -> ReportsScreen(s, vm, pad)
            else -> MoreScreen(s, vm, pad)
        }
    }
    if (addType != null) DocSheet(s, vm, addType!!, null) { addType = null }
    if (s.selectedParty != null) PartyDetail(
        s, vm,
        onDismiss = { vm.closePartyDialog() },
        onQuick = { t ->
            quickParty = s.selectedParty
            quickType = t
            vm.closePartyDialog()
        }
    )
    if (quickParty != null && quickType != null) {
        DocSheet(s, vm, quickType!!, null, initialParty = quickParty) { quickParty = null; quickType = null }
    }
    if (s.locked) LockDialog(s, vm, activity)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(s: UiState, vm: MainViewModel, pad: PaddingValues, onQuick: (DocType) -> Unit) {
    val t = s.totals
    val lateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    var showRangePicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                val label = when (p) {
                    Period.TODAY -> "اليوم"; Period.YESTERDAY -> "أمس"; Period.WEEK -> "الأسبوع"
                    Period.MONTH -> "الشهر"; Period.YEAR -> "السنة"; Period.CUSTOM -> "مخصص"
                }
                FilterChip(
                    selected = s.period == p,
                    onClick = {
                        if (p == Period.CUSTOM && (s.customFrom == null || s.customTo == null)) {
                            // المرّة الأولى: افتح منتقي التاريخين، وسيُضبط الفترة عند الاختيار.
                            showRangePicker = true
                        } else {
                            vm.setPeriod(p)
                        }
                    },
                    label = {
                        val cf = s.customFrom
                        val ct = s.customTo
                        if (p == Period.CUSTOM && cf != null && ct != null) {
                            val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
                            Text("${fmt.format(Date(cf))} → ${fmt.format(Date(ct))}")
                        } else Text(label)
                    }
                )
            }
        }
        if (showRangePicker) {
            RangePicker(
                initialFrom = s.customFrom,
                initialTo = s.customTo,
                onDismiss = { showRangePicker = false },
                onConfirm = { from, to ->
                    vm.setCustomRange(from, to)
                    showRangePicker = false
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onQuick(DocType.SALE) }, modifier = Modifier.weight(1f)) { Text("بيع سريع") }
            Button(onClick = { onQuick(DocType.COLLECT) }, modifier = Modifier.weight(1f)) { Text("تحصيل") }
            Button(onClick = { onQuick(DocType.EXPENSE) }, modifier = Modifier.weight(1f)) { Text("مصروف") }
        }
        Spacer(Modifier.height(8.dp))
        HeroMetric("صافي النقد خلال الفترة", t.cashNet, t.cashNet >= 0, "النقد الداخل ناقص الخارج (مبيعات نقدية + تحصيل + إيراد − مصروف − شراء − سداد)")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitMetric("لك (عملاء)", s.owedToYou, true, Modifier.weight(1f))
            SplitMetric("عليك (موردون)", s.youOwe, false, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        ComparisonCard(s.totals.sales, s.prevTotals.sales)
        if (s.agingAlert > 0) {
            Text("تنبيه: ${s.agingAlert} حساب بديون أقدم من 60 يومًا", color = MaterialTheme.colorScheme.error)
        }
        if (s.late.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("أبرز المتأخرين", fontWeight = FontWeight.Bold)
            s.late.take(3).forEachIndexed { i, l -> LateCard(l, i, lateFmt) }
        }
        Spacer(Modifier.height(8.dp))
        Text("مؤشرات الفترة", fontWeight = FontWeight.Bold)
        Metric("مبيعات", t.sales, true)
        Metric("مصروفات", t.expenses, false)
        Metric("ربح تقديري", t.estimatedProfit, t.estimatedProfit >= 0)
        Text("الربح تقديري ولا يشمل تكلفة مخزون غير مسجّل.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        
        Text("تحليل العمليات", fontWeight = FontWeight.Bold)
        DoughnutChart(listOf("مبيعات" to t.sales, "مصروف" to t.expenses, "تحصيل" to t.collections, "سداد" to t.payments))
        
        val salesDocs = s.docs.filter { it.type == "SALE" }.sortedBy { it.occurredAt }
        if (salesDocs.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("حركة المبيعات الأخيرة", fontWeight = FontWeight.Bold)
            val fmt = remember { java.text.SimpleDateFormat("dd/MM", java.util.Locale.US) }
            // Group by day for the chart, take last 6 days
            val grouped = salesDocs.groupBy { fmt.format(java.util.Date(it.occurredAt)) }
                .map { it.key to it.value.sumOf { doc -> doc.amountMinor } }
                .takeLast(6)
            SmoothLineChart(grouped)
        }
    }
}

@Composable
private fun HeroMetric(title: String, minor: Long, positive: Boolean, subtitle: String) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            AnimatedCounter(
                targetValue = minor,
                format = { Money(it).format() },
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SplitMetric(title: String, minor: Long, positive: Boolean, modifier: Modifier = Modifier) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        modifier.padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            AnimatedCounter(
                targetValue = minor,
                format = { Money(it).format() },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}

@Composable
private fun MiniBars(values: List<Pair<String, Long>>) {
    val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
        val w = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = (v.second.toFloat() / max) * size.height * 0.85f
            drawRect(Color(0xFF0F7B5A), Offset(i * w + 8, size.height - h), Size(w - 16, h))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        values.forEach { Text(it.first, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun Metric(title: String, minor: Long, positive: Boolean) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(Money(minor).format(), fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ComparisonCard(current: Long, previous: Long) {
    val diff = current - previous
    val pct = if (previous == 0L) null else (diff * 100) / previous
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("مقارنة المبيعات بالفترة السابقة", style = MaterialTheme.typography.labelMedium)
            Text("الحالية: ${Money(current).format()} — السابقة: ${Money(previous).format()}", fontWeight = FontWeight.Bold)
            val sign = if (diff >= 0) "+" else ""
            val color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Text("$sign${Money(diff).format()}${pct?.let { " ($sign$it%)" } ?: ""}", color = color)
        }
    }
}

@Composable
private fun PartiesScreen(s: UiState, vm: MainViewModel, pad: PaddingValues) {
    var customersTab by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
        Row {
            FilterChip(selected = customersTab, onClick = { customersTab = true }, label = { Text("عملاء") })
            Spacer(Modifier.padding(8.dp))
            FilterChip(selected = !customersTab, onClick = { customersTab = false }, label = { Text("موردون") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { show = true }) { Text("إضافة") }
        }
        val list = if (customersTab) s.customers else s.suppliers
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (customersTab) "لا عملاء بعد" else "لا موردين بعد", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("اضغط زر + لإضافة أول حساب", style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn {
            itemsIndexed(list, key = { _, it -> it.id }) { i, p ->
                PartyCard(p, i) { vm.openParty(p) }
            }
        }
    }
    if (show) PartyDialog(customersTab, vm) { show = false }
}

@Composable
private fun PartyCard(p: PartyEntity, index: Int, onClick: () -> Unit) {
    val balanceColor = partyBalanceColor(p)
    val limitRatio = if (p.creditLimitMinor > 0) (p.cachedBalanceMinor.toFloat() / p.creditLimitMinor).coerceIn(0f, 1.2f) else 0f
    val limitColor = when {
        limitRatio >= 1f -> MaterialTheme.colorScheme.error
        limitRatio >= 0.8f -> Color(0xFFD4A84B)
        else -> MaterialTheme.colorScheme.primary
    }
    AnimatedCard(
        index = index,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).pulseOnClick().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(balanceColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(p.name.take(1), fontWeight = FontWeight.Bold, color = balanceColor, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                if (p.category.isNotBlank() && p.category != "عادي") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                        Spacer(Modifier.width(4.dp))
                        Text(p.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (p.creditLimitMinor > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { limitRatio.coerceAtMost(1f) },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = limitColor,
                            trackColor = limitColor.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${(limitRatio * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = limitColor)
                    }
                }
                if (p.phone.isNotBlank()) Text(p.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money(p.cachedBalanceMinor).format(), fontWeight = FontWeight.Bold, color = balanceColor, fontSize = 16.sp)
                Text(if (p.kind == "CUSTOMER") "لك" else "عليك", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun partyBalanceColor(p: PartyEntity): Color {
    if (p.cachedBalanceMinor == 0L) return MaterialTheme.colorScheme.onSurfaceVariant
    val inFavor = if (p.kind == "CUSTOMER") p.cachedBalanceMinor > 0 else p.cachedBalanceMinor < 0
    return if (inFavor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
}

@Composable
private fun PartyDialog(customer: Boolean, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var open by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (customer) "عميل جديد" else "مورد جديد") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") })
                OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف (اختياري)") })
                OutlinedTextField(open, { open = it }, label = { Text("رصيد افتتاحي") })
                OutlinedTextField(category, { category = it }, label = { Text("تصنيف (اختياري)") })
                OutlinedTextField(limit, { limit = it }, label = { Text("حد الحساب (اختياري)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.addParty(
                    if (customer) PartyKind.CUSTOMER else PartyKind.SUPPLIER,
                    name, phone, open, category, limit
                )
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun PartyDetail(
    s: UiState, vm: MainViewModel,
    onDismiss: () -> Unit, onQuick: (DocType) -> Unit
) {
    val p = s.selectedParty ?: return
    val st = s.partyStats
    var editing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("الرصيد", style = MaterialTheme.typography.labelMedium)
                Text(
                    Money(p.cachedBalanceMinor).format(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = partyBalanceColor(p)
                )
                if (p.category.isNotBlank() && p.category != "عادي") {
                    Text("تصنيف: ${p.category}", style = MaterialTheme.typography.bodySmall)
                }
                if (p.creditLimitMinor > 0) {
                    Text("حد الحساب: ${Money(p.creditLimitMinor).format()}", style = MaterialTheme.typography.bodySmall)
                    if (p.kind == "CUSTOMER" && p.cachedBalanceMinor >= p.creditLimitMinor) {
                        Text("تنبيه: بلغ الرصيد حد الحساب", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (p.phone.isNotBlank()) Text("الهاتف: ${p.phone}", style = MaterialTheme.typography.bodySmall)
                if (st == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("جارٍ التحميل…")
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("مبيعات: ${Money(st.sales).format()}")
                    Text("تحصيل: ${Money(st.collections).format()}")
                    Text("نسبة التحصيل: ${st.collectionRate}%")
                    if (p.kind == "SUPPLIER") {
                        Text("مشتريات: ${Money(st.purchases).format()}")
                        Text("سداد: ${Money(st.payments).format()}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("آخر العمليات", fontWeight = FontWeight.Bold)
                    if (st.docs.isEmpty()) Text("لا عمليات بعد.")
                    st.docs.take(5).forEach { d ->
                        Text("${arabicType(d.type)}  ${Money(d.amountMinor).format()}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("إجراء سريع", fontWeight = FontWeight.Bold)
                Row {
                    if (p.kind == "CUSTOMER") {
                        TextButton(onClick = { onQuick(DocType.SALE) }) { Text("بيع") }
                        TextButton(onClick = { onQuick(DocType.COLLECT) }) { Text("تحصيل") }
                    } else {
                        TextButton(onClick = { onQuick(DocType.PURCHASE) }) { Text("شراء") }
                        TextButton(onClick = { onQuick(DocType.PAY) }) { Text("سداد") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.shareStatement(p) }) { Text("مشاركة الكشف") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { editing = true }) { Text("تعديل") }
                TextButton(onClick = { vm.closeParty(p.id); onDismiss() }) { Text("إغلاق الحساب") }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
    if (editing) PartyEditDialog(p, vm) { editing = false }
}

@Composable
private fun PartyEditDialog(p: PartyEntity, vm: MainViewModel, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf(p.category) }
    var limit by remember {
        mutableStateOf(if (p.creditLimitMinor == 0L) "" else Money(p.creditLimitMinor).toBigDecimal().toPlainString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل ${p.name}") },
        text = {
            Column {
                OutlinedTextField(category, { category = it }, label = { Text("تصنيف") })
                OutlinedTextField(limit, { limit = it }, label = { Text("حد الحساب") })
            }
        },
        confirmButton = {
            Button(onClick = { vm.updatePartyExtra(p.id, category, limit); onDismiss() }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocsScreen(s: UiState, vm: MainViewModel, pad: PaddingValues) {
    var editing by remember { mutableStateOf<DocumentEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val dayFmt = remember { SimpleDateFormat("EEEE dd MMMM", Locale("ar")) }
    val preFiltered = if (typeFilter == null) s.docs else s.docs.filter { it.type == typeFilter }
    val filtered = if (query.isBlank()) preFiltered else preFiltered.filter {
        it.notes.contains(query, true) ||
            it.docNumber.contains(query, true) ||
            arabicType(it.type).contains(query, true)
    }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
        OutlinedTextField(
            query, { query = it },
            label = { Text("بحث") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("الكل") })
            listOf("SALE" to "بيع", "COLLECT" to "تحصيل", "PURCHASE" to "شراء", "EXPENSE" to "مصروف", "PAY" to "سداد").forEach { (code, label) ->
                FilterChip(selected = typeFilter == code, onClick = { typeFilter = if (typeFilter == code) null else code }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
        if (filtered.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("لا عمليات في هذه الفترة", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(if (query.isBlank()) "اضغط زر + لإضافة عملية" else "لا نتائج مطابقة", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Group docs by day
        val grouped = filtered.groupBy { dayFmt.format(Date(it.occurredAt)) }
        grouped.forEach { (day, docs) ->
            item {
                Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            itemsIndexed(docs, key = { _, it -> it.id }) { index, d ->
                val color = docColor(d.type)
                val icon = docIcon(d.type)
                AnimatedCard(index = index, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).pulseOnClick()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(arabicType(d.type), fontWeight = FontWeight.SemiBold)
                            Text(fmt.format(Date(d.occurredAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (d.notes.isNotBlank()) Text(d.notes, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money(d.amountMinor).format(), fontWeight = FontWeight.Bold, color = color)
                            Row {
                                TextButton(onClick = { editing = d }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("تعديل", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = { vm.deleteDoc(d.id) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("أرشفة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
        }
    }
    if (editing != null) DocSheet(s, vm, DocType.SALE, editing) { editing = null }
}

@Composable
private fun docColor(type: String): Color = when (type) {
    "SALE", "COLLECT", "INCOME" -> MaterialTheme.colorScheme.primary
    "PURCHASE", "EXPENSE", "PAY" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun docIcon(type: String): String = when (type) {
    "SALE" -> "🛒"; "COLLECT" -> "💰"; "PURCHASE" -> "📦"; "EXPENSE" -> "💸"
    "PAY" -> "🏦"; "INCOME" -> "📈"; "TRANSFER" -> "🔄"; "OPENING" -> "📋"
    else -> "📄"
}

@Composable
private fun ReportsScreen(s: UiState, vm: MainViewModel, pad: PaddingValues) {
    val t = s.totals
    val lateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    LaunchedEffect(s.shop?.id) { vm.loadAging(); vm.loadLate() }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("تقرير الفترة", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Metric("مبيعات", t.sales, true)
        Metric("مشتريات", t.purchases, false)
        Metric("مصروفات", t.expenses, false)
        Metric("تحصيل", t.collections, true)
        Metric("سداد", t.payments, false)
        Metric("صافي نقدي", t.cashNet, t.cashNet >= 0)
        Metric("ربح تقديري", t.estimatedProfit, t.estimatedProfit >= 0)
        Spacer(Modifier.height(8.dp))
        Text("تحليل الدخل والمصروفات", fontWeight = FontWeight.Bold)
        DoughnutChart(listOf("مبيعات" to t.sales, "مصروف" to t.expenses, "مشتريات" to t.purchases))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.exportPdf() }, modifier = Modifier.weight(1f)) { Text("PDF") }
            Button(onClick = { vm.exportExcel() }, modifier = Modifier.weight(1f)) { Text("Excel") }
        }
        Spacer(Modifier.height(8.dp))
        Text("أعمار ديون العملاء", fontWeight = FontWeight.Bold)
        Text("0–30 | 31–60 | 61–90 | +90", style = MaterialTheme.typography.bodySmall)
        if (s.aging.isEmpty()) Text("لا ديون مستحقة للعرض.", style = MaterialTheme.typography.bodySmall)
        s.aging.forEach { a -> AgingCard(a) }
        Spacer(Modifier.height(8.dp))
        Text("العملاء المتأخرون", fontWeight = FontWeight.Bold)
        if (s.late.isEmpty()) Text("لا متأخرات.", style = MaterialTheme.typography.bodySmall)
        s.late.forEachIndexed { i, l -> LateCard(l, i, lateFmt) }
    }
}

@Composable
private fun AgingCard(a: AgingRow) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(a.party.name, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                AgeCell("0–30", a.b0)
                AgeCell("31–60", a.b31)
                AgeCell("61–90", a.b61)
                AgeCell("+90", a.b90, warn = true)
            }
        }
    }
}

@Composable
private fun AgeCell(label: String, minor: Long, warn: Boolean = false) {
    val color = when {
        minor == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        warn -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
                        Text(Money(minor).format(), fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun LateCard(l: LedgerRepository.LateRow, index: Int, fmt: SimpleDateFormat) {
    val color = if (l.daysLate > 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AnimatedCard(index = index, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).pulseOnClick()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.party.name, fontWeight = FontWeight.Bold)
                val lastTxt = l.lastDate?.let { fmt.format(Date(it)) } ?: "—"
                Text("آخر حركة: $lastTxt", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money(l.balanceMinor).format(), fontWeight = FontWeight.Bold)
                Text("${l.daysLate} يوم", color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MoreScreen(s: UiState, vm: MainViewModel, pad: PaddingValues) {
    var shopName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var closeNotes by remember { mutableStateOf("") }
    var csv by remember { mutableStateOf("") }
    var backupPw by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshBackups() }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {

        Section("المحلات", initiallyExpanded = true) {
            s.shops.forEach { sh ->
                TextButton(onClick = { vm.selectShop(sh) }) {
                    Text(if (s.shop?.id == sh.id) "✓ ${sh.name}" else sh.name)
                }
            }
            OutlinedTextField(shopName, { shopName = it }, label = { Text("اسم محل جديد") })
            Button(onClick = { if (shopName.isNotBlank()) { vm.addShop(shopName); shopName = "" } }) { Text("إنشاء محل") }
        }

        Section("إغلاق اليوم") {
            Text("النقد المتوقع: ${Money(s.totals.cashNet).format()}")
            OutlinedTextField(cash, { cash = it }, label = { Text("النقد الفعلي") })
            OutlinedTextField(closeNotes, { closeNotes = it }, label = { Text("ملاحظات") })
            Button(onClick = { vm.closeDay(cash, closeNotes) }) { Text("إغلاق اليوم") }
        }

        Section("الأمان") {
            OutlinedTextField(pin, { pin = it }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation())
            Row {
                Button(onClick = { vm.savePin(pin) }) { Text("حفظ القفل") }
                TextButton(onClick = { vm.clearPin() }) { Text("إلغاء PIN") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بصمة عند الفتح", modifier = Modifier.weight(1f))
                Switch(s.biometric, { vm.toggleBio(it) })
            }
        }

        Section("النسخ الاحتياطي") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نسخ تلقائي يومي", modifier = Modifier.weight(1f))
                Switch(s.autoBackup, { vm.toggleBackup(it) })
            }
            Button(onClick = { vm.backupNow() }) { Text("نسخة الآن ومشاركة") }
            OutlinedTextField(backupPw, { backupPw = it }, label = { Text("كلمة مرور النسخة (للتشفير)") }, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { vm.backupEncrypted(backupPw) }) { Text("نسخة مشفرة ومشاركة") }
            Button(onClick = { vm.exportCsv() }) { Text("تصدير CSV كامل ومشاركة") }
            Spacer(Modifier.height(8.dp))
            if (s.backups.isEmpty()) Text("لا نسخ بعد.", style = MaterialTheme.typography.bodySmall)
            val backupFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
            s.backups.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        backupFmt.format(Date(f.lastModified())) + if (f.name.endsWith(".enc")) " 🔒" else "",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.restoreBackup(f, backupPw) }) { Text("استعادة") }
                }
            }
        }

        Section("استيراد CSV") {
            Text("الاسم, النوع, المبلغ, نوع العملية", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(csv, { csv = it }, label = { Text("الصق CSV") }, minLines = 4)
            Row {
                Button(onClick = { vm.previewCsv(csv) }) { Text("معاينة") }
                TextButton(onClick = { vm.commitCsv() }, enabled = s.csvPreview.isNotEmpty()) { Text("تنفيذ") }
            }
            s.csvPreview.take(20).forEach { r ->
                Text("سطر ${r.line}: ${r.name} ${r.amount} ${r.error ?: "جاهز"}")
            }
        }

        Section("سجل التدقيق") {
            if (s.audit.isEmpty()) Text("لا سجل بعد.", style = MaterialTheme.typography.bodySmall)
            val auditFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
            s.audit.take(30).forEach { a ->
                Text("${auditFmt.format(Date(a.at))} — ${a.action} ${a.entity} ${a.detail}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Section(title: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (expanded) content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocSheet(
    s: UiState, vm: MainViewModel, initialType: DocType,
    existing: DocumentEntity? = null,
    initialParty: PartyEntity? = null,
    onDismiss: () -> Unit
) {
    val existingType = existing?.type?.let { runCatching { DocType.valueOf(it) }.getOrNull() } ?: initialType
    var type by remember { mutableStateOf(existingType) }
    var amount by remember { mutableStateOf(existing?.let { Money(it.amountMinor).toBigDecimal().toPlainString() } ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var docNo by remember { mutableStateOf(existing?.docNumber ?: "") }
    var credit by remember { mutableStateOf(existing?.paymentMethod == "CREDIT") }
    var occurredAt by remember { mutableStateOf(existing?.occurredAt ?: System.currentTimeMillis()) }
    var party by remember {
        mutableStateOf(
            initialParty
                ?: existing?.partyId?.let { pid -> (s.customers + s.suppliers).firstOrNull { it.id == pid } }
        )
    }
    var partyQuery by remember { mutableStateOf(party?.name ?: "") }
    var showDate by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "عملية جديدة" else "تعديل عملية") },
        text = {
            Column {
                if (existing == null) {
                    FlowTypes(type) { type = it }
                } else {
                    Text("النوع: ${arabicType(type.name)}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") })
                if (existing == null && type in listOf(DocType.SALE, DocType.COLLECT, DocType.PURCHASE, DocType.PAY)) {
                    OutlinedTextField(partyQuery, { partyQuery = it }, label = { Text("الاسم") })
                    val pool = if (type == DocType.SALE || type == DocType.COLLECT) s.customers else s.suppliers
                    pool.filter { it.name.contains(partyQuery, true) }.take(5).forEach {
                        TextButton(onClick = { party = it; partyQuery = it.name }) { Text(it.name) }
                    }
                    val exact = pool.firstOrNull { it.name.equals(partyQuery.trim(), ignoreCase = true) }
                    if (partyQuery.isNotBlank() && exact == null && party == null) {
                        val who = if (type == DocType.SALE || type == DocType.COLLECT) "عميل" else "مورد"
                        Text("سيُنشأ $who جديد عند الحفظ", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (type == DocType.SALE || type == DocType.PURCHASE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("آجل", modifier = Modifier.weight(1f)); Switch(credit, { credit = it })
                    }
                }
                OutlinedTextField(docNo, { docNo = it }, label = { Text("رقم سند (اختياري)") })
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                TextButton(onClick = { showDate = true }) { Text("التاريخ: ${dateFmt.format(Date(occurredAt))}") }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (existing == null) {
                    val pool = if (type == DocType.SALE || type == DocType.COLLECT) s.customers else s.suppliers
                    val matched = pool.firstOrNull { it.name.equals(partyQuery.trim(), ignoreCase = true) }
                    val finalPartyId = party?.id ?: matched?.id
                    val newName = if (finalPartyId == null) partyQuery.trim().takeIf { it.isNotBlank() } else null
                    vm.addDoc(type, amount, finalPartyId, credit, notes, docNo, newPartyName = newName, occurredAt = occurredAt)
                } else {
                    vm.updateDoc(existing.id, amount, notes, docNo, credit, occurredAt)
                }
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { occurredAt = combineWithCurrentTime(it) }
                    showDate = false
                }) { Text("موافق") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("إلغاء") } }
        ) { DatePicker(state = dateState) }
    }
}

private fun combineWithCurrentTime(utcMidnight: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
    cal.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
    cal.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
    return cal.timeInMillis
}

/**
 * منتقي نطاق زمني مخصص (من/إلى) للوحة الرئيسية.
 * يعيد تاريخين كبداية/نهاية اليوم بالتوقيت المحلي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePicker(
    initialFrom: Long?,
    initialTo: Long?,
    onDismiss: () -> Unit,
    onConfirm: (from: Long, to: Long) -> Unit
) {
    val today by remember { mutableStateOf(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis) }
    val startState = rememberDatePickerState(initialSelectedDateMillis = initialFrom ?: today)
    val endState = rememberDatePickerState(initialSelectedDateMillis = initialTo ?: System.currentTimeMillis())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نطاق الفترة المخصصة") },
        text = {
            Column {
                Text("من", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                DatePicker(state = startState, showModeToggle = false)
                Spacer(Modifier.height(8.dp))
                Text("إلى", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                DatePicker(state = endState, showModeToggle = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val from = startState.selectedDateMillis ?: return@TextButton
                val to = endState.selectedDateMillis ?: return@TextButton
                val (start, end) = if (from <= to) from to to else to to from
                onConfirm(combineWithCurrentTime(start), endOfDay(combineWithCurrentTime(end)))
            }) { Text("تطبيق") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** نهاية اليوم المحلي لتاريخ مختار (23:59:59.999). */
private fun endOfDay(localMillis: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = localMillis }
    c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
    return c.timeInMillis
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowTypes(sel: DocType, on: (DocType) -> Unit) {
    val map = listOf(
        DocType.SALE to "بيع", DocType.COLLECT to "تحصيل", DocType.PURCHASE to "شراء",
        DocType.PAY to "سداد", DocType.EXPENSE to "مصروف", DocType.INCOME to "إيراد",
        DocType.TRANSFER to "تحويل"
    )
    FlowRow {
        map.forEach { (t, l) ->
            FilterChip(selected = sel == t, onClick = { on(t) }, label = { Text(l) })
        }
    }
}

@Composable
private fun LockDialog(s: UiState, vm: MainViewModel, activity: FragmentActivity?) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("قفل دفتري") },
        text = {
            Column {
                OutlinedTextField(pin, { pin = it }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation())
                if (s.biometric && activity != null) {
                    TextButton(onClick = {
                        AppLock.prompt(activity, onOk = { vm.unlockOk() }, onFail = {})
                    }) { Text("فتح بالبصمة") }
                }
            }
        },
        confirmButton = { Button(onClick = { vm.unlock(pin) }) { Text("فتح") } }
    )
}

private fun arabicType(t: String) = when (t) {
    "SALE" -> "بيع"; "PURCHASE" -> "شراء"; "EXPENSE" -> "مصروف"; "INCOME" -> "إيراد"
    "COLLECT" -> "تحصيل"; "PAY" -> "سداد"; "TRANSFER" -> "تحويل"; "OPENING" -> "افتتاحي"
    else -> t
}
