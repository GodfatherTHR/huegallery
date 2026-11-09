-- Secondary indexes for HueGallery (idempotent).

DECLARE
    PROCEDURE drop_index_if_exists(p_index VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP INDEX ' || p_index;
    EXCEPTION
        WHEN OTHERS THEN
            -- ORA-01418: specified index does not exist
            IF SQLCODE != -1418 THEN
                RAISE;
            END IF;
    END;
BEGIN
    drop_index_if_exists('idx_image_tags_tag');
    drop_index_if_exists('idx_images_user');
    drop_index_if_exists('idx_images_uploaded');
END;
/

CREATE INDEX idx_image_tags_tag ON image_tags (tag_id);
CREATE INDEX idx_images_user ON images (user_id);
CREATE INDEX idx_images_uploaded ON images (uploaded_at DESC, image_id DESC);

COMMIT;
