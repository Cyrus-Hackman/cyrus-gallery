package com.cyrus.gallery.data.auth

import android.app.Activity
import android.content.Context
import com.cyrus.gallery.R
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface AuthState {
    data class SignedOut(val message: String? = null) : AuthState
    data object Authenticating : AuthState
    data class SignedIn(val displayName: String, val accountId: String) : AuthState
}

interface OneDriveAuthManager {
    val authState: StateFlow<AuthState>
    suspend fun restoreSession(): Result<String>
    suspend fun signIn(activity: Activity): Result<String>
    suspend fun signOut(): Result<Unit>
    suspend fun getAccessToken(): String?
}

@Singleton
class OneDriveAuthManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OneDriveAuthManager {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var clientApp: ISingleAccountPublicClientApplication? = null
    private var currentAccount: IAccount? = null

    // Note: Do not include 'offline_access' in MSAL scopes array; MSAL manages refresh tokens internally.
    // For personal accounts, Files.Read provides access to OneDrive files.
    private val scopes = arrayOf("User.Read", "Files.Read")

    private suspend fun ensureClient(): Result<ISingleAccountPublicClientApplication> {
        clientApp?.let { return Result.success(it) }

        return suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                R.raw.auth_config_single_account,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        clientApp = application
                        continuation.resume(Result.success(application))
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resume(Result.failure(exception))
                    }
                },
            )
        }
    }

    override suspend fun restoreSession(): Result<String> {
        _authState.value = AuthState.Authenticating
        val appResult = ensureClient()
        if (appResult.isFailure) {
            val err = appResult.exceptionOrNull()?.message ?: "Client initialization failed"
            _authState.value = AuthState.SignedOut(err)
            return Result.failure(appResult.exceptionOrNull() ?: Exception(err))
        }

        val app = appResult.getOrThrow()
        return suspendCancellableCoroutine { continuation ->
            app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    if (activeAccount == null) {
                        _authState.value = AuthState.SignedOut()
                        continuation.resume(Result.failure(IllegalStateException("No active account")))
                        return
                    }
                    currentAccount = activeAccount
                    app.acquireTokenSilentAsync(
                        scopes,
                        activeAccount.authority,
                        object : SilentAuthenticationCallback {
                            override fun onSuccess(result: IAuthenticationResult) {
                                currentAccount = result.account
                                _authState.value = AuthState.SignedIn(
                                    displayName = result.account.username,
                                    accountId = result.account.id,
                                )
                                continuation.resume(Result.success(result.accessToken))
                            }

                            override fun onError(exception: MsalException) {
                                _authState.value = AuthState.SignedOut(exception.message)
                                continuation.resume(Result.failure(exception))
                            }
                        },
                    )
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    if (currentAccount == null) {
                        _authState.value = AuthState.SignedOut()
                    }
                }

                override fun onError(exception: MsalException) {
                    _authState.value = AuthState.SignedOut(exception.message)
                    continuation.resume(Result.failure(exception))
                }
            })
        }
    }

    override suspend fun signIn(activity: Activity): Result<String> {
        _authState.value = AuthState.Authenticating
        val appResult = ensureClient()
        if (appResult.isFailure) {
            val err = appResult.exceptionOrNull()?.message ?: "Initialization error"
            _authState.value = AuthState.SignedOut(err)
            return Result.failure(appResult.exceptionOrNull() ?: Exception(err))
        }

        val app = appResult.getOrThrow()
        return suspendCancellableCoroutine { continuation ->
            app.signIn(activity, "", scopes, object : AuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    currentAccount = result.account
                    _authState.value = AuthState.SignedIn(
                        displayName = result.account.username,
                        accountId = result.account.id,
                    )
                    continuation.resume(Result.success(result.accessToken))
                }

                override fun onError(exception: MsalException) {
                    _authState.value = AuthState.SignedOut(exception.message)
                    continuation.resume(Result.failure(exception))
                }

                override fun onCancel() {
                    _authState.value = AuthState.SignedOut("Sign-in cancelled")
                    continuation.resume(Result.failure(Exception("Cancelled")))
                }
            })
        }
    }

    override suspend fun signOut(): Result<Unit> {
        val app = clientApp ?: return Result.success(Unit)
        return suspendCancellableCoroutine { continuation ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    currentAccount = null
                    _authState.value = AuthState.SignedOut()
                    continuation.resume(Result.success(Unit))
                }

                override fun onError(exception: MsalException) {
                    continuation.resume(Result.failure(exception))
                }
            })
        }
    }

    override suspend fun getAccessToken(): String? {
        val app = clientApp ?: ensureClient().getOrNull() ?: return null
        val account = currentAccount ?: return null

        return suspendCancellableCoroutine { continuation ->
            app.acquireTokenSilentAsync(
                scopes,
                account.authority,
                object : SilentAuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        currentAccount = result.account
                        continuation.resume(result.accessToken)
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resume(null)
                    }
                },
            )
        }
    }
}
