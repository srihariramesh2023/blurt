package com.blurt.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import com.blurt.app.ai.AiKeyStore
import com.blurt.app.ai.CategoryBackfiller
import com.blurt.app.ai.CaptureAnalyzer
import com.blurt.app.ai.FallbackCaptureAnalyzer
import com.blurt.app.ai.GeminiCaptureAnalyzer
import com.blurt.app.ai.GeminiEmbeddingProvider
import com.blurt.app.ai.GroqCaptureAnalyzer
import com.blurt.app.ai.SemanticSearchEngine
import com.blurt.app.auth.AuthRepository
import com.blurt.app.auth.FirebaseAuthRepository
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.local.BlurtDatabase
import com.blurt.app.data.sync.RtdbCaptureRemote
import com.blurt.app.data.sync.SyncEngine
import com.blurt.app.notifications.ReminderScheduler
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
        com.blurt.app.ui.components.BlurtSound.init(this)
        android.util.Log.d(
            "BlurtAi",
            "captureAnalyzer=${container.captureAnalyzer?.javaClass?.simpleName} " +
                "groq=${container.aiKeyStore.groqKey() != null} " +
                "gemini=${container.aiKeyStore.geminiKey() != null}",
        )
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
            BlurtDatabase.MIGRATION_5_6,
            BlurtDatabase.MIGRATION_6_7,
            BlurtDatabase.MIGRATION_7_8,
            BlurtDatabase.MIGRATION_8_9,
        )
        .build()

    val authRepository: AuthRepository = FirebaseAuthRepository(context)

    val themePreferences = ThemePreferences(context)
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode

    val captureRepository: CaptureRepository = CaptureRepository(
        dao = database.captureDao(),
    )

    val reminderScheduler = ReminderScheduler(context)

    /**
     * Encrypted storage for user-supplied AI keys (BYOK) — a Groq key for
     * classification and a Gemini key for the classification fallback plus
     * semantic-search embeddings. Keys are kept in the Android Keystore; the
     * analyzers resolve them at call time, so a key pasted in the avatar menu
     * takes effect immediately.
     */
    val aiKeyStore = AiKeyStore(context)

    /**
     * Capture analysis (intent + category + time extraction). Both analyzers
     * resolve the user's BYOK key from the encrypted store at call time — with
     * no key pasted, each returns null instantly and the save falls back to
     * unclassified. Groq is preferred; Gemini is the fallback.
     */
    val captureAnalyzer: CaptureAnalyzer? = buildCaptureAnalyzer(context)

    private fun buildCaptureAnalyzer(context: Context): CaptureAnalyzer? {
        val groq = GroqCaptureAnalyzer(
            apiKeyProvider = { aiKeyStore.groqKey() },
            model = GroqCaptureAnalyzer.DEFAULT_MODEL,
        )
        val gemini = GeminiCaptureAnalyzer(
            apiKeyProvider = { aiKeyStore.geminiKey() },
            packageName = context.packageName,
            certSha1 = signingCertSha1(context),
        )
        return FallbackCaptureAnalyzer(groq, gemini)
    }

    /**
     * Semantic search, always constructed. The embedding provider resolves the
     * user's Gemini BYOK key at call time — a pasted key activates
     * meaning-based search with no rebuild; with no key, every call degrades
     * to keyword search.
     */
    val semanticSearch: SemanticSearchEngine? = SemanticSearchEngine(
        dao = database.captureDao(),
        embeddingDao = database.embeddingDao(),
        provider = GeminiEmbeddingProvider(
            apiKeyProvider = { aiKeyStore.geminiKey() },
            packageName = context.packageName,
            certSha1 = signingCertSha1(context),
        ),
    )

    private val captureRemote = RtdbCaptureRemote(context)

    private val syncEngine = SyncEngine(
        scope = appScope,
        dao = database.captureDao(),
        remote = captureRemote,
        authState = authRepository.authState,
    ).also { it.start() }

    // Tags pre-existing blurts (saved before analysis existed, or offline)
    // with their AI category in the background after each sign-in.
    @Suppress("unused")
    private val categoryBackfiller = CategoryBackfiller(
        scope = appScope,
        repository = captureRepository,
        analyzer = captureAnalyzer,
        authState = authRepository.authState,
    ).also { it.start() }

    /**
     * SHA-1 of the certificate that actually signed this APK (colon-separated
     * hex, the format Google's Android-app key restrictions expect). Derived
     * at runtime so the header always matches the real signer.
     */
    internal fun signingCertSha1(context: Context): String = runCatching {
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
