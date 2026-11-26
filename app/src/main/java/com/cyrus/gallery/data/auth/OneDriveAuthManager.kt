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
import com.microsoft.identity.client.exception.MsalUiRequiredException
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
    data class Offline(val displayName: String, val accountId: String, val message: String? = null) : AuthState
}

interface OneDriveAuthManager {
    val authState: StateFlow<AuthState>
    val cachedAccessToken: String?
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

    @Volatile
    override var cachedAccessToken: String? = null
        private set

    @Volatile
    private var clientApp: ISingleAccountPublicClientApplication? = null
    @Volatile
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
                        if (continuation.isActive) {
                            continuation.resume(Result.success(application))
                        }
                    }

                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(exception))
                        }
                    }
                },
            )
        }
    }

    private suspend fun loadCurrentAccount(app: ISingleAccountPublicClientApplication): Result<IAccount?> =
        suspendCancellableCoroutine { continuation ->
            app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(activeAccount))
                    }
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    if (currentAccount == null) {
                        this@OneDriveAuthManagerImpl.currentAccount = null
                        cachedAccessToken = null
                        _authState.value = AuthState.SignedOut()
                    } else {
                        this@OneDriveAuthManagerImpl.currentAccount = currentAccount
                        if (priorAccount?.id != currentAccount.id) {
                            cachedAccessToken = null
                        }
                    }
                    if (continuation.isActive) {
                        continuation.resume(Result.success(currentAccount))
                    }
                }

                override fun onError(exception: MsalException) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exception))
                    }
                }
            })
        }

    private suspend fun clearStaleMsalAccount(app: ISingleAccountPublicClientApplication) {
        currentAccount = null
        cachedAccessToken = null
        suspendCancellableCoroutine<Unit> { continuation ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onError(exception: MsalException) {
                    // Best-effort cleanup so interactive signIn isn't blocked by a stale single account
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            })
        }
    }

    private suspend fun acquireTokenSilent(
        app: ISingleAccountPublicClientApplication,
        account: IAccount,
    ): Result<IAuthenticationResult> = suspendCancellableCoroutine { continuation ->
        app.acquireTokenSilentAsync(
            scopes,
            account.authority,
            object : SilentAuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(result))
                    }
                }

                override fun onError(exception: MsalException) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exception))
                    }
                }
            },
        )
    }

    override suspend fun restoreSession(): Result<String> {
        val currentToken = cachedAccessToken
        if (_authState.value is AuthState.SignedIn && currentAccount != null && !currentToken.isNullOrEmpty()) {
            return Result.success(currentToken)
        }
        if (_authState.value !is AuthState.Offline) {
            _authState.value = AuthState.Authenticating
        }

        val appResult = ensureClient()
        if (appResult.isFailure) {
            val err = appResult.exceptionOrNull()?.message ?: "Client initialization failed"
            val acct = currentAccount
            _authState.value = if (acct != null) {
                AuthState.Offline(acct.username, acct.id, err)
            } else {
                AuthState.SignedOut(err)
            }
            return Result.failure(appResult.exceptionOrNull() ?: Exception(err))
        }

        val app = appResult.getOrThrow()
        val accountResult = loadCurrentAccount(app)
        if (accountResult.isFailure) {
            val exception = accountResult.exceptionOrNull()
            cachedAccessToken = null
            val acct = currentAccount
            if (exception is MsalUiRequiredException) {
                clearStaleMsalAccount(app)
                _authState.value = AuthState.SignedOut(exception.message)
            } else if (acct != null) {
                _authState.value = AuthState.Offline(acct.username, acct.id, exception?.message)
            } else {
                _authState.value = AuthState.SignedOut(exception?.message)
            }
            return Result.failure(exception ?: Exception("Failed to load Microsoft account"))
        }

        val activeAccount = accountResult.getOrNull()
        if (activeAccount == null) {
            currentAccount = null
            cachedAccessToken = null
            _authState.value = AuthState.SignedOut()
            return Result.failure(IllegalStateException("No active account"))
        }

        currentAccount = activeAccount
        val tokenResult = acquireTokenSilent(app, activeAccount)
        return if (tokenResult.isSuccess) {
            val auth = tokenResult.getOrThrow()
            currentAccount = auth.account
            cachedAccessToken = auth.accessToken
            _authState.value = AuthState.SignedIn(
                displayName = auth.account.username,
                accountId = auth.account.id,
            )
            Result.success(auth.accessToken)
        } else {
            val exception = tokenResult.exceptionOrNull()
            cachedAccessToken = null
            // Preserve account offline state for transient/network failures; clear stale MSAL session when UI interaction is mandatory
            if (exception is MsalUiRequiredException) {
                clearStaleMsalAccount(app)
                _authState.value = AuthState.SignedOut(exception.message)
            } else {
                _authState.value = AuthState.Offline(
                    displayName = activeAccount.username,
                    accountId = activeAccount.id,
                    message = exception?.message,
                )
            }
            Result.failure(exception ?: Exception("Silent token acquisition failed"))
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
        // Clear any leftover single-account session in MSAL before starting interactive signIn
        val existingAccount = currentAccount ?: loadCurrentAccount(app).getOrNull()
        if (existingAccount != null) {
            clearStaleMsalAccount(app)
        }
        _authState.value = AuthState.Authenticating

        return suspendCancellableCoroutine { continuation ->
            app.signIn(activity, "", scopes, object : AuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    currentAccount = result.account
                    cachedAccessToken = result.accessToken
                    _authState.value = AuthState.SignedIn(
                        displayName = result.account.username,
                        accountId = result.account.id,
                    )
                    if (continuation.isActive) {
                        continuation.resume(Result.success(result.accessToken))
                    }
                }

                override fun onError(exception: MsalException) {
                    cachedAccessToken = null
                    _authState.value = AuthState.SignedOut(exception.message)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exception))
                    }
                }

                override fun onCancel() {
                    cachedAccessToken = null
                    _authState.value = AuthState.SignedOut("Sign-in cancelled")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Cancelled")))
                    }
                }
            })
        }
    }

    override suspend fun signOut(): Result<Unit> {
        val app = clientApp
        if (app == null) {
            currentAccount = null
            cachedAccessToken = null
            _authState.value = AuthState.SignedOut()
            return Result.success(Unit)
        }
        return suspendCancellableCoroutine { continuation ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    currentAccount = null
                    cachedAccessToken = null
                    _authState.value = AuthState.SignedOut()
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }

                override fun onError(exception: MsalException) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exception))
                    }
                }
            })
        }
    }

    override suspend fun getAccessToken(): String? {
        if (_authState.value is AuthState.Authenticating) return null
        val app = clientApp ?: ensureClient().getOrNull() ?: return null
        val account = currentAccount ?: loadCurrentAccount(app).getOrNull()?.also {
            currentAccount = it
        } ?: return null

        val tokenResult = acquireTokenSilent(app, account)
        if (tokenResult.isSuccess) {
            val authResult = tokenResult.getOrThrow()
            currentAccount = authResult.account
            cachedAccessToken = authResult.accessToken
            return authResult.accessToken
        }
        if (tokenResult.exceptionOrNull() is MsalUiRequiredException && _authState.value !is AuthState.Authenticating) {
            clearStaleMsalAccount(app)
            _authState.value = AuthState.SignedOut(tokenResult.exceptionOrNull()?.message)
        }
        return null
    }
}
