const mongoose = require("mongoose");

const userSchema = new mongoose.Schema(
  {
    accountId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Account",
      default: null,
      index: true,
    },
    identity: {
      type: String,
      required: false,
      unique: true,
      trim: true,
      lowercase: true,
      sparse: true,
    },
    fullName: {
      type: String,
      required: true,
      trim: true,
      maxlength: 100,
    },
    username: {
      type: String,
      required: true,
      unique: true,
      trim: true,
      lowercase: true,
      minlength: 3,
      maxlength: 30,
      index: true,
    },
    bio: {
      type: String,
      default: "",
      maxlength: 160,
    },
    avatar: {
      type: String,
      default: null,
    },
    avatarPublicId: {
      type: String,
      default: null,
    },
    banner: {
      type: String,
      default: null,
    },
    bannerPublicId: {
      type: String,
      default: null,
    },
    passwordHash: {
      type: String,
      default: null,
    },
    role: {
      type: String,
      enum: ["user", "moderator", "admin"],
      default: "user",
      index: true,
    },
    isPrivateAccount: {
      type: Boolean,
      default: false,
    },
    type: {
      type: String,
      enum: ["student", "blogger"],
      default: "student",
      index: true,
    },
    followersCount: {
      type: Number,
      default: 0,
    },
    followingCount: {
      type: Number,
      default: 0,
    },
    postsCount: {
      type: Number,
      default: 0,
    },
    googleId: {
      type: String,
      default: null,
      index: true,
    },
    birthday: {
      type: Date,
      default: null,
    },
    location: {
      type: String,
      default: "",
      maxlength: 120,
      index: true,
    },
    school: {
      type: String,
      default: "",
      maxlength: 150,
      index: true,
    },
    region: {
      type: String,
      default: "",
      maxlength: 100,
      index: true,
    },
    district: {
      type: String,
      default: "",
      maxlength: 100,
      index: true,
    },
    grade: {
      type: String,
      default: "",
      maxlength: 50,
      index: true,
    },
    group: {
      type: String,
      default: "",
      maxlength: 50,
      index: true,
    },
    lastLoginAt: {
      type: Date,
      default: null,
    },
    lastSeenAt: {
      type: Date,
      default: null,
    },
    tokenInvalidBefore: {
      type: Date,
      default: null,
    },
    isSuspended: {
      type: Boolean,
      default: false,
    },
    suspendedUntil: {
      type: Date,
      default: null,
    },
    suspensionReason: {
      type: String,
      default: null,
    },
  },
  { timestamps: true }
);

userSchema.index({ username: 1, fullName: 1 });
userSchema.index({ region: 1, district: 1, school: 1, grade: 1, group: 1 });
userSchema.index({ accountId: 1, createdAt: -1 });

module.exports = mongoose.model("User", userSchema);
