// Root build file: only declares plugin versions (from the version catalog) for the modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
}
