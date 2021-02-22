package com.tools.niviewer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

public class CompressionUtils {
    private static final String TAG = "CompressionUtils";

    public static byte[] compress(ByteBuffer byteBuffer) throws IOException {

        byte[] data = new byte[byteBuffer.capacity()];
        byteBuffer.get(data, 0, data.length);

        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

        deflater.finish();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer); // returns the generated code... index
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        return outputStream.toByteArray();
    }
}