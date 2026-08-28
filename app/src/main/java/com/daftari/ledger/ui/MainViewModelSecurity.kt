package com.daftari.ledger.ui

import androidx.lifecycle.viewModelScope
import com.daftari.ledger.R
import com.daftari.ledger.backup.AutoBackupWorker
import com.daftari.ledger.data.LedgerException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.unlock(pin: String) = viewModelScope.launch {
    val now = System.currentTimeMillis()
    val settings = repo.settings.get()
    val lockedUntil = settings?.pinLockedUntil ?: 0L
    if (lockedUntil > now) {
        message(R.string.msg_pin_locked, ((lockedUntil - now + 999) / 1000).toInt())
        return@launch
    }
    if (repo.pinOk(pin)) {
        repo.updatePinProtection(0, 0)
        mutableState.update { it.copy(locked = false, pinLockedUntil = 0, message = text(R.string.msg_unlocked)) }
    } else {
        val attempts = (settings?.failedPinAttempts ?: 0) + 1
        if (attempts >= MAX_PIN_ATTEMPTS) {
            val until = now + PIN_LOCK_MILLIS
            repo.updatePinProtection(0, until)
            mutableState.update { it.copy(pinLockedUntil = until, message = text(R.string.msg_pin_locked, 30)) }
        } else {
            repo.updatePinProtection(attempts, 0)
            message(R.string.msg_wrong_pin_attempts, MAX_PIN_ATTEMPTS - attempts)
        }
    }
}

internal fun MainViewModel.savePin(pin: String) = viewModelScope.launch {
    if (pin.length < 4) return@launch message(R.string.msg_pin_too_short)
    repo.setPin(pin)
    repo.updatePinProtection(0, 0)
    mutableState.update { it.copy(hasPin = true, pinLockedUntil = 0, message = text(R.string.msg_pin_saved)) }
}

internal fun MainViewModel.clearPin() = viewModelScope.launch {
    repo.setPin(null)
    repo.updatePinProtection(0, 0)
    mutableState.update { it.copy(hasPin = false, locked = false, pinLockedUntil = 0, message = text(R.string.msg_pin_removed)) }
}

internal fun MainViewModel.toggleBackup(enabled: Boolean) = viewModelScope.launch {
    repo.setAutoBackup(enabled)
    mutableState.update { it.copy(autoBackup = enabled) }
    AutoBackupWorker.schedule(getApplication(), enabled)
}

internal fun MainViewModel.toggleBiometric(enabled: Boolean) = viewModelScope.launch {
    repo.setBiometric(enabled)
    mutableState.update { it.copy(biometric = enabled) }
}

internal fun MainViewModel.togglePrivacy(enabled: Boolean) = viewModelScope.launch {
    repo.setPrivacyMode(enabled)
    mutableState.update { it.copy(hideBalances = enabled) }
}

internal fun MainViewModel.toggleLatinDigits(enabled: Boolean) = viewModelScope.launch {
    repo.setLatinDigits(enabled)
    mutableState.update { it.copy(latinDigits = enabled) }
}

internal fun MainViewModel.updateCurrency(code: String) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        repo.updateShopCurrency(shop.id, code, actorEmployeeId = currentActorId())
        val updated = repo.shops.get(shop.id)
        mutableState.update { it.copy(shop = updated ?: shop, message = text(R.string.msg_currency_saved)) }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.addCategory(kind: String, name: String) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        repo.addCategory(shop.id, kind, name, actorEmployeeId = currentActorId())
        message(R.string.msg_category_added)
    } catch (error: Exception) {
        dynamicError(error)
    }
}

private const val MAX_PIN_ATTEMPTS = 5
private const val PIN_LOCK_MILLIS = 30_000L
