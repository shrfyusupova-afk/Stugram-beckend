const cloudinary = require("cloudinary").v2;

const { env } = require("./env");

const placeholderValues = new Set(["your_cloud_name", "your_api_key", "your_api_secret"]);

const isCloudinaryConfigured = () =>
  Boolean(env.cloudinaryName && env.cloudinaryKey && env.cloudinarySecret) &&
  !placeholderValues.has(env.cloudinaryName) &&
  !placeholderValues.has(env.cloudinaryKey) &&
  !placeholderValues.has(env.cloudinarySecret);

if (isCloudinaryConfigured()) {
  cloudinary.config({
    cloud_name: env.cloudinaryName,
    api_key: env.cloudinaryKey,
    api_secret: env.cloudinarySecret,
  });
}

module.exports = { cloudinary, isCloudinaryConfigured };
