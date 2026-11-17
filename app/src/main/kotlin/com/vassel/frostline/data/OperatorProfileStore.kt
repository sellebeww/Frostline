// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The local operator. [userId] is what custody events carry as "user"; name and role stay on the device. */
data class OperatorProfile(val userId: String, val name: String, val role: String)

interface OperatorStore {
    fun get(): OperatorProfile?

    /** Returns null when [name] is blank. An existing profile is replaced but keeps its [OperatorProfile.userId]. */
    fun save(name: String, role: String): OperatorProfile?
}

/** Created on first use. Stored in private preferences, which the backup rules exclude. */
@Singleton
class OperatorProfileStore @Inject constructor(@ApplicationContext context: Context) : OperatorStore {
    private val prefs = context.getSharedPreferences("frostline_operator", Context.MODE_PRIVATE)

    override fun get(): OperatorProfile? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return OperatorProfile(id, name, prefs.getString(KEY_ROLE, "") ?: "")
    }

    override fun save(name: String, role: String): OperatorProfile? {
        val cleanName = name.trim().take(MAX_LENGTH)
        if (cleanName.isEmpty()) return null
        val profile = OperatorProfile(get()?.userId ?: UUID.randomUUID().toString(), cleanName, role.trim().take(MAX_LENGTH))
        prefs.edit { putString(KEY_ID, profile.userId).putString(KEY_NAME, profile.name).putString(KEY_ROLE, profile.role) }
        return profile
    }

    private companion object {
        const val KEY_ID = "user_id"
        const val KEY_NAME = "name"
        const val KEY_ROLE = "role"
        const val MAX_LENGTH = 60
    }
}
