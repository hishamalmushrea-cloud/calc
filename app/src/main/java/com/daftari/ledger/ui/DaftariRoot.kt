package com.daftari.ledger.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind
import com.daftari.ledger.security.AppLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftariRoot(s: UiState, vm: MainViewModel, activity: FragmentActivity? = null) {
    var tab by remember { mutableIntStateOf(0) }
    var addType by remember { mutableStateOf<DocType?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) {
        s.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("دفتري", fontWeight = FontWeight.Bold)
                    Text(s.shop?.name ?: "—", style = MaterialTheme.typography.bodySmall)
                }
            })
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (tab <= 3) FloatingActionButton(onClick = { addType = DocType.SALE }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة")
            }
        },
        bottomBar = {
            NavigationBar {
                val items = listOf("الرئيسية", "الحسابات", "العمليات", "التقارير", "المزيد")
                val icons = listOf(Icons.Default.Home, Icons.Default.People, Icons.Default.ReceiptLong, Icons.Default.Assessment, Icons.Default.MoreHoriz)
                items.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], null) },
                        label = { Text(label) }
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
    if (addType != null) AddSheet(s, vm, addType!!) { addType = null }
    if (s.selectedParty != null) PartyDetail(s, vm) { vm.closePartyDialog() }
    if (s.locked) LockDialog(s, vm, activity)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(s: UiState, vm: MainViewModel, pad: PaddingValues, onQuick: (DocType) -> Unit) {
    val t = s.totals
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.filter { it != Period.CUSTOM }.forEach { p ->
                FilterChip(selected = s.period == p, onClick = { vm.setPeriod(p) }, label = {
                    Text(
                        when (p) {
                            Period.TODAY -> "اليوم"; Period.YESTERDAY -> "أمس"; Period.WEEK -> "الأسبوع"
                            Period.MONTH -> "الشهر"; Period.YEAR -> "السنة"; else -> ""
                        }
                    )
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onQuick(DocType.SALE) }, modifier = Modifier.weight(1f)) { Text("بيع سريع") }
            Button(onClick = { onQuick(DocType.COLLECT) }, modifier = Modifier.weight(1f)) { Text("تحصيل") }
            Button(onClick = { onQuick(DocType.EXPENSE) }, modifier = Modifier.weight(1f)) { Text("مصروف") }
        }
        Spacer(Modifier.height(8.dp))
        ComparisonCard(s.totals.sales, s.prevTotals.sales)
        if (s.agingAlert > 0) {
            Text("تنبيه: ${s.agingAlert} حساب بديون أقدم من 60 يومًا", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Metric("لك (عملاء)", s.owedToYou, true)
        Metric("عليك (موردون)", s.youOwe, false)
        Metric("مبيعات الفترة", t.sales, true)
        Metric("مصروفات الفترة", t.expenses, false)
        Metric("صافي نقدي", t.cashNet, t.cashNet >= 0)
        Metric("ربح تقديري", t.estimatedProfit, t.estimatedProfit >= 0)
        Text("الربح تقديري ولا يشمل تكلفة مخزون غير مسجّل.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text("حركة الفترة", fontWeight = FontWeight.Bold)
        MiniBars(listOf("مبيعات" to t.sales, "مصروف" to t.expenses, "تحصيل" to t.collections, "سداد" to t.payments))
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
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (positive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        )
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = Modifier.weight(1f))
            Text(Money(minor).format(), fontWeight = FontWeight.Bold)
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
        if (list.isEmpty()) Text("لا توجد حسابات بعد. أضف اسمًا للبدء.", modifier = Modifier.padding(24.dp))
        LazyColumn {
            items(list, key = { it.id }) { p ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { vm.openParty(p) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        val label = if (p.kind == "CUSTOMER") "لك" else "عليك"
                        Text("$label: ${Money(p.cachedBalanceMinor).format()}")
                        if (p.phone.isNotBlank()) Text(p.phone, style = MaterialTheme.typography.bodySmall)
                        Text("اضغط لعرض الكشف", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (show) PartyDialog(customersTab, vm) { show = false }
}

@Composable
private fun PartyDialog(customer: Boolean, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var open by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (customer) "عميل جديد" else "مورد جديد") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") })
                OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف (اختياري)") })
                OutlinedTextField(open, { open = it }, label = { Text("رصيد افتتاحي") })
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.addParty(if (customer) PartyKind.CUSTOMER else PartyKind.SUPPLIER, name, phone, open)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun PartyDetail(s: UiState, vm: MainViewModel, onDismiss: () -> Unit) {
    val p = s.selectedParty ?: return
    val st = s.partyStats
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("الرصيد: ${Money(p.cachedBalanceMinor).format()}", fontWeight = FontWeight.Bold)
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
            }
        },
        confirmButton = {
            Button(onClick = { vm.shareStatement(p) }) { Text("مشاركة الكشف") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.closeParty(p.id); onDismiss() }) { Text("إغلاق الحساب") }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
}

@Composable
private fun DocsScreen(s: UiState, vm: MainViewModel, pad: PaddingValues) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
        if (s.docs.isEmpty()) item { Text("لا عمليات في هذه الفترة.") }
        items(s.docs, key = { it.id }) { d ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(arabicType(d.type), fontWeight = FontWeight.Bold)
                    Text(Money(d.amountMinor).format())
                    Text(fmt.format(Date(d.occurredAt)), style = MaterialTheme.typography.bodySmall)
                    if (d.notes.isNotBlank()) Text(d.notes)
                    TextButton(onClick = { vm.deleteDoc(d.id) }) { Text("أرشفة") }
                }
            }
        }
    }
}

@Composable
private fun ReportsScreen(s: UiState, vm: MainViewModel, pad: PaddingValues) {
    val t = s.totals
    LaunchedEffect(s.shop?.id) { vm.loadAging() }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("تقرير الفترة", style = MaterialTheme.typography.titleLarge)
        Text("مبيعات: ${Money(t.sales).format()}")
        Text("مشتريات: ${Money(t.purchases).format()}")
        Text("مصروفات: ${Money(t.expenses).format()}")
        Text("تحصيل: ${Money(t.collections).format()}")
        Text("سداد: ${Money(t.payments).format()}")
        Text("صافي نقدي: ${Money(t.cashNet).format()}")
        Text("ربح تقديري: ${Money(t.estimatedProfit).format()}")
        MiniBars(listOf("مبيعات" to t.sales, "مصروف" to t.expenses, "مشتريات" to t.purchases))
        Button(onClick = { vm.exportPdf() }, modifier = Modifier.padding(vertical = 8.dp)) { Text("تصدير PDF ومشاركة") }
        Text("أعمار ديون العملاء", fontWeight = FontWeight.Bold)
        Text("0–30 | 31–60 | 61–90 | +90")
        if (s.aging.isEmpty()) Text("لا ديون مستحقة للعرض.")
        s.aging.forEach { a ->
            Text("${a.party.name}: ${Money(a.b0).format()} | ${Money(a.b31).format()} | ${Money(a.b61).format()} | ${Money(a.b90).format()}")
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
    LaunchedEffect(Unit) { vm.refreshBackups() }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("المحلات", fontWeight = FontWeight.Bold)
        s.shops.forEach { sh ->
            TextButton(onClick = { vm.selectShop(sh) }) {
                Text(if (s.shop?.id == sh.id) "✓ ${sh.name}" else sh.name)
            }
        }
        OutlinedTextField(shopName, { shopName = it }, label = { Text("اسم محل جديد") })
        Button(onClick = { if (shopName.isNotBlank()) { vm.addShop(shopName); shopName = "" } }) { Text("إنشاء محل") }
        Spacer(Modifier.height(12.dp))
        Text("إغلاق اليوم", fontWeight = FontWeight.Bold)
        Text("النقد المتوقع: ${Money(s.totals.cashNet).format()}")
        OutlinedTextField(cash, { cash = it }, label = { Text("النقد الفعلي") })
        OutlinedTextField(closeNotes, { closeNotes = it }, label = { Text("ملاحظات") })
        Button(onClick = { vm.closeDay(cash, closeNotes) }) { Text("إغلاق اليوم") }
        Spacer(Modifier.height(12.dp))
        Text("الأمان", fontWeight = FontWeight.Bold)
        OutlinedTextField(pin, { pin = it }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation())
        Row {
            Button(onClick = { vm.savePin(pin) }) { Text("حفظ القفل") }
            TextButton(onClick = { vm.clearPin() }) { Text("إلغاء PIN") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("بصمة عند الفتح", modifier = Modifier.weight(1f))
            Switch(s.biometric, { vm.toggleBio(it) })
        }
        Spacer(Modifier.height(12.dp))
        Text("النسخ الاحتياطي", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("نسخ تلقائي يومي", modifier = Modifier.weight(1f))
            Switch(s.autoBackup, { vm.toggleBackup(it) })
        }
        Button(onClick = { vm.backupNow() }) { Text("نسخة الآن ومشاركة") }
        Button(onClick = { vm.exportCsv() }) { Text("تصدير CSV كامل ومشاركة") }
        Spacer(Modifier.height(8.dp))
        Text("النسخ المحفوظة", fontWeight = FontWeight.Bold)
        if (s.backups.isEmpty()) Text("لا نسخ بعد.")
        val backupFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
        s.backups.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(backupFmt.format(Date(f.lastModified())), modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.restoreBackup(f) }) { Text("استعادة") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("استيراد CSV", fontWeight = FontWeight.Bold)
        Text("الاسم, النوع, المبلغ, نوع العملية", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(csv, { csv = it }, label = { Text("الصق CSV") }, minLines = 4)
        Row {
            Button(onClick = { vm.previewCsv(csv) }) { Text("معاينة") }
            TextButton(onClick = { vm.commitCsv() }, enabled = s.csvPreview.isNotEmpty()) { Text("تنفيذ") }
        }
        s.csvPreview.take(20).forEach { r ->
            Text("سطر ${r.line}: ${r.name} ${r.amount} ${r.error ?: "جاهز"}")
        }
        Spacer(Modifier.height(12.dp))
        Text("سجل التدقيق", fontWeight = FontWeight.Bold)
        if (s.audit.isEmpty()) Text("لا سجل بعد.")
        val auditFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
        s.audit.take(30).forEach { a ->
            Text("${auditFmt.format(Date(a.at))} — ${a.action} ${a.entity} ${a.detail}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddSheet(s: UiState, vm: MainViewModel, initialType: DocType, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(initialType) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var docNo by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf(false) }
    var party by remember { mutableStateOf<PartyEntity?>(null) }
    var partyQuery by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عملية جديدة") },
        text = {
            Column {
                FlowTypes(type) { type = it }
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") })
                if (type in listOf(DocType.SALE, DocType.COLLECT, DocType.PURCHASE, DocType.PAY)) {
                    OutlinedTextField(partyQuery, { partyQuery = it }, label = { Text("الاسم") })
                    val pool = if (type == DocType.SALE || type == DocType.COLLECT) s.customers else s.suppliers
                    pool.filter { it.name.contains(partyQuery, true) }.take(5).forEach {
                        TextButton(onClick = { party = it; partyQuery = it.name }) { Text(it.name) }
                    }
                    if (type == DocType.SALE || type == DocType.PURCHASE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("آجل", modifier = Modifier.weight(1f)); Switch(credit, { credit = it })
                        }
                    }
                }
                OutlinedTextField(docNo, { docNo = it }, label = { Text("رقم سند (اختياري)") })
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.addDoc(type, amount, party?.id, credit, notes, docNo)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
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
