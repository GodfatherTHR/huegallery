package com.gallery.service;

import com.gallery.dao.ImageDao;
import com.gallery.dao.TagDao;
import com.gallery.model.Tag;
import com.gallery.util.DbUtil;
import com.gallery.util.TagParser;
import com.gallery.util.ValidationException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Tag queries, autocomplete and owner-checked tag editing. */
public class TagService {

    private static final int CLOUD_LIMIT = 40;
    private static final int AUTOCOMPLETE_LIMIT = 10;

    private final TagDao tagDao = new TagDao();
    private final ImageDao imageDao = new ImageDao();

    public List<Tag> findByImage(long imageId) throws SQLException {
        return tagDao.findByImage(imageId);
    }

    /** Most-used tags for the home page tag cloud. */
    public List<Tag> cloud() throws SQLException {
        return tagDao.findCloud(CLOUD_LIMIT);
    }

    /** Most-used tags, capped by the caller. Used by the AI assistant for context. */
    public List<Tag> cloud(int limit) throws SQLException {
        return tagDao.findCloud(Math.max(1, limit));
    }

    /** Autocomplete over tag names, tolerant of raw user input. */
    public List<Tag> autocomplete(String query) throws SQLException {
        String normalised = normaliseQuery(query);
        if (normalised.isEmpty()) {
            return tagDao.findCloud(AUTOCOMPLETE_LIMIT);
        }
        return tagDao.autocomplete(normalised, AUTOCOMPLETE_LIMIT);
    }

    /**
     * Replaces the tags on an image. Only the owner may do this; orphan tags
     * are removed afterwards.
     */
    public void replaceTags(long imageId, long userId, String rawTags) throws SQLException {
        List<String> tags = TagParser.parse(rawTags);
        try (Connection connection = DbUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Long> owner = imageDao.findOwnerId(connection, imageId);
                if (owner.isEmpty()) {
                    throw new ValidationException("That image no longer exists.");
                }
                if (owner.get() != userId) {
                    throw new ValidationException("You can only edit tags on your own images.");
                }
                tagDao.clearLinks(connection, imageId);
                for (String tag : tags) {
                    long tagId = tagDao.getOrCreate(connection, tag);
                    tagDao.link(connection, imageId, tagId);
                }
                tagDao.deleteOrphans(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Comma-joined tag names for prefilling the edit form. */
    public String commaSeparatedTags(long imageId) throws SQLException {
        return String.join(", ", tagDao.findByImage(imageId).stream().map(Tag::getName).toList());
    }

    private String normaliseQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 -]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
