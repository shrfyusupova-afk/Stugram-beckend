const bcrypt = require("bcryptjs");

const User = require("../models/User");
const { env } = require("../config/env");
const { getOrCreateSettings } = require("./settingsService");
const logger = require("../utils/logger");
const { maskIdentity } = require("./otpDeliveryService");

const getBootstrapLoginConfig = () => {
  if (!env.enableBootstrapUser) {
    return null;
  }

  if (
    !env.bootstrapUserIdentity ||
    !env.bootstrapUserPassword ||
    !env.bootstrapUserUsername ||
    !env.bootstrapUserFullName
  ) {
    throw new Error(
      "Bootstrap user is enabled but BOOTSTRAP_USER_IDENTITY, BOOTSTRAP_USER_PASSWORD, BOOTSTRAP_USER_USERNAME, and BOOTSTRAP_USER_FULL_NAME are not fully configured"
    );
  }

  return {
    identity: env.bootstrapUserIdentity,
    password: env.bootstrapUserPassword,
    username: env.bootstrapUserUsername.toLowerCase(),
    fullName: env.bootstrapUserFullName,
    role: env.bootstrapUserRole,
  };
};

const ensureBootstrapLoginUser = async () => {
  const bootstrapLogin = getBootstrapLoginConfig();
  if (!bootstrapLogin) {
    logger.info("Bootstrap login user is disabled");
    return null;
  }

  const passwordHash = await bcrypt.hash(bootstrapLogin.password, 12);

  let user = await User.findOne({ identity: bootstrapLogin.identity }).select("_id identity username fullName role");

  if (!user) {
    user = await User.create({
      identity: bootstrapLogin.identity,
      fullName: bootstrapLogin.fullName,
      username: bootstrapLogin.username,
      passwordHash,
      bio: "",
      isPrivateAccount: false,
      role: bootstrapLogin.role,
      lastLoginAt: null,
      isSuspended: false,
      suspendedUntil: null,
      suspensionReason: null,
    });

    await getOrCreateSettings(user.id);

    logger.info("Bootstrap login user created", {
      identity: maskIdentity(bootstrapLogin.identity),
      username: bootstrapLogin.username,
    });

    return user;
  }

  user.passwordHash = passwordHash;
  user.isSuspended = false;
  user.suspendedUntil = null;
  user.suspensionReason = null;
  user.role = bootstrapLogin.role;

  if (!user.username) {
    user.username = bootstrapLogin.username;
  }

  if (!user.fullName) {
    user.fullName = bootstrapLogin.fullName;
  }

  await user.save();
  await getOrCreateSettings(user.id);

  logger.info("Bootstrap login user ensured", {
    identity: maskIdentity(bootstrapLogin.identity),
    userId: user.id,
  });

  return user;
};

module.exports = {
  ensureBootstrapLoginUser,
};
