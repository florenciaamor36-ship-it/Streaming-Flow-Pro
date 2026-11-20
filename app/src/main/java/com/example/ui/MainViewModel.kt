package com.example.ui

import com.example.BuildConfig
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.StreamingRepository
import com.example.data.repository.UserSession
import com.example.util.DateFormatter
import com.example.util.ExpirationStatus
import com.example.util.NotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SalesFilter {
    ALL,
    EXPIRING_SOON, // 0 to 3 days
    EXPIRED,
    PENDING_PAYMENT,
    ACTIVE
}

data class FinancialMetrics(
    val totalSalesIncome: Double,
    val totalCost: Double,
    val netProfit: Double,
    val activeProfilesCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val totalAccountsCount: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(application)
    val userSession: StateFlow<UserSession?> = authRepository.userSession
    private val database = AppDatabase.getInstance(application)
    private val streamingRepository = StreamingRepository(database)
    private val driveSyncManager = com.example.data.drive.DriveSyncManager(application, database)
    private val _driveSyncEnabled = MutableStateFlow(driveSyncManager.isEnabled(authRepository.userSession.value?.userId.orEmpty()))
    val driveSyncEnabled: StateFlow<Boolean> = _driveSyncEnabled.asStateFlow()
    private val _driveSyncStatus = MutableStateFlow("Drive sync is off")
    val driveSyncStatus: StateFlow<String> = _driveSyncStatus.asStateFlow()

    fun setDriveSyncEnabled(enabled: Boolean) {
        driveSyncManager.setEnabled(authRepository.userSession.value?.userId.orEmpty(), enabled)
        _driveSyncEnabled.value = enabled
        _driveSyncStatus.value = if (enabled) "Optional sync enabled. Nothing uploads until you tap a sync action." else "Drive sync is off"
    }

    fun syncDrive(accessToken: String, googleEmail: String, operation: DriveOperation) {
        val session = userSession.value
        if (session == null || !authRepository.isVerifiedFirebaseSession(session)) {
            _driveSyncStatus.value = "Drive sync requires a verified Firebase sign-in; local/guest sessions are not eligible."
            return
        }
        viewModelScope.launch {
            _driveSyncStatus.value = "Checking your Drive snapshot…"
            val result = try { when (operation) {
                DriveOperation.SYNC -> driveSyncManager.sync(session.userId, session.email.orEmpty(), googleEmail, accessToken)
                DriveOperation.CREATE_BACKUP -> driveSyncManager.confirmInitialUpload(session.userId, session.email.orEmpty(), googleEmail, accessToken)
                DriveOperation.RESTORE_BACKUP -> driveSyncManager.confirmInitialRestore(session.userId, session.email.orEmpty(), googleEmail, accessToken)
            } } catch (error: Exception) {
                _driveSyncStatus.value = "Drive sync failed: ${error.message ?: "network or API error"}. Local data was kept."
                return@launch
            }
            _driveSyncStatus.value = when (result) {
                is com.example.data.drive.DriveSyncResult.Complete -> "${result.action}: ${result.accountCount} accounts, ${result.saleCount} sales, ${result.clientCount} clients."
                is com.example.data.drive.DriveSyncResult.Blocked -> "${result.action}: ${result.detail}"
            }
        }
    }

    enum class DriveOperation { SYNC, CREATE_BACKUP, RESTORE_BACKUP }

    fun isDriveEligible(): Boolean = userSession.value?.let { authRepository.isVerifiedFirebaseSession(it) } == true

    fun driveOAuthConfigured(): Boolean = BuildConfig.GOOGLE_DRIVE_CLIENT_ID.isNotBlank()

    init {
        viewModelScope.launch { userSession.collect { session -> _driveSyncEnabled.value = driveSyncManager.isEnabled(session?.userId.orEmpty()) } }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _salesFilter = MutableStateFlow(SalesFilter.ALL)
    val salesFilter: StateFlow<SalesFilter> = _salesFilter.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<StreamingAccountEntity>> = userSession
        .flatMapLatest { session ->
            if (session != null) {
                streamingRepository.populateSampleDataIfEmpty(session.userId)
                streamingRepository.getAccountsForUser(session.userId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allSales: StateFlow<List<ProfileSaleEntity>> = userSession
        .flatMapLatest { session ->
            if (session != null) {
                streamingRepository.getSalesForUser(session.userId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val clients: StateFlow<List<ClientEntity>> = userSession
        .flatMapLatest { session ->
            if (session != null) {
                streamingRepository.getClientsForUser(session.userId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSales: StateFlow<List<ProfileSaleEntity>> = combine(
        allSales,
        searchQuery,
        salesFilter
    ) { salesList, query, filter ->
        salesList.filter { sale ->
            val matchesQuery = query.isBlank() ||
                    sale.clientName.contains(query, ignoreCase = true) ||
                    sale.platformName.contains(query, ignoreCase = true) ||
                    sale.profileName.contains(query, ignoreCase = true) ||
                    sale.clientPhone.contains(query)

            val status = DateFormatter.getExpirationStatus(sale.expirationDate)
            val matchesFilter = when (filter) {
                SalesFilter.ALL -> true
                SalesFilter.EXPIRING_SOON -> status == ExpirationStatus.EXPIRING_SOON || status == ExpirationStatus.EXPIRING_TODAY
                SalesFilter.EXPIRED -> status == ExpirationStatus.EXPIRED
                SalesFilter.PENDING_PAYMENT -> sale.paymentStatus == "PENDING"
                SalesFilter.ACTIVE -> status == ExpirationStatus.ACTIVE
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val metrics: StateFlow<FinancialMetrics> = allSales.map { sales ->
        val totalIncome = sales.sumOf { it.salePrice }
        val totalCost = sales.sumOf { it.costPrice }
        val profit = totalIncome - totalCost

        var active = 0
        var expiring = 0
        var expired = 0

        sales.forEach { sale ->
            when (DateFormatter.getExpirationStatus(sale.expirationDate)) {
                ExpirationStatus.ACTIVE -> active++
                ExpirationStatus.EXPIRING_SOON, ExpirationStatus.EXPIRING_TODAY -> expiring++
                ExpirationStatus.EXPIRED -> expired++
            }
        }

        FinancialMetrics(
            totalSalesIncome = totalIncome,
            totalCost = totalCost,
            netProfit = profit,
            activeProfilesCount = active,
            expiringCount = expiring,
            expiredCount = expired,
            totalAccountsCount = accounts.value.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FinancialMetrics(0.0, 0.0, 0.0, 0, 0, 0, 0)
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSalesFilter(filter: SalesFilter) {
        _salesFilter.value = filter
    }

    // --- AUTH ACTIONS ---
    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authError.value = "Por favor ingresa tu correo y contraseña"
            return
        }
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authRepository.signInWithEmail(email, pass)
            _isAuthLoading.value = false
            result.onFailure {
                _authError.value = it.message ?: "Error al iniciar sesión"
            }
        }
    }

    fun register(email: String, pass: String) {
        if (email.isBlank() || pass.length < 6) {
            _authError.value = "La contraseña debe tener al menos 6 caracteres"
            return
        }
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authRepository.registerWithEmail(email, pass)
            _isAuthLoading.value = false
            result.onFailure {
                _authError.value = it.message ?: "Error al registrar usuario"
            }
        }
    }

    private val _licenseStatus = MutableStateFlow(com.example.util.LicenseManager.getLicenseStatus(application))
    val licenseStatus: StateFlow<com.example.util.LicenseStatus> = _licenseStatus.asStateFlow()

    init {
        viewModelScope.launch {
            userSession.collect { session ->
                if (session != null) {
                    com.example.util.LicenseManager.syncCloudLicense(getApplication(), session.userId)
                    refreshLicenseStatus()
                }
            }
        }
    }

    fun refreshLicenseStatus() {
        _licenseStatus.value = com.example.util.LicenseManager.getLicenseStatus(getApplication())
    }

    fun redeemLicenseToken(token: String): Result<Int> {
        val currentUserId = userSession.value?.userId ?: ""
        val res = com.example.util.LicenseManager.redeemToken(getApplication(), token, currentUserId)
        if (res.isSuccess) {
            refreshLicenseStatus()
        }
        return res
    }

    fun loginAsGuest() {
        authRepository.signInAsGuest()
    }

    fun logout() {
        authRepository.signOut()
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // --- CRUD ACTIONS ---
    fun saveAccount(account: StreamingAccountEntity) {
        val userId = userSession.value?.userId ?: return
        viewModelScope.launch {
            streamingRepository.saveAccount(account.copy(userId = userId))
        }
    }

    fun deleteAccount(accountId: String) {
        val userId = userSession.value?.userId ?: return
        viewModelScope.launch {
            streamingRepository.deleteAccount(userId, accountId)
        }
    }

    fun saveSale(sale: ProfileSaleEntity) {
        val userId = userSession.value?.userId ?: return
        viewModelScope.launch {
            streamingRepository.saveSale(sale.copy(userId = userId))
        }
    }

    fun deleteSale(saleId: String) {
        val userId = userSession.value?.userId ?: return
        viewModelScope.launch {
            streamingRepository.deleteSale(userId, saleId)
        }
    }

    fun togglePaymentStatus(sale: ProfileSaleEntity) {
        val userId = userSession.value?.userId ?: return
        val newStatus = if (sale.paymentStatus == "PAID") "PENDING" else "PAID"
        viewModelScope.launch {
            streamingRepository.updatePaymentStatus(userId, sale.id, newStatus)
        }
    }

    fun renewSale(sale: ProfileSaleEntity, durationDays: Int = 30) {
        val userId = userSession.value?.userId ?: return
        val newExpiration = DateFormatter.addDaysToCurrentTime(durationDays)
        viewModelScope.launch {
            streamingRepository.renewSale(userId, sale.id, newExpiration)
        }
    }

    fun saveClient(client: ClientEntity) {
        val userId = userSession.value?.userId ?: return
        viewModelScope.launch {
            streamingRepository.saveClient(client.copy(userId = userId))
        }
    }

    fun deleteClient(clientId: String) {
        val userId = userSession.value?.userId ?: return
        viewModelScope.launch {
            streamingRepository.deleteClient(userId, clientId)
        }
    }

    fun triggerNotificationsForExpiringSales() {
        NotificationHelper.notifyAllExpiringSales(
            getApplication(),
            allSales.value
        )
    }
}
