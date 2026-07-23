package com.agricore.work.application.service;

import com.agricore.work.domain.exception.WorkException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Set;

@Component
final class TaskAttachmentContentInspector {

    private static final byte[] JPEG_PREFIX = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_PREFIX = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] RIFF_PREFIX = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};
    private static final byte[] PNG_IHDR = {0x49, 0x48, 0x44, 0x52};
    private static final byte[] PNG_IEND = {0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
    private static final Set<String> WEBP_CHUNKS = Set.of("VP8 ", "VP8L", "VP8X");

    DetectedContent inspect(InputStream input) {
        SampledContent sample;
        try {
            sample = sample(input);
        } catch (IOException exception) {
            throw new WorkException("ATTACHMENT_READ_FAILED", "Attachment content could not be read", 400);
        }
        if (isJpeg(sample)) {
            return new DetectedContent("image/jpeg", "jpg");
        }
        if (isPng(sample)) {
            return new DetectedContent("image/png", "png");
        }
        if (isWebp(sample)) {
            return new DetectedContent("image/webp", "webp");
        }
        throw new WorkException(
                "UNSUPPORTED_ATTACHMENT_CONTENT",
                "Only JPEG, PNG, and WebP task attachments are supported",
                415
        );
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(content, prefix.length), prefix);
    }

    private static boolean isJpeg(SampledContent sample) {
        byte[] tail = sample.tail();
        return sample.length() >= 16
                && startsWith(sample.header(), JPEG_PREFIX)
                && tail.length >= 2
                && tail[tail.length - 2] == (byte) 0xFF
                && tail[tail.length - 1] == (byte) 0xD9;
    }

    private static boolean isPng(SampledContent sample) {
        return sample.length() >= 45
                && startsWith(sample.header(), PNG_PREFIX)
                && Arrays.equals(Arrays.copyOfRange(sample.header(), 12, 16), PNG_IHDR)
                && Arrays.equals(sample.tail(), PNG_IEND);
    }

    private static boolean isWebp(SampledContent sample) {
        byte[] header = sample.header();
        if (sample.length() < 20
                || !startsWith(header, RIFF_PREFIX)
                || !Arrays.equals(Arrays.copyOfRange(header, 8, 12), WEBP_MARKER)) {
            return false;
        }
        long declaredRiffSize = Integer.toUnsignedLong(
                ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
        );
        String chunk = new String(header, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return declaredRiffSize == sample.length() - 8 && WEBP_CHUNKS.contains(chunk);
    }

    private static SampledContent sample(InputStream input) throws IOException {
        byte[] header = new byte[32];
        int headerLength = 0;
        byte[] tail = new byte[12];
        int tailLength = 0;
        long length = 0;
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            int headerCopy = Math.min(read, header.length - headerLength);
            if (headerCopy > 0) {
                System.arraycopy(buffer, 0, header, headerLength, headerCopy);
                headerLength += headerCopy;
            }
            if (read >= tail.length) {
                System.arraycopy(buffer, read - tail.length, tail, 0, tail.length);
                tailLength = tail.length;
            } else {
                int retained = Math.min(tailLength, tail.length - read);
                System.arraycopy(tail, tailLength - retained, tail, 0, retained);
                System.arraycopy(buffer, 0, tail, retained, read);
                tailLength = retained + read;
            }
            length += read;
        }
        return new SampledContent(
                Arrays.copyOf(header, headerLength),
                Arrays.copyOf(tail, tailLength),
                length
        );
    }

    record DetectedContent(String contentType, String extension) {
    }

    private record SampledContent(byte[] header, byte[] tail, long length) {
    }
}
