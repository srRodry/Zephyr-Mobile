package xendroid.compose.core

import java.util.concurrent.Executor

/** Serializes blocking native pause/resume fences away from Android's input thread.
 * Requests coalesce while a fence is pending; resume must never overtake pause.
 * The executor and native core share the lifetime of the single-shot :emu process.
 */
internal class AsyncPauseController(
    private val executor: Executor,
    private val apply: (Boolean) -> Unit,
) {
    data class State(
        val requested: Boolean = false,
        val confirmed: Boolean = false,
        val pending: Boolean = false,
        val failure: String? = null,
    )

    @Volatile var state = State()
        private set
    private val lock = Any()
    private var scheduled = false

    fun request(paused: Boolean) {
        synchronized(lock) {
            state = state.copy(requested = paused, failure = null)
            if (scheduled || state.confirmed == paused) return
            scheduled = true
            state = state.copy(pending = true)
        }
        try {
            executor.execute(::drain)
        } catch (error: Exception) {
            synchronized(lock) {
                scheduled = false
                state = state.copy(pending = false, failure = error.toString())
            }
        }
    }

    private fun drain() {
        while (true) {
            val target = synchronized(lock) {
                if (state.confirmed == state.requested) {
                    scheduled = false
                    state = state.copy(pending = false)
                    return
                }
                state.requested
            }
            try {
                apply(target)
            } catch (error: Exception) {
                synchronized(lock) {
                    scheduled = false
                    state = state.copy(pending = false, failure = error.toString())
                }
                return
            }
            synchronized(lock) { state = state.copy(confirmed = target) }
        }
    }
}
