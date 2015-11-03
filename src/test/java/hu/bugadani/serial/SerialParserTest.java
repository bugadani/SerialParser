package hu.bugadani.serial;

import org.junit.Test;

import static org.junit.Assert.*;

public class SerialParserTest {

    private int called = 0;

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateFrameId() {
        new SerialParser
                .Builder()
                .addFrameDefinition(
                        new SerialParser.FrameDefinition(0, '-').setDataLength(1)
                )
                .addFrameDefinition(
                        new SerialParser.FrameDefinition(0, '+').setDataLength(1)
                );
    }

    @Test(expected = IllegalStateException.class)
    public void testInitializationGuard() {
        SerialParser.FrameDefinition frame = new SerialParser.FrameDefinition(0, '-').setDataLength(1);
        new SerialParser
                .Builder()
                .addFrameDefinition(frame);
        frame.setDataLength(2);
    }

    @Test(expected = IllegalStateException.class)
    public void testVariableFrameLength() {
        new SerialParser
                .Builder()
                .addFrameDefinition(
                        new SerialParser.FrameDefinition(0, (byte) '-')
                );
    }

    @Test
    public void testMatchBytes() throws Exception {
        SerialParser.FrameMatchListener listener = new SerialParser.FrameMatchListener() {
            public void onFrameMatched(SerialParser.FrameDefinition frame, byte[] data) {
                switch (called++) {
                    case 0:
                        assertTrue(frame.isFrame(1));
                        assertArrayEquals("123".getBytes(), data);
                        break;
                    case 1:
                        assertTrue(frame.isFrame(1));
                        assertArrayEquals("45".getBytes(), data);
                        break;
                    case 2:
                        assertTrue(frame.isFrame(0));
                        assertArrayEquals("asdfjk".getBytes(), data);
                        break;
                    default:
                        fail();
                        break;
                }
            }
        };
        SerialParser parser = new SerialParser
                .Builder()
                .setBufferSize(10)
                .addFrameDefinition(
                        new SerialParser.FrameDefinition(0, '-')
                                .setDataLength(6)
                                .addListener(listener)
                )
                .addFrameDefinition(
                        new SerialParser.FrameDefinition(1, "+")
                                .setTerminatingByte((byte) ';')
                                .addListener(listener)
                )
                .build();

        parser.add("+123;+45;  -asdfjk;".getBytes());
        assertEquals(3, called);
    }

    @Test
    public void testMatchVariableLengthData() throws Exception {
        SerialParser.FrameMatchListener listener = new SerialParser.FrameMatchListener() {
            public void onFrameMatched(SerialParser.FrameDefinition frame, byte[] data) {
                assertArrayEquals("123456789".getBytes(), data);
                called = 1;
            }
        };
        SerialParser parser = new SerialParser
                .Builder()
                .setBufferSize(11)
                .addFrameDefinition(
                        new SerialParser.FrameDefinition(1, "+")
                                .setTerminatingByte((byte) ';')
                                .addListener(listener)
                )
                .build();

        parser.add(" something that will not be matched  +123456789;".getBytes());
        assertEquals(1, called);
    }
}