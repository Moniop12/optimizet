package com.monai.optimizer.optimizer

/**
 * Representasi satu app di System App Freezer.
 * [isFrozen] = suspended via `pm suspend`
 * [isDisabled] = disabled via `pm disable-user`
 */
data class FrozenAppItem(
    val name: String,
    val pkg: String,
    val isSystem: Boolean,
    val isFrozen: Boolean,
    val isDisabled: Boolean,
) {
    /** Frozen = suspended OR disabled */
    val isLocked: Boolean get() = isFrozen || isDisabled

    val typeLabel: String get() = if (isSystem) "System" else "User"

    val stateLabel: String get() = when {
        isDisabled -> "Disabled"
        isFrozen   -> "Frozen"
        else       -> "Active"
    }
}
