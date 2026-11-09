package com.gallery.util;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Image decoding, thumbnailing and filename hygiene. The format is determined
 * by ImageIO's registered readers, never by the uploaded filename or the
 * Content-Type header.
 */
public final class ImageUtil {

    public static final int MAX_THUMB_SIDE = 300;
    public static final long MAX_PIXELS = 40_000_000L;

    private ImageUtil() {
    }

    /** Result of decoding an uploaded byte array. */
    public record DecodedImage(BufferedImage image, String mimeType, String formatName) {
    }

    /**
     * Decodes image bytes and detects the real format. Returns {@code null}
     * when no ImageIO reader accepts the data.
     */
    public static DecodedImage decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new ValidationException(
                            "That image has too many pixels (maximum 40 megapixels).");
                }
                String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    return null;
                }
                return new DecodedImage(image, mimeTypeFor(formatName), formatName);
            } finally {
                reader.dispose();
            }
        } catch (ValidationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String mimeTypeFor(String formatName) {
        return switch (formatName) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    /**
     * Returns an opaque RGB copy of the image, compositing transparent pixels
     * onto a white background.
     */
    public static BufferedImage toOpaqueRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage opaque = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return opaque;
    }

    /**
     * Creates a JPEG thumbnail whose longest side is at most 300 px. Small
     * images are not upscaled. The thumbnail is always opaque (white matte).
     */
    public static byte[] createThumbnail(BufferedImage source) throws IOException {
        BufferedImage opaque = toOpaqueRgb(source);
        int width = opaque.getWidth();
        int height = opaque.getHeight();
        int longest = Math.max(width, height);
        int targetWidth = width;
        int targetHeight = height;
        if (longest > MAX_THUMB_SIDE) {
            double scale = MAX_THUMB_SIDE / (double) longest;
            targetWidth = Math.max(1, (int) Math.round(width * scale));
            targetHeight = Math.max(1, (int) Math.round(height * scale));
        }
        BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnail.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(opaque, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(thumbnail, "jpg", out)) {
            throw new IOException("No JPEG encoder available");
        }
        return out.toByteArray();
    }

    /** Strips paths and unsafe characters from the client-supplied filename. */
    public static String sanitiseFileName(String raw) {
        if (raw == null) {
            return "upload";
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        name = name.replaceAll("_+", "_");
        if (name.isBlank() || name.equals("_")) {
            return "upload";
        }
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name;
    }
}
