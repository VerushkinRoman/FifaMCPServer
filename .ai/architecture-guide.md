# Универсальный Архитектурный Шаблон для KMP / Android / iOS Проектов

## 1. Общая Архитектура

### 1.1. Clean Architecture + MVI
Проект строится на **Clean Architecture** с **MVI (Model-View-Intent)** в presentation слое.

**Структура слоев:**
```
[Presentation Layer] <- [Domain Layer] <- [Data Layer]
       ↓                      ↓                  ↓
    Compose UI          Use Cases          Repositories
    ViewModels          Models              DataSources
    States              Repository Int.     DTOs
    Events/Actions      Errors
```

**Принципы:**
- **Domain** не зависит от фреймворков и внешних источников
- **Data** реализует интерфейсы из Domain
- **Presentation** общается с Domain через Use Cases или напрямую через репозитории

---

## 2. Базовые Классы (Core)

### 2.1. Result и Error (Domain Layer)

```kotlin
// domain/utils/Result.kt
package com.yourproject.domain.utils

typealias DomainError = Error

sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : DomainError>(val error: E) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>
val EmptySuccessResult = Result.Success(Unit)

// Функции-расширения для удобной работы
inline fun <T, E : Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> {
    return when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
    }
}

fun <T, E : Error> Result<T, E>.asEmptyDataResult(): EmptyResult<E> = map { }

inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T, E : Error> Result<T, E>.onError(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Error) action(error)
    return this
}

fun <T> Result<T, Error>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    is Result.Error -> null
}

// domain/utils/Error.kt
package com.yourproject.domain.utils

interface Error

// Базовые ошибки
enum class DomainErrorType : Error {
    UnknownError,
    NetworkError,
    DataConversionError,
    CacheError,
    // ... другие
}
```

### 2.2. Dispatchers (Domain Layer)

```kotlin
// domain/utils/Dispatchers.kt
package com.yourproject.domain.utils

import kotlinx.coroutines.CoroutineDispatcher

interface Dispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class DispatchersImpl : Dispatchers {
    override val main: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main
    override val io: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
    override val default: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
    override val unconfined: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
}
```

### 2.3. BaseSharedViewModel (Presentation Layer)

```kotlin
// presentation/utils/BaseSharedViewModel.kt
package com.yourproject.presentation.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseSharedViewModel<State : Any, Action, Event>(
    initialState: State
) : ViewModel() {
    
    private val _viewStates = MutableStateFlow(initialState)
    val viewStates = _viewStates.asStateFlow()
    
    private val _viewActions = MutableSharedFlow<Action?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val viewActions = _viewActions.asSharedFlow()
    
    protected var viewState: State
        get() = _viewStates.value
        set(value) { _viewStates.value = value }
    
    protected var viewAction: Action?
        get() = _viewActions.replayCache.lastOrNull()
        set(value) { _viewActions.tryEmit(value) }
    
    abstract fun obtainEvent(viewEvent: Event)
    
    protected fun withViewModelScope(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(block = block)
    }
    
    // Использование в compose
    @Composable
    fun <T : R, R> SharedFlow<T>.collectAsStateWithLifecycle(
        context: CoroutineContext = EmptyCoroutineContext
    ): State<R?> {
        return collectAsState(initial = null, context = context)
    }
}
```

---

## 3. Структура Пакетов (Feature-first)

```
com.yourproject/
├── common/                    # Общие компоненты
│   ├── core/
│   │   ├── di/               # DI модули
│   │   └── platform/         # Платформенные зависимости
│   ├── compose/              # Общие UI компоненты
│   │   ├── theme/            # Тема
│   │   ├── components/       # Переиспользуемые компоненты
│   │   └── utils/            # UI утилиты
│   └── utils/                # Общие утилиты
│
├── feature_<name>/            # Фича
│   ├── compose/              # Presentation слой
│   │   ├── <FeatureName>Screen.kt
│   │   ├── components/       # UI компоненты фичи
│   │   └── presentation/
│   │       ├── <FeatureName>ViewModel.kt
│   │       ├── di/           # DI для ViewModel
│   │       └── models/
│   │           ├── <FeatureName>State.kt
│   │           ├── <FeatureName>Event.kt
│   │           └── <FeatureName>Action.kt
│   ├── domain/               # Domain слой
│   │   ├── models/           # Бизнес-сущности
│   │   ├── repository/       # Интерфейсы репозиториев
│   │   ├── usecases/         # Use Cases (опционально)
│   │   ├── utils/            # Domain ошибки
│   │   └── di/               # DI для Domain
│   └── data/                 # Data слой
│       ├── datasource/       # Источники данных
│       ├── models/           # DTO
│       ├── repository/       # Реализации репозиториев
│       └── di/               # DI для Data
│
├── navigation/               # Навигация
│   ├── compose/              # Навигационные компоненты
│   ├── domain/
│   │   └── repository/       # Навигационные репозитории
│   └── presentation/
│       └── screens/          # Определения экранов
│
└── di/                       # Корневой DI модуль
```

---

## 4. Примеры Реализации Компонентов

### 4.1. Domain Layer

**Модель:**
```kotlin
// domain/models/User.kt
package com.yourproject.feature_profile.domain.models

data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String?
)
```

**Интерфейс репозитория:**
```kotlin
// domain/repository/ProfileRepository.kt
package com.yourproject.feature_profile.domain.repository

import com.yourproject.domain.utils.EmptyResult
import com.yourproject.domain.utils.Error
import com.yourproject.domain.utils.Result
import com.yourproject.feature_profile.domain.models.User

interface ProfileRepository {
    suspend fun getCurrentUser(): Result<User, Error>
    suspend fun updateUserName(name: String): EmptyResult<Error>
    suspend fun deleteAccount(): EmptyResult<Error>
}
```

**Use Case (опционально):**
```kotlin
// domain/usecases/GetCurrentUserUseCase.kt
package com.yourproject.feature_profile.domain.usecases

import com.yourproject.domain.utils.Dispatchers
import com.yourproject.domain.utils.Result
import com.yourproject.feature_profile.domain.models.User
import com.yourproject.feature_profile.domain.repository.ProfileRepository
import kotlinx.coroutines.withContext

class GetCurrentUserUseCase(
    private val repository: ProfileRepository,
    private val dispatchers: Dispatchers
) {
    suspend operator fun invoke(): Result<User, Error> = 
        withContext(dispatchers.io) {
            repository.getCurrentUser()
        }
}
```

### 4.2. Data Layer

**DTO:**
```kotlin
// data/models/UserDTO.kt
package com.yourproject.feature_profile.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    val id: String,
    val name: String,
    val email: String,
    val avatar: String?
)

// Маппинг
fun UserDTO.toDomain(): User {
    return User(
        id = id,
        name = name,
        email = email,
        avatarUrl = avatar
    )
}
```

**DataSource (Online):**
```kotlin
// data/datasource/ProfileRemoteDataSource.kt
package com.yourproject.feature_profile.data.datasource

import com.yourproject.domain.utils.EmptyResult
import com.yourproject.domain.utils.Error
import com.yourproject.domain.utils.Result
import com.yourproject.feature_profile.data.models.UserDTO

interface ProfileRemoteDataSource {
    suspend fun getUser(): Result<UserDTO, Error>
    suspend fun updateName(name: String): EmptyResult<Error>
    suspend fun deleteUser(): EmptyResult<Error>
}
```

**DataSource (Offline):**
```kotlin
// data/datasource/ProfileLocalDataSource.kt
package com.yourproject.feature_profile.data.datasource

import com.yourproject.feature_profile.data.models.UserDTO

interface ProfileLocalDataSource {
    fun getCachedUser(): UserDTO?
    fun cacheUser(user: UserDTO)
    fun clearCache()
}
```

**Реализация репозитория:**
```kotlin
// data/repository/ProfileRepositoryImpl.kt
package com.yourproject.feature_profile.data.repository

import com.yourproject.domain.utils.Dispatchers
import com.yourproject.domain.utils.EmptyResult
import com.yourproject.domain.utils.Error
import com.yourproject.domain.utils.Result
import com.yourproject.domain.utils.map
import com.yourproject.domain.utils.onSuccess
import com.yourproject.feature_profile.data.datasource.ProfileLocalDataSource
import com.yourproject.feature_profile.data.datasource.ProfileRemoteDataSource
import com.yourproject.feature_profile.data.models.toDomain
import com.yourproject.feature_profile.domain.repository.ProfileRepository
import kotlinx.coroutines.withContext

class ProfileRepositoryImpl(
    private val remoteDataSource: ProfileRemoteDataSource,
    private val localDataSource: ProfileLocalDataSource,
    private val dispatchers: Dispatchers
) : ProfileRepository {
    
    override suspend fun getCurrentUser(): Result<User, Error> = 
        withContext(dispatchers.io) {
            // Сначала проверяем кэш
            localDataSource.getCachedUser()?.let {
                return@withContext Result.Success(it.toDomain())
            }
            
            // Если нет в кэше, запрашиваем с сервера
            remoteDataSource.getUser().map { userDTO ->
                localDataSource.cacheUser(userDTO)
                userDTO.toDomain()
            }
        }
    
    override suspend fun updateUserName(name: String): EmptyResult<Error> = 
        withContext(dispatchers.io) {
            remoteDataSource.updateName(name).onSuccess {
                // Обновляем кэш
                localDataSource.getCachedUser()?.let { user ->
                    localDataSource.cacheUser(user.copy(name = name))
                }
            }
        }
    
    override suspend fun deleteAccount(): EmptyResult<Error> = 
        withContext(dispatchers.io) {
            remoteDataSource.deleteUser().onSuccess {
                localDataSource.clearCache()
            }
        }
}
```

### 4.3. Presentation Layer

**State:**
```kotlin
// presentation/models/ProfileState.kt
package com.yourproject.feature_profile.presentation.models

import com.yourproject.feature_profile.domain.models.User

data class ProfileState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val isEditingName: Boolean = false,
    val error: Error? = null
)
```

**Event:**
```kotlin
// presentation/models/ProfileEvent.kt
package com.yourproject.feature_profile.presentation.models

sealed interface ProfileEvent {
    object LoadProfile : ProfileEvent
    object OnEditNameClick : ProfileEvent
    class OnNameChanged(val name: String) : ProfileEvent
    object OnSaveName : ProfileEvent
    object OnCancelEdit : ProfileEvent
    object OnDeleteAccount : ProfileEvent
}
```

**Action:**
```kotlin
// presentation/models/ProfileAction.kt
package com.yourproject.feature_profile.presentation.models

import com.yourproject.domain.utils.Error

sealed interface ProfileAction {
    class ShowError(val error: Error) : ProfileAction
    object NavigateBack : ProfileAction
}
```

**ViewModel:**
```kotlin
// presentation/ProfileViewModel.kt
package com.yourproject.feature_profile.presentation

import com.yourproject.domain.utils.Dispatchers
import com.yourproject.domain.utils.EmptySuccessResult
import com.yourproject.domain.utils.onError
import com.yourproject.domain.utils.onSuccess
import com.yourproject.feature_profile.domain.repository.ProfileRepository
import com.yourproject.feature_profile.presentation.models.ProfileAction
import com.yourproject.feature_profile.presentation.models.ProfileEvent
import com.yourproject.feature_profile.presentation.models.ProfileState
import com.yourproject.presentation.utils.BaseSharedViewModel

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val dispatchers: Dispatchers
) : BaseSharedViewModel<ProfileState, ProfileAction, ProfileEvent>(
    initialState = ProfileState()
) {
    
    init {
        obtainEvent(ProfileEvent.LoadProfile)
    }
    
    override fun obtainEvent(viewEvent: ProfileEvent) {
        when (viewEvent) {
            ProfileEvent.LoadProfile -> loadProfile()
            ProfileEvent.OnEditNameClick -> startEditing()
            is ProfileEvent.OnNameChanged -> updateName(viewEvent.name)
            ProfileEvent.OnSaveName -> saveName()
            ProfileEvent.OnCancelEdit -> cancelEditing()
            ProfileEvent.OnDeleteAccount -> deleteAccount()
        }
    }
    
    private fun loadProfile() {
        viewState = viewState.copy(isLoading = true)
        
        withViewModelScope {
            repository.getCurrentUser()
                .onSuccess { user ->
                    viewState = viewState.copy(
                        user = user,
                        isLoading = false
                    )
                }
                .onError { error ->
                    viewState = viewState.copy(isLoading = false)
                    viewAction = ProfileAction.ShowError(error)
                }
        }
    }
    
    private fun startEditing() {
        viewState = viewState.copy(isEditingName = true)
    }
    
    private fun updateName(name: String) {
        viewState = viewState.copy(
            user = viewState.user?.copy(name = name)
        )
    }
    
    private fun saveName() {
        val newName = viewState.user?.name ?: return
        
        viewState = viewState.copy(isLoading = true)
        
        withViewModelScope {
            repository.updateUserName(newName)
                .onSuccess {
                    viewState = viewState.copy(
                        isLoading = false,
                        isEditingName = false
                    )
                }
                .onError { error ->
                    viewState = viewState.copy(isLoading = false)
                    viewAction = ProfileAction.ShowError(error)
                }
        }
    }
    
    private fun cancelEditing() {
        viewState = viewState.copy(isEditingName = false)
        // Восстанавливаем имя из кэша
        loadProfile()
    }
    
    private fun deleteAccount() {
        viewState = viewState.copy(isLoading = true)
        
        withViewModelScope {
            repository.deleteAccount()
                .onSuccess {
                    viewState = viewState.copy(isLoading = false)
                    viewAction = ProfileAction.NavigateBack
                }
                .onError { error ->
                    viewState = viewState.copy(isLoading = false)
                    viewAction = ProfileAction.ShowError(error)
                }
        }
    }
}
```

**Compose Screen:**
```kotlin
// compose/ProfileScreen.kt
package com.yourproject.feature_profile.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourproject.feature_profile.presentation.ProfileViewModel
import com.yourproject.feature_profile.presentation.models.ProfileEvent
import com.yourproject.presentation.utils.collectAsStateWithLifecycle
import org.kodein.di.compose.viewmodel.rememberViewModel

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val viewModel: ProfileViewModel by rememberViewModel()
    
    val state by viewModel.viewStates().collectAsStateWithLifecycle()
    val action by viewModel.viewActions().collectAsStateWithLifecycle()
    
    LaunchedEffect(action) {
        action?.let { action ->
            when (action) {
                is ProfileAction.ShowError -> {
                    // Показать ошибку (Snackbar, Dialog)
                }
                ProfileAction.NavigateBack -> {
                    // Навигация назад
                }
            }
            viewModel.obtainEvent(ProfileEvent.ClearAction)
        }
    }
    
    ProfileContent(
        state = state,
        onEvent = viewModel::obtainEvent
    )
}

@Composable
private fun ProfileContent(
    state: ProfileState,
    onEvent: (ProfileEvent) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.user != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // UI компоненты
                    Text(
                        text = state.user.name,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Text(
                        text = state.user.email,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (state.isEditingName) {
                        EditNameContent(
                            currentName = state.user.name,
                            onNameChange = { onEvent(ProfileEvent.OnNameChanged(it)) },
                            onSave = { onEvent(ProfileEvent.OnSaveName) },
                            onCancel = { onEvent(ProfileEvent.OnCancelEdit) }
                        )
                    } else {
                        Button(
                            onClick = { onEvent(ProfileEvent.OnEditNameClick) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit Name")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { onEvent(ProfileEvent.OnDeleteAccount) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Account")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditNameContent(
    currentName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = currentName,
            onValueChange = onNameChange,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
            
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }
    }
}
```

---

## 5. DI с Kodein

**Корневой DI модуль:**
```kotlin
// common/core/di/Inject.kt
package com.yourproject.common.core.di

import org.kodein.di.DirectDI
import org.kodein.di.instance

object Inject {
    private var _di: DirectDI? = null
    
    val di: DirectDI
        get() = requireNotNull(_di)
    
    fun createDependencies(tree: DirectDI) {
        _di = tree
    }
    
    fun isInitialized(): Boolean = _di != null
    
    inline fun <reified T> instance(tag: Any? = null): T {
        return di.instance(tag)
    }
}
```

**Domain DI:**
```kotlin
// domain/di/ProfileDomainModule.kt
package com.yourproject.feature_profile.domain.di

import com.yourproject.feature_profile.domain.repository.ProfileRepository
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance

val profileDomainModule = DI.Module("ProfileDomainModule") {
    bindProvider<ProfileRepository> {
        // Реализация будет в Data слое
        instance()
    }
}
```

**Data DI:**
```kotlin
// data/di/ProfileDataModule.kt
package com.yourproject.feature_profile.data.di

import com.yourproject.feature_profile.data.datasource.ProfileLocalDataSource
import com.yourproject.feature_profile.data.datasource.ProfileRemoteDataSource
import com.yourproject.feature_profile.data.datasource.ProfileRemoteDataSourceImpl
import com.yourproject.feature_profile.data.repository.ProfileRepositoryImpl
import com.yourproject.feature_profile.domain.repository.ProfileRepository
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance

val profileDataModule = DI.Module("ProfileDataModule") {
    bindProvider<ProfileRemoteDataSource> {
        ProfileRemoteDataSourceImpl(
            httpClient = instance()
        )
    }
    
    bindProvider<ProfileLocalDataSource> {
        // Реализация с SharedPreferences / DataStore
        instance()
    }
    
    bindProvider<ProfileRepository> {
        ProfileRepositoryImpl(
            remoteDataSource = instance(),
            localDataSource = instance(),
            dispatchers = instance()
        )
    }
}
```

**Presentation DI:**
```kotlin
// presentation/di/ProfileViewModelModule.kt
package com.yourproject.feature_profile.presentation.di

import com.yourproject.feature_profile.presentation.ProfileViewModel
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance

val profileViewModelModule = DI.Module("ProfileViewModelModule") {
    bindProvider {
        ProfileViewModel(
            repository = instance(),
            dispatchers = instance()
        )
    }
}
```

**Инициализация:**
```kotlin
// common/core/platform/PlatformSDK.kt
package com.yourproject.common.core.platform

import com.yourproject.common.core.di.Inject
import com.yourproject.common.core.di.commonModule
import com.yourproject.feature_profile.data.di.profileDataModule
import com.yourproject.feature_profile.domain.di.profileDomainModule
import com.yourproject.feature_profile.presentation.di.profileViewModelModule
import org.kodein.di.DI

object PlatformSDK {
    fun init(configuration: PlatformConfiguration) {
        Inject.createDependencies(
            DI {
                importAll(
                    commonModule,
                    platformModule,
                    profileDomainModule,
                    profileDataModule,
                    profileViewModelModule
                )
            }.direct
        )
    }
}
```

---

## 6. Работа с Database (Room / SQLDelight)

```kotlin
// data/datasource/UserLocalDataSource.kt
interface UserLocalDataSource {
    suspend fun getUsers(): Result<List<UserEntity>, Error>
    suspend fun saveUser(user: UserEntity): EmptyResult<Error>
    suspend fun deleteUser(id: String): EmptyResult<Error>
}

// data/datasource/UserLocalDataSourceImpl.kt
class UserLocalDataSourceImpl(
    private val database: AppDatabase,
    private val dispatchers: Dispatchers
) : UserLocalDataSource {
    
    override suspend fun getUsers(): Result<List<UserEntity>, Error> = 
        withContext(dispatchers.io) {
            try {
                Result.Success(database.userDao().getAll())
            } catch (e: Exception) {
                Result.Error(DomainErrorType.CacheError)
            }
        }
    
    override suspend fun saveUser(user: UserEntity): EmptyResult<Error> = 
        withContext(dispatchers.io) {
            try {
                database.userDao().insert(user)
                EmptySuccessResult
            } catch (e: Exception) {
                Result.Error(DomainErrorType.CacheError)
            }
        }
}
```

---

## 7. Работа с Network (Ktor)

```kotlin
// common/data/di/NetworkModule.kt
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val networkModule = DI.Module("NetworkModule") {
    bindSingleton<HttpClient> {
        HttpClient {
            install(Logging) {
                level = LogLevel.ALL
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
}
```

**Пример RemoteDataSource:**
```kotlin
// data/datasource/UserRemoteDataSource.kt
class UserRemoteDataSourceImpl(
    private val httpClient: HttpClient
) : UserRemoteDataSource {
    
    override suspend fun getUsers(): Result<List<UserDTO>, Error> = 
        withContext(dispatchers.io) {
            try {
                val response = httpClient.get("$BASE_URL/users")
                val users = response.body<List<UserDTO>>()
                Result.Success(users)
            } catch (e: Exception) {
                when (e) {
                    is ConnectException -> Result.Error(NetworkError.NoConnection)
                    is SocketTimeoutException -> Result.Error(NetworkError.Timeout)
                    else -> Result.Error(NetworkError.Unknown(e.message))
                }
            }
        }
}
```

---

## 8. Android и iOS Платформенные Внедрения

**Android:**
```kotlin
// androidMain/.../PlatformConfiguration.android.kt
actual class PlatformConfiguration(
    val androidContext: Context,
    val getActivity: () -> ComponentActivity?,
    // ... другие платформенные зависимости
)

actual val platformModule = DI.Module("AndroidModule") {
    bindSingleton<PlatformConfiguration> {
        PlatformConfiguration(
            androidContext = instance(),
            getActivity = { /* ... */ }
        )
    }
}
```

**iOS:**
```kotlin
// iosMain/.../PlatformConfiguration.ios.kt
actual class PlatformConfiguration(
    // ... iOS специфичные зависимости
)

actual val platformModule = DI.Module("IosModule") {
    bindSingleton<PlatformConfiguration> {
        PlatformConfiguration()
    }
}
```

---

## 9. Тестирование

```kotlin
// test/.../ProfileViewModelTest.kt
class ProfileViewModelTest {
    
    private lateinit var viewModel: ProfileViewModel
    private val repository: ProfileRepository = mockk()
    private val dispatchers = TestDispatchers()
    
    @Before
    fun setup() {
        viewModel = ProfileViewModel(repository, dispatchers)
    }
    
    @Test
    fun `loadProfile success updates state`() = runTest {
        val user = User("1", "Test", "test@test.com", null)
        coEvery { repository.getCurrentUser() } returns Result.Success(user)
        
        viewModel.obtainEvent(ProfileEvent.LoadProfile)
        
        viewModel.viewStates.value.let { state ->
            assertFalse(state.isLoading)
            assertEquals(user, state.user)
            assertNull(state.error)
        }
    }
    
    @Test
    fun `loadProfile error shows error action`() = runTest {
        coEvery { repository.getCurrentUser() } returns Result.Error(DomainErrorType.UnknownError)
        
        viewModel.obtainEvent(ProfileEvent.LoadProfile)
        
        viewModel.viewActions.firstOrNull()?.let { action ->
            assertTrue(action is ProfileAction.ShowError)
            assertNotNull(action.error)
        }
    }
}
```

---

## 10. Best Practices & Conventions

### 10.1. Именование
- **State:** `<FeatureName>State`
- **Event:** `<FeatureName>Event`
- **Action:** `<FeatureName>Action`
- **ViewModel:** `<FeatureName>ViewModel`
- **Repository:** `<Domain>Repository` (интерфейс), `<Domain>RepositoryImpl` (реализация)
- **DataSource:** `<Source><Type>DataSource` (например, `UserRemoteDataSource`)

### 10.2. Структура фичи
```
feature_<name>/
├── compose/          # UI слои
│   ├── <Name>Screen.kt
│   ├── components/   # UI компоненты для этой фичи
│   └── presentation/
│       ├── <Name>ViewModel.kt
│       ├── di/
│       └── models/
├── domain/           # Бизнес-логика
│   ├── models/
│   ├── repository/
│   ├── usecases/
│   ├── utils/
│   └── di/
└── data/             # Работа с данными
    ├── datasource/
    ├── models/
    ├── repository/
    └── di/
```

### 10.3. Правила
1. **Не дублируй код:** Выноси общую логику в `common` пакет.
2. **Всегда используй Result:** Все операции в Data и Domain слоях должны возвращать `Result<T, Error>`.
3. **Внедряй Dispatchers:** Все Repository и UseCases должны принимать `Dispatchers` и использовать `withContext`.
4. **Разделяй ответственность:** ViewModel отвечает только за логику UI, UseCases — за бизнес-логику.
5. **Immutable State:** State всегда должен быть неизменяемым (`data class` с `val`).
6. **Одноразовые действия:** Используй `Action` для событий, которые должны быть выполнены один раз (навигация, показ тостов).
7. **Тестируй:** Пиши тесты для ViewModel, UseCases и Repository.

---
