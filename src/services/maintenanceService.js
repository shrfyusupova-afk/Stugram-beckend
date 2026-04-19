const DevicePushToken = require("../models/DevicePushToken");
const PasswordResetToken = require("../models/PasswordResetToken");
const Session = require("../models/Session");
const Story = require("../models/Story");
const StoryLike = require("../models/StoryLike");
const StoryComment = require("../models/StoryComment");
const { env } = require("../config/env");
const { destroyCloudinaryAsset } = require("../utils/media");
const logger = require("../utils/logger");

let maintenanceInterval = null;

const subtractDays = (days) => new Date(Date.now() - days * 24 * 60 * 60 * 1000);
const subtractHours = (hours) => new Date(Date.now() - hours * 60 * 60 * 1000);

const cleanupExpiredStories = async () => {
  const cutoff = subtractHours(env.expiredStoryRetentionHours);
  const expiredStories = await Story.find({ expiresAt: { $lte: cutoff } }).select("_id media").lean();

  if (!expiredStories.length) {
    return { deletedStories: 0, deletedStoryLikes: 0, deletedStoryComments: 0, deletedMediaAssets: 0 };
  }

  const storyIds = expiredStories.map((story) => story._id);

  const [storyDeleteResult, likeDeleteResult, commentDeleteResult, mediaResults] = await Promise.all([
    Story.deleteMany({ _id: { $in: storyIds } }),
    StoryLike.deleteMany({ story: { $in: storyIds } }),
    StoryComment.deleteMany({ story: { $in: storyIds } }),
    Promise.allSettled(
      expiredStories.map((story) =>
        destroyCloudinaryAsset(story.media.publicId, story.media.type === "video" ? "video" : "image")
      )
    ),
  ]);

  const deletedMediaAssets = mediaResults.filter((result) => result.status === "fulfilled").length;

  return {
    deletedStories: storyDeleteResult.deletedCount || 0,
    deletedStoryLikes: likeDeleteResult.deletedCount || 0,
    deletedStoryComments: commentDeleteResult.deletedCount || 0,
    deletedMediaAssets,
  };
};

const cleanupStalePushTokens = async () => {
  const cutoff = subtractDays(env.inactivePushTokenRetentionDays);
  const result = await DevicePushToken.deleteMany({
    isActive: false,
    $or: [{ updatedAt: { $lte: cutoff } }, { lastSeenAt: { $lte: cutoff } }],
  });

  return { deletedPushTokens: result.deletedCount || 0 };
};

const cleanupOldSessions = async () => {
  const cutoff = subtractDays(env.revokedSessionRetentionDays);
  const result = await Session.deleteMany({
    isRevoked: true,
    $or: [{ updatedAt: { $lte: cutoff } }, { lastUsedAt: { $lte: cutoff } }],
  });

  return { deletedRevokedSessions: result.deletedCount || 0 };
};

const cleanupPasswordResetArtifacts = async () => {
  const cutoff = subtractDays(env.passwordResetRetentionDays);
  const result = await PasswordResetToken.deleteMany({
    $or: [{ usedAt: { $ne: null } }, { expiresAt: { $lte: new Date() } }],
    $and: [
      {
        $or: [
          { updatedAt: { $lte: cutoff } },
          { usedAt: { $lte: cutoff } },
          { expiresAt: { $lte: cutoff } },
        ],
      },
    ],
  });

  return { deletedPasswordResetArtifacts: result.deletedCount || 0 };
};

const runMaintenanceCleanup = async () => {
  const [stories, pushTokens, sessions, passwordReset] = await Promise.all([
    cleanupExpiredStories(),
    cleanupStalePushTokens(),
    cleanupOldSessions(),
    cleanupPasswordResetArtifacts(),
  ]);

  const summary = {
    ...stories,
    ...pushTokens,
    ...sessions,
    ...passwordReset,
  };

  logger.info("Maintenance cleanup completed", summary);
  return summary;
};

const startMaintenanceCleanupScheduler = () => {
  if (!env.enableMaintenanceCleanupScheduler || maintenanceInterval) {
    return false;
  }

  const intervalMs = Math.max(env.maintenanceCleanupIntervalMinutes, 5) * 60 * 1000;
  maintenanceInterval = setInterval(() => {
    runMaintenanceCleanup().catch((error) => {
      logger.error("Maintenance cleanup run failed", { message: error.message });
    });
  }, intervalMs);

  maintenanceInterval.unref?.();
  logger.info("Maintenance cleanup scheduler started", {
    intervalMinutes: Math.max(env.maintenanceCleanupIntervalMinutes, 5),
  });
  return true;
};

const stopMaintenanceCleanupScheduler = () => {
  if (!maintenanceInterval) return false;
  clearInterval(maintenanceInterval);
  maintenanceInterval = null;
  logger.info("Maintenance cleanup scheduler stopped");
  return true;
};

module.exports = {
  runMaintenanceCleanup,
  startMaintenanceCleanupScheduler,
  stopMaintenanceCleanupScheduler,
};
