package com.gallery.dao;

import com.gallery.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for IMAGE_HISTOGRAMS. */
public class HistogramDao {

    /** A stored histogram row; records keep the in-memory scan allocation light. */
    public record HistogramRow(long imageId, String bins) {
    }

    private static final String MERGE = "MERGE INTO image_histograms h "
            + "USING (SELECT ? AS image_id, ? AS bins FROM dual) s ON (h.image_id = s.image_id) "
            + "WHEN MATCHED THEN UPDATE SET h.bins = s.bins, h.computed_at = SYSTIMESTAMP "
            + "WHEN NOT MATCHED THEN INSERT (image_id, bins) VALUES (s.image_id, s.bins)";

    public void upsert(Connection connection, long imageId, String bins) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(MERGE)) {
            ps.setLong(1, imageId);
            ps.setString(2, bins);
            ps.executeUpdate();
        }
    }

    /** Convenience for the reindex tool: one connection and transaction per row. */
    public void save(long imageId, String bins) throws SQLException {
        try (Connection connection = DbUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsert(connection, imageId, bins);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public Optional<String> findBins(long imageId) throws SQLException {
        String sql = "SELECT bins FROM image_histograms WHERE image_id = ?";
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public List<HistogramRow> findAllExcept(long imageId) throws SQLException {
        String sql = "SELECT image_id, bins FROM image_histograms WHERE image_id <> ?";
        return query(sql, imageId);
    }

    public List<HistogramRow> findAll() throws SQLException {
        String sql = "SELECT image_id, bins FROM image_histograms";
        return query(sql, null);
    }

    public List<Long> findMissingImageIds() throws SQLException {
        String sql = "SELECT i.image_id FROM images i WHERE NOT EXISTS "
                + "(SELECT 1 FROM image_histograms h WHERE h.image_id = i.image_id) "
                + "ORDER BY i.image_id";
        List<Long> ids = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private List<HistogramRow> query(String sql, Long excludedId) throws SQLException {
        List<HistogramRow> rows = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (excludedId != null) {
                ps.setLong(1, excludedId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new HistogramRow(rs.getLong("image_id"), rs.getString("bins")));
                }
            }
        }
        return rows;
    }
}
