package com.gallery.service;

import com.gallery.dao.HistogramDao;
import com.gallery.dao.ImageDao;
import com.gallery.dao.TagDao;
import com.gallery.model.Image;
import com.gallery.model.ImageBinary;
import com.gallery.model.PageResult;
import com.gallery.util.ColourHistogram;
import com.gallery.util.DbUtil;
import com.gallery.util.ImageUtil;
import com.gallery.util.TagParser;
import com.gallery.util.ValidationException;
import com.gallery.util.ValidationUtil;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Upload pipeline, listings, search and ownership-checked deletion.
 * Uploads are processed and persisted inside a single transaction.
 */
public class ImageService {

    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final List<String> ALLOWED_MIME_TYPES =
            List.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final ImageDao imageDao = new ImageDao();
    private final TagDao tagDao = new TagDao();
    private final HistogramDao histogramDao = new HistogramDao();

    /**
     * Validates and stores an uploaded image: decode, thumbnail, histogram,
     * IMAGES row, tag upserts and IMAGE_TAGS links. If any step fails the whole
     * transaction is rolled back.
     *
     * @return the stored image metadata, including its generated id
     */
    public Image upload(long userId, String rawTitle, String rawDescription, String rawTags,
            String originalFileName, byte[] data) throws SQLException {
        String title = ValidationUtil.requireText(rawTitle, "Title", 150);
        String description = ValidationUtil.optionalText(rawDescription, "Description", 1000);
        if (data == null || data.length == 0) {
            throw new ValidationException("Choose an image to upload.");
        }
        if (data.length > MAX_FILE_BYTES) {
            throw new ValidationException("Images must be 8 MB or smaller.");
        }
        List<String> tags = TagParser.parse(rawTags);

        try (Connection connection = DbUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ImageUtil.DecodedImage decoded = ImageUtil.decode(data);
                if (decoded == null) {
                    throw new ValidationException(
                            "That file is not a readable image. Please choose a JPEG, PNG, GIF or WebP file.");
                }
                if (!ALLOWED_MIME_TYPES.contains(decoded.mimeType())) {
                    throw new ValidationException("Only JPEG, PNG, GIF and WebP images are supported.");
                }
                BufferedImage image = decoded.image();
                long pixels = (long) image.getWidth() * image.getHeight();
                if (pixels > ImageUtil.MAX_PIXELS) {
                    throw new ValidationException("That image has too many pixels (maximum 40 megapixels).");
                }

                byte[] thumbnail;
                try {
                    thumbnail = ImageUtil.createThumbnail(image);
                } catch (IOException e) {
                    throw new IllegalStateException("Could not generate a thumbnail", e);
                }
                double[] histogram = ColourHistogram.compute(image);
                String bins = ColourHistogram.serialise(histogram);

                Image metadata = new Image();
                metadata.setUserId(userId);
                metadata.setTitle(title);
                metadata.setDescription(description);
                metadata.setFileName(ImageUtil.sanitiseFileName(originalFileName));
                metadata.setMimeType(decoded.mimeType());
                metadata.setFileSize(data.length);
                metadata.setWidth(image.getWidth());
                metadata.setHeight(image.getHeight());

                long imageId = imageDao.insert(connection, metadata, data, thumbnail);
                for (String tag : tags) {
                    long tagId = tagDao.getOrCreate(connection, tag);
                    tagDao.link(connection, imageId, tagId);
                }
                histogramDao.upsert(connection, imageId, bins);

                connection.commit();
                metadata.setImageId(imageId);
                return metadata;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public Optional<Image> find(long imageId) throws SQLException {
        return imageDao.findById(imageId);
    }

    /** Metadata for a binary request: mime type, upload time (ETag) and BLOB length. */
    public Optional<ImageBinary> binaryInfo(long imageId, boolean thumbnail) throws SQLException {
        return imageDao.findBinaryMeta(imageId, thumbnail)
                .map(meta -> new ImageBinary(meta.mimeType(), meta.uploadedAt(), meta.length()));
    }

    /** Streams the full image or its thumbnail in 8 KB chunks. */
    public void streamBinary(long imageId, boolean thumbnail, java.io.OutputStream out)
            throws SQLException, java.io.IOException {
        if (thumbnail) {
            imageDao.streamThumbnail(imageId, out);
        } else {
            imageDao.streamImageData(imageId, out);
        }
    }

    public PageResult<Image> listAll(int requestedPage, int size) throws SQLException {
        long total = imageDao.countAll();
        return paginate(requestedPage, size, total, imageDao::findPage);
    }

    public PageResult<Image> listForUser(long userId, int requestedPage, int size) throws SQLException {
        long total = imageDao.countByUser(userId);
        return paginate(requestedPage, size, total,
                (offset, limit) -> imageDao.findByUser(userId, offset, limit));
    }

    /** Searches by normalised tag names, in ANY (OR) or ALL (AND) mode. */
    public PageResult<Image> searchByTags(List<String> tags, boolean all, int requestedPage, int size)
            throws SQLException {
        if (tags == null || tags.isEmpty()) {
            return new PageResult<>(List.of(), 1, size, 0);
        }
        long total = imageDao.countByTags(tags, all);
        return paginate(requestedPage, size, total,
                (offset, limit) -> imageDao.searchByTags(tags, all, offset, limit));
    }

    /**
     * Deletes an image owned by the user and removes any tags that no longer
     * have links. Foreign keys cascade IMAGE_TAGS and IMAGE_HISTOGRAMS.
     */
    public void delete(long imageId, long userId) throws SQLException {
        try (Connection connection = DbUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean deleted = imageDao.delete(connection, imageId, userId);
                if (!deleted) {
                    throw new ValidationException("You can only delete your own images.");
                }
                tagDao.deleteOrphans(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private PageResult<Image> paginate(int requestedPage, int size, long total, PageFetcher fetcher)
            throws SQLException {
        int page = Math.max(1, requestedPage);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) size));
        page = Math.min(page, totalPages);
        int offset = (page - 1) * size;
        List<Image> items = total == 0 ? List.of() : fetcher.fetch(offset, size);
        return new PageResult<>(items, page, size, total);
    }

    @FunctionalInterface
    private interface PageFetcher {
        List<Image> fetch(int offset, int limit) throws SQLException;
    }
}
