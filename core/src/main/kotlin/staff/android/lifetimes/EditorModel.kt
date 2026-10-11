package staff.android.lifetimes

data class CartLine(
    val quantity: Int = 2,
    val revision: Long = 0,
    val confirmations: Int = 0,
)

data class QuantityDraft(val raw: String, val baseRevision: Long)

data class EditorState(
    val cart: CartLine? = null,
    val draft: QuantityDraft? = null,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val candidate: Int?
        get() = draft?.raw?.trim()?.toIntOrNull()?.takeIf { it in 0..5 }
    val conflict: Boolean
        get() = draft != null && cart != null && draft.baseRevision != cart.revision
    val canConfirm: Boolean
        get() = cart != null && candidate != null && !conflict && !busy
}

data class Assignment(val id: String, val title: String)

fun interface NotePreviewer {
    suspend fun preview(id: String, note: String): String
}
