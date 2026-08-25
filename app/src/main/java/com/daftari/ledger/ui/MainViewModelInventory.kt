package com.daftari.ledger.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.daftari.ledger.R
import com.daftari.ledger.data.InvoiceLineInput
import com.daftari.ledger.data.LedgerException
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.InventoryMath
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind
import com.daftari.ledger.domain.StaffPermission
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.openInventory() {
    if (!state.value.can(StaffPermission.VIEW_ACCOUNTS) && !state.value.can(StaffPermission.RECORD_SALE)) return
    mutableState.update { it.copy(inventory = it.inventory.copy(screenOpen = true)) }
}

internal fun MainViewModel.closeInventory() {
    mutableState.update { it.copy(inventory = it.inventory.copy(screenOpen = false, invoiceOpen = false)) }
}

internal fun MainViewModel.saveItem(draft: ItemDraft) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        currentActorId()?.let { staff.requirePermission(it, StaffPermission.MANAGE_SETTINGS) }
        val sell = Money.fromMajor(draft.sellPrice, shop.fractionDigits) ?: return@launch message(R.string.msg_invalid_amount)
        val cost = if (draft.costPrice.isBlank()) Money(0, shop.fractionDigits)
        else Money.fromMajor(draft.costPrice, shop.fractionDigits) ?: return@launch message(R.string.msg_invalid_amount)
        val qty = InventoryMath.parseQty(draft.qty) ?: return@launch message(R.string.msg_invalid_qty)
        val reorder = InventoryMath.parseQty(draft.reorderQty) ?: 0L
        repo.upsertItem(
            shopId = shop.id,
            id = draft.id,
            name = draft.name,
            sku = draft.sku,
            unit = draft.unit,
            sellPriceMinor = sell.minor,
            costPriceMinor = cost.minor,
            qtyMilli = qty,
            reorderQtyMilli = reorder,
            trackStock = draft.trackStock
        )
        message(R.string.msg_item_saved)
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.archiveItem(id: Long) = viewModelScope.launch {
    try {
        currentActorId()?.let { staff.requirePermission(it, StaffPermission.MANAGE_SETTINGS) }
        repo.archiveItem(id)
        message(R.string.msg_item_archived)
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.setInvoiceSheet(open: Boolean, type: DocType = DocType.SALE) {
    mutableState.update { it.copy(inventory = it.inventory.copy(invoiceOpen = open, invoiceType = type)) }
}

internal fun MainViewModel.saveInvoice(draft: InvoiceDraft) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        val actorId = currentActorId()
        if (actorId != null) {
            staff.requirePermission(actorId, if (draft.type == DocType.SALE) StaffPermission.RECORD_SALE else StaffPermission.VIEW_ACCOUNTS)
        }
        val lines = draft.lines.map { line ->
            val qty = InventoryMath.parseQty(line.qty) ?: throw LedgerException(getApplication<Application>().getString(R.string.msg_invalid_qty))
            val price = Money.fromMajor(line.unitPrice, shop.fractionDigits)
                ?: throw LedgerException(getApplication<Application>().getString(R.string.msg_invalid_amount))
            InvoiceLineInput(line.itemId, qty, price.minor)
        }
        var partyId = draft.partyId
        if (partyId == null && !draft.newPartyName.isNullOrBlank()) {
            val kind = if (draft.type == DocType.SALE) PartyKind.CUSTOMER else PartyKind.SUPPLIER
            partyId = repo.addParty(shop.id, kind, draft.newPartyName.trim())
        }
        repo.postInvoice(
            shopId = shop.id,
            type = draft.type,
            lines = lines,
            occurredAt = System.currentTimeMillis(),
            partyId = partyId,
            paymentMethod = if (draft.credit) "CREDIT" else "CASH",
            notes = draft.notes,
            documentNumber = draft.documentNumber,
            dueAt = draft.dueAt,
            actorEmployeeId = actorId
        )
        refreshAll()
        mutableState.update { it.copy(inventory = it.inventory.copy(invoiceOpen = false), message = text(R.string.msg_invoice_saved)) }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}
