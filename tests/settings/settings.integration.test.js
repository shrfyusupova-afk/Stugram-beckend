const { setupIntegrationTestSuite } = require("../helpers/integration");
const {
  authHeader,
  createAuthenticatedUser,
  fakeImageBuffer,
} = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Settings, privacy, and support integration", () => {
  it("syncs notification settings and hidden words preferences", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "settings@example.com",
      username: "settings_user",
    });

    const getNotificationSettings = await client
      .get("/api/v1/settings/me/notifications")
      .set(authHeader(accessToken));

    expect(getNotificationSettings.statusCode).toBe(200);
    expect(getNotificationSettings.body.data.likes).toBe(true);

    const updateNotificationSettings = await client
      .patch("/api/v1/settings/me/notifications")
      .set(authHeader(accessToken))
      .send({
        likes: false,
        comments: true,
        followRequests: true,
        messages: false,
        mentions: true,
        system: true,
      });

    expect(updateNotificationSettings.statusCode).toBe(200);
    expect(updateNotificationSettings.body.data.likes).toBe(false);
    expect(updateNotificationSettings.body.data.messages).toBe(false);

    const hiddenWordsResponse = await client
      .patch("/api/v1/settings/me/hidden-words")
      .set(authHeader(accessToken))
      .send({
        words: ["Spam", " scam ", "spam"],
        hideComments: true,
        hideMessages: false,
        hideStoryReplies: true,
      });

    expect(hiddenWordsResponse.statusCode).toBe(200);
    expect(hiddenWordsResponse.body.data.words).toEqual(["spam", "scam"]);

    const unreadCountResponse = await client
      .get("/api/v1/notifications/unread-count")
      .set(authHeader(accessToken));

    expect(unreadCountResponse.statusCode).toBe(200);
    expect(unreadCountResponse.body.data.unreadCount).toBeGreaterThanOrEqual(0);
  });

  it("supports close friends and blocked accounts management", async () => {
    const client = getClient();
    const { user: actor, accessToken } = await createAuthenticatedUser({
      identity: "privacy-actor@example.com",
      username: "privacy_actor",
    });
    const { user: target } = await createAuthenticatedUser({
      identity: "privacy-target@example.com",
      username: "privacy_target",
    });

    const addCloseFriendResponse = await client
      .post(`/api/v1/close-friends/${target._id}`)
      .set(authHeader(accessToken));

    expect(addCloseFriendResponse.statusCode).toBe(201);

    const closeFriendsListResponse = await client
      .get("/api/v1/close-friends/me")
      .set(authHeader(accessToken));

    expect(closeFriendsListResponse.statusCode).toBe(200);
    expect(closeFriendsListResponse.body.data).toHaveLength(1);

    const blockResponse = await client
      .post(`/api/v1/chats/users/${target._id}/block`)
      .set(authHeader(accessToken));

    expect(blockResponse.statusCode).toBe(200);

    const blockedAccountsResponse = await client
      .get("/api/v1/blocks/me")
      .set(authHeader(accessToken));

    expect(blockedAccountsResponse.statusCode).toBe(200);
    expect(blockedAccountsResponse.body.data[0].user._id.toString()).toBe(target._id.toString());

    const unblockResponse = await client
      .delete(`/api/v1/blocks/${target._id}`)
      .set(authHeader(accessToken));

    expect(unblockResponse.statusCode).toBe(200);
    expect(actor._id).toBeTruthy();
  });

  it("creates, lists, and fetches support tickets", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "support@example.com",
      username: "support_user",
    });

    const createSupportResponse = await client
      .post("/api/v1/support/problems")
      .set(authHeader(accessToken))
      .field("category", "bug")
      .field("subject", "Crash on messages")
      .field("description", "The app crashes when opening messages.")
      .field("appVersion", "1.0.0")
      .field("deviceInfo", "Android 14")
      .attach("screenshot", fakeImageBuffer(), {
        filename: "support.jpg",
        contentType: "image/jpeg",
      });

    expect(createSupportResponse.statusCode).toBe(201);

    const listResponse = await client
      .get("/api/v1/support/problems/me")
      .set(authHeader(accessToken));

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.body.data).toHaveLength(1);

    const detailResponse = await client
      .get(`/api/v1/support/problems/${createSupportResponse.body.data._id}`)
      .set(authHeader(accessToken));

    expect(detailResponse.statusCode).toBe(200);
    expect(detailResponse.body.data.subject).toBe("Crash on messages");
  });

  it("supports admin-side support ticket triage workflow", async () => {
    const client = getClient();
    const { SupportTicket, AuditLog } = getModels();
    const { user: requester, accessToken: requesterToken } = await createAuthenticatedUser({
      identity: "support-admin-requester@example.com",
      username: "support_admin_requester",
    });
    const { user: moderator, accessToken: moderatorToken } = await createAuthenticatedUser({
      identity: "support-moderator@example.com",
      username: "support_moderator",
      role: "moderator",
    });
    const { user: admin, accessToken: adminToken } = await createAuthenticatedUser({
      identity: "support-admin@example.com",
      username: "support_admin",
      role: "admin",
    });

    const createSupportResponse = await client
      .post("/api/v1/support/problems")
      .set(authHeader(requesterToken))
      .field("category", "chat")
      .field("subject", "Messages fail to load")
      .field("description", "Messages screen keeps showing a loading spinner.")
      .field("appVersion", "2.1.0")
      .field("deviceInfo", "Android 15 / Pixel")
      .attach("screenshot", fakeImageBuffer(), {
        filename: "support-admin.jpg",
        contentType: "image/jpeg",
      });

    expect(createSupportResponse.statusCode).toBe(201);

    const ticketId = createSupportResponse.body.data._id;

    const adminListResponse = await client
      .get("/api/v1/admin/support/tickets")
      .query({
        status: "open",
        category: "chat",
        search: "support_admin_requester",
      })
      .set(authHeader(adminToken));

    expect(adminListResponse.statusCode).toBe(200);
    expect(adminListResponse.body.data).toHaveLength(1);
    expect(adminListResponse.body.data[0].user.username).toBe("support_admin_requester");

    const assignResponse = await client
      .patch(`/api/v1/admin/support/tickets/${ticketId}/assign`)
      .set(authHeader(adminToken))
      .send({
        assignedTo: moderator._id.toString(),
      });

    expect(assignResponse.statusCode).toBe(200);
    expect(assignResponse.body.data.assignedTo.username).toBe("support_moderator");

    const reviewingResponse = await client
      .patch(`/api/v1/admin/support/tickets/${ticketId}/status`)
      .set(authHeader(moderatorToken))
      .send({
        status: "reviewing",
      });

    expect(reviewingResponse.statusCode).toBe(200);
    expect(reviewingResponse.body.data.status).toBe("reviewing");

    const noteResponse = await client
      .post(`/api/v1/admin/support/tickets/${ticketId}/notes`)
      .set(authHeader(moderatorToken))
      .send({
        note: "Issue reproduced on Android 15. Investigating chat sync path.",
      });

    expect(noteResponse.statusCode).toBe(200);
    expect(noteResponse.body.data.internalNotes).toHaveLength(1);

    const resolveResponse = await client
      .patch(`/api/v1/admin/support/tickets/${ticketId}/status`)
      .set(authHeader(adminToken))
      .send({
        status: "resolved",
      });

    expect(resolveResponse.statusCode).toBe(200);
    expect(resolveResponse.body.data.status).toBe("resolved");

    const detailResponse = await client
      .get(`/api/v1/admin/support/tickets/${ticketId}`)
      .set(authHeader(adminToken));

    expect(detailResponse.statusCode).toBe(200);
    expect(detailResponse.body.data.user._id.toString()).toBe(requester._id.toString());
    expect(detailResponse.body.data.screenshot.url).toBeTruthy();
    expect(detailResponse.body.data.deviceInfo).toContain("Android 15");
    expect(detailResponse.body.data.internalNotes).toHaveLength(1);

    const storedTicket = await SupportTicket.findById(ticketId).lean();
    expect(storedTicket.status).toBe("resolved");
    expect(storedTicket.assignedTo.toString()).toBe(moderator._id.toString());
    expect(storedTicket.internalNotes).toHaveLength(1);

    expect(
      await AuditLog.countDocuments({
        category: "support",
        "details.ticketId": ticketId,
      })
    ).toBe(4);
  });

  it("returns a lightweight activity aggregate feed", async () => {
    const client = getClient();
    const { user: recipient, accessToken } = await createAuthenticatedUser({
      identity: "activity-recipient@example.com",
      username: "activity_recipient",
    });
    const { user: actor, accessToken: actorToken } = await createAuthenticatedUser({
      identity: "activity-actor@example.com",
      username: "activity_actor",
    });

    await client
      .post(`/api/v1/follows/${recipient._id}`)
      .set(authHeader(actorToken));

    const activityResponse = await client
      .get("/api/v1/activity/me")
      .set(authHeader(accessToken));

    expect(activityResponse.statusCode).toBe(200);
    expect(Array.isArray(activityResponse.body.data.recentActivity)).toBe(true);
    expect(activityResponse.body.data.summary).toBeTruthy();
  });
});
