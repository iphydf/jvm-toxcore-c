package chat.tox.ktox

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The profiles on disk. Their Tox never starts here. */
class ProfilesTest {
    private object NoPlatform : Platform {
        override fun copy(text: String) {}

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
        ): ByteArray? = null

        override fun videoImages(): VideoImages = error("no pictures here")
    }

    private val root = createTempDirectory("ktox-profiles").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun ids(profiles: Profiles) = profiles.clients.value.map { it.id.name }

    @Test
    fun oldProfile_becomesTheFirst() {
        File(root, "profile.tox").writeText("tox")
        File(root, "chats.db").writeText("db")
        File(root, "files/1").mkdirs()
        val profiles = Profiles(root, NoPlatform, emptyList())
        assertEquals(listOf("1"), ids(profiles))
        assertEquals("tox", File(root, "profiles/1/profile.tox").readText())
        assertEquals("db", File(root, "profiles/1/chats.db").readText())
        assertTrue(File(root, "profiles/1/files/1").isDirectory)
        assertFalse(File(root, "profile.tox").exists())
        profiles.close()
    }

    @Test
    fun added_isShownAndStaysShown() {
        val profiles = Profiles(root, NoPlatform, emptyList())
        profiles.add()
        assertEquals(listOf("1", "2"), ids(profiles))
        assertEquals("2", profiles.shown.value.id.name)
        profiles.close()
        val again = Profiles(root, NoPlatform, emptyList())
        assertEquals(listOf("1", "2"), ids(again))
        assertEquals("2", again.shown.value.id.name)
        again.close()
    }

    @Test
    fun delete_keepsTheLastAndOneInACall() {
        val profiles = Profiles(root, NoPlatform, emptyList())
        profiles.delete(ProfileId("1"))
        assertEquals(listOf("1"), ids(profiles))
        profiles.add()
        profiles.line.take(ProfileId("1"))
        profiles.delete(ProfileId("1"))
        assertEquals(listOf("1", "2"), ids(profiles))
        profiles.line.release(ProfileId("1"))
        profiles.delete(ProfileId("1"))
        assertEquals(listOf("2"), ids(profiles))
        assertFalse(File(root, "profiles/1").exists())
        profiles.close()
    }
}
