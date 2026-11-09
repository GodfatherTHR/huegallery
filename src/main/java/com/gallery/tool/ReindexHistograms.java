package com.gallery.tool;

import com.gallery.dao.HistogramDao;
import com.gallery.dao.ImageDao;
import com.gallery.util.ColourHistogram;
import com.gallery.util.DbUtil;
import com.gallery.util.ImageUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Command-line backfill utility. Recomputes histograms for images that are
 * missing one, or for every image when run with {@code --all}.
 *
 * <p>Usage: {@code java -cp "WEB-INF/classes:WEB-INF/lib/*" com.gallery.tool.ReindexHistograms [--all]}</p>
 */
public final class ReindexHistograms {

    private ReindexHistograms() {
    }

    public static void main(String[] args) {
        boolean all = args.length > 0 && "--all".equalsIgnoreCase(args[0]);
        ImageDao imageDao = new ImageDao();
        HistogramDao histogramDao = new HistogramDao();
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        try {
            List<Long> imageIds = all ? imageDao.findAllImageIds() : histogramDao.findMissingImageIds();
            System.out.println("Reindexing " + imageIds.size() + " image(s)"
                    + (all ? " (full rebuild)" : " (missing histograms only)"));
            for (Long imageId : imageIds) {
                try {
                    byte[] data = imageDao.readImageData(imageId);
                    ImageUtil.DecodedImage decoded = ImageUtil.decode(data);
                    if (decoded == null) {
                        skipped++;
                        System.out.println("  skip " + imageId + ": not a readable image");
                        continue;
                    }
                    String bins = ColourHistogram.serialise(ColourHistogram.compute(decoded.image()));
                    histogramDao.save(imageId, bins);
                    updated++;
                } catch (SQLException | IOException | RuntimeException e) {
                    failed++;
                    System.out.println("  fail " + imageId + ": " + e.getMessage());
                }
            }
            System.out.println("Done. updated=" + updated + " skipped=" + skipped + " failed=" + failed);
        } catch (SQLException e) {
            System.err.println("Could not query images: " + e.getMessage());
            System.exit(1);
        } finally {
            DbUtil.close();
        }
    }
}
