package de.throughput.ircbot.handler;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;

public class ExifTool {

    /**
     * Adds the given description to the given JPEG image's exif tags.
     */
    public static byte[] addDescription(byte[] imageBytes, String description) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        TiffOutputSet outputSet = null;

        // Retrieve existing EXIF metadata
        ImageMetadata metadata = Imaging.getMetadata(inputStream, null);
        if (metadata instanceof JpegImageMetadata jpegMetadata) {
            TiffImageMetadata exif = jpegMetadata.getExif();
            if (exif != null) {
                // Clone existing metadata if available
                outputSet = exif.getOutputSet();
            }
        }

        // If no EXIF metadata exists, create a new set
        if (outputSet == null) {
            outputSet = new TiffOutputSet();
        }

        // Add the ImageDescription tag
        TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
        exifDirectory.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);  // Remove if exists to avoid duplicates
        exifDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, toAsciiOnly(description));

        // Write updated EXIF metadata back to the image in memory
        new ExifRewriter().updateExifMetadataLossless(new ByteArrayInputStream(imageBytes), outputStream, outputSet);

        return outputStream.toByteArray();
    }

    private static String toAsciiOnly(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("[^\\x00-\\x7F]", ""); // Removes diacritical marks and non-ASCII characters
    }
}
