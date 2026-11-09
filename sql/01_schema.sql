-- HueGallery schema for Oracle Database 21c
-- Safe to re-run: existing tables are dropped first (in FK-safe order).
-- Run as the application schema owner (for example GALLERY), not as SYS.

DECLARE
    PROCEDURE drop_table_if_exists(p_table VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP TABLE ' || p_table || ' CASCADE CONSTRAINTS PURGE';
    EXCEPTION
        WHEN OTHERS THEN
            -- ORA-00942: table or view does not exist
            IF SQLCODE != -942 THEN
                RAISE;
            END IF;
    END;
BEGIN
    drop_table_if_exists('IMAGE_HISTOGRAMS');
    drop_table_if_exists('IMAGE_TAGS');
    drop_table_if_exists('IMAGES');
    drop_table_if_exists('TAGS');
    drop_table_if_exists('USERS');
END;
/

CREATE TABLE users (
    user_id       NUMBER GENERATED ALWAYS AS IDENTITY,
    username      VARCHAR2(50)  NOT NULL,
    password_hash VARCHAR2(100) NOT NULL,
    created_at    TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT users_pk PRIMARY KEY (user_id),
    CONSTRAINT users_username_uk UNIQUE (username)
);

CREATE TABLE images (
    image_id    NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id     NUMBER        NOT NULL,
    title       VARCHAR2(150) NOT NULL,
    description VARCHAR2(1000),
    file_name   VARCHAR2(255),
    mime_type   VARCHAR2(50)  NOT NULL,
    file_size   NUMBER        NOT NULL,
    width       NUMBER,
    height      NUMBER,
    image_data  BLOB          NOT NULL,
    thumbnail   BLOB          NOT NULL,
    uploaded_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT images_pk PRIMARY KEY (image_id),
    CONSTRAINT images_user_fk FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE tags (
    tag_id NUMBER GENERATED ALWAYS AS IDENTITY,
    name   VARCHAR2(40) NOT NULL,
    CONSTRAINT tags_pk PRIMARY KEY (tag_id),
    CONSTRAINT tags_name_uk UNIQUE (name)
);

CREATE TABLE image_tags (
    image_id NUMBER NOT NULL,
    tag_id   NUMBER NOT NULL,
    CONSTRAINT image_tags_pk PRIMARY KEY (image_id, tag_id),
    CONSTRAINT image_tags_image_fk FOREIGN KEY (image_id)
        REFERENCES images (image_id) ON DELETE CASCADE,
    CONSTRAINT image_tags_tag_fk FOREIGN KEY (tag_id)
        REFERENCES tags (tag_id) ON DELETE CASCADE
);

CREATE TABLE image_histograms (
    image_id    NUMBER NOT NULL,
    bins        VARCHAR2(1000) NOT NULL,
    computed_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT image_histograms_pk PRIMARY KEY (image_id),
    CONSTRAINT image_histograms_image_fk FOREIGN KEY (image_id)
        REFERENCES images (image_id) ON DELETE CASCADE
);

COMMIT;
