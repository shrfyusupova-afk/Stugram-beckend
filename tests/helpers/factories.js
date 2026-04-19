const bcrypt = require("bcryptjs");

const fakeImageBuffer = () => Buffer.from("fake-image-content");
const fakeVideoBuffer = () => Buffer.from("fake-video-content");

let uniqueCounter = 0;
const nextUnique = () => {
  uniqueCounter += 1;
  return uniqueCounter;
};

const getModels = () => ({
  User: require("../../src/models/User"),
  Post: require("../../src/models/Post"),
  Story: require("../../src/models/Story"),
  Follow: require("../../src/models/Follow"),
  Conversation: require("../../src/models/Conversation"),
  Message: require("../../src/models/Message"),
  GroupConversation: require("../../src/models/GroupConversation"),
  GroupMessage: require("../../src/models/GroupMessage"),
  CallSession: require("../../src/models/CallSession"),
  DevicePushToken: require("../../src/models/DevicePushToken"),
  PasswordResetToken: require("../../src/models/PasswordResetToken"),
  Session: require("../../src/models/Session"),
  SupportTicket: require("../../src/models/SupportTicket"),
  AuditLog: require("../../src/models/AuditLog"),
});

const authHeader = (token) => ({ Authorization: `Bearer ${token}` });

const createUser = async (overrides = {}) => {
  const { User } = getModels();
  const suffix = nextUnique();
  const passwordHash = overrides.passwordHash || (await bcrypt.hash(overrides.password || "Password123", 12));

  return User.create({
    identity: overrides.identity || `user${suffix}@example.com`,
    fullName: overrides.fullName || `Test User ${suffix}`,
    username: overrides.username || `testuser${suffix}`,
    bio: overrides.bio || "",
    passwordHash,
    avatar: overrides.avatar || null,
    avatarPublicId: overrides.avatarPublicId || null,
    banner: overrides.banner || null,
    bannerPublicId: overrides.bannerPublicId || null,
    role: overrides.role || "user",
    isPrivateAccount: overrides.isPrivateAccount || false,
    followersCount: overrides.followersCount || 0,
    followingCount: overrides.followingCount || 0,
    postsCount: overrides.postsCount || 0,
    googleId: overrides.googleId || null,
    birthday: overrides.birthday || null,
    location: overrides.location || "",
    school: overrides.school || "",
    region: overrides.region || "",
    district: overrides.district || "",
    grade: overrides.grade || "",
    group: overrides.group || "",
    ...overrides,
  });
};

const createAuthenticatedUser = async (overrides = {}) => {
  const user = await createUser(overrides);
  const { signAccessToken } = require("../../src/utils/token");
  const accessToken = signAccessToken(user._id);
  return { user, accessToken };
};

const createPost = async ({ authorId, media, ...overrides }) => {
  const { Post } = getModels();
  return Post.create({
    author: authorId,
    media: media || [
      {
        url: "https://example.test/post.jpg",
        publicId: "posts/test",
        type: "image",
        width: 1080,
        height: 1080,
        duration: null,
      },
    ],
    caption: overrides.caption || "",
    hashtags: overrides.hashtags || [],
    location: overrides.location || "",
    ...overrides,
  });
};

const createStory = async ({ authorId, ...overrides }) => {
  const { Story } = getModels();
  return Story.create({
    author: authorId,
    media: overrides.media || {
      url: "https://example.test/story.jpg",
      publicId: "stories/test",
      type: "image",
      width: 1080,
      height: 1920,
      duration: null,
    },
    caption: overrides.caption || "",
    viewers: overrides.viewers || [],
    likesCount: overrides.likesCount || 0,
    commentsCount: overrides.commentsCount || 0,
    expiresAt: overrides.expiresAt || new Date(Date.now() + 24 * 60 * 60 * 1000),
    ...overrides,
  });
};

const createFollow = async ({ followerId, followingId }) => {
  const { Follow } = getModels();
  return Follow.create({
    follower: followerId,
    following: followingId,
    status: "accepted",
  });
};

const createConversation = async ({ participants, createdBy, ...overrides }) => {
  const { Conversation } = getModels();
  return Conversation.create({
    participants,
    createdBy: createdBy || participants[0],
    lastMessage: overrides.lastMessage || "",
    lastMessageAt: overrides.lastMessageAt || new Date(),
    mutedBy: overrides.mutedBy || [],
  });
};

const createMessage = async ({ conversationId, senderId, ...overrides }) => {
  const { Message } = getModels();
  return Message.create({
    conversation: conversationId,
    sender: senderId,
    text: overrides.text || "Hello",
    messageType: overrides.messageType || "text",
    media: overrides.media || null,
    replyToMessage: overrides.replyToMessage || null,
    reactions: overrides.reactions || [],
    seenBy: overrides.seenBy || [senderId],
    deletedFor: overrides.deletedFor || [],
    ...overrides,
  });
};

const createGroupConversation = async ({ ownerId, memberIds, ...overrides }) => {
  const { GroupConversation } = getModels();
  return GroupConversation.create({
    name: overrides.name || "Test Group",
    avatar: overrides.avatar || null,
    avatarPublicId: overrides.avatarPublicId || null,
    owner: ownerId,
    members: [ownerId, ...(memberIds || [])].map((userId) => ({ user: userId })),
    lastMessage: overrides.lastMessage || "",
    lastMessageAt: overrides.lastMessageAt || new Date(),
    ...overrides,
  });
};

const createGroupMessage = async ({ groupId, senderId, ...overrides }) => {
  const { GroupMessage } = getModels();
  return GroupMessage.create({
    groupConversation: groupId,
    sender: senderId,
    text: overrides.text || "Group hello",
    messageType: overrides.messageType || "text",
    media: overrides.media || null,
    replyToMessage: overrides.replyToMessage || null,
    reactions: overrides.reactions || [],
    seenBy: overrides.seenBy || [senderId],
    deletedFor: overrides.deletedFor || [],
    ...overrides,
  });
};

const createCallSession = async ({ initiatorId, participantIds, ...overrides }) => {
  const { CallSession } = getModels();
  return CallSession.create({
    initiator: initiatorId,
    participants: participantIds,
    conversationId: overrides.conversationId || null,
    groupId: overrides.groupId || null,
    callType: overrides.callType || "audio",
    status: overrides.status || "ringing",
    startedAt: overrides.startedAt || new Date(),
    answeredAt: overrides.answeredAt || null,
    endedAt: overrides.endedAt || null,
    lastSignalAt: overrides.lastSignalAt || new Date(),
    ...overrides,
  });
};

module.exports = {
  authHeader,
  createAuthenticatedUser,
  createCallSession,
  createConversation,
  createFollow,
  createGroupConversation,
  createGroupMessage,
  createMessage,
  createPost,
  createStory,
  createUser,
  fakeImageBuffer,
  fakeVideoBuffer,
  getModels,
};
