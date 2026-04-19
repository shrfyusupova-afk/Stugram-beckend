const multer = require("multer");

const ApiError = require("../utils/ApiError");
const { env } = require("../config/env");

const imageAndVideoMimeTypes = [
  "image/jpeg",
  "image/png",
  "image/webp",
  "video/mp4",
  "video/quicktime",
  "video/webm",
];

const chatUploadMimeTypes = [
  ...imageAndVideoMimeTypes,
  "audio/mp4",
  "audio/x-m4a",
  "audio/webm",
  "audio/ogg",
  "audio/wav",
  "audio/x-wav",
  "application/pdf",
  "application/zip",
  "application/x-zip-compressed",
  "text/plain",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
];

const imageMimeTypes = ["image/jpeg", "image/png", "image/webp"];

const startsWithBytes = (buffer, bytes, offset = 0) => {
  if (!Buffer.isBuffer(buffer) || buffer.length < offset + bytes.length) return false;
  return bytes.every((byte, index) => buffer[offset + index] === byte);
};

const includesAscii = (buffer, text, offset = 0, windowLength = 16) => {
  if (!Buffer.isBuffer(buffer) || buffer.length < offset + windowLength) return false;
  return buffer.slice(offset, offset + windowLength).toString("ascii").includes(text);
};

const detectFileKind = (buffer) => {
  if (startsWithBytes(buffer, [0xff, 0xd8, 0xff])) return "image/jpeg";
  if (startsWithBytes(buffer, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) return "image/png";
  if (startsWithBytes(buffer, [0x52, 0x49, 0x46, 0x46]) && includesAscii(buffer, "WEBP", 8, 8)) return "image/webp";
  if (includesAscii(buffer, "ftyp", 4, 12)) return "container/mp4";
  if (startsWithBytes(buffer, [0x1a, 0x45, 0xdf, 0xa3])) return "container/webm";
  if (startsWithBytes(buffer, [0x4f, 0x67, 0x67, 0x53])) return "audio/ogg";
  if (startsWithBytes(buffer, [0x52, 0x49, 0x46, 0x46]) && includesAscii(buffer, "WAVE", 8, 8)) return "audio/wav";
  if (startsWithBytes(buffer, [0x49, 0x44, 0x33])) return "audio/mpeg";
  if (buffer.length >= 2 && buffer[0] === 0xff && (buffer[1] & 0xe0) === 0xe0) return "audio/mpeg";
  if (startsWithBytes(buffer, [0x25, 0x50, 0x44, 0x46])) return "application/pdf";
  if (startsWithBytes(buffer, [0x50, 0x4b, 0x03, 0x04])) return "application/zip";
  if (startsWithBytes(buffer, [0xd0, 0xcf, 0x11, 0xe0])) return "application/msword";
  const asciiSample = buffer.slice(0, Math.min(buffer.length, 64)).toString("utf8");
  if (asciiSample && /^[\x09\x0A\x0D\x20-\x7E]*$/.test(asciiSample)) return "text/plain";
  return null;
};

const isDetectedMimeTypeCompatible = (declaredMimeType, detectedMimeType) => {
  if (!declaredMimeType || !detectedMimeType) return false;
  if (declaredMimeType === detectedMimeType) return true;

  if (detectedMimeType === "container/mp4") {
    return ["video/mp4", "video/quicktime", "audio/mp4", "audio/x-m4a"].includes(declaredMimeType);
  }

  if (detectedMimeType === "container/webm") {
    return ["video/webm", "audio/webm"].includes(declaredMimeType);
  }

  if (detectedMimeType === "application/zip") {
    return [
      "application/zip",
      "application/x-zip-compressed",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ].includes(declaredMimeType);
  }

  if (detectedMimeType === "audio/wav") {
    return ["audio/wav", "audio/x-wav"].includes(declaredMimeType);
  }

  return false;
};

const sanitizeFileMetadata = (file) => {
  const originalname = typeof file.originalname === "string" ? file.originalname.trim() : "";
  if (!originalname || originalname.length > 180) {
    throw new ApiError(400, "Invalid upload filename");
  }

  if (/[\\/]/.test(originalname) || /\0/.test(originalname)) {
    throw new ApiError(400, "Unsafe upload filename");
  }
};

const validateUploadedFile = (file, allowedMimeTypes) => {
  if (!file || !Buffer.isBuffer(file.buffer) || file.buffer.length === 0) {
    throw new ApiError(400, "Uploaded file is empty or malformed");
  }

  sanitizeFileMetadata(file);

  if (!allowedMimeTypes.includes(file.mimetype)) {
    throw new ApiError(400, "Unsupported file type");
  }

  const detectedMimeType = detectFileKind(file.buffer);
  if (!detectedMimeType) {
    throw new ApiError(400, "Uploaded file content is invalid");
  }

  if (!isDetectedMimeTypeCompatible(file.mimetype, detectedMimeType)) {
    throw new ApiError(400, "Uploaded file content does not match its file type");
  }
};

const createBaseUploader = (allowedMimeTypes) =>
  multer({
    storage: multer.memoryStorage(),
    limits: {
      fileSize: env.mediaMaxFileSizeBytes,
    },
    fileFilter: (_req, file, cb) => {
      try {
        sanitizeFileMetadata(file);
        if (!allowedMimeTypes.includes(file.mimetype)) {
          cb(new ApiError(400, "Unsupported file type"));
          return;
        }
        cb(null, true);
      } catch (error) {
        cb(error);
      }
    },
  });

const wrapUploader = (uploader, allowedMimeTypes) => (req, res, next) => {
  uploader(req, res, (error) => {
    if (error) {
      next(error);
      return;
    }

    try {
      const files = Array.isArray(req.files) ? req.files : req.file ? [req.file] : [];
      files.forEach((file) => validateUploadedFile(file, allowedMimeTypes));
      next();
    } catch (validationError) {
      next(validationError);
    }
  });
};

const uploadAvatar = wrapUploader(createBaseUploader(imageMimeTypes).single("avatar"), imageMimeTypes);
const uploadBanner = wrapUploader(createBaseUploader(imageMimeTypes).single("banner"), imageMimeTypes);
const uploadGroupAvatar = wrapUploader(createBaseUploader(imageMimeTypes).single("avatar"), imageMimeTypes);
const uploadSupportScreenshot = wrapUploader(createBaseUploader(imageMimeTypes).single("screenshot"), imageMimeTypes);
const uploadPostMedia = wrapUploader(createBaseUploader(imageAndVideoMimeTypes).array("media", 10), imageAndVideoMimeTypes);
const uploadStoryMedia = wrapUploader(createBaseUploader(imageAndVideoMimeTypes).single("media"), imageAndVideoMimeTypes);
const uploadChatMedia = wrapUploader(createBaseUploader(chatUploadMimeTypes).single("media"), chatUploadMimeTypes);

module.exports = {
  uploadAvatar,
  uploadBanner,
  uploadGroupAvatar,
  uploadSupportScreenshot,
  uploadPostMedia,
  uploadStoryMedia,
  uploadChatMedia,
};
