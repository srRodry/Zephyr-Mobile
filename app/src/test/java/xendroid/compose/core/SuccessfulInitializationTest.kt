package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SuccessfulInitializationTest {
    @Test fun failedConfigWriteCanBeRetriedAndSuccessIsNotRepeated() {
        val initialization = SuccessfulInitialization()
        var attempts = 0
        assertThrows(IOException::class.java) {
            initialization.run { attempts++; throw IOException("storage unavailable") }
        }
        initialization.run { attempts++ }
        initialization.run { attempts++ }
        assertEquals(2, attempts)
    }

    @Test fun missingNativeUpdatesCanBeRetried() {
        val initialization = SuccessfulInitialization()
        var attempts = 0
        assertThrows(UnsatisfiedLinkError::class.java) {
            initialization.run { attempts++; throw UnsatisfiedLinkError("native updates unavailable") }
        }
        initialization.run { attempts++ }
        assertEquals(2, attempts)
    }

    @Test fun concurrentRequestsOnlyApplySuccessfulUpdatesOnce() {
        val initialization = SuccessfulInitialization()
        val pool = Executors.newFixedThreadPool(4)
        var applications = 0
        try {
            (1..12).map { pool.submit { initialization.run { applications++ } } }
                .forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, applications)
        } finally { pool.shutdownNow() }
    }
}
