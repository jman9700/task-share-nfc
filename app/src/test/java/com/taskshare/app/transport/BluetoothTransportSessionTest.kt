package com.taskshare.app.transport

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Exercises syncApkIfOutdated's role-negotiation with two BluetoothTransportSession instances
 * linked by in-memory piped streams, standing in for two real Bluetooth sockets. This is the
 * trickiest new logic in the APK-distribution work — both sides must independently agree on
 * roles and on whether/which direction bytes should move — so it's worth a real two-ended test
 * rather than trusting it by inspection.
 */
class BluetoothTransportSessionTest {

    private data class Linked(val active: BluetoothTransportSession, val passive: BluetoothTransportSession)

    /** Two sessions wired to each other via piped streams, playing the active and passive roles. */
    private fun linkedSessions(): Linked {
        val activeToPassive = PipedOutputStream()
        val passiveReadsFromActive = PipedInputStream(activeToPassive)
        val passiveToActive = PipedOutputStream()
        val activeReadsFromPassive = PipedInputStream(passiveToActive)

        val active = BluetoothTransportSession(DataInputStream(activeReadsFromPassive), DataOutputStream(activeToPassive)) {}
        val passive = BluetoothTransportSession(DataInputStream(passiveReadsFromActive), DataOutputStream(passiveToActive)) {}
        return Linked(active, passive)
    }

    // `runBlocking<Unit>` (not plain `runBlocking`) on every test method here: JUnit4 test
    // methods must return Unit/void, and without the explicit type argument, an expression body
    // would infer the method's return type from the lambda's last statement (e.g. File.delete()
    // returns Boolean) instead of coercing it to Unit — which JUnit rejects at class-validation
    // time with "should be void", not a compile error, so it's easy to miss.
    @Test
    fun `active side older than passive receives the passive side's apk bytes`() = runBlocking<Unit> {
        val (active, passive) = linkedSessions()
        val sourceApk = File.createTempFile("passive-source", ".apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val destination = File.createTempFile("active-dest", ".apk")

        val activeResult = async {
            active.syncApkIfOutdated(
                localVersionCode = 1, peerVersionCode = 2, isActiveSide = true,
                localApkSource = File("unused"), destination = destination,
            )
        }
        val passiveResult = async {
            passive.syncApkIfOutdated(
                localVersionCode = 2, peerVersionCode = 1, isActiveSide = false,
                localApkSource = sourceApk, destination = File("unused"),
            )
        }

        val received = activeResult.await()
        assertNull(passiveResult.await())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), received?.readBytes())

        sourceApk.delete()
        destination.delete()
    }

    @Test
    fun `active side already newer than passive receives nothing`() = runBlocking<Unit> {
        val (active, passive) = linkedSessions()
        val destination = File.createTempFile("active-dest", ".apk")

        val activeResult = async {
            active.syncApkIfOutdated(
                localVersionCode = 5, peerVersionCode = 3, isActiveSide = true,
                localApkSource = File("unused"), destination = destination,
            )
        }
        val passiveResult = async {
            passive.syncApkIfOutdated(
                localVersionCode = 3, peerVersionCode = 5, isActiveSide = false,
                localApkSource = File("unused"), destination = File("unused"),
            )
        }

        assertNull(activeResult.await())
        assertNull(passiveResult.await())
        destination.delete()
    }

    @Test
    fun `equal versions transfer nothing either way`() = runBlocking<Unit> {
        val (active, passive) = linkedSessions()

        val activeResult = async {
            active.syncApkIfOutdated(
                localVersionCode = 4, peerVersionCode = 4, isActiveSide = true,
                localApkSource = File("unused"), destination = File("unused"),
            )
        }
        val passiveResult = async {
            passive.syncApkIfOutdated(
                localVersionCode = 4, peerVersionCode = 4, isActiveSide = false,
                localApkSource = File("unused"), destination = File("unused"),
            )
        }

        assertNull(activeResult.await())
        assertNull(passiveResult.await())
    }
}
