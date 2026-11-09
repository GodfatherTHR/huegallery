-- HueGallery PL/SQL helpers.
-- Safe to re-run. Run after 01_schema.sql as the application schema owner.
-- These objects are optional: the Java application also cleans orphan tags
-- inline, so the web app keeps working if this script is not installed.

SET SERVEROUTPUT ON;

-- 1. Audit table used by the delete trigger.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM user_tables
    WHERE table_name = 'IMAGE_DELETE_LOG';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE image_delete_log (
                log_id        NUMBER GENERATED ALWAYS AS IDENTITY,
                image_id      NUMBER NOT NULL,
                title         VARCHAR2(150),
                owner_user_id NUMBER,
                mime_type     VARCHAR2(50),
                file_size     NUMBER,
                deleted_at    TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT image_delete_log_pk PRIMARY KEY (log_id)
            )';
    END IF;
END;
/

-- 2. Stored procedure: remove tags that no image uses any more.
CREATE OR REPLACE PROCEDURE prune_orphan_tags (p_removed OUT NUMBER) AS
BEGIN
    DELETE FROM tags t
    WHERE NOT EXISTS (
        SELECT 1 FROM image_tags it WHERE it.tag_id = t.tag_id
    );
    p_removed := SQL%ROWCOUNT;
END;
/

-- 3. Stored function: number of images that use a tag.
CREATE OR REPLACE FUNCTION tag_image_count (p_tag_id IN NUMBER) RETURN NUMBER IS
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM image_tags
    WHERE tag_id = p_tag_id;
    RETURN v_count;
END;
/

-- 4. Trigger: keep an audit row whenever an image is deleted.
CREATE OR REPLACE TRIGGER trg_images_delete_log
AFTER DELETE ON images
FOR EACH ROW
BEGIN
    INSERT INTO image_delete_log (image_id, title, owner_user_id, mime_type, file_size)
    VALUES (:OLD.image_id, :OLD.title, :OLD.user_id, :OLD.mime_type, :OLD.file_size);
END;
/

COMMIT;

-- Example calls:
--   DECLARE n NUMBER; BEGIN prune_orphan_tags(n); DBMS_OUTPUT.PUT_LINE(n); END;
--   /
--   SELECT tag_image_count(tag_id) FROM tags WHERE name = 'sunset';
