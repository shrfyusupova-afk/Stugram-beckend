const crypto = require("crypto");

const TelegramLinkCode = require("../models/TelegramLinkCode");
const TelegramChatState = require("../models/TelegramChatState");
const TelegramSupportMessage = require("../models/TelegramSupportMessage");
const OtpCode = require("../models/OtpCode");
const User = require("../models/User");
const { env } = require("../config/env");
const { hashOtp } = require("../utils/token");
const ApiError = require("../utils/ApiError");
const logger = require("../utils/logger");

const LINK_CODE_EXPIRES_MINUTES = 10;
const REGISTER_OTP_EXPIRES_MINUTES = 15;
const TELEGRAM_API_BASE = "https://api.telegram.org";

const MENU_LABEL_REGISTER = "📝 Ro'yxatdan o'tish";
const MENU_LABEL_SUPPORT = "📩 Adminga murojaat qilish";
const MENU_LABEL_CHANNELS = "📢 Bizning kanalimiz";

// Public Telegram sticker (animated checkmark, "AnimatedEmojies" set) used to
// confirm a support message was received; verified live against this bot.
const CHECKMARK_STICKER_FILE_ID =
  "CAACAgEAAxUAAWpOjtoGmIrhroZeNnA9_XRwV2pbAAKfAwACid9YRM6KQLzK3HtFPAQ";

const isTelegramConfigured = () => Boolean(env.telegramBotToken);

const identityForChat = (chatId) => `tg:${chatId}`;

const generateOtp = () => String(Math.floor(100000 + Math.random() * 900000));

const sendTelegramMessage = async (chatId, text, extra = {}) => {
  if (!isTelegramConfigured()) {
    throw new Error("Telegram bot is not configured");
  }

  const response = await fetch(`${TELEGRAM_API_BASE}/bot${env.telegramBotToken}/sendMessage`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ chat_id: chatId, text, ...extra }),
  });

  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    logger.error("telegram_send_message_failed", {
      status: response.status,
      message: errorText.slice(0, 300),
    });
    throw new Error(`Telegram sendMessage failed with status ${response.status}`);
  }

  return response.json();
};

const replySafely = (chatId, text, extra = {}) =>
  sendTelegramMessage(chatId, text, extra).catch(() => {});

const sendTelegramSticker = async (chatId, stickerFileId) => {
  if (!isTelegramConfigured()) {
    throw new Error("Telegram bot is not configured");
  }

  const response = await fetch(`${TELEGRAM_API_BASE}/bot${env.telegramBotToken}/sendSticker`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ chat_id: chatId, sticker: stickerFileId }),
  });

  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    logger.error("telegram_send_sticker_failed", {
      status: response.status,
      message: errorText.slice(0, 300),
    });
    throw new Error(`Telegram sendSticker failed with status ${response.status}`);
  }

  return response.json();
};

const sendStickerSafely = (chatId, stickerFileId) =>
  sendTelegramSticker(chatId, stickerFileId).catch(() => {});

const createLinkCode = async () => {
  if (!isTelegramConfigured()) {
    throw new ApiError(503, "Telegram login is not configured");
  }

  const code = crypto.randomBytes(6).toString("hex");
  const expiresAt = new Date(Date.now() + LINK_CODE_EXPIRES_MINUTES * 60 * 1000);

  await TelegramLinkCode.create({ code, expiresAt });

  return {
    code,
    expiresAt,
    botUsername: env.telegramBotUsername,
    deepLink: env.telegramBotUsername ? `https://t.me/${env.telegramBotUsername}?start=${code}` : null,
  };
};

const getLinkStatus = async (code) => {
  const record = await TelegramLinkCode.findOne({ code });

  if (!record || record.expiresAt < new Date()) {
    throw new ApiError(404, "Link code expired or not found");
  }

  if (!record.linked) {
    return { linked: false };
  }

  const identity = record.chatId ? identityForChat(record.chatId) : null;
  const existingUser = identity
    ? await User.findOne({ identity }).select("username")
    : null;

  return {
    linked: true,
    alreadyRegistered: Boolean(existingUser),
    existingUsername: existingUser?.username || null,
    identity,
    phoneNumber: record.phoneNumber,
    telegramUsername: record.telegramUsername,
    firstName: record.firstName,
    // The register OTP is only meaningful for fresh registrations; the code
    // param is the shared secret that authorizes reading it.
    otp: existingUser ? null : record.otp,
  };
};

const CONTACT_REQUEST_KEYBOARD = {
  reply_markup: {
    keyboard: [
      [{ text: "📱 Telefon raqamni yuborish", request_contact: true }],
      [{ text: MENU_LABEL_SUPPORT }, { text: MENU_LABEL_CHANNELS }],
    ],
    resize_keyboard: true,
    is_persistent: true,
  },
};

// Shown once registration/linking is done (or already-registered): the
// permanent bottom menu with support + channels, no more contact-request row.
const MAIN_MENU_KEYBOARD = {
  reply_markup: {
    keyboard: [[{ text: MENU_LABEL_SUPPORT }, { text: MENU_LABEL_CHANNELS }]],
    resize_keyboard: true,
    is_persistent: true,
  },
};

// Shown on a bare "/start" (no code): the bot can be discovered and used on
// its own, without the app having opened it first.
const DEFAULT_MENU_KEYBOARD = {
  reply_markup: {
    keyboard: [
      [{ text: MENU_LABEL_REGISTER }],
      [{ text: MENU_LABEL_CHANNELS }, { text: MENU_LABEL_SUPPORT }],
    ],
    resize_keyboard: true,
    is_persistent: true,
  },
};

const handleStartCommand = async (message) => {
  const chat = message.chat;
  const code = message.text.replace("/start", "").trim();

  if (!code) {
    const existingUser = await User.findOne({ identity: identityForChat(chat.id) }).select("username");
    if (existingUser) {
      await replySafely(
        chat.id,
        `Assalomu alaykum! 👋\n\nSiz allaqachon ro'yxatdan o'tgansiz (@${existingUser.username}). Ilovadagi "Kirish" bo'limidan username va parolingiz bilan kiring. ✅`,
        MAIN_MENU_KEYBOARD
      );
      return;
    }

    await replySafely(
      chat.id,
      "Assalomu alaykum! 👋\n\nStugram — talabalar uchun zamonaviy ijtimoiy tarmoq. Ro'yxatdan o'tishni boshlash uchun pastdagi tugmani bosing.",
      DEFAULT_MENU_KEYBOARD
    );
    return;
  }

  const record = await TelegramLinkCode.findOne({ code });
  if (!record || record.expiresAt < new Date()) {
    await replySafely(
      chat.id,
      "Bu havolaning muddati o'tgan. ⏳\nIlovaga qaytib, \"Telegram orqali ro'yxatdan o'tish\" tugmasini qaytadan bosing."
    );
    return;
  }

  record.chatId = String(chat.id);
  record.telegramUsername = chat.username || null;
  record.firstName = chat.first_name || null;

  const existingUser = await User.findOne({ identity: identityForChat(chat.id) }).select("username");
  if (existingUser) {
    // Let the app stop waiting and show the "already registered" path.
    record.linked = true;
    await record.save();
    await replySafely(
      chat.id,
      `Siz allaqachon ro'yxatdan o'tgansiz (@${existingUser.username}). ✅\nIlovadagi \"Kirish\" bo'limidan username va parolingiz bilan kiring. Parolni unutgan bo'lsangiz, \"Parolni unutdingizmi?\" tugmasini bosing.`,
      MAIN_MENU_KEYBOARD
    );
    return;
  }

  await record.save();
  await replySafely(
    chat.id,
    "Stugram'ga xush kelibsiz! 👋\n\nRo'yxatdan o'tishni davom ettirish uchun pastdagi tugma orqali telefon raqamingizni yuboring.",
    CONTACT_REQUEST_KEYBOARD
  );
};

// Fired when the user presses "Ro'yxatdan o'tish" inside the bot directly
// (as opposed to arriving via the app's "/start <code>" deep link) — mints
// its own link code with chatId already attached.
const handleRegisterButtonPress = async (chat) => {
  const existingUser = await User.findOne({ identity: identityForChat(chat.id) }).select("username");
  if (existingUser) {
    await replySafely(
      chat.id,
      `Siz allaqachon ro'yxatdan o'tgansiz (@${existingUser.username}). Ilovadagi "Kirish" bo'limidan kiring. ✅`,
      MAIN_MENU_KEYBOARD
    );
    return;
  }

  let record = await TelegramLinkCode.findOne({
    chatId: String(chat.id),
    linked: false,
    expiresAt: { $gt: new Date() },
  }).sort({ createdAt: -1 });

  if (!record) {
    record = await TelegramLinkCode.create({
      code: crypto.randomBytes(6).toString("hex"),
      chatId: String(chat.id),
      telegramUsername: chat.username || null,
      firstName: chat.first_name || null,
      expiresAt: new Date(Date.now() + LINK_CODE_EXPIRES_MINUTES * 60 * 1000),
    });
  }

  await replySafely(
    chat.id,
    "Ro'yxatdan o'tishni davom ettirish uchun pastdagi tugma orqali telefon raqamingizni yuboring.",
    CONTACT_REQUEST_KEYBOARD
  );
};

const handleContactMessage = async (message) => {
  const chat = message.chat;
  const contact = message.contact;

  // Only accept the user's own contact card, not a forwarded one.
  if (contact.user_id && String(contact.user_id) !== String(chat.id)) {
    await replySafely(chat.id, "Iltimos, o'zingizning telefon raqamingizni yuboring.", CONTACT_REQUEST_KEYBOARD);
    return;
  }

  const record = await TelegramLinkCode.findOne({
    chatId: String(chat.id),
    linked: false,
    expiresAt: { $gt: new Date() },
  }).sort({ createdAt: -1 });

  if (!record) {
    await replySafely(
      chat.id,
      "Faol so'rov topilmadi. Ilovaga qaytib, \"Telegram orqali ro'yxatdan o'tish\" tugmasini qaytadan bosing.",
      MAIN_MENU_KEYBOARD
    );
    return;
  }

  const identity = identityForChat(chat.id);

  const existingUser = await User.findOne({ identity }).select("username");
  if (existingUser) {
    record.linked = true;
    await record.save();
    await replySafely(
      chat.id,
      `Siz allaqachon ro'yxatdan o'tgansiz (@${existingUser.username}). Ilovadagi \"Kirish\" bo'limidan kiring. ✅`,
      MAIN_MENU_KEYBOARD
    );
    return;
  }

  // Contact share proves ownership of this Telegram account, so the register
  // OTP is created pre-verified: the app picks it up via link-status and
  // finishes registration without the user retyping anything.
  const otp = generateOtp();
  await OtpCode.deleteMany({ identity, purpose: "register" });
  await OtpCode.create({
    identity,
    purpose: "register",
    codeHash: hashOtp(identity, otp),
    isVerified: true,
    expiresAt: new Date(Date.now() + REGISTER_OTP_EXPIRES_MINUTES * 60 * 1000),
  });

  const rawPhone = String(contact.phone_number || "").replace(/\s+/g, "");
  record.phoneNumber = rawPhone && !rawPhone.startsWith("+") ? `+${rawPhone}` : rawPhone || null;
  record.firstName = chat.first_name || record.firstName;
  record.otp = otp;
  record.linked = true;
  await record.save();

  logger.info("telegram_contact_linked", { chatId: String(chat.id) });

  await replySafely(
    chat.id,
    "Raqamingiz tasdiqlandi! ✅\n\nAgar Stugram ilovasi ochiq bo'lsa, ro'yxatdan o'tish o'zi davom etadi. Aks holda pastdagi tugmani bosing.",
    MAIN_MENU_KEYBOARD
  );

  // Inline keyboard "url" buttons only accept http(s) (custom app schemes are
  // rejected by Telegram), so route through an https bridge page that
  // redirects into the app via stugram://telegram-register.
  const bridgeLink = `${env.publicBaseUrl}/app/telegram-register?code=${record.code}`;
  await replySafely(chat.id, "👇 Ro'yxatdan o'tishni yakunlash uchun bosing:", {
    reply_markup: { inline_keyboard: [[{ text: "📲 Ilovani ochish", url: bridgeLink }]] },
  });
};

const DEFAULT_CHANNELS_TEXT =
  "Bizning rasmiy kanal va chat havolalarimiz tez orada shu yerda paydo bo'ladi. 🔗";

const handleChannelsButton = async (chat) => {
  await replySafely(chat.id, env.telegramChannelsText || DEFAULT_CHANNELS_TEXT, MAIN_MENU_KEYBOARD);
};

const handleSupportButtonPress = async (chat) => {
  await TelegramChatState.findOneAndUpdate(
    { chatId: String(chat.id) },
    { chatId: String(chat.id), awaitingSupportMessage: true },
    { upsert: true }
  );
  await replySafely(
    chat.id,
    "✍️ Murojaatingizni yozib qoldirishingiz mumkin. Biz 48 soat ichida javob qaytaramiz.",
    MAIN_MENU_KEYBOARD
  );
};

const handleSupportMessageSubmission = async (message) => {
  const chat = message.chat;

  await TelegramSupportMessage.create({
    chatId: String(chat.id),
    telegramUsername: chat.username || null,
    firstName: chat.first_name || null,
    message: message.text,
  });

  await TelegramChatState.findOneAndUpdate(
    { chatId: String(chat.id) },
    { awaitingSupportMessage: false }
  );

  logger.info("telegram_support_message_received", { chatId: String(chat.id) });

  await replySafely(
    chat.id,
    "✅ Qabul qilindi! Murojaatingiz ma'muriyatga yuborildi. 48 soat ichida javob beramiz.",
    MAIN_MENU_KEYBOARD
  );
  await sendStickerSafely(chat.id, CHECKMARK_STICKER_FILE_ID);
};

// Called by the Telegram webhook for every bot update. Handles the
// registration handshake ("/start <code>" + contact share) and the
// persistent bottom menu (contact admin / channels).
const handleTelegramUpdate = async (update) => {
  const message = update?.message;
  const chat = message?.chat;

  if (!message || !chat || chat.type !== "private") {
    return;
  }

  if (message.contact) {
    await handleContactMessage(message);
    return;
  }

  const text = typeof message.text === "string" ? message.text : null;

  if (text && text.startsWith("/start")) {
    await handleStartCommand(message);
    return;
  }

  if (text === MENU_LABEL_REGISTER) {
    await handleRegisterButtonPress(chat);
    return;
  }

  if (text === MENU_LABEL_CHANNELS) {
    await handleChannelsButton(chat);
    return;
  }

  if (text === MENU_LABEL_SUPPORT) {
    await handleSupportButtonPress(chat);
    return;
  }

  if (text) {
    const state = await TelegramChatState.findOne({ chatId: String(chat.id) });
    if (state?.awaitingSupportMessage) {
      await handleSupportMessageSubmission(message);
    }
  }
};

module.exports = {
  isTelegramConfigured,
  sendTelegramMessage,
  createLinkCode,
  getLinkStatus,
  handleTelegramUpdate,
};
