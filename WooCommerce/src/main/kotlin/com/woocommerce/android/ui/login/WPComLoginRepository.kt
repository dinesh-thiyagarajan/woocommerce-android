package com.woocommerce.android.ui.login

import com.woocommerce.android.OnChangedException
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.dispatchAndAwait
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.AccountActionBuilder
import org.wordpress.android.fluxc.generated.AuthenticationActionBuilder
import org.wordpress.android.fluxc.store.AccountStore.AuthenticatePayload
import org.wordpress.android.fluxc.store.AccountStore.AuthenticationError
import org.wordpress.android.fluxc.store.AccountStore.AuthenticationErrorType.NEEDS_2FA
import org.wordpress.android.fluxc.store.AccountStore.FetchAuthOptionsPayload
import org.wordpress.android.fluxc.store.AccountStore.OnAuthOptionsFetched
import org.wordpress.android.fluxc.store.AccountStore.OnAuthenticationChanged
import org.wordpress.android.login.AuthOptions
import javax.inject.Inject

class WPComLoginRepository @Inject constructor(
    private val dispatcher: Dispatcher
) {
    suspend fun fetchAuthOptions(emailOrUsername: String): Result<AuthOptions> {
        val action = AccountActionBuilder.newFetchAuthOptionsAction(
            FetchAuthOptionsPayload(emailOrUsername)
        )
        val event: OnAuthOptionsFetched = dispatcher.dispatchAndAwait(action)

        return when {
            event.isError -> Result.failure(OnChangedException(event.error))
            else -> Result.success(
                AuthOptions(
                    isPasswordless = event.isPasswordless,
                    isEmailVerified = event.isEmailVerified
                )
            )
        }
    }

    suspend fun login(emailOrUsername: String, password: String): Result<Unit> {
        WooLog.i(
            WooLog.T.LOGIN,
            "Signing in using WPCom email or username: $emailOrUsername"
        )
        return submitAuthRequest(emailOrUsername, password, null, false)
    }

    suspend fun submitTwoStepCode(emailOrUsername: String, password: String, twoStepCode: String): Result<Unit> {
        WooLog.i(WooLog.T.LOGIN, "Sumbitting 2FA verification code")

        return submitAuthRequest(emailOrUsername, password, twoStepCode, false)
    }

    suspend fun requestTwoStepSMS(emailOrUsername: String, password: String): Result<SMSRequestResult> {
        WooLog.i(WooLog.T.LOGIN, "Submitting 2FA verification code")

        return submitAuthRequest(emailOrUsername, password, null, true)
            .map {
                // If we get a successful response, then this means the user was successfully signed in
                // This can happen only if 2FA was disabled in the account before sending the request
                SMSRequestResult.UserSignedIn
            }
            .recoverCatching {
                if (((it as? OnChangedException)?.error as? AuthenticationError)?.type == NEEDS_2FA) {
                    SMSRequestResult.SMSRequested
                } else {
                    throw it
                }
            }
    }

    private suspend fun submitAuthRequest(
        emailOrUsername: String,
        password: String,
        twoStepCode: String?,
        shouldRequestTwoStepCode: Boolean
    ): Result<Unit> {
        val payload = AuthenticatePayload(emailOrUsername, password).apply {
            this.twoStepCode = twoStepCode
            this.shouldSendTwoStepSms = shouldRequestTwoStepCode
        }
        val event: OnAuthenticationChanged =
            dispatcher.dispatchAndAwait(AuthenticationActionBuilder.newAuthenticateAction(payload))

        return if (event.isError) {
            WooLog.w(
                WooLog.T.LOGIN,
                "Authentication request failed: " + event.error.type + " - " + event.error.message
            )
            Result.failure(OnChangedException(event.error))
        } else {
            WooLog.i(WooLog.T.LOGIN, "Authentication Succeeded for user ${event.userName}")
            Result.success(Unit)
        }
    }

    enum class SMSRequestResult {
        UserSignedIn, SMSRequested
    }
}
