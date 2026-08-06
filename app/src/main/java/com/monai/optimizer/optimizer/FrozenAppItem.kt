package com.monai.optimizer.optimizer

/**
 * Data Model App Freezer dengan Proteksi Cerdas Sistem.
 */
data class FrozenAppItem(
    val name: String,
    val pkg: String,
    val isSystem: Boolean,
    val isFrozen: Boolean,        // Status Freeze (suspended atau disabled)
    val isDisabled: Boolean,      // Status Disabled via pm disable-user
    val isCritical: Boolean = false, // True jika app sistem vital (TIDAK BISA DIBEKUKAN demi keamanan HP)
) {
    val isLocked: Boolean get() = isFrozen || isDisabled

    val typeLabel: String get() = when {
        isCritical -> "Protected System"
        isSystem   -> "System"
        else       -> "User App"
    }

    val stateLabel: String get() = when {
        isCritical -> "Protected"
        isDisabled -> "Disabled"
        isFrozen   -> "Frozen"
        else       -> "Active"
    }
}