const mongoose = require('mongoose');

const SettingsSchema = new mongoose.Schema({
    userId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        unique: true
    },
    isPrivateAccount: { type: Boolean, default: false },
    isDarkMode: { type: Boolean, default: true },
    readReceipts: { type: Boolean, default: true },
    dataSaver: { type: Boolean, default: false },
    videoAutoPlay: { type: Boolean, default: true },
    sensitiveFilter: { type: Boolean, default: false },
    notifications: {
        likes: { type: Boolean, default: true },
        comments: { type: Boolean, default: true },
        followRequests: { type: Boolean, default: true },
        messages: { type: Boolean, default: true }
    }
}, { timestamps: true });

module.exports = mongoose.model('Settings', SettingsSchema);
