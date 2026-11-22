static void
on_conference_connected (Tox *, Tox_Conference_Number conference_number, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (0);
  event.i32 (conference_number);
}

static void
on_conference_invite (Tox *, Tox_Friend_Number friend_number, Tox_Conference_Type type, uint8_t const *cookie, size_t cookie_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (1);
  event.i32 (friend_number);
  event.i32 (type);
  event.bytes (cookie, cookie_length);
}

static void
on_conference_message (Tox *, Tox_Conference_Number conference_number, Tox_Conference_Peer_Number peer_number, Tox_Message_Type type, uint8_t const *message, size_t message_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (2);
  event.i32 (conference_number);
  event.i32 (peer_number);
  event.i32 (type);
  event.bytes (message, message_length);
}

static void
on_conference_peer_list_changed (Tox *, Tox_Conference_Number conference_number, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (3);
  event.i32 (conference_number);
}

static void
on_conference_peer_name (Tox *, Tox_Conference_Number conference_number, Tox_Conference_Peer_Number peer_number, uint8_t const *name, size_t name_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (4);
  event.i32 (conference_number);
  event.i32 (peer_number);
  event.bytes (name, name_length);
}

static void
on_conference_title (Tox *, Tox_Conference_Number conference_number, Tox_Conference_Peer_Number peer_number, uint8_t const *title, size_t title_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (5);
  event.i32 (conference_number);
  event.i32 (peer_number);
  event.bytes (title, title_length);
}

static void
on_file_chunk_request (Tox *, Tox_Friend_Number friend_number, Tox_File_Number file_number, uint64_t position, size_t length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (6);
  event.i32 (friend_number);
  event.i32 (file_number);
  event.i64 (position);
  event.i64 (length);
}

static void
on_file_recv (Tox *, Tox_Friend_Number friend_number, Tox_File_Number file_number, uint32_t kind, uint64_t file_size, uint8_t const *filename, size_t filename_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (7);
  event.i32 (friend_number);
  event.i32 (file_number);
  event.i32 (kind);
  event.i64 (file_size);
  event.bytes (filename, filename_length);
}

static void
on_file_recv_chunk (Tox *, Tox_Friend_Number friend_number, Tox_File_Number file_number, uint64_t position, uint8_t const *data, size_t data_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (8);
  event.i32 (friend_number);
  event.i32 (file_number);
  event.i64 (position);
  event.bytes (data, data_length);
}

static void
on_file_recv_control (Tox *, Tox_Friend_Number friend_number, Tox_File_Number file_number, Tox_File_Control control, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (9);
  event.i32 (friend_number);
  event.i32 (file_number);
  event.i32 (control);
}

static void
on_friend_connection_status (Tox *, Tox_Friend_Number friend_number, Tox_Connection connection_status, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (10);
  event.i32 (friend_number);
  event.i32 (connection_status);
}

static void
on_friend_lossless_packet (Tox *, Tox_Friend_Number friend_number, uint8_t const *data, size_t data_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (11);
  event.i32 (friend_number);
  event.bytes (data, data_length);
}

static void
on_friend_lossy_packet (Tox *, Tox_Friend_Number friend_number, uint8_t const *data, size_t data_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (12);
  event.i32 (friend_number);
  event.bytes (data, data_length);
}

static void
on_friend_message (Tox *, Tox_Friend_Number friend_number, Tox_Message_Type type, uint8_t const *message, size_t message_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (13);
  event.i32 (friend_number);
  event.i32 (type);
  event.bytes (message, message_length);
}

static void
on_friend_name (Tox *, Tox_Friend_Number friend_number, uint8_t const *name, size_t name_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (14);
  event.i32 (friend_number);
  event.bytes (name, name_length);
}

static void
on_friend_read_receipt (Tox *, Tox_Friend_Number friend_number, Tox_Friend_Message_Id message_id, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (15);
  event.i32 (friend_number);
  event.i32 (message_id);
}

static void
on_friend_request (Tox *, uint8_t const *public_key, uint8_t const *message, size_t message_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (16);
  event.bytes (public_key, TOX_PUBLIC_KEY_SIZE);
  event.bytes (message, message_length);
}

static void
on_friend_status (Tox *, Tox_Friend_Number friend_number, Tox_User_Status status, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (17);
  event.i32 (friend_number);
  event.i32 (status);
}

static void
on_friend_status_message (Tox *, Tox_Friend_Number friend_number, uint8_t const *message, size_t message_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (18);
  event.i32 (friend_number);
  event.bytes (message, message_length);
}

static void
on_friend_typing (Tox *, Tox_Friend_Number friend_number, bool typing, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (19);
  event.i32 (friend_number);
  event.b (typing);
}

static void
on_group_custom_packet (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, uint8_t const *data, size_t data_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (20);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.bytes (data, data_length);
}

static void
on_group_custom_private_packet (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, uint8_t const *data, size_t data_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (21);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.bytes (data, data_length);
}

static void
on_group_invite (Tox *, Tox_Friend_Number friend_number, uint8_t const *invite_data, size_t invite_data_length, uint8_t const *group_name, size_t group_name_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (22);
  event.i32 (friend_number);
  event.bytes (invite_data, invite_data_length);
  event.bytes (group_name, group_name_length);
}

static void
on_group_join_fail (Tox *, Tox_Group_Number group_number, Tox_Group_Join_Fail fail_type, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (23);
  event.i32 (group_number);
  event.i32 (fail_type);
}

static void
on_group_message (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, Tox_Message_Type message_type, uint8_t const *message, size_t message_length, Tox_Group_Message_Id message_id, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (24);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.i32 (message_type);
  event.bytes (message, message_length);
  event.i32 (message_id);
}

static void
on_group_moderation (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number source_peer_id, Tox_Group_Peer_Number target_peer_id, Tox_Group_Mod_Event mod_type, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (25);
  event.i32 (group_number);
  event.i32 (source_peer_id);
  event.i32 (target_peer_id);
  event.i32 (mod_type);
}

static void
on_group_password (Tox *, Tox_Group_Number group_number, uint8_t const *password, size_t password_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (26);
  event.i32 (group_number);
  event.bytes (password, password_length);
}

static void
on_group_peer_exit (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, Tox_Group_Exit_Type exit_type, uint8_t const *name, size_t name_length, uint8_t const *part_message, size_t part_message_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (27);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.i32 (exit_type);
  event.bytes (name, name_length);
  event.bytes (part_message, part_message_length);
}

static void
on_group_peer_join (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (28);
  event.i32 (group_number);
  event.i32 (peer_id);
}

static void
on_group_peer_limit (Tox *, Tox_Group_Number group_number, uint32_t peer_limit, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (29);
  event.i32 (group_number);
  event.i32 (peer_limit);
}

static void
on_group_peer_name (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, uint8_t const *name, size_t name_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (30);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.bytes (name, name_length);
}

static void
on_group_peer_status (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, Tox_User_Status status, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (31);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.i32 (status);
}

static void
on_group_privacy_state (Tox *, Tox_Group_Number group_number, Tox_Group_Privacy_State privacy_state, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (32);
  event.i32 (group_number);
  event.i32 (privacy_state);
}

static void
on_group_private_message (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, Tox_Message_Type message_type, uint8_t const *message, size_t message_length, Tox_Group_Message_Id message_id, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (33);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.i32 (message_type);
  event.bytes (message, message_length);
  event.i32 (message_id);
}

static void
on_group_self_join (Tox *, Tox_Group_Number group_number, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (34);
  event.i32 (group_number);
}

static void
on_group_topic (Tox *, Tox_Group_Number group_number, Tox_Group_Peer_Number peer_id, uint8_t const *topic, size_t topic_length, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (35);
  event.i32 (group_number);
  event.i32 (peer_id);
  event.bytes (topic, topic_length);
}

static void
on_group_topic_lock (Tox *, Tox_Group_Number group_number, Tox_Group_Topic_Lock topic_lock, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (36);
  event.i32 (group_number);
  event.i32 (topic_lock);
}

static void
on_group_voice_state (Tox *, Tox_Group_Number group_number, Tox_Group_Voice_State voice_state, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (37);
  event.i32 (group_number);
  event.i32 (voice_state);
}

static void
on_self_connection_status (Tox *, Tox_Connection connection_status, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (38);
  event.i32 (connection_status);
}

static void
register_callbacks (Tox *self)
{
  tox_callback_conference_connected (self, on_conference_connected);
  tox_callback_conference_invite (self, on_conference_invite);
  tox_callback_conference_message (self, on_conference_message);
  tox_callback_conference_peer_list_changed (self, on_conference_peer_list_changed);
  tox_callback_conference_peer_name (self, on_conference_peer_name);
  tox_callback_conference_title (self, on_conference_title);
  tox_callback_file_chunk_request (self, on_file_chunk_request);
  tox_callback_file_recv (self, on_file_recv);
  tox_callback_file_recv_chunk (self, on_file_recv_chunk);
  tox_callback_file_recv_control (self, on_file_recv_control);
  tox_callback_friend_connection_status (self, on_friend_connection_status);
  tox_callback_friend_lossless_packet (self, on_friend_lossless_packet);
  tox_callback_friend_lossy_packet (self, on_friend_lossy_packet);
  tox_callback_friend_message (self, on_friend_message);
  tox_callback_friend_name (self, on_friend_name);
  tox_callback_friend_read_receipt (self, on_friend_read_receipt);
  tox_callback_friend_request (self, on_friend_request);
  tox_callback_friend_status (self, on_friend_status);
  tox_callback_friend_status_message (self, on_friend_status_message);
  tox_callback_friend_typing (self, on_friend_typing);
  tox_callback_group_custom_packet (self, on_group_custom_packet);
  tox_callback_group_custom_private_packet (self, on_group_custom_private_packet);
  tox_callback_group_invite (self, on_group_invite);
  tox_callback_group_join_fail (self, on_group_join_fail);
  tox_callback_group_message (self, on_group_message);
  tox_callback_group_moderation (self, on_group_moderation);
  tox_callback_group_password (self, on_group_password);
  tox_callback_group_peer_exit (self, on_group_peer_exit);
  tox_callback_group_peer_join (self, on_group_peer_join);
  tox_callback_group_peer_limit (self, on_group_peer_limit);
  tox_callback_group_peer_name (self, on_group_peer_name);
  tox_callback_group_peer_status (self, on_group_peer_status);
  tox_callback_group_privacy_state (self, on_group_privacy_state);
  tox_callback_group_private_message (self, on_group_private_message);
  tox_callback_group_self_join (self, on_group_self_join);
  tox_callback_group_topic (self, on_group_topic);
  tox_callback_group_topic_lock (self, on_group_topic_lock);
  tox_callback_group_voice_state (self, on_group_voice_state);
  tox_callback_self_connection_status (self, on_self_connection_status);
}
