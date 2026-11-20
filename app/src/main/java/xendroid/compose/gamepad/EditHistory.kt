package xendroid.compose.gamepad

/**
 * The touch editor's undo: the state before each change, the newest [limit] kept. A change that
 * leaves the state as it was adds nothing. Pure, tested on the JVM.
 */
class EditHistory<T : Any>(private val limit: Int = 40) {
    private val stack = ArrayDeque<T>()
    private val undone = ArrayDeque<T>()

    val canRedo: Boolean get() = undone.isNotEmpty()
    val canUndo: Boolean get() = stack.isNotEmpty()
    val size: Int get() = stack.size

    /** [before] is the state a change is about to replace. */
    fun push(before: T) {
        undone.clear()
        if (stack.lastOrNull() == before) return
        append(before)
    }

    private fun append(before: T) {
        stack.addLast(before)
        while (stack.size > limit) stack.removeFirst()
    }

    /** The state before the last change, which is forgotten; null when there is none. */
    fun undo(): T? = stack.removeLastOrNull()

    fun undo(current: T): T? = undo()?.also { undone.addLast(current) }

    fun redo(current: T): T? = undone.removeLastOrNull()?.also { append(current) }

    fun clear() {
        stack.clear()
        undone.clear()
    }
}
