package com.umc.nuvibe.global.service;


import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class ImageThumbnailGenerator {

    private static final int MAX_EDGE = 320;
    private static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;

    public byte[] generate(byte[] originalBytes) throws IOException {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IllegalArgumentException("이미지 데이터가 비어 있습니다.");
        }

        if (originalBytes.length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("이미지 파일이 너무 큽니다.");
        }

        try (var input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(originalBytes))) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("읽을 수 없는 이미지입니다.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);

                // 전체 이미지를 메모리에 펼치기 전에 해상도를 확인한다.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                            "이미지 해상도가 허용 범위를 벗어났습니다.");
                }

                BufferedImage original = reader.read(0);

                double scale = Math.min(
                        1.0,
                        (double) MAX_EDGE / Math.max(width, height)
                );

                int targetWidth = Math.max(
                        1, (int) Math.round(width * scale));
                int targetHeight = Math.max(
                        1, (int) Math.round(height * scale));

                BufferedImage thumbnail = new BufferedImage(
                        targetWidth,
                        targetHeight,
                        BufferedImage.TYPE_INT_ARGB
                );

                Graphics2D graphics = thumbnail.createGraphics();
                try {
                    graphics.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC
                    );
                    graphics.drawImage(
                            original, 0, 0,
                            targetWidth, targetHeight, null
                    );
                } finally {
                    graphics.dispose();
                }

                try (var output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(thumbnail, "png", output)) {
                        throw new IOException("PNG 인코더를 찾지 못했습니다.");
                    }
                    return output.toByteArray();
                }
            } finally {
                reader.dispose();
            }
        }
    }
}