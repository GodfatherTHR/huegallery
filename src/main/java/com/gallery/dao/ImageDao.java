package com.gallery.dao;

import com.gallery.model.Image;
import com.gallery.util.DbUtil;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * JDBC access for IMAGES. List queries intentionally select metadata only:
 * BLOBs are read exclusively by the binary servlet and by the reindex tool.
 */
public class ImageDao {

    private static final String COLUMNS = "i.image_id, i.user_id, i.title, i.description, i.file_name, "
            + "i.mime_type, i.file_size, i.width, i.height, i.uploaded_at, u.username";
    private static final String FROM = " FROM images i JOIN users u ON u.user_id = i.user_id ";
    private static final String ORDER = " ORDER BY i.uploaded_at DESC, i.image_id DESC ";

    /** Metadata used while serving a BLOB. */
    public record BinaryMeta(String mimeType, Timestamp uploadedAt, long length) {
    }

    public long insert(Connection connection, Image image, byte[] imageData, byte[] thumbnail)
            throws SQLException {
        String sql = "INSERT INTO images (user_id, title, description, file_name, mime_type, file_size, "
                + "width, height, image_data, thumbnail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, new String[] { "IMAGE_ID" })) {
            ps.setLong(1, image.getUserId());
            ps.setString(2, image.getTitle());
            if (image.getDescription() == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, image.getDescription());
            }
            ps.setString(4, image.getFileName());
            ps.setString(5, image.getMimeType());
            ps.setLong(6, image.getFileSize());
            ps.setInt(7, image.getWidth());
            ps.setInt(8, image.getHeight());
            ps.setBinaryStream(9, new java.io.ByteArrayInputStream(imageData), imageData.length);
            ps.setBinaryStream(10, new java.io.ByteArrayInputStream(thumbnail), thumbnail.length);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("The database did not return a generated image id");
        }
    }

    public Optional<Image> findById(long imageId) throws SQLException {
        String sql = "SELECT " + COLUMNS + FROM + "WHERE i.image_id = ?";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Image> findPage(int offset, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + FROM + ORDER + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, offset);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    public long countAll() throws SQLException {
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM images");
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    public List<Image> findByUser(long userId, int offset, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + FROM + "WHERE i.user_id = ? " + ORDER
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, offset);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    public long countByUser(long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM images WHERE user_id = ?";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public Optional<Long> findOwnerId(long imageId) throws SQLException {
        try (Connection connection = DbUtil.getConnection()) {
            return findOwnerId(connection, imageId);
        }
    }

    public Optional<Long> findOwnerId(Connection connection, long imageId) throws SQLException {
        String sql = "SELECT user_id FROM images WHERE image_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }

    /** Deletes an image the user owns. Foreign keys cascade links and histograms. */
    public boolean delete(Connection connection, long imageId, long userId) throws SQLException {
        String sql = "DELETE FROM images WHERE image_id = ? AND user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Fetches the metadata needed to answer a binary request, including ETag data. */
    public Optional<BinaryMeta> findBinaryMeta(long imageId, boolean thumbnail) throws SQLException {
        String sql = thumbnail
                ? "SELECT 'image/jpeg' AS mime_type, uploaded_at, DBMS_LOB.GETLENGTH(thumbnail) AS data_length "
                        + "FROM images WHERE image_id = ?"
                : "SELECT mime_type, uploaded_at, DBMS_LOB.GETLENGTH(image_data) AS data_length "
                        + "FROM images WHERE image_id = ?";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new BinaryMeta(rs.getString("mime_type"),
                        rs.getTimestamp("uploaded_at"), rs.getLong("data_length")));
            }
        }
    }

    public void streamImageData(long imageId, OutputStream out) throws SQLException, java.io.IOException {
        stream("SELECT image_data FROM images WHERE image_id = ?", imageId, out);
    }

    public void streamThumbnail(long imageId, OutputStream out) throws SQLException, java.io.IOException {
        stream("SELECT thumbnail FROM images WHERE image_id = ?", imageId, out);
    }

    private void stream(String sql, long imageId, OutputStream out) throws SQLException, java.io.IOException {
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                try (InputStream in = rs.getBinaryStream(1)) {
                    if (in == null) {
                        return;
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    /** Reads the full BLOB into memory. Only for the offline reindex tool. */
    public byte[] readImageData(long imageId) throws SQLException, java.io.IOException {
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT image_data FROM images WHERE image_id = ?")) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                try (InputStream in = rs.getBinaryStream(1)) {
                    return in == null ? null : in.readAllBytes();
                }
            }
        }
    }

    public List<Image> findByIds(Collection<Long> imageIds) throws SQLException {
        List<Image> images = new ArrayList<>();
        if (imageIds == null || imageIds.isEmpty()) {
            return images;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(imageIds.size(), "?"));
        String sql = "SELECT " + COLUMNS + FROM + "WHERE i.image_id IN (" + placeholders + ")";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long id : imageIds) {
                ps.setLong(index++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                images.addAll(mapAll(rs));
            }
        }
        return images;
    }

    public List<Long> findAllImageIds() throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT image_id FROM images ORDER BY image_id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    /**
     * Tag search. {@code all=true} requires every tag (AND), otherwise any tag
     * matches (OR). Only bind placeholders are generated dynamically; tag
     * values are always bound.
     */
    public List<Image> searchByTags(List<String> tagNames, boolean all, int offset, int limit)
            throws SQLException {
        if (tagNames == null || tagNames.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(tagNames.size(), "?"));
        String inner;
        if (all) {
            inner = "SELECT it.image_id FROM image_tags it JOIN tags t ON t.tag_id = it.tag_id "
                    + "WHERE t.name IN (" + placeholders + ") "
                    + "GROUP BY it.image_id HAVING COUNT(DISTINCT it.tag_id) = ?";
        } else {
            inner = "SELECT DISTINCT it.image_id FROM image_tags it JOIN tags t ON t.tag_id = it.tag_id "
                    + "WHERE t.name IN (" + placeholders + ")";
        }
        String sql = "SELECT " + COLUMNS + FROM + "WHERE i.image_id IN (" + inner + ")" + ORDER
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = bindTags(ps, tagNames);
            if (all) {
                ps.setInt(index++, tagNames.size());
            }
            ps.setInt(index++, offset);
            ps.setInt(index, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    public long countByTags(List<String> tagNames, boolean all) throws SQLException {
        if (tagNames == null || tagNames.isEmpty()) {
            return 0L;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(tagNames.size(), "?"));
        String sql;
        if (all) {
            sql = "SELECT COUNT(*) FROM (SELECT it.image_id FROM image_tags it "
                    + "JOIN tags t ON t.tag_id = it.tag_id WHERE t.name IN (" + placeholders + ") "
                    + "GROUP BY it.image_id HAVING COUNT(DISTINCT it.tag_id) = ?)";
        } else {
            sql = "SELECT COUNT(DISTINCT it.image_id) FROM image_tags it "
                    + "JOIN tags t ON t.tag_id = it.tag_id WHERE t.name IN (" + placeholders + ")";
        }
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = bindTags(ps, tagNames);
            if (all) {
                ps.setInt(index, tagNames.size());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Simple title/description search used by the AI assistant. */
    public List<Image> searchByText(String query, int limit) throws SQLException {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT).trim()) + "%";
        String sql = "SELECT " + COLUMNS + FROM
                + "WHERE LOWER(i.title) LIKE ? ESCAPE '\\' "
                + "OR LOWER(i.description) LIKE ? ESCAPE '\\'" + ORDER
                + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private int bindTags(PreparedStatement ps, List<String> tagNames) throws SQLException {
        int index = 1;
        for (String name : tagNames) {
            ps.setString(index++, name);
        }
        return index;
    }

    private List<Image> mapAll(ResultSet rs) throws SQLException {
        List<Image> images = new ArrayList<>();
        while (rs.next()) {
            images.add(map(rs));
        }
        return images;
    }

    private Image map(ResultSet rs) throws SQLException {
        Image image = new Image();
        image.setImageId(rs.getLong("image_id"));
        image.setUserId(rs.getLong("user_id"));
        image.setTitle(rs.getString("title"));
        image.setDescription(rs.getString("description"));
        image.setFileName(rs.getString("file_name"));
        image.setMimeType(rs.getString("mime_type"));
        image.setFileSize(rs.getLong("file_size"));
        image.setWidth(rs.getInt("width"));
        image.setHeight(rs.getInt("height"));
        image.setUploadedAt(rs.getTimestamp("uploaded_at"));
        image.setUsername(rs.getString("username"));
        return image;
    }
}
