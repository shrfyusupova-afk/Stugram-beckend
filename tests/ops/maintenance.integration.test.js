const { setupIntegrationTestSuite } = require("../helpers/integration");
const { createAuthenticatedUser, createStory, getModels } = require("../helpers/factories");

const { env } = require("../../src/config/env");
const {
  runMaintenanceCleanup,
  startMaintenanceCleanupScheduler,
  stopMaintenanceCleanupScheduler,
} = require("../../src/services/maintenanceService");

setupIntegrationTestSuite();

describe("Maintenance cleanup integration", () => {
  afterEach(() => {
    stopMaintenanceCleanupScheduler();
    env.enableMaintenanceCleanupScheduler = false;
  });

  it("cleans expired stories, stale inactive push tokens, revoked sessions, and old reset artifacts", async () => {
    const { DevicePushToken, PasswordResetToken, Session, Story, StoryLike, StoryComment } = getModels();
    const { user } = await createAuthenticatedUser({
      identity: "maintenance@example.com",
      username: "maintenance_user",
    });

    const expiredStory = await createStory({
      authorId: user._id,
      expiresAt: new Date(Date.now() - 8 * 60 * 60 * 1000),
      media: {
        url: "https://example.test/expired-story.jpg",
        publicId: "stories/expired-cleanup",
        type: "image",
      },
    });

    await StoryLike.create({ story: expiredStory._id, user: user._id });
    await StoryComment.create({ story: expiredStory._id, author: user._id, content: "cleanup" });

    await DevicePushToken.create({
      user: user._id,
      token: "inactive-push-token",
      platform: "android",
      deviceId: "cleanup-device",
      isActive: false,
      updatedAt: new Date(Date.now() - 40 * 24 * 60 * 60 * 1000),
      lastSeenAt: new Date(Date.now() - 40 * 24 * 60 * 60 * 1000),
    });

    const revokedSession = await Session.create({
      sessionId: "revoked-session-id",
      familyId: "revoked-family-id",
      refreshJti: "revoked-refresh-jti",
      user: user._id,
      tokenHash: "revoked-token-hash",
      expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
      isRevoked: true,
      updatedAt: new Date(Date.now() - 100 * 24 * 60 * 60 * 1000),
    });
    await Session.updateOne(
      { _id: revokedSession._id },
      {
        lastUsedAt: new Date(Date.now() - 100 * 24 * 60 * 60 * 1000),
        updatedAt: new Date(Date.now() - 100 * 24 * 60 * 60 * 1000),
      },
      { timestamps: false }
    );

    await PasswordResetToken.create({
      user: user._id,
      identity: user.identity,
      tokenHash: "reset-token-hash",
      expiresAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000),
      usedAt: new Date(Date.now() - 9 * 24 * 60 * 60 * 1000),
      updatedAt: new Date(Date.now() - 9 * 24 * 60 * 60 * 1000),
    });

    const result = await runMaintenanceCleanup();

    expect(result.deletedStories).toBeGreaterThanOrEqual(1);
    expect(result.deletedPushTokens).toBeGreaterThanOrEqual(1);
    expect(result.deletedRevokedSessions).toBeGreaterThanOrEqual(1);
    expect(result.deletedPasswordResetArtifacts).toBeGreaterThanOrEqual(1);

    expect(await Story.countDocuments({ _id: expiredStory._id })).toBe(0);
    expect(await StoryLike.countDocuments({ story: expiredStory._id })).toBe(0);
    expect(await StoryComment.countDocuments({ story: expiredStory._id })).toBe(0);
  });

  it("returns a zeroed summary when nothing requires cleanup", async () => {
    const result = await runMaintenanceCleanup();

    expect(result).toEqual({
      deletedStories: 0,
      deletedStoryLikes: 0,
      deletedStoryComments: 0,
      deletedMediaAssets: 0,
      deletedPushTokens: 0,
      deletedRevokedSessions: 0,
      deletedPasswordResetArtifacts: 0,
    });
  });

  it("starts the cleanup scheduler once and stops it safely", () => {
    env.enableMaintenanceCleanupScheduler = true;

    expect(startMaintenanceCleanupScheduler()).toBe(true);
    expect(startMaintenanceCleanupScheduler()).toBe(false);
    expect(stopMaintenanceCleanupScheduler()).toBe(true);
    expect(stopMaintenanceCleanupScheduler()).toBe(false);
  });
});
