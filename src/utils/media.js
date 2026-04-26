const streamifier = require("streamifier");

const { cloudinary, isCloudinaryConfigured } = require("../config/cloudinary");
const { env } = require("../config/env");
const ApiError = require("./ApiError");

const uploadBufferToCloudinary = (fileBuffer, folder, resourceType = "auto") =>
  new Promise((resolve, reject) => {
    if (!isCloudinaryConfigured()) {
      reject(new ApiError(503, "Media upload is not configured on the server"));
      return;
    }

    const uploadStream = cloudinary.uploader.upload_stream(
      { folder, resource_type: resourceType },
      (error, result) => {
        if (error) {
          reject(new ApiError(500, "Media upload failed", error));
          return;
        }

        if (!result?.secure_url || !result?.public_id || !result?.resource_type) {
          reject(new ApiError(502, "Media upload returned an incomplete response"));
          return;
        }

        resolve({
          url: result.secure_url,
          publicId: result.public_id,
          resourceType: result.resource_type,
          width: result.width || null,
          height: result.height || null,
          duration: result.duration || null,
        });
      }
    );

    streamifier.createReadStream(fileBuffer).pipe(uploadStream);
  });

const destroyCloudinaryAsset = async (publicId, resourceType = "image") => {
  if (!publicId) return null;
  if (!isCloudinaryConfigured()) return null;
  return cloudinary.uploader.destroy(publicId, { resource_type: resourceType });
};

const buildCloudinaryVideoThumbnailUrl = (publicId) => {
  if (!publicId || !isCloudinaryConfigured()) return null;
  return cloudinary.url(publicId, {
    resource_type: "video",
    secure: true,
    format: "jpg",
    transformation: [{ width: 720, crop: "limit", quality: "auto" }],
  });
};

const resolveUploadedMessageType = (uploaded, expectedType) => {
  if (expectedType === "file") return "file";
  if (expectedType === "voice") return "voice";
  if (expectedType === "round_video") return "round_video";
  return uploaded.resourceType === "video" ? "video" : "image";
};

const getCloudinaryDestroyType = (messageType) => {
  if (messageType === "file") return "raw";
  if (messageType === "voice" || messageType === "round_video" || messageType === "video") return "video";
  return "image";
};

const validateUploadedMedia = async ({ uploaded, expectedType }) => {
  const resolvedType = resolveUploadedMessageType(uploaded, expectedType);

  if (expectedType && expectedType !== resolvedType) {
    await destroyCloudinaryAsset(uploaded.publicId, getCloudinaryDestroyType(resolvedType));
    throw new ApiError(400, "Uploaded file type does not match messageType");
  }

  if (resolvedType === "video" && uploaded.duration && uploaded.duration > env.mediaMaxVideoDurationSeconds) {
    await destroyCloudinaryAsset(uploaded.publicId, getCloudinaryDestroyType(resolvedType));
    throw new ApiError(400, `Video duration exceeds ${env.mediaMaxVideoDurationSeconds} seconds`);
  }

  if (resolvedType === "voice" && uploaded.duration && uploaded.duration > env.chatMaxAudioDurationSeconds) {
    await destroyCloudinaryAsset(uploaded.publicId, getCloudinaryDestroyType(resolvedType));
    throw new ApiError(400, `Voice duration exceeds ${env.chatMaxAudioDurationSeconds} seconds`);
  }

  if (resolvedType === "round_video" && uploaded.duration && uploaded.duration > env.chatMaxRoundVideoDurationSeconds) {
    await destroyCloudinaryAsset(uploaded.publicId, getCloudinaryDestroyType(resolvedType));
    throw new ApiError(400, `Round video duration exceeds ${env.chatMaxRoundVideoDurationSeconds} seconds`);
  }

  return resolvedType;
};

module.exports = {
  uploadBufferToCloudinary,
  destroyCloudinaryAsset,
  buildCloudinaryVideoThumbnailUrl,
  validateUploadedMedia,
  getCloudinaryDestroyType,
};
