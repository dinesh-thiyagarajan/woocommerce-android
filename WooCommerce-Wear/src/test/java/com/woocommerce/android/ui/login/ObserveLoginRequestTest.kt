package com.woocommerce.android.ui.login

import com.woocommerce.android.BaseUnitTest
import com.woocommerce.android.ui.login.ObserveLoginRequest.LoginRequestState
import com.woocommerce.android.ui.login.ObserveLoginRequest.LoginRequestState.Failed
import com.woocommerce.android.ui.login.ObserveLoginRequest.LoginRequestState.Logged
import com.woocommerce.android.ui.login.ObserveLoginRequest.LoginRequestState.Waiting
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ObserveLoginRequestTest : BaseUnitTest() {

    private val loginRepository: LoginRepository = mock()

    @Test
    fun `when user is logged in, return Logged state`() = testBlocking {
        // Given
        val events = mutableListOf<LoginRequestState>()
        whenever(loginRepository.isUserLoggedIn).thenReturn(flowOf(true))

        // When
        ObserveLoginRequest(loginRepository).invoke()
            .onEach { events.add(it) }
            .launchIn(this)

        // Then
        assertThat(events).isEqualTo(listOf(Logged))
    }

    @Test
    fun `when user is not logged in and waiting timeout, return Failed state`() = testBlocking {
        // Given
        val events = mutableListOf<LoginRequestState>()
        whenever(loginRepository.isUserLoggedIn).thenReturn(flowOf(false))

        // When
        ObserveLoginRequest(loginRepository).invoke()
            .onEach { events.add(it) }
            .launchIn(this)

        // Then
        advanceUntilIdle()
        assertThat(events).isEqualTo(listOf(Waiting, Failed))
    }

    @Test
    fun `when user is not logged in and waiting, return Waiting state`() = testBlocking {
        // Given
        val events = mutableListOf<LoginRequestState>()
        whenever(loginRepository.isUserLoggedIn).thenReturn(flowOf(false))

        // When
        ObserveLoginRequest(loginRepository).invoke()
            .onEach { events.add(it) }
            .launchIn(this)

        // Then
        assertThat(events).isEqualTo(listOf(Waiting))
    }
}
