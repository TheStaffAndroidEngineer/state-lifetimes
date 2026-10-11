package staff.android.lifetimes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One delegate, hence one DataStore instance for this file in this process.
val Context.cartDataStore by preferencesDataStore(name = "chapter7-cart")

class RevisionConflict : IllegalStateException("Cart changed; reload before confirming")

interface CartRepository {
    val line: Flow<CartLine>
    suspend fun confirm(baseRevision: Long, quantity: Int)
    suspend fun externalUpdate()
    suspend fun reset()
}

/** A deliberately small, local durable fixture, not a synchronized commerce backend. */
class DataStoreCartRepository(private val store: DataStore<Preferences>) : CartRepository {
    private val quantityKey = intPreferencesKey("quantity")
    private val revisionKey = longPreferencesKey("revision")
    private val confirmationsKey = intPreferencesKey("confirmations")

    private fun read(p: Preferences) = CartLine(
        p[quantityKey] ?: 2,
        p[revisionKey] ?: 0,
        p[confirmationsKey] ?: 0,
    )

    override val line: Flow<CartLine> = store.data.map(::read)

    override suspend fun confirm(baseRevision: Long, quantity: Int) {
        require(quantity in 0..5)
        store.edit { preferences ->
            val current = read(preferences)
            if (current.revision != baseRevision) throw RevisionConflict()
            if (current.quantity != quantity) {
                preferences[quantityKey] = quantity
                preferences[revisionKey] = current.revision + 1
                preferences[confirmationsKey] = current.confirmations + 1
            }
        }
    }

    override suspend fun externalUpdate() {
        store.edit { preferences ->
            val current = read(preferences)
            preferences[quantityKey] = if (current.quantity == 4) 1 else 4
            preferences[revisionKey] = current.revision + 1
        }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }
}
