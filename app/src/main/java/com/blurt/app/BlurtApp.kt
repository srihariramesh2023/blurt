package com.blurt.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import com.blurt.app.ai.GeminiEmbeddingProvider
import com.blurt.app.ai.SemanticSearchEngine
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
            BlurtDatabase.MIGRATION_4_5,
        )
        .build()

    val authRepository: AuthRepository = FirebaseAuthRepository(context)

    val themePreferences = ThemePreferences(context)
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode

    val captureRepository: CaptureRepository = CaptureRepository(
        dao = database.captureDao(),
    )

    // Semantic search is enabled only when a Gemini API key was supplied at
    // build time (local.properties gemini.apiKey / GEMINI_API_KEY env). With
    // no key, the engine is null and search falls back to plain keywords.
    val semanticSearch: SemanticSearchEngine? = if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
        SemanticSearchEngine(
            dao = database.captureDao(),
            embeddingDao = database.embeddingDao(),
            provider = GeminiEmbeddingProvider(
                apiKey = BuildConfig.GEMINI_API_KEY,
                packageName = context.packageName,
                certSha1 = signingCertSha1(context),
            ),
        )
    } else {
        null
    }

    private val captureRemote = RtdbCaptureRemote(context)

    private val syncEngine = SyncEngine(
        scope = appScope,
        dao = database.captureDao(),
        remote = captureRemote,
        authState = authRepository.authState,
    ).also { it.start() }

    /**
     * SHA-1 of the certificate that actually signed this APK (colon-separated
     * hex, the format Google's Android-app key restrictions expect). Derived
     * at runtime so the header always matches the real signer.
     */
    private fun signingCertSha1(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull()
            ?: info.signingInfo?.signingCertificateHistory?.firstOrNull()
            ?: return@runCatching ""
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(signer.toByteArray())
        digest.joinToString(":") { "%02X".format(it) }
    }.getOrDefault("")
}
