package hu.bugadani.serial;

import org.junit.Test;

import static org.junit.Assert.*;

public class FrameMatchListenerAggregatorTest {

    boolean listenerCalled = false;

    @Test
    public void testListeners() {
        SerialParser.FrameMatchListener.Aggregator aggregator = new SerialParser.FrameMatchListener.Aggregator();

        final byte[] expected = "abcd".getBytes();

        aggregator.add(new SerialParser.FrameMatchListener() {
            public void onFrameMatched(SerialParser.FrameDefinition frame, byte[] data) {
                assertArrayEquals(expected, data);
                listenerCalled = true;
            }
        });

        SerialParser.FrameMatchListener removedListener = new SerialParser.FrameMatchListener() {
            public void onFrameMatched(SerialParser.FrameDefinition frame, byte[] data) {
                fail("listener should not have been called");
            }
        };
        aggregator.add(removedListener);
        aggregator.remove(removedListener);

        aggregator.onFrameMatched(null, "abcd".getBytes());
        assertTrue(listenerCalled);
    }
}