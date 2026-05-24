package im.tox.tox4j.e2e

import kotlin.test.Test

/**
 * One-shot integration scenario that pays the multi-tox connection
 * setup cost a single time and then runs a sequence of per-feature
 * subtests against the same harness. Mirrors
 * `rs-toxcore-c/toxcore/tests/integration_test.rs::integration_suite`.
 *
 * Tagged `e2e` in BUILD.bazel so the fast tier
 * (`--test_tag_filters=-e2e`) skips it.
 */
class Tox4jIntegrationTest {
    @Test
    fun integrationSuite() {
        Tox4jHarness().use { h ->
            h.addTox()
            h.addTox()
            h.connect(0, 1)
            h.waitForFriendConnected(0, 1)
            h.waitForFriendConnected(1, 0)

            SubtestMessage.run(h)
            SubtestCustomPacket.run(h)
            SubtestFile.run(h)
            SubtestFileCancel.run(h)
            SubtestFriendInfo.run(h)
            SubtestConference.run(h)
            SubtestGroup.run(h)
            SubtestToxav.run(h)
            SubtestPersistence.run(h)
        }
    }
}
