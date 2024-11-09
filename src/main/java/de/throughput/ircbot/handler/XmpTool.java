package de.throughput.ircbot.handler;

import lombok.experimental.UtilityClass;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.xml.XmpSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class XmpTool {

    /**
     * Adds a prompt and original prompt to the JPEG image using an XMP tag.
     */
    public static byte[] addPrompt(byte[] imageBytes, String imagePrompt, String originalPrompt) throws IOException {
        try {
            // Create XMP metadata and add Dublin Core schema
            XMPMetadata xmpMetadata = XMPMetadata.createXMPMetadata();
            DublinCoreSchema dcSchema = xmpMetadata.createAndAddDublinCoreSchema();
            dcSchema.setDescription(imagePrompt);  // Set the prompt field
            if (originalPrompt != null && !imagePrompt.equals(originalPrompt)) {
                dcSchema.setSource(originalPrompt);
            }

            // Serialize the XMP metadata to a byte array
            ByteArrayOutputStream xmpOutputStream = new ByteArrayOutputStream();

            new XmpSerializer().serialize(xmpMetadata, xmpOutputStream, true);

            // Embed the XMP metadata in the JPEG image using JpegXmpRewriter
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new JpegXmpRewriter().updateXmpXml(imageBytes, outputStream, xmpOutputStream.toString(StandardCharsets.UTF_8));

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
