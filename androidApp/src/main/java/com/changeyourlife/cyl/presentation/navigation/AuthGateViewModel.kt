package com.changeyourlife.cyl.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.domain.repository.AuthRepository
import com.changeyourlife.cyl.domain.repository.AuthState
import com.changeyourlife.cyl.data.share.IncomingShareCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val incomingShareCoordinator: IncomingShareCoordinator,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading,
    )

    val pendingShareDraftId: StateFlow<String?> = incomingShareCoordinator.pendingDraftId

    fun consumeShareNavigation(draftId: String) {
        incomingShareCoordinator.consumeNavigation(draftId)
    }
}
