package com.daftari.ledger.ui

import com.daftari.ledger.data.ItemEntity
import com.daftari.ledger.domain.DocType

data class InventoryUiState(
    val screenOpen: Boolean = false,
    val items: List<ItemEntity> = emptyList(),
    val lowStockCount: Int = 0,
    val invoiceOpen: Boolean = false,
    val invoiceType: DocType = DocType.SALE
)

data class ItemDraft(
    val id: Long? = null,
    val name: String,
    val sku: String = "",
    val unit: String = "",
    val sellPrice: String,
    val costPrice: String = "",
    val qty: String = "0",
    val reorderQty: String = "0",
    val trackStock: Boolean = true
)

data class InvoiceLineDraft(
    val itemId: Long,
    val itemName: String,
    val qty: String,
    val unitPrice: String
)

data class InvoiceDraft(
    val type: DocType,
    val partyId: Long?,
    val newPartyName: String?,
    val credit: Boolean,
    val notes: String,
    val documentNumber: String,
    val dueAt: Long?,
    val lines: List<InvoiceLineDraft>
)
