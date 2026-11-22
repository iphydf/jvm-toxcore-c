package chat.tox.ktox

import androidx.compose.ui.graphics.ImageBitmap
import im.tox.tox4j.core.enums.ToxGroupRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Clients on a network of their own: the others join through the first. */
class ToxClientTest {
    /**
     * Takes pictures as they are, so tests can check what arrives. Its microphone hums, its camera films grey,
     * and its speaker keeps the pitch of the sound it played.
     */
    private class TestPlatform : Platform {
        /** The pitch of each frame played, in Hz, or 0 if it was quiet. */
        val heard = MutableStateFlow(listOf<Int>())

        override fun copy(text: String) {}

        override fun videoImages(): VideoImages = error("tests don't show pictures")

        override fun openSound(
            recorded: (ShortArray) -> Unit,
            playout: Playout,
        ): CallSound {
            val thread =
                thread(name = "test sound") {
                    var n = 0L
                    while (!Thread.interrupted()) {
                        recorded(ShortArray(AUDIO_FRAME) { (sin(2 * PI * 440 * (n + it) / AUDIO_RATE) * 8000).toInt().toShort() })
                        n += AUDIO_FRAME
                        val played = playout.next()
                        heard.update { it + pitch(played) }
                        try {
                            Thread.sleep(20)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            return object : CallSound {
                override fun close() {
                    thread.interrupt()
                    thread.join()
                }
            }
        }

        override val cameras get() = 1

        override fun openCamera(
            front: Boolean,
            frame: (VideoFrame) -> Unit,
        ): AutoCloseable {
            val thread =
                thread(name = "test camera") {
                    while (!Thread.interrupted()) {
                        frame(VideoFrame(64, 48, ByteArray(64 * 48) { 100 }, ByteArray(32 * 24) { -128 }, ByteArray(32 * 24) { -128 }))
                        try {
                            Thread.sleep(50)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            return AutoCloseable {
                thread.interrupt()
                thread.join()
            }
        }

        override fun pickFile(
            images: Boolean,
            picked: (PickedFile) -> Unit,
        ) {}

        override fun open(
            file: File,
            name: String,
        ) {}

        override fun saveToDownloads(
            file: File,
            name: String,
        ): String? = null

        override fun decodeImage(
            file: File,
            size: Int,
        ): ImageBitmap? = null

        override fun avatarPng(
            image: ByteArray,
            size: Int,
        ) = image
    }

    private val dirs = mutableListOf<File>()
    private val clients = mutableListOf<ToxClient>()

    private fun newDir() = createTempDirectory("ktox-client").toFile().also(dirs::add)

    private fun client(
        nodes: List<Node>,
        dir: File = newDir(),
        platform: Platform = TestPlatform(),
        line: CallLine = CallLine(),
    ) = ToxClient(dir, ProfileId("1"), platform, line, nodes).also(clients::add).apply { start() }

    @AfterTest
    fun cleanUp() {
        clients.forEach(ToxClient::close)
        dirs.forEach { it.deleteRecursively() }
    }

    private fun test(body: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(TIMEOUT_MS, body) }

    private suspend fun ToxClient.address() = state.first { it.profile.address.isNotEmpty() }.profile.address

    private suspend fun ToxClient.friend() = state.first { it.friends.singleOrNull()?.online == true }.friends.single()

    @Test
    fun close_stopsTheLoopBeforeTox() =
        test {
            val failures = mutableListOf<Throwable>()
            val handler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(failures) { failures += e } }
            try {
                val a = client(emptyList())
                a.address()
                a.close()
                clients.remove(a)
                // Longer than the loop waits between turns.
                delay(500)
                assertEquals(emptyList(), synchronized(failures) { failures.toList() })
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(handler)
            }
        }

    /** a and b, friends and online. */
    private suspend fun friends(
        aPlatform: Platform = TestPlatform(),
        bPlatform: Platform = TestPlatform(),
        aLine: CallLine = CallLine(),
    ): Pair<ToxClient, ToxClient> {
        val a = client(emptyList(), platform = aPlatform, line = aLine)
        val b = client(listOf(a.localNode()), platform = bPlatform)
        assertNull(b.addFriend(a.address(), "Hi, it's b"))
        a.accept(
            a.state
                .first { it.requests.isNotEmpty() }
                .requests
                .single(),
        )
        a.friend()
        b.friend()
        return a to b
    }

    @Test
    fun friends_chatAndGetReceipts() =
        test {
            val a = client(emptyList())
            val aAddress = a.address()
            val b = client(listOf(a.localNode()))

            assertNull(b.addFriend(aAddress, "Hi, it's b"))
            val request =
                a.state
                    .first { it.requests.isNotEmpty() }
                    .requests
                    .single()
            assertEquals("Hi, it's b", request.message)
            a.accept(request)

            val aKey = FriendKey(aAddress.take(PUBLIC_KEY_HEX))
            b.send(aKey, "hello a")
            // Sent before a's friend connection came up: it goes once it does.
            b.state.first {
                it.friends
                    .single()
                    .last
                    ?.delivered == true
            }
            val friendOfA =
                a.state
                    .first {
                        it.friends
                            .singleOrNull()
                            ?.last
                            ?.text == "hello a"
                    }.friends
                    .single()
            assertEquals(1, friendOfA.unread)
            assertEquals(request.key, friendOfA.key)
        }

    @Test
    fun file_arrivesWhenAccepted() =
        test {
            val (a, b) = friends()
            val data = Random.nextBytes(100_000)
            b.sendFile(b.friend().key, PickedFile("notes.txt") { data.inputStream() })

            val offered =
                a.state
                    .first {
                        it.friends
                            .single()
                            .last
                            ?.file
                            ?.state == FileState.OFFERED
                    }.friends
                    .single()
                    .last!!
            assertEquals("notes.txt", offered.file!!.name)
            a.acceptFile(offered)

            val done =
                a.state
                    .first {
                        it.friends
                            .single()
                            .last
                            ?.file
                            ?.state == FileState.DONE
                    }.friends
                    .single()
                    .last!!
            assertContentEquals(data, done.file!!.file.readBytes())
            b.state.first {
                it.friends
                    .single()
                    .last
                    ?.file
                    ?.state == FileState.DONE
            }
        }

    @Test
    fun file_goesOnAfterTheReceiverRestarts() =
        test {
            val (a, b) = friends()
            val aDir = dirs.first()
            val data = Random.nextBytes(3_000_000)
            b.sendFile(b.friend().key, PickedFile("big.bin") { data.inputStream() })
            val offered =
                a.state
                    .first {
                        it.friends
                            .single()
                            .last
                            ?.file
                            ?.state == FileState.OFFERED
                    }.friends
                    .single()
                    .last!!
            a.acceptFile(offered)
            a.state.first { it.progress.values.any { done -> done > 100_000 } }
            a.close()
            clients.remove(a)

            val again = client(listOf(b.localNode()), aDir)
            val stopped =
                again.state
                    .first { it.friends.isNotEmpty() }
                    .friends
                    .single()
                    .last!!
                    .file!!
            assertEquals(FileState.ACTIVE, stopped.state)
            assertTrue(stopped.done > 0, "the restart forgot how far it got")
            val done =
                again.state
                    .first {
                        it.friends
                            .single()
                            .last
                            ?.file
                            ?.state == FileState.DONE
                    }.friends
                    .single()
                    .last!!
            assertContentEquals(data, done.file!!.file.readBytes())
        }

    @Test
    fun avatar_arrivesAndGoes() =
        test {
            val (a, b) = friends()
            val picture = Random.nextBytes(5_000)
            assertTrue(b.setAvatar(picture))
            val arrived =
                a.state
                    .first { it.friends.single().avatar != null }
                    .friends
                    .single()
                    .avatar!!
            assertContentEquals(picture, arrived.file.readBytes())

            b.setAvatar(null)
            a.state.first { it.friends.single().avatar == null }
        }

    /** a starts a group and b joins it from an invite; both are in it after. */
    private suspend fun groupOfTwo(
        a: ToxClient,
        b: ToxClient,
        private: Boolean,
    ): GroupId {
        assertNull(a.createGroup("Team", private))
        val id =
            a.state
                .first { it.groups.singleOrNull()?.connected == true }
                .groups
                .single()
                .id
        a.inviteFriend(id, a.friend().key)
        val invite =
            b.state
                .first { it.invites.isNotEmpty() }
                .invites
                .single()
        assertEquals("Team", invite.name)
        b.acceptInvite(invite)
        a.state.first {
            it.groups
                .single()
                .peers.size == 2
        }
        b.state.first {
            it.groups
                .singleOrNull()
                ?.peers
                ?.size == 2
        }
        return id
    }

    @Test
    fun group_invitedFriendChats() =
        test {
            val (a, b) = friends()
            val id = groupOfTwo(a, b, private = true)
            assertEquals(
                id,
                b.state.value.groups
                    .single()
                    .id,
            )
            assertEquals(
                ToxGroupRole.FOUNDER,
                a.state.value.groups
                    .single()
                    .selfRole,
            )

            b.sendGroupMessage(id, "hi team")
            val arrived =
                a.state
                    .first {
                        it.groups
                            .single()
                            .last
                            ?.text == "hi team"
                    }.groups
                    .single()
            assertEquals(1, arrived.unread)
            assertTrue(!arrived.last!!.outgoing)

            a.sendGroupMessage(id, "welcome")
            b.state.first {
                it.groups
                    .single()
                    .last
                    ?.text == "welcome"
            }
        }

    @Test
    fun group_staysAfterTheFounderRestarts() =
        test {
            val (a, b) = friends()
            val aDir = dirs.first()
            val id = groupOfTwo(a, b, private = true)
            a.close()
            clients.remove(a)

            val again = client(listOf(b.localNode()), aDir)
            val group =
                again.state
                    .first {
                        it.groups
                            .singleOrNull()
                            ?.peers
                            ?.size == 2
                    }.groups
                    .single()
            assertEquals(id, group.id)
            assertEquals(ToxGroupRole.FOUNDER, group.selfRole)
        }

    @Test
    fun group_joinerKeepsItWhenKilled() =
        test {
            val (a, b) = friends()
            val id = groupOfTwo(a, b, private = true)
            // A turn of b's loop after joining, which saves what changed.
            b.localNode()

            // Android kills apps without closing them: the next start has only what's on disk.
            val copy = newDir()
            dirs[1].copyRecursively(copy, overwrite = true)
            val again = client(emptyList(), copy)
            val state = again.state.first { it.profile.address.isNotEmpty() }
            assertEquals(listOf(id), state.groups.map { it.id })
        }

    @Test
    fun call_soundAndVideoGoBothWays() =
        test {
            val aPlatform = TestPlatform()
            val bPlatform = TestPlatform()
            val (a, b) = friends(aPlatform, bPlatform)
            b.call(b.friend().key, video = true)
            val ringing = a.state.first { it.call != null }.call!!
            assertTrue(ringing.ringing && ringing.videoCall && !ringing.outgoing)

            a.answer(video = true)
            b.state.first { it.call?.answered == true }
            // b heard its ringback tone until now.
            val rang = bPlatform.heard.value.size
            // The hum came through as a hum.
            aPlatform.heard.first { heard -> heard.count { it in HUM } >= 10 }
            bPlatform.heard.first { heard -> heard.drop(rang).count { it in HUM } >= 10 }
            assertEquals(
                64,
                a.callVideo.friend
                    .filterNotNull()
                    .first()
                    .width,
            )
            assertEquals(
                64,
                b.callVideo.friend
                    .filterNotNull()
                    .first()
                    .width,
            )

            b.hangUp()
            val note =
                a.state.first {
                    it.call == null &&
                        it.friends
                            .single()
                            .last
                            ?.call != null
                }
            assertEquals(
                CallOutcome.ANSWERED,
                note.friends
                    .single()
                    .last!!
                    .call!!
                    .outcome,
            )
        }

    @Test
    fun call_missedCountsAsUnread() =
        test {
            val (a, b) = friends()
            b.call(b.friend().key, video = false)
            a.state.first { it.call != null }
            b.hangUp()
            val friend =
                a.state
                    .first {
                        it.call == null &&
                            it.friends
                                .single()
                                .last
                                ?.call != null
                    }.friends
                    .single()
            assertEquals("Missed call", friend.last!!.callText())
            assertEquals(1, friend.unread)
        }

    @Test
    fun call_whileAnotherProfileHasOneIsMissed() =
        test {
            val line = CallLine()
            val (a, b) = friends(aLine = line)
            // Another of a's profiles is in a call.
            line.take(ProfileId("2"))
            b.call(b.friend().key, video = false)
            val friend =
                a.state
                    .first {
                        it.friends
                            .single()
                            .last
                            ?.call != null
                    }.friends
                    .single()
            assertEquals("Missed call", friend.last!!.callText())
            assertNull(a.state.value.call)
            b.state.first { it.call == null }
        }

    private companion object {
        /** Louder than silence: the hum is 8000. */
        const val LOUD = 2000
        const val TIMEOUT_MS = 120_000L
        const val PUBLIC_KEY_HEX = 64

        /** The hum's 440 Hz, give or take what Opus does to it. */
        val HUM = 400..480

        /** The pitch of a loud frame, from how often it crosses zero, or 0 for a quiet one. */
        fun pitch(frame: SoundFrame): Int {
            val samples = frame.pcm.filterIndexed { i, _ -> i % frame.channels == 0 }
            if (samples.maxOf { abs(it.toInt()) } < LOUD) return 0
            val crossings = samples.zipWithNext().count { (x, y) -> (x < 0) != (y < 0) }
            return crossings * frame.rate / samples.size / 2
        }
    }
}
