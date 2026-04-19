const ApiError = require("../../../utils/ApiError");
const { getPagination } = require("../../../utils/pagination");
const Post = require("../../../models/Post");
const User = require("../../../models/User");
const { createAuditLog } = require("../../../services/auditLogService");

const listPostsForModeration = async (query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = {};

  if (query.visibility === "hidden") filter.isHiddenByAdmin = true;
  if (query.visibility === "visible") filter.isHiddenByAdmin = false;
  if (query.search) filter.caption = { $regex: query.search, $options: "i" };

  const [items, total] = await Promise.all([
    Post.find(filter)
      .populate("author", "username fullName avatar")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    Post.countDocuments(filter),
  ]);

  return {
    items,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
      appliedFilters: {
        search: query.search || null,
        visibility: query.visibility || null,
      },
    },
  };
};

const hidePost = async (adminUser, postId, payload, meta = {}) => {
  const post = await Post.findById(postId);
  if (!post) throw new ApiError(404, "Post not found");

  post.isHiddenByAdmin = true;
  post.hiddenByAdminAt = new Date();
  post.hiddenByAdminReason = payload.reason || "";
  await post.save();

  await createAuditLog({
    actor: adminUser.id,
    action: "admin_panel.hide_post",
    category: "abuse",
    status: "warning",
    targetUser: post.author,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { postId, reason: payload.reason || null },
  });

  return post;
};

const deletePost = async (adminUser, postId, meta = {}) => {
  const post = await Post.findById(postId);
  if (!post) throw new ApiError(404, "Post not found");

  await Post.deleteOne({ _id: postId });
  await User.updateOne({ _id: post.author }, { $inc: { postsCount: -1 } });

  await createAuditLog({
    actor: adminUser.id,
    action: "admin_panel.delete_post",
    category: "abuse",
    status: "warning",
    targetUser: post.author,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { postId },
  });

  return { deleted: true, postId };
};

module.exports = {
  deletePost,
  hidePost,
  listPostsForModeration,
};
