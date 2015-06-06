package im.tox.tox4j.core.bench

import im.tox.tox4j.bench.PerformanceReportBase._
import im.tox.tox4j.bench.{ Confidence, TimingReport }
import im.tox.tox4j.core.{ ToxCoreConstants, ToxCore }
import im.tox.tox4j.core.enums.{ ToxMessageType, ToxUserStatus, ToxConnection, ToxFileControl }
import im.tox.tox4j.impl.jni.ToxCoreImpl

/**
 * Work in progress benchmarks.
 */
final class BenchWip extends TimingReport {

  protected override def confidence = Confidence.high

  val publicKey = Array.ofDim[Byte](ToxCoreConstants.PUBLIC_KEY_SIZE)
  val data = Array.ofDim[Byte](ToxCoreConstants.MAX_CUSTOM_PACKET_SIZE)

  def invokePerformance(method: String, f: ToxCoreImpl => Unit): Unit = {
    performance of method in {
      usingTox(iterations1k) in {
        case (sz, tox: ToxCoreImpl) =>
          (0 until sz) foreach { _ =>
            f(tox)
            tox.iterate()
          }
      }
    }
  }

  def invokeAllCallbacks(tox: ToxCoreImpl): Unit = {
    tox.invokeFileChunkRequest(1, 2, 3, 4)
    tox.invokeFileRecv(1, 2, 3, 4, data)
    tox.invokeFileRecvChunk(1, 2, 3, data)
    tox.invokeFileRecvControl(1, 2, ToxFileControl.PAUSE)
    tox.invokeFriendConnectionStatus(1, ToxConnection.TCP)
    tox.invokeFriendLosslessPacket(1, data)
    tox.invokeFriendLossyPacket(1, data)
    tox.invokeFriendMessage(1, ToxMessageType.NORMAL, 2, data)
    tox.invokeFriendName(1, data)
    tox.invokeFriendReadReceipt(1, 2)
    tox.invokeFriendRequest(publicKey, 1, data)
    tox.invokeFriendStatus(1, ToxUserStatus.AWAY)
    tox.invokeFriendStatusMessage(1, data)
    tox.invokeFriendTyping(1, isTyping = true)
    tox.invokeSelfConnectionStatus(ToxConnection.TCP)
  }

  timing of classOf[ToxCore] in {

    measure method "iterate" in {
      usingTox(iterations1k) in {
        case (sz, tox: ToxCoreImpl) =>
          (0 until sz) foreach { _ =>
            tox.iterate()
          }
      }
    }

    performance of "enqueuing a callback" in {
      usingTox(iterations1k) in {
        case (sz, tox: ToxCoreImpl) =>
          (0 until sz) foreach { _ =>
            tox.invokeFileChunkRequest(1, 2, 3, 4)
          }
      }
    }

    performance of "enqueue all callbacks" in {
      usingTox(iterations1k) in {
        case (sz, tox: ToxCoreImpl) =>
          (0 until sz) foreach { _ =>
            invokeAllCallbacks(tox)
          }
      }
    }

    performance of "call all callbacks" in {
      usingTox(iterations1k) in {
        case (sz, tox: ToxCoreImpl) =>
          (0 until sz) foreach { _ =>
            invokeAllCallbacks(tox)
            tox.iterate()
          }
      }
    }

    invokePerformance("invokeFileChunkRequest", _.invokeFileChunkRequest(1, 2, 3, 4))
    invokePerformance("invokeFileRecv", _.invokeFileRecv(1, 2, 3, 4, data))
    invokePerformance("invokeFileRecvChunk", _.invokeFileRecvChunk(1, 2, 3, data))
    invokePerformance("invokeFileRecvControl", _.invokeFileRecvControl(1, 2, ToxFileControl.PAUSE))
    invokePerformance("invokeFriendConnectionStatus", _.invokeFriendConnectionStatus(1, ToxConnection.TCP))
    invokePerformance("invokeFriendLosslessPacket", _.invokeFriendLosslessPacket(1, data))
    invokePerformance("invokeFriendLossyPacket", _.invokeFriendLossyPacket(1, data))
    invokePerformance("invokeFriendMessage", _.invokeFriendMessage(1, ToxMessageType.NORMAL, 2, data))
    invokePerformance("invokeFriendName", _.invokeFriendName(1, data))
    invokePerformance("invokeFriendReadReceipt", _.invokeFriendReadReceipt(1, 2))
    invokePerformance("invokeFriendRequest", _.invokeFriendRequest(publicKey, 1, data))
    invokePerformance("invokeFriendStatus", _.invokeFriendStatus(1, ToxUserStatus.AWAY))
    invokePerformance("invokeFriendStatusMessage", _.invokeFriendStatusMessage(1, data))
    invokePerformance("invokeFriendTyping", _.invokeFriendTyping(1, isTyping = true))
    invokePerformance("invokeSelfConnectionStatus", _.invokeSelfConnectionStatus(ToxConnection.TCP))

  }

  /**
   * Benchmarks we're not currently working on.
   */
  object HoldingPen {

    measure method "iterate" in {
      usingTox(iterations1k) in {
        case (sz, tox: ToxCoreImpl) =>
          (0 until sz) foreach { _ =>
            tox.iterate()
          }
      }
    }

    performance of "enqueuing a callback" in {
      usingTox(iterations1k) in {
        case (sz, tox: ToxCoreImpl) =>
          (0 until sz) foreach { _ =>
            tox.invokeFileChunkRequest(1, 2, 3, 4)
          }
      }
    }

  }

}
