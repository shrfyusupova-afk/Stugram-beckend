const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const Brevo = require('@getbrevo/brevo');

// Brevo API sozlamalari
let apiInstance = new Brevo.TransactionalEmailsApi();
apiInstance.setApiKey(Brevo.TransactionalEmailsApiApiKeys.apiKey, process.env.BREVO_API_KEY);

// 1-QADAM: OTP yuborish
router.post('/send-otp', async (req, res) => {
    const { identity } = req.body;
    const otpCode = Math.floor(100000 + Math.random() * 900000).toString();

    // Validatsiya
    const isGmail = identity.endsWith("@gmail.com") && identity.length > 10;
    const isPhone = identity.startsWith("+998") && identity.length === 13;

    if (!isGmail && !isPhone) {
        return res.status(400).json({ error: "Faqat @gmail.com yoki +998XXXXXXXXX formatida kiriting" });
    }

    try {
        let user = await User.findOne({ identity });
        if (!user) {
            user = new User({ identity, otp: otpCode });
        } else {
            user.otp = otpCode;
        }
        await user.save();

        if (identity.includes('@')) {
            const sendSmtpEmail = new Brevo.SendSmtpEmail();
            sendSmtpEmail.subject = "StuGram - Tasdiqlash kodi";
            sendSmtpEmail.htmlContent = `
                <div style="font-family: Arial, sans-serif; text-align: center; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <h2 style="color: #1e293b;">StuGram</h2>
                    <p>Sizning tasdiqlash kodingiz:</p>
                    <h1 style="color: #1E60FF; letter-spacing: 5px;">${otpCode}</h1>
                    <p style="font-size: 12px; color: #666;">Ushbu kodni hech kimga bermang.</p>
                </div>`;
            sendSmtpEmail.sender = { name: "StuGram", email: "noreply@stugram.uz" };
            sendSmtpEmail.to = [{ email: identity }];

            await apiInstance.sendTransacEmail(sendSmtpEmail);
            console.log(`[BREVO] OTP sent to ${identity}`);
        } else {
            console.log(`[STUGRAM] OTP for ${identity}: ${otpCode}`);
        }

        res.status(200).json({ message: "Kod yuborildi", debugOtp: otpCode });
    } catch (err) {
        console.error("Brevo Error:", err);
        res.status(500).json({ error: "Kod yuborishda xatolik yuz berdi" });
    }
});

// 2-QADAM: OTPni tekshirish
router.post('/verify-otp', async (req, res) => {
    const { identity, otp } = req.body;
    try {
        const user = await User.findOne({ identity, otp });
        if (user) {
            res.status(200).json({ message: "OK" });
        } else {
            res.status(400).json({ error: "Noto'g'ri tasdiqlash kodi!" });
        }
    } catch (err) {
        res.status(500).json({ error: "Server xatosi" });
    }
});

// 3-QADAM: To'liq ro'yxatdan o'tish (Register)
router.post('/register', async (req, res) => {
    const { identity, password, fullName, username, region, district, school, grade, group } = req.body;
    try {
        let user = await User.findOne({ identity });
        if (!user) return res.status(404).json({ error: "Avval kodni tasdiqlang" });

        const usernameExists = await User.findOne({ username });
        if (usernameExists) return res.status(400).json({ error: "Ushbu username band" });

        const salt = await bcrypt.genSalt(10);
        user.password = await bcrypt.hash(password, salt);
        user.fullName = fullName;
        user.username = username;
        user.region = region;
        user.district = district;
        user.school = school;
        user.grade = grade;
        user.group = group;
        user.isVerified = true;
        user.otp = null;

        await user.save();
        const token = jwt.sign({ id: user._id }, process.env.JWT_SECRET, { expiresIn: '30d' });
        res.status(201).json({ token, user: { fullName, username, identity } });
    } catch (err) {
        res.status(400).json({ error: "Ro'yxatdan o'tishda xatolik" });
    }
});

// LOGIN
router.post('/login', async (req, res) => {
    const { username, password } = req.body;
    try {
        const user = await User.findOne({ $or: [{ username: username }, { identity: username }] });
        if (!user) return res.status(400).json({ error: "Foydalanuvchi topilmadi" });
        if (!user.isVerified) return res.status(400).json({ error: "Hisob tasdiqlanmagan" });

        const isMatch = await bcrypt.compare(password, user.password);
        if (!isMatch) return res.status(400).json({ error: "Parol noto'g'ri" });

        const token = jwt.sign({ id: user._id }, process.env.JWT_SECRET, { expiresIn: '30d' });
        res.json({ token, user: { fullName: user.fullName, username: user.username, identity: user.identity } });
    } catch (err) {
        res.status(500).json({ error: "Tizimga kirishda xatolik yuz berdi" });
    }
});

module.exports = router;
