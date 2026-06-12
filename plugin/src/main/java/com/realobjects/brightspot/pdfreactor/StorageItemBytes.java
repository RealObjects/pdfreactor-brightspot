package com.realobjects.brightspot.pdfreactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.psddev.dari.util.StorageItem;

/**
 * Reads a {@link StorageItem}'s bytes server-side. Used for ICC profiles
 * uploaded via {@link PdfReactorSiteSettings}: the application reads the
 * stored profile and embeds its bytes in the conversion request, so the
 * PDFreactor host never has to fetch the profile URL.
 */
final class StorageItemBytes {

    private StorageItemBytes() {
    }

    /**
     * @param item Nullable.
     * @return Nullable. {@code null} for no item.
     * @throws PdfReactorException If the item is set but its data cannot be
     *         read (a configuration error worth surfacing).
     */
    static byte[] read(StorageItem item) {
        if (item == null) {
            return null;
        }
        try (InputStream in = item.getData()) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();

        } catch (IOException error) {
            throw new PdfReactorException(
                    "Could not read the uploaded ICC profile [" + item.getPath() + "]: "
                            + error.getMessage(),
                    error);
        }
    }
}
