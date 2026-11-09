package com.gallery.dao;

import com.gallery.model.Tag;
import com.gallery.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access for TAGS and IMAGE_TAGS. Tag creation uses MERGE so concurrent
 * uploads cannot create duplicate names.
 */
public class TagDao {

    /** Returns the id of an existing tag or creates it, inside the caller's transaction. */
    public long getOrCreate(Connection connection, String name) throws SQLException {
        String merge = "MERGE INTO tags t USING (SELECT ? AS name FROM dual) s ON (t.name = s.name) "
                + "WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)";
        try (PreparedStatement ps = connection.prepareStatement(merge)) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT tag_id FROM tags WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Could not create or find tag: " + name);
    }

    public void link(Connection connection, long imageId, long tagId) throws SQLException {
        String sql = "MERGE INTO image_tags it USING (SELECT ? AS image_id, ? AS tag_id FROM dual) s "
                + "ON (it.image_id = s.image_id AND it.tag_id = s.tag_id) "
                + "WHEN NOT MATCHED THEN INSERT (image_id, tag_id) VALUES (s.image_id, s.tag_id)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            ps.setLong(2, tagId);
            ps.executeUpdate();
        }
    }

    public void clearLinks(Connection connection, long imageId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM image_tags WHERE image_id = ?")) {
            ps.setLong(1, imageId);
            ps.executeUpdate();
        }
    }

    public List<Tag> findByImage(long imageId) throws SQLException {
        String sql = "SELECT t.tag_id, t.name FROM tags t JOIN image_tags it ON it.tag_id = t.tag_id "
                + "WHERE it.image_id = ? ORDER BY t.name";
        List<Tag> tags = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(new Tag(rs.getLong("tag_id"), rs.getString("name"), 0));
                }
            }
        }
        return tags;
    }

    public List<Tag> findCloud(int limit) throws SQLException {
        String sql = "SELECT t.tag_id, t.name, COUNT(it.image_id) AS usage_count FROM tags t "
                + "JOIN image_tags it ON it.tag_id = t.tag_id "
                + "GROUP BY t.tag_id, t.name ORDER BY usage_count DESC, t.name "
                + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        return findWithUsage(sql, limit);
    }

    public List<Tag> autocomplete(String query, int limit) throws SQLException {
        String sql = "SELECT t.tag_id, t.name, COUNT(it.image_id) AS usage_count FROM tags t "
                + "JOIN image_tags it ON it.tag_id = t.tag_id WHERE t.name LIKE ? "
                + "GROUP BY t.tag_id, t.name ORDER BY usage_count DESC, t.name "
                + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        List<Tag> tags = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(new Tag(rs.getLong("tag_id"), rs.getString("name"),
                            rs.getLong("usage_count")));
                }
            }
        }
        return tags;
    }

    public int deleteOrphans(Connection connection) throws SQLException {
        String sql = "DELETE FROM tags t WHERE NOT EXISTS "
                + "(SELECT 1 FROM image_tags it WHERE it.tag_id = t.tag_id)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    private List<Tag> findWithUsage(String sql, int limit) throws SQLException {
        List<Tag> tags = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(new Tag(rs.getLong("tag_id"), rs.getString("name"),
                            rs.getLong("usage_count")));
                }
            }
        }
        return tags;
    }
}
