package xendroid.compose.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AsyncPauseControllerTest {
    @Test fun blockedNativePauseDoesNotBlockCallerOrAllowResumeToOvertakeIt() {
        val worker = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val calls = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val controller = AsyncPauseController(worker) { paused ->
            calls.add(paused)
            if (paused) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            } else resumed.countDown()
        }
        try {
            controller.request(true) // Returns while the native fence is still blocked.
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(controller.state.pending)
            assertFalse(controller.state.confirmed)
            repeat(100) { controller.request(true) }
            controller.request(false)
            assertEquals(listOf(true), calls.toList())
            release.countDown()
            assertTrue(resumed.await(2, TimeUnit.SECONDS))
            worker.shutdown()
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS))
            assertEquals(listOf(true, false), calls.toList())
            assertFalse(controller.state.pending)
            assertFalse(controller.state.confirmed)
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test fun coalescesBeforeWorkerStartsAndCanRetryAfterFailure() {
        val work = ArrayDeque<Runnable>()
        val calls = mutableListOf<Boolean>()
        var fail = true
        val controller = AsyncPauseController(Executor { work.addLast(it) }) {
            if (fail) throw IllegalStateException("test failure")
            calls.add(it)
        }
        controller.request(true)
        controller.request(false)
        assertEquals(1, work.size)
        work.removeFirst().run()
        assertTrue(calls.isEmpty())
        assertFalse(controller.state.pending)
        controller.request(true)
        work.removeFirst().run()
        assertNotNull(controller.state.failure)
        assertFalse(controller.state.confirmed)
        fail = false
        controller.request(true)
        work.removeFirst().run()
        assertEquals(listOf(true), calls)
        assertTrue(controller.state.confirmed)
        assertFalse(controller.state.pending)
        assertNull(controller.state.failure)
    }
}
