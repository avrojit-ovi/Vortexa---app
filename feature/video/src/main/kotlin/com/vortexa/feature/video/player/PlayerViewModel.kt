package com.vortexa.feature.video.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.common.preferences.DecoderMode
import com.vortexa.core.common.preferences.SettingsRepository
import com.vortexa.core.common.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = settings.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    fun setDecoderMode(mode: DecoderMode) {
        viewModelScope.launch { settings.setDecoderMode(mode) }
    }

    fun setKidsLockPin(pin: String) {
        viewModelScope.launch { settings.setKidsLockPin(pin) }
    }
}
