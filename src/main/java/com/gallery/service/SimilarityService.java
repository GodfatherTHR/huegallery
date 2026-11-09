package com.gallery.service;

import com.gallery.dao.HistogramDao;
import com.gallery.dao.HistogramDao.HistogramRow;
import com.gallery.dao.ImageDao;
import com.gallery.model.Image;
import com.gallery.model.SimilarImage;
import com.gallery.util.ColourHistogram;
import com.gallery.util.HistogramComparator;
import com.gallery.util.HistogramIntersectionComparator;
import com.gallery.util.ImageUtil;
import com.gallery.util.ValidationException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Colour-histogram similarity. Histograms are loaded in a single query and
 * scored in memory; this is intentionally simple and is documented as a
 * scalability limit in the README.
 */
public class SimilarityService {

    public static final int DEFAULT_LIMIT = 12;

    private final HistogramDao histogramDao = new HistogramDao();
    private final ImageDao imageDao = new ImageDao();
    private final HistogramComparator comparator;

    public SimilarityService() {
        this(new HistogramIntersectionComparator());
    }

    public SimilarityService(HistogramComparator comparator) {
        this.comparator = comparator;
    }

    /**
     * Ranks every other stored image against the target image.
     *
     * @param imageId the image whose histogram is the query vector
     * @param limit maximum number of results
     * @return matches ordered by descending score; empty when the image has no histogram
     */
    public List<SimilarImage> findSimilar(long imageId, int limit) throws SQLException {
        Optional<String> stored = histogramDao.findBins(imageId);
        if (stored.isEmpty()) {
            return List.of();
        }
        double[] target = ColourHistogram.parse(stored.get());
        return rank(target, histogramDao.findAllExcept(imageId), limit);
    }

    /**
     * Ranks the whole gallery against an arbitrary target histogram. Used by
     * the AI colour search, where the target comes from colour names rather
     * than a stored image.
     */
    public List<SimilarImage> searchByHistogram(double[] target, int limit) throws SQLException {
        if (target == null || target.length != ColourHistogram.BINS) {
            return List.of();
        }
        return rank(target, histogramDao.findAll(), limit);
    }

    /**
     * Compares an unsaved example image against the whole gallery. Nothing is
     * persisted.
     */
    public List<SimilarImage> searchByExample(byte[] data, int limit) throws SQLException {
        if (data == null || data.length == 0) {
            throw new ValidationException("Choose an image to search with.");
        }
        if (data.length > ImageService.MAX_FILE_BYTES) {
            throw new ValidationException("Images must be 8 MB or smaller.");
        }
        ImageUtil.DecodedImage decoded = ImageUtil.decode(data);
        if (decoded == null) {
            throw new ValidationException(
                    "That file is not a readable image. Please choose a JPEG, PNG, GIF or WebP file.");
        }
        double[] target = ColourHistogram.compute(decoded.image());
        return rank(target, histogramDao.findAll(), limit);
    }

    /** The three strongest bins of an image, as centre colours for swatches. */
    public List<ColourHistogram.Colour> dominantColours(long imageId) throws SQLException {
        return histogramDao.findBins(imageId)
                .map(ColourHistogram::parse)
                .map(histogram -> ColourHistogram.topColours(histogram, 3))
                .orElse(List.of());
    }

    private List<SimilarImage> rank(double[] target, List<HistogramRow> candidates, int limit)
            throws SQLException {
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (HistogramRow row : candidates) {
            try {
                double[] histogram = ColourHistogram.parse(row.bins());
                scored.add(new Scored(row.imageId(), comparator.similarity(target, histogram)));
            } catch (IllegalArgumentException e) {
                // Skip corrupt rows rather than failing the whole request.
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        if (limit > 0 && scored.size() > limit) {
            scored = scored.subList(0, limit);
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        List<Long> ids = scored.stream().map(Scored::imageId).toList();
        Map<Long, Image> metadata = new HashMap<>();
        for (Image image : imageDao.findByIds(ids)) {
            metadata.put(image.getImageId(), image);
        }
        List<SimilarImage> results = new ArrayList<>(scored.size());
        for (Scored match : scored) {
            Image image = metadata.get(match.imageId());
            if (image != null) {
                results.add(new SimilarImage(image, match.score()));
            }
        }
        return results;
    }

    private record Scored(long imageId, double score) {
    }
}
