const express = require("express");

const catchAsync = require("../utils/catchAsync");
const { env } = require("../config/env");
const { handleTelegramUpdate } = require("../services/telegramService");

const router = express.Router();

// Telegram calls this URL directly (no auth middleware applies here); we
// verify the secret token Telegram echoes back on every webhook request
// instead, per https://core.telegram.org/bots/api#setwebhook.
router.post(
  "/webhook",
  catchAsync(async (req, res) => {
    if (env.telegramWebhookSecret) {
      const suppliedSecret = req.headers["x-telegram-bot-api-secret-token"];
      if (suppliedSecret !== env.telegramWebhookSecret) {
        return res.sendStatus(401);
      }
    }

    await handleTelegramUpdate(req.body);
    return res.sendStatus(200);
  })
);

module.exports = router;
