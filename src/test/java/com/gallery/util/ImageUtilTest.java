package com.gallery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageUtilTest {

    @Test
    void thumbnailCapsTheLongestSideAtThreeHundredPixels() throws IOException {
        BufferedImage source = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(ImageUtil.createThumbnail(source)));
        assertEquals(300, thumbnail.getWidth());
        assertEquals(150, thumbnail.getHeight());
    }

    @Test
    void smallImagesAreNotUpscaled() throws IOException {
        BufferedImage source = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(ImageUtil.createThumbnail(source)));
        assertEquals(120, thumbnail.getWidth());
        assertEquals(90, thumbnail.getHeight());
    }

    @Test
    void transparentAreasAreCompositedOntoWhite() throws IOException {
        BufferedImage source = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(ImageUtil.createThumbnail(source)));
        Color centre = new Color(thumbnail.getRGB(20, 20));
        assertTrue(centre.getRed() > 250, "red=" + centre.getRed());
        assertTrue(centre.getGreen() > 250, "green=" + centre.getGreen());
        assertTrue(centre.getBlue() > 250, "blue=" + centre.getBlue());
    }

    @Test
    void decodeRejectsNonImageBytes() {
        assertNull(ImageUtil.decode("this is definitely not an image".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void decodeReportsTheRealFormatNotTheFileName() {
        BufferedImage source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            ImageIO.write(source, "png", out);
            ImageUtil.DecodedImage decoded = ImageUtil.decode(out.toByteArray());
            assertEquals("image/png", decoded.mimeType());
            assertEquals("png", decoded.formatName());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void decodeReadsWebPImages() {
        byte[] webp = Base64.getDecoder().decode(
                "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoIAAgAAUAmJaACdLoB+AADsAD+8JtD/xZyMR2eb/8Cd+BO/Anf+ECAAAA=");
        ImageUtil.DecodedImage decoded = ImageUtil.decode(webp);
        assertEquals("image/webp", decoded.mimeType());
        assertEquals("webp", decoded.formatName());
        Color centre = new Color(decoded.image().getRGB(4, 4));
        assertTrue(centre.getRed() > 180, "red=" + centre.getRed());
    }

    @Test
    void sanitiseFileNameRemovesPathsAndUnsafeCharacters() {
        assertEquals("passwd", ImageUtil.sanitiseFileName("../../etc/passwd"));
        assertEquals("my_photo_.png", ImageUtil.sanitiseFileName("my photo!.png"));
        assertEquals("upload", ImageUtil.sanitiseFileName("///"));
        assertEquals("upload", ImageUtil.sanitiseFileName(null));
    }
}
