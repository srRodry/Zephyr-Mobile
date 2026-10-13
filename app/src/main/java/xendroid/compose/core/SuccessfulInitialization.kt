package xendroid.compose.core

/** Failed initialization remains retryable; successful work runs once per instance. */
internal class SuccessfulInitialization {
    private var complete = false

    @Synchronized
    fun run(initialize: () -> Unit) {
        if (complete) return
        initialize()
        complete = true
    }
}
