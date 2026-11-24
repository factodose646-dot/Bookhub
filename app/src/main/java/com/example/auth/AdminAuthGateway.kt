package com.example.auth

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class AdminRole {
    LOCAL_ADMINISTRATOR,
    CLOUD_ADMINISTRATOR,
}

data class AdminSession(
    val adminName: String,
    val adminEmail: String,
    val role: AdminRole,
    val sessionToken: String,
    val authenticatedAtMillis: Long,
)

data class StoredAdminCredential(
    val adminName: String,
    val adminEmail: String,
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

sealed interface AdminAuthStatus {
    data object NeedsFirstTimeSetup : AdminAuthStatus
    data class Locked(
        val adminName: String,
        val adminEmail: String,
        val failedAttempts: Int,
        val cooldownRemainingSeconds: Int,
    ) : AdminAuthStatus
    data class Authenticated(val session: AdminSession) : AdminAuthStatus
}

sealed interface AdminAuthResult {
    data class Success(val session: AdminSession) : AdminAuthResult
    data class Failure(val message: String, val cooldownSeconds: Int = 0) : AdminAuthResult
}

interface AdminAuthPersistence {
    fun loadCredential(): StoredAdminCredential?
    fun saveCredential(credential: StoredAdminCredential)
}

class InMemoryAdminAuthPersistence : AdminAuthPersistence {
    private var stored: StoredAdminCredential? = null
    override fun loadCredential(): StoredAdminCredential? = stored
    override fun saveCredential(credential: StoredAdminCredential) {
        stored = credential
    }
}

class SharedPreferencesAdminAuthPersistence(
    context: Context,
) : AdminAuthPersistence {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadCredential(): StoredAdminCredential? {
        val hash = prefs.getString(KEY_HASH, null) ?: return null
        val salt = prefs.getString(KEY_SALT, null) ?: return null
        val name = prefs.getString(KEY_NAME, "Library Admin") ?: "Library Admin"
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val iterations = prefs.getInt(KEY_ITERATIONS, DEFAULT_ITERATIONS)
        return StoredAdminCredential(
            adminName = name,
            adminEmail = email,
            saltBase64 = salt,
            hashBase64 = hash,
            iterations = iterations,
        )
    }

    override fun saveCredential(credential: StoredAdminCredential) {
        prefs.edit()
            .putString(KEY_NAME, credential.adminName)
            .putString(KEY_EMAIL, credential.adminEmail)
            .putString(KEY_SALT, credential.saltBase64)
            .putString(KEY_HASH, credential.hashBase64)
            .putInt(KEY_ITERATIONS, credential.iterations)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "bookhub_admin_auth_prefs"
        const val KEY_NAME = "admin_name"
        const val KEY_EMAIL = "admin_email"
        const val KEY_SALT = "admin_salt_b64"
        const val KEY_HASH = "admin_hash_b64"
        const val KEY_ITERATIONS = "admin_iterations"
        const val DEFAULT_ITERATIONS = 65_536
    }
}

/**
 * Modular admin authentication contract prepared so Phase 6 can plug in
 * Firebase Authentication + server-verified custom claims without rewriting UI or ViewModel guards.
 */
interface AdminAuthGateway {
    fun getStatus(currentSession: AdminSession?, nowMillis: Long = System.currentTimeMillis()): AdminAuthStatus
    fun setupFirstAdmin(
        adminName: String,
        adminEmail: String,
        passcode: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): AdminAuthResult

    fun authenticate(
        passcode: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): AdminAuthResult

    fun changePasscode(
        session: AdminSession?,
        currentPasscode: String,
        newPasscode: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): AdminAuthResult

    fun verifySession(session: AdminSession?): Boolean
    fun logout(session: AdminSession?)
}

class LocalPasscodeAdminAuthGateway(
    private val persistence: AdminAuthPersistence,
) : AdminAuthGateway {

    private val random = SecureRandom()
    private var activeToken: String? = null
    private var failedAttempts: Int = 0
    private var lockedUntilMillis: Long = 0L

    override fun getStatus(currentSession: AdminSession?, nowMillis: Long): AdminAuthStatus {
        val credential = persistence.loadCredential() ?: return AdminAuthStatus.NeedsFirstTimeSetup
        if (currentSession != null && verifySession(currentSession)) {
            return AdminAuthStatus.Authenticated(currentSession)
        }
        val remainingSec = ((lockedUntilMillis - nowMillis).coerceAtLeast(0L) / 1000L).toInt()
        return AdminAuthStatus.Locked(
            adminName = credential.adminName,
            adminEmail = credential.adminEmail,
            failedAttempts = failedAttempts,
            cooldownRemainingSeconds = remainingSec,
        )
    }

    override fun setupFirstAdmin(
        adminName: String,
        adminEmail: String,
        passcode: String,
        nowMillis: Long,
    ): AdminAuthResult {
        if (persistence.loadCredential() != null) {
            return AdminAuthResult.Failure("Administrator credentials are already configured.")
        }
        val trimmedName = adminName.trim().ifEmpty { "Library Admin" }
        val trimmedEmail = adminEmail.trim()
        if (passcode.length < MIN_PASSCODE_LENGTH) {
            return AdminAuthResult.Failure(
                "Passcode must be at least $MIN_PASSCODE_LENGTH characters.",
            )
        }

        val salt = ByteArray(SALT_BYTES).apply { random.nextBytes(this) }
        val hash = pbkdf2(passcode, salt, ITERATIONS)
        val credential = StoredAdminCredential(
            adminName = trimmedName,
            adminEmail = trimmedEmail,
            saltBase64 = encodeBase64(salt),
            hashBase64 = encodeBase64(hash),
            iterations = ITERATIONS,
        )
        persistence.saveCredential(credential)
        failedAttempts = 0
        lockedUntilMillis = 0L

        val session = issueSession(credential, nowMillis)
        return AdminAuthResult.Success(session)
    }

    override fun authenticate(
        passcode: String,
        nowMillis: Long,
    ): AdminAuthResult {
        val credential = persistence.loadCredential()
            ?: return AdminAuthResult.Failure("No administrator profile configured yet.")

        if (nowMillis < lockedUntilMillis) {
            val remaining = (((lockedUntilMillis - nowMillis) + 999L) / 1000L).toInt()
            return AdminAuthResult.Failure(
                message = "Too many failed attempts. Wait ${remaining}s and try again.",
                cooldownSeconds = remaining,
            )
        }

        if (passcode.isEmpty()) {
            return AdminAuthResult.Failure("Please enter your administrator passcode.")
        }

        val salt = decodeBase64(credential.saltBase64)
        val expectedHash = decodeBase64(credential.hashBase64)
        val candidateHash = pbkdf2(passcode, salt, credential.iterations)

        return if (MessageDigest.isEqual(expectedHash, candidateHash)) {
            failedAttempts = 0
            lockedUntilMillis = 0L
            AdminAuthResult.Success(issueSession(credential, nowMillis))
        } else {
            failedAttempts += 1
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntilMillis = nowMillis + LOCKOUT_DURATION_MS
                failedAttempts = 0
                AdminAuthResult.Failure(
                    message = "Incorrect passcode. Admin login paused for 30 seconds.",
                    cooldownSeconds = (LOCKOUT_DURATION_MS / 1000L).toInt(),
                )
            } else {
                val remainingTries = MAX_FAILED_ATTEMPTS - failedAttempts
                AdminAuthResult.Failure(
                    message = "Incorrect passcode. $remainingTries attempt(s) left before cooldown.",
                )
            }
        }
    }

    override fun changePasscode(
        session: AdminSession?,
        currentPasscode: String,
        newPasscode: String,
        nowMillis: Long,
    ): AdminAuthResult {
        if (!verifySession(session)) {
            return AdminAuthResult.Failure("Administrator authorization required.")
        }
        val credential = persistence.loadCredential()
            ?: return AdminAuthResult.Failure("Administrator credentials not found.")
        if (newPasscode.length < MIN_PASSCODE_LENGTH) {
            return AdminAuthResult.Failure("New passcode must be at least $MIN_PASSCODE_LENGTH characters.")
        }

        val currentSalt = decodeBase64(credential.saltBase64)
        val expectedHash = decodeBase64(credential.hashBase64)
        val candidateHash = pbkdf2(currentPasscode, currentSalt, credential.iterations)
        if (!MessageDigest.isEqual(expectedHash, candidateHash)) {
            return AdminAuthResult.Failure("Current passcode does not match.")
        }

        val newSalt = ByteArray(SALT_BYTES).apply { random.nextBytes(this) }
        val newHash = pbkdf2(newPasscode, newSalt, ITERATIONS)
        val updated = credential.copy(
            saltBase64 = encodeBase64(newSalt),
            hashBase64 = encodeBase64(newHash),
            iterations = ITERATIONS,
        )
        persistence.saveCredential(updated)
        return AdminAuthResult.Success(issueSession(updated, nowMillis))
    }

    override fun verifySession(session: AdminSession?): Boolean {
        val currentToken = activeToken ?: return false
        return session != null &&
            currentToken.isNotEmpty() &&
            MessageDigest.isEqual(
                currentToken.toByteArray(Charsets.UTF_8),
                session.sessionToken.toByteArray(Charsets.UTF_8),
            )
    }

    override fun logout(session: AdminSession?) {
        if (session == null || verifySession(session)) {
            activeToken = null
        }
    }

    private fun issueSession(credential: StoredAdminCredential, nowMillis: Long): AdminSession {
        val tokenBytes = ByteArray(32).apply { random.nextBytes(this) }
        val token = encodeBase64(tokenBytes)
        activeToken = token
        return AdminSession(
            adminName = credential.adminName,
            adminEmail = credential.adminEmail,
            role = AdminRole.LOCAL_ADMINISTRATOR,
            sessionToken = token,
            authenticatedAtMillis = nowMillis,
        )
    }

    private fun pbkdf2(passcode: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            out.append(BASE64_ALPHABET[(triple ushr 18) and 0x3F])
            out.append(BASE64_ALPHABET[(triple ushr 12) and 0x3F])
            out.append(if (hasB1) BASE64_ALPHABET[(triple ushr 6) and 0x3F] else '=')
            out.append(if (hasB2) BASE64_ALPHABET[triple and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    private fun decodeBase64(value: String): ByteArray {
        val clean = value.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)
        val out = ArrayList<Byte>((clean.length * 3) / 4)
        var i = 0
        while (i + 3 < clean.length) {
            val c0 = base64Index(clean[i])
            val c1 = base64Index(clean[i + 1])
            val c2Char = clean[i + 2]
            val c3Char = clean[i + 3]
            val c2 = if (c2Char == '=') 0 else base64Index(c2Char)
            val c3 = if (c3Char == '=') 0 else base64Index(c3Char)
            if (c0 < 0 || c1 < 0 || (c2Char != '=' && c2 < 0) || (c3Char != '=' && c3 < 0)) {
                return ByteArray(0)
            }
            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            out.add(((triple ushr 16) and 0xFF).toByte())
            if (c2Char != '=') out.add(((triple ushr 8) and 0xFF).toByte())
            if (c3Char != '=') out.add((triple and 0xFF).toByte())
            i += 4
        }
        return out.toByteArray()
    }

    private fun base64Index(ch: Char): Int = when (ch) {
        in 'A'..'Z' -> ch - 'A'
        in 'a'..'z' -> (ch - 'a') + 26
        in '0'..'9' -> (ch - '0') + 52
        '+' -> 62
        '/' -> 63
        else -> -1
    }

    private companion object {
        const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        const val MIN_PASSCODE_LENGTH = 6
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val ITERATIONS = 65_536
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L
    }
}
