package com.simsilica.ethereal.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Regression coverage for bit widths, alignment, and truncated input. */
public class BitStreamBoundsTest {
    private interface IoOperation {
        void run() throws IOException;
    }

    private static void assertInvalidWidthRejected(IoOperation operation)
            throws IOException {
        try {
            operation.run();
            fail("Invalid bit width was accepted");
        } catch (IllegalArgumentException expected) {
            // Width validation must fail before reading or writing any bits.
        }
    }

    @Test
    public void rejectsInvalidIntReadWidths() throws IOException {
        int[] invalidWidths = {Integer.MIN_VALUE, -1, 0, 33, Integer.MAX_VALUE};

        for (final int width : invalidWidths) {
            assertInvalidWidthRejected(new IoOperation() {
                @Override
                public void run() throws IOException {
                    ByteArrayInputStream bytes =
                            new ByteArrayInputStream(new byte[16]);
                    new BitInputStream(bytes).readBits(width);
                }
            });
        }
    }

    @Test
    public void rejectsInvalidLongReadWidths() throws IOException {
        int[] invalidWidths = {Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE};

        for (final int width : invalidWidths) {
            assertInvalidWidthRejected(new IoOperation() {
                @Override
                public void run() throws IOException {
                    ByteArrayInputStream bytes =
                            new ByteArrayInputStream(new byte[16]);
                    new BitInputStream(bytes).readLongBits(width);
                }
            });
        }
    }

    @Test
    public void rejectsInvalidIntWriteWidthsWithoutChangingStream()
            throws IOException {
        int[] invalidWidths = {Integer.MIN_VALUE, -1, 0, 33, Integer.MAX_VALUE};

        for (final int width : invalidWidths) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final BitOutputStream output = new BitOutputStream(bytes);

            assertInvalidWidthRejected(new IoOperation() {
                @Override
                public void run() throws IOException {
                    output.writeBits(123, width);
                }
            });

            // A valid write after rejection must start at the first bit.
            output.writeBits(5, 3);
            output.close();
            assertArrayEquals(new byte[]{(byte) 0xa0}, bytes.toByteArray());
        }
    }

    @Test
    public void rejectsInvalidLongWriteWidthsWithoutChangingStream()
            throws IOException {
        int[] invalidWidths = {Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE};

        for (final int width : invalidWidths) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final BitOutputStream output = new BitOutputStream(bytes);

            assertInvalidWidthRejected(new IoOperation() {
                @Override
                public void run() throws IOException {
                    output.writeLongBits(123L, width);
                }
            });

            // A valid write after rejection must start at the first bit.
            output.writeLongBits(5L, 3);
            output.close();
            assertArrayEquals(new byte[]{(byte) 0xa0}, bytes.toByteArray());
        }
    }

    @Test
    public void allValidWidthsRoundTripUnaligned() throws IOException {
        Random random = new Random(20261013L);

        for (int width = 1; width <= 64; width++) {
            for (int sample = 0; sample < 5; sample++) {
                long value = random.nextLong();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                BitOutputStream output = new BitOutputStream(bytes);

                output.writeBits(5, 3);
                output.writeLongBits(value, width);
                output.writeBits(3, 2);
                output.close();

                BitInputStream input = new BitInputStream(
                        new ByteArrayInputStream(bytes.toByteArray()));
                long mask = width == 64 ? -1L : (1L << width) - 1;

                assertEquals(5, input.readBits(3));
                assertEquals(value & mask, input.readLongBits(width));
                assertEquals(3, input.readBits(2));
                input.close();
            }
        }

        for (int width = 1; width <= 32; width++) {
            int value = random.nextInt();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BitOutputStream output = new BitOutputStream(bytes);

            output.writeBits(1, 1);
            output.writeBits(value, width);
            output.close();

            BitInputStream input = new BitInputStream(
                    new ByteArrayInputStream(bytes.toByteArray()));
            int mask = width == 32 ? -1 : (1 << width) - 1;

            assertEquals(1, input.readBits(1));
            assertEquals(value & mask, input.readBits(width));
            input.close();
        }
    }

    @Test
    public void truncatedInputFailsCleanly() throws IOException {
        try {
            BitInputStream input = new BitInputStream(
                    new ByteArrayInputStream(new byte[1]));
            input.readBits(9);
            fail("Truncated input was accepted");
        } catch (IOException expected) {
            // A one-byte stream cannot satisfy a nine-bit read.
        }
    }
}
