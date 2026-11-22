static void
on_audio_bit_rate (ToxAV *, Tox_Friend_Number friend_number, uint32_t audio_bit_rate, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (0);
  event.i32 (friend_number);
  event.i32 (audio_bit_rate);
}

static void
on_audio_receive_frame (ToxAV *, Tox_Friend_Number friend_number, int16_t const *pcm, size_t sample_count, uint8_t channels, uint32_t sampling_rate, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (1);
  event.i32 (friend_number);
  event.shorts (pcm, sample_count * channels);
  event.i64 (sample_count);
  event.i32 (channels);
  event.i32 (sampling_rate);
}

static void
on_call (ToxAV *, Tox_Friend_Number friend_number, bool audio_enabled, bool video_enabled, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (2);
  event.i32 (friend_number);
  event.b (audio_enabled);
  event.b (video_enabled);
}

static void
on_call_state (ToxAV *, Tox_Friend_Number friend_number, uint32_t state, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (3);
  event.i32 (friend_number);
  event.i32 (state);
}

static void
on_video_bit_rate (ToxAV *, Tox_Friend_Number friend_number, uint32_t video_bit_rate, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (4);
  event.i32 (friend_number);
  event.i32 (video_bit_rate);
}

static void
on_video_receive_frame (ToxAV *, Tox_Friend_Number friend_number, uint16_t width, uint16_t height, uint8_t const *y, uint8_t const *u, uint8_t const *v, int32_t ystride, int32_t ustride, int32_t vstride, void *user_data)
{
  auto event = static_cast<EventWriter *> (user_data)->event (5);
  event.i32 (friend_number);
  event.i32 (width);
  event.i32 (height);
  event.bytes (y, size_expr::max(width, size_expr::abs(ystride)) * height);
  event.bytes (u, size_expr::max((width + 1) / 2, size_expr::abs(ustride)) * ((height + 1) / 2));
  event.bytes (v, size_expr::max((width + 1) / 2, size_expr::abs(vstride)) * ((height + 1) / 2));
  event.i32 (ystride);
  event.i32 (ustride);
  event.i32 (vstride);
}

static void
register_callbacks (ToxAV *self, EventWriter *events)
{
  toxav_callback_audio_bit_rate (self, on_audio_bit_rate, events);
  toxav_callback_audio_receive_frame (self, on_audio_receive_frame, events);
  toxav_callback_call (self, on_call, events);
  toxav_callback_call_state (self, on_call_state, events);
  toxav_callback_video_bit_rate (self, on_video_bit_rate, events);
  toxav_callback_video_receive_frame (self, on_video_receive_frame, events);
}
