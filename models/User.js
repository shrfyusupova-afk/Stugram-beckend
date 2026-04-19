const mongoose = require('mongoose');

const UserSchema = new mongoose.Schema({
    identity: {
        type: String,
        required: true,
        unique: true,
        trim: true
    },
    fullName: {
        type: String,
        trim: true
    },
    username: {
        type: String,
        unique: true,
        sparse: true,
        trim: true
    },
    password: {
        type: String
    },
    region: {
        type: String
    },
    district: {
        type: String
    },
    school: {
        type: String
    },
    grade: {
        type: String
    },
    group: {
        type: String
    },
    otp: {
        type: String
    },
    isVerified: {
        type: Boolean,
        default: false
    },
    createdAt: {
        type: Date,
        default: Date.now
    }
});

module.exports = mongoose.model('User', UserSchema);
