package com.daftari.ledger.ui

/**
 * إغلاق كل الشاشات الثانوية التي تُعرض فوق التبويبات.
 *
 * شريط التنقّل السفلي يغيّر التبويب المختار فقط، ومحتوى الشاشة كان يفحص
 * `screenOpen` قبل التبويب، فالبقاء داخل دفتر الحسابات مثلًا كان يجعل الضغط على
 * «العمليات» أو «بيع» بلا أثر. لذلك يُغلق هذا الدالة أي شاشة ثانوية قبل التبويب.
 */
internal fun MainViewModel.closeSecondaryScreens() {
    val current = state.value
    if (current.book.screenOpen) closeAccountsBook()
    if (current.inventory.screenOpen) closeInventory()
    if (current.googleBackup.screenOpen) closeGoogleBackup()
    if (current.employees.screenOpen) closeEmployees()
}
