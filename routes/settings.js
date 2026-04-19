const express = require('express');
const router = express.Router();
const Settings = require('../models/Settings');

// Sozlamalarni olish (Foydalanuvchi ID bo'yicha)
router.get('/:userId', async (req, res) => {
    try {
        let settings = await Settings.findOne({ userId: req.params.userId });
        if (!settings) {
            // Agar sozlamalar hali mavjud bo'lmasa, yangisini yaratamiz
            settings = new Settings({ userId: req.params.userId });
            await settings.save();
        }
        res.json(settings);
    } catch (err) {
        res.status(500).json({ message: err.message });
    }
});

// Sozlamalarni yangilash
router.post('/update', async (req, res) => {
    const { userId, ...updates } = req.body;
    try {
        const settings = await Settings.findOneAndUpdate(
            { userId: userId },
            { $set: updates },
            { new: true, upsert: true }
        );
        res.json(settings);
    } catch (err) {
        res.status(400).json({ message: err.message });
    }
});

module.exports = router;
