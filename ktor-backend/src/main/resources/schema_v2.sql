-- Production-ready PostgreSQL Schema V2

CREATE TYPE post_type AS ENUM ('POST', 'REEL');
CREATE TYPE follow_status AS ENUM ('PENDING', 'ACCEPTED');
CREATE TYPE notification_type AS ENUM ('FOLLOW', 'LIKE', 'COMMENT', 'FOLLOW_REQUEST');

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    full_name VARCHAR(100),
    bio TEXT,
    profile_pic_url TEXT,
    cover_pic_url TEXT,
    is_verified BOOLEAN DEFAULT FALSE,
    is_private BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE posts (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    caption TEXT,
    likes_count INT DEFAULT 0,
    type VARCHAR(10) DEFAULT 'POST',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE follows (
    follower_id INT REFERENCES users(id) ON DELETE CASCADE,
    following_id INT REFERENCES users(id) ON DELETE CASCADE,
    status follow_status DEFAULT 'ACCEPTED',
    PRIMARY KEY (follower_id, following_id)
);

CREATE TABLE notifications (
    id SERIAL PRIMARY KEY,
    receiver_id INT REFERENCES users(id) ON DELETE CASCADE,
    sender_id INT REFERENCES users(id) ON DELETE CASCADE,
    type notification_type NOT NULL,
    post_id INT REFERENCES posts(id) ON DELETE CASCADE,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_posts_user_id ON posts(user_id);
