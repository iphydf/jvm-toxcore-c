package im.tox.tox4j.core.options

import im.tox.tox4j.core.ToxCoreConstants

data class ToxOptions(
    val ipv6Enabled: Boolean = true,
    val udpEnabled: Boolean = true,
    val localDiscoveryEnabled: Boolean = true,
    val dhtAnnouncementsEnabled: Boolean = true,
    val proxy: ProxyOptions.Type = ProxyOptions.None,
    val startPort: UShort = ToxCoreConstants.DEFAULT_START_PORT,
    val endPort: UShort = ToxCoreConstants.DEFAULT_END_PORT,
    val tcpPort: UShort = ToxCoreConstants.DEFAULT_TCP_PORT,
    val holePunchingEnabled: Boolean = true,
    val saveData: SaveDataOptions.Type = SaveDataOptions.None,
    val experimentalOwnedData: Boolean = false,
    val experimentalThreadSafety: Boolean = false,
    val experimentalGroupsPersistence: Boolean = false,
    val experimentalDisableDns: Boolean = false,
)
