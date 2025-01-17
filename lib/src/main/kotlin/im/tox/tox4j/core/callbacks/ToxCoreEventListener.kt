package im.tox.tox4j.core.callbacks

interface ToxCoreEventListener<ToxCoreState> :
    ConferenceConnectedCallback<ToxCoreState>,
    ConferenceInviteCallback<ToxCoreState>,
    ConferenceMessageCallback<ToxCoreState>,
    ConferencePeerListChangedCallback<ToxCoreState>,
    ConferencePeerNameCallback<ToxCoreState>,
    ConferenceTitleCallback<ToxCoreState>,
    FileChunkRequestCallback<ToxCoreState>,
    FileRecvCallback<ToxCoreState>,
    FileRecvChunkCallback<ToxCoreState>,
    FileRecvControlCallback<ToxCoreState>,
    FriendConnectionStatusCallback<ToxCoreState>,
    FriendLosslessPacketCallback<ToxCoreState>,
    FriendLossyPacketCallback<ToxCoreState>,
    FriendMessageCallback<ToxCoreState>,
    FriendNameCallback<ToxCoreState>,
    FriendReadReceiptCallback<ToxCoreState>,
    FriendRequestCallback<ToxCoreState>,
    FriendStatusCallback<ToxCoreState>,
    FriendStatusMessageCallback<ToxCoreState>,
    FriendTypingCallback<ToxCoreState>,
    GroupCustomPacketCallback<ToxCoreState>,
    GroupCustomPrivatePacketCallback<ToxCoreState>,
    GroupInviteCallback<ToxCoreState>,
    GroupJoinFailCallback<ToxCoreState>,
    GroupMessageCallback<ToxCoreState>,
    GroupModerationCallback<ToxCoreState>,
    GroupPasswordCallback<ToxCoreState>,
    GroupPeerExitCallback<ToxCoreState>,
    GroupPeerJoinCallback<ToxCoreState>,
    GroupPeerLimitCallback<ToxCoreState>,
    GroupPeerNameCallback<ToxCoreState>,
    GroupPeerStatusCallback<ToxCoreState>,
    GroupPrivacyStateCallback<ToxCoreState>,
    GroupPrivateMessageCallback<ToxCoreState>,
    GroupSelfJoinCallback<ToxCoreState>,
    GroupTopicCallback<ToxCoreState>,
    GroupTopicLockCallback<ToxCoreState>,
    GroupVoiceStateCallback<ToxCoreState>,
    SelfConnectionStatusCallback<ToxCoreState>
