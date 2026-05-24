package im.tox.tox4j.core.options

import im.tox.tox4j.core.ToxCoreConstants
import im.tox.tox4j.core.data.ToxSavedata
import im.tox.tox4j.core.data.ToxSecretKey
import im.tox.tox4j.core.enums.ToxProxyType
import im.tox.tox4j.core.enums.ToxSavedataType
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToxOptionsTest {
    private val baseOptions = ToxOptions(localDiscoveryEnabled = false)

    @Test
    fun defaults_areConservative() {
        val defaults = ToxOptions()
        assertEquals(true, defaults.ipv6Enabled)
        assertEquals(true, defaults.udpEnabled)
        assertEquals(true, defaults.localDiscoveryEnabled)
        assertEquals(ToxCoreConstants.DEFAULT_START_PORT, defaults.startPort)
        assertEquals(ToxCoreConstants.DEFAULT_END_PORT, defaults.endPort)
        assertEquals(ToxCoreConstants.DEFAULT_TCP_PORT, defaults.tcpPort)
        assertTrue(defaults.proxy is ProxyOptions.None)
        assertTrue(defaults.saveData is SaveDataOptions.None)
    }

    @Test
    fun proxyNone_hasNoneEnum() {
        assertEquals(ToxProxyType.NONE, ProxyOptions.None.proxyType)
    }

    @Test
    fun proxyHttp_carriesHostPort() {
        val proxy = ProxyOptions.Http("proxy.example.com", 8080.toUShort())
        assertEquals(ToxProxyType.HTTP, proxy.proxyType)
        assertEquals("proxy.example.com", proxy.proxyAddress)
        assertEquals(8080.toUShort(), proxy.proxyPort)
    }

    @Test
    fun proxySocks5_carriesHostPort() {
        val proxy = ProxyOptions.Socks5("127.0.0.1", 9050.toUShort())
        assertEquals(ToxProxyType.SOCKS5, proxy.proxyType)
        assertEquals("127.0.0.1", proxy.proxyAddress)
        assertEquals(9050.toUShort(), proxy.proxyPort)
    }

    @Test
    fun saveData_none_isEmpty() {
        assertEquals(ToxSavedataType.NONE, SaveDataOptions.None.kind)
        assertEquals(0, SaveDataOptions.None.data.size)
    }

    @Test
    fun saveData_toxSave_exposesData() {
        val raw = byteArrayOf(1, 2, 3, 4)
        val save = SaveDataOptions.ToxSave(raw)
        assertEquals(ToxSavedataType.TOX_SAVE, save.kind)
        assertContentEquals(raw, save.data)
    }

    @Test
    fun saveData_secretKey_exposesData() {
        val key = ToxSecretKey(ByteArray(ToxCoreConstants.SECRET_KEY_SIZE) { 7 })
        val save = SaveDataOptions.SecretKey(key)
        assertEquals(ToxSavedataType.SECRET_KEY, save.kind)
        assertContentEquals(key.value, save.data)
    }

    @Test
    fun construct_withProxyNone_ok() = ToxCoreImpl(baseOptions.copy(proxy = ProxyOptions.None)).use { }

    @Test
    fun construct_withSaveDataNone_ok() = ToxCoreImpl(baseOptions.copy(saveData = SaveDataOptions.None)).use { }

    @Test
    fun construct_withIpv4Only_ok() = ToxCoreImpl(baseOptions.copy(ipv6Enabled = false)).use { }

    @Test
    fun construct_withTcpOnly_ok() = ToxCoreImpl(baseOptions.copy(udpEnabled = false)).use { }

    @Test
    fun construct_withTcpServerEnabled_ok() = ToxCoreImpl(baseOptions.copy(tcpPort = 0.toUShort())).use { }

    @Test
    fun construct_withSinglePortRange_ok() = ToxCoreImpl(baseOptions.copy(startPort = 40000.toUShort(), endPort = 40000.toUShort())).use { }

    @Test
    fun construct_withWidePortRange_ok() = ToxCoreImpl(baseOptions.copy(startPort = 33445.toUShort(), endPort = 33545.toUShort())).use { }

    @Test
    fun construct_withToxSavedataRoundTrip_ok() {
        val data: ToxSavedata
        ToxCoreImpl(baseOptions).use { tox -> data = tox.savedata }
        ToxCoreImpl(baseOptions.copy(saveData = SaveDataOptions.ToxSave(data.value))).use { }
    }

    @Test
    fun construct_withSecretKeyData_ok() {
        val sk: ToxSecretKey
        ToxCoreImpl(baseOptions).use { tox -> sk = tox.secretKey }
        ToxCoreImpl(baseOptions.copy(saveData = SaveDataOptions.SecretKey(sk))).use { }
    }

    @Test
    fun copy_isStructural() {
        val a = ToxOptions(ipv6Enabled = false, udpEnabled = true)
        val b = a.copy(ipv6Enabled = true)
        assertEquals(false, a.ipv6Enabled)
        assertEquals(true, b.ipv6Enabled)
        assertEquals(a.udpEnabled, b.udpEnabled)
    }

    @Test
    fun toxCoreImpl_dropsSavedataAfterInit() {
        // The C side keeps its own copy, so the options don't hold on to the bytes.
        val data: ToxSavedata
        ToxCoreImpl(baseOptions).use { tox -> data = tox.savedata }
        val withSavedata = baseOptions.copy(saveData = SaveDataOptions.ToxSave(data.value))
        ToxCoreImpl(withSavedata).use { tox ->
            assertEquals(SaveDataOptions.None, tox.options.saveData)
            assertEquals(0, tox.options.saveData.data.size)
        }
    }

    @Test
    fun toxSave_equals_isContentBased() {
        val bytes1 = byteArrayOf(1, 2, 3, 4, 5)
        val bytes2 = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(SaveDataOptions.ToxSave(bytes1), SaveDataOptions.ToxSave(bytes2))
        assertEquals(
            SaveDataOptions.ToxSave(bytes1).hashCode(),
            SaveDataOptions.ToxSave(bytes2).hashCode(),
        )
    }

    @Test
    fun toxSave_equals_distinguishesContent() {
        val a = SaveDataOptions.ToxSave(byteArrayOf(1, 2, 3))
        val b = SaveDataOptions.ToxSave(byteArrayOf(1, 2, 4))
        assertTrue(a != b)
    }

    @Test
    fun toxSave_toString_omitsBytes() {
        // Save data holds the secret key.
        assertEquals("ToxSave(data=<5 bytes>)", SaveDataOptions.ToxSave(ByteArray(5)).toString())
    }

    @Test
    fun secretKey_equals_isContentBased() {
        val k1 = ToxSecretKey(ByteArray(ToxCoreConstants.SECRET_KEY_SIZE) { 1 })
        val k2 = ToxSecretKey(ByteArray(ToxCoreConstants.SECRET_KEY_SIZE) { 1 })
        assertEquals(SaveDataOptions.SecretKey(k1), SaveDataOptions.SecretKey(k2))
    }

    @Test
    fun secretKey_toString_omitsBytes() {
        val k = ToxSecretKey(ByteArray(ToxCoreConstants.SECRET_KEY_SIZE))
        assertEquals(
            "SecretKey(<${ToxCoreConstants.SECRET_KEY_SIZE} bytes>)",
            SaveDataOptions.SecretKey(k).toString(),
        )
    }
}
