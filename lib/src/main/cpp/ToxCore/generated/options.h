JAVA_METHOD (jint, toxNew,
  jboolean ipv6Enabled,
  jboolean udpEnabled,
  jboolean localDiscoveryEnabled,
  jboolean dhtAnnouncementsEnabled,
  jint proxyType,
  jstring proxyHost,
  jint proxyPort,
  jint startPort,
  jint endPort,
  jint tcpPort,
  jboolean holePunchingEnabled,
  jint savedataType,
  jbyteArray savedata,
  jboolean experimentalOwnedData,
  jboolean experimentalGroupsPersistence,
  jboolean experimentalDisableDns)
{
  UTFChars proxyHostChars (env, proxyHost);
  auto savedataBytes = fromJavaArray (env, savedata);
  return new_instance (env, [&] (Tox_Options *opts)
    {
      bool ok = true;
      tox_options_set_experimental_thread_safety (opts, true);
      tox_options_set_ipv6_enabled (opts, ipv6Enabled);
      tox_options_set_udp_enabled (opts, udpEnabled);
      tox_options_set_local_discovery_enabled (opts, localDiscoveryEnabled);
      tox_options_set_dht_announcements_enabled (opts, dhtAnnouncementsEnabled);
      tox_options_set_proxy_type (opts, Enum::valueOf<Tox_Proxy_Type> (env, proxyType));
      ok = tox_options_set_proxy_host (opts, proxyHostChars.data ()) && ok;
      tox_options_set_proxy_port (opts, static_cast<uint16_t> (proxyPort));
      tox_options_set_start_port (opts, static_cast<uint16_t> (startPort));
      tox_options_set_end_port (opts, static_cast<uint16_t> (endPort));
      tox_options_set_tcp_port (opts, static_cast<uint16_t> (tcpPort));
      tox_options_set_hole_punching_enabled (opts, holePunchingEnabled);
      tox_options_set_savedata_type (opts, Enum::valueOf<Tox_Savedata_Type> (env, savedataType));
      ok = tox_options_set_savedata_data (opts, savedataBytes.data (), savedataBytes.size ()) && ok;
      tox_options_set_experimental_owned_data (opts, experimentalOwnedData);
      tox_options_set_experimental_groups_persistence (opts, experimentalGroupsPersistence);
      tox_options_set_experimental_disable_dns (opts, experimentalDisableDns);
      return ok;
    });
}
