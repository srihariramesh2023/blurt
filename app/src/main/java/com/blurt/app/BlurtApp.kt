package com.blurt.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.blurt.app.auth.AuthRepository
import com.blurt.app.auth.FirebaseAuthRepository
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.local.BlurtDatabase
import com.blurt.app.data.sync.RtdbCaptureRemote
import com.blurt.app.data.sync.SyncEngine
import com.blurt.app.ui.theme.ThemeMode
import com.blurt.app.ui.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

class BlurtApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual dependency container. Kept intentionally small — no DI framework
 * needed for a single-database app.
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: BlurtDatabase = Room.databaseBuilder(
        context,
        BlurtDatabase::class.java,
        "blurt.db",
    )
        .addMigrations(
            BlurtDatabase.MIGRATION_1_2,
            BlurtDatabase.MIGRATION_2_3,
            BlurtDatabase.MIGRATION_3_4,
        )
        .build()

    val authRepository: AuthRepository = FirebaseAuthRepository(context)

    val themePreferences = ThemePreferences(context)
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode

    val captureRepository: CaptureRepository = CaptureRepository(
        dao = database.captureDao(),
    )

    private val captureRemote = RtdbCaptureRemote(context)

    private val syncEngine = SyncEngine(
        scope = appScope,
        dao = database.captureDao(),
        remote = captureRemote,
        authState = authRepository.authState,
    ).also { it.start() }
}
