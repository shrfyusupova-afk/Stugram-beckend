const mongoose = require("mongoose");
const Block = require("../models/Block");
const chatSecurityService = require("./chatSecurityService");
const { getPagination } = require("../utils/pagination");

const blockedUserPreviewProjection = {
  _id: "$blockedUser._id",
  username: "$blockedUser.username",
  fullName: "$blockedUser.fullName",
  avatar: "$blockedUser.avatar",
  bio: "$blockedUser.bio",
  isPrivateAccount: "$blockedUser.isPrivateAccount",
};

const getBlockedAccounts = async (currentUserId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const [result] = await Block.aggregate([
    { $match: { blocker: new mongoose.Types.ObjectId(String(currentUserId)) } },
    {
      $lookup: {
        from: "users",
        localField: "blocked",
        foreignField: "_id",
        as: "blockedUser",
      },
    },
    { $unwind: "$blockedUser" },
    { $sort: { createdAt: -1 } },
    {
      $facet: {
        items: [
          { $skip: skip },
          { $limit: limit },
          {
            $project: {
              _id: 0,
              blockedAt: "$createdAt",
              user: blockedUserPreviewProjection,
            },
          },
        ],
        meta: [{ $count: "total" }],
      },
    },
  ]);

  const total = result?.meta?.[0]?.total || 0;

  return {
    items: result?.items || [],
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
    },
  };
};

const unblockUser = async (currentUserId, targetUserId, meta = {}) =>
  chatSecurityService.unblockUser(currentUserId, targetUserId, meta);

const blockUser = async (currentUserId, targetUserId, meta = {}) =>
  chatSecurityService.blockUser(currentUserId, targetUserId, meta);

module.exports = {
  getBlockedAccounts,
  blockUser,
  unblockUser,
};
