-- PostgreSQL Schema for Social Media Profile System

CREATE TYPE post_type AS ENUM ('POST', 'REEL');

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    full_name VARCHAR(100),
    bio TEXT,
    profile_pic_url TEXT,
    cover_pic_url TEXT,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE posts (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    caption TEXT,
    likes_count INT DEFAULT 0,
    type VARCHAR(10) DEFAULT 'POST', -- 'POST' or 'REEL'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE follows (
    follower_id INT REFERENCES users(id) ON DELETE CASCADE,
    following_id INT REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (follower_id, following_id)
);

-- Sample Data
INSERT INTO users (username, email, full_name, bio) VALUES
('johndoe', 'john@example.com', 'John Doe', 'Android Developer & Tech Enthusiast'),
('janedoe', 'jane@example.com', 'Jane Doe', 'Traveler | Photographer');

INSERT INTO posts (user_id, image_url, caption, type) VALUES
(1, 'https://example.com/p1.jpg', 'My first post!', 'POST'),
(1, 'https://example.com/r1.mp4', 'Check out this reel!', 'REEL'),
(2, 'https://example.com/p2.jpg', 'Beautiful sunset', 'POST');
