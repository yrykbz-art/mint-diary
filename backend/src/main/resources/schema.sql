CREATE TABLE IF NOT EXISTS diary (
    diary_date DATE PRIMARY KEY,
    content MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS media (
    id CHAR(36) PRIMARY KEY,
    file_name VARCHAR(100) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS diary_image (
    diary_date DATE NOT NULL,
    media_id CHAR(36) NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY (diary_date, media_id),
    UNIQUE (diary_date, position),
    FOREIGN KEY (diary_date) REFERENCES diary(diary_date) ON DELETE CASCADE,
    FOREIGN KEY (media_id) REFERENCES media(id)
);

CREATE TABLE IF NOT EXISTS anniversary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    original_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    cover_image_id CHAR(36),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (cover_image_id) REFERENCES media(id)
);
