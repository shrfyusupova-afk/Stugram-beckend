const DevicePushToken = require("../models/DevicePushToken");
const logger = require("../utils/logger");

const normalizeTokenValue = (payload = {}) => payload.token || payload.pushToken || null;

const registerPushToken = async (userId, payload) => {
  const now = new Date();
  const tokenValue = normalizeTokenValue(payload);

  if (!tokenValue) {
    throw new Error("Push token is required");
  }

  const existingByDevice = await DevicePushToken.findOne({
    user: userId,
    deviceId: payload.deviceId,
  });

  if (existingByDevice) {
    existingByDevice.token = tokenValue;
    existingByDevice.platform = payload.platform;
    existingByDevice.appVersion = payload.appVersion || null;
    existingByDevice.isActive = true;
    existingByDevice.lastSeenAt = now;
    await existingByDevice.save();
    logger.info("Push token refreshed", {
      userId: String(userId),
      deviceId: payload.deviceId,
      platform: payload.platform,
    });
    return existingByDevice;
  }

  const existingByToken = await DevicePushToken.findOne({ token: tokenValue });
  if (existingByToken) {
    existingByToken.user = userId;
    existingByToken.deviceId = payload.deviceId;
    existingByToken.platform = payload.platform;
    existingByToken.appVersion = payload.appVersion || null;
    existingByToken.isActive = true;
    existingByToken.lastSeenAt = now;
    await existingByToken.save();
    logger.info("Push token re-associated", {
      userId: String(userId),
      deviceId: payload.deviceId,
      platform: payload.platform,
    });
    return existingByToken;
  }

  const created = await DevicePushToken.create({
    user: userId,
    token: tokenValue,
    platform: payload.platform,
    deviceId: payload.deviceId,
    appVersion: payload.appVersion || null,
    isActive: true,
    lastSeenAt: now,
  });

  logger.info("Push token registered", {
    userId: String(userId),
    deviceId: payload.deviceId,
    platform: payload.platform,
  });
  return created;
};

const removePushToken = async (userId, payload) => {
  const filter = { user: userId };

  const tokenValue = normalizeTokenValue(payload);
  if (tokenValue) {
    filter.token = tokenValue;
  }

  if (payload.deviceId) {
    filter.deviceId = payload.deviceId;
  }

  const token = await DevicePushToken.findOneAndUpdate(
    filter,
    {
      isActive: false,
      lastSeenAt: new Date(),
    },
    { new: true }
  );

  if (token) {
    logger.info("Push token invalidated", {
      userId: String(userId),
      deviceId: token.deviceId,
      platform: token.platform,
    });
  }

  return {
    updated: true,
    removed: Boolean(token),
    tokenId: token?._id || null,
  };
};

const getActivePushTokensForUser = async (userId) =>
  DevicePushToken.find({ user: userId, isActive: true })
    .select("token platform deviceId appVersion lastSeenAt user")
    .sort({ updatedAt: -1 })
    .lean();

const deactivatePushTokens = async (tokens, reason = "invalid_token") => {
  if (!tokens?.length) return { updated: 0 };

  const result = await DevicePushToken.updateMany(
    { token: { $in: tokens } },
    {
      isActive: false,
      lastFailureReason: reason,
      lastFailureAt: new Date(),
    }
  );

  return { updated: result.modifiedCount || 0 };
};

module.exports = {
  registerPushToken,
  removePushToken,
  getActivePushTokensForUser,
  deactivatePushTokens,
  normalizeTokenValue,
};
