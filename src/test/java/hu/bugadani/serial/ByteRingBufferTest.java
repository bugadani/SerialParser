package hu.bugadani.serial;

import org.junit.Before;
import org.junit.Test;

import java.nio.BufferOverflowException;

import static org.junit.Assert.*;

public class ByteRingBufferTest {

    private ByteRingBuffer buffer;

    @Before
    public void setUp() {
        buffer = new ByteRingBuffer(10);
    }

    @Test(expected = BufferOverflowException.class)
    public void testGetSize() throws Exception {
        try {
            assertEquals(0, buffer.getSize());
            buffer.add(new byte[5]);
            assertEquals(5, buffer.getSize());
            buffer.add(new byte[5]);
            assertEquals(10, buffer.getSize());
        } catch (BufferOverflowException e) {
            fail("exception is thrown early");
        }
        buffer.add(new byte[5]);
        fail("exception is not thrown");
    }

    @Test
    public void testRemoveOne() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte b = buffer.remove();

        assertEquals(9, buffer.getSize());
        assertEquals((byte)'a', b);
    }

    @Test
    public void testPeekOne() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte b = buffer.peek();

        assertEquals(10, buffer.getSize());
        assertEquals((byte)'a', b);
    }

    @Test
    public void testPeekOffset() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte b = buffer.peek(2);

        assertEquals(10, buffer.getSize());
        assertEquals((byte)'c', b);
    }

    @Test
    public void testPeekMore() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte[] b = buffer.peekMultiple(2);

        assertEquals(10, buffer.getSize());
        assertArrayEquals("ab".getBytes(), b);
    }

    @Test
    public void testRemoveMore() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte[] b = buffer.remove(2);

        assertEquals(8, buffer.getSize());
        assertArrayEquals("ab".getBytes(), b);
    }

    @Test
    public void testSetCapacityGrowing() throws Exception {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        buffer.setCapacity(12);

        assertEquals(10, buffer.getSize());
        assertEquals(12, buffer.getCapacity());

        assertArrayEquals(bytes, buffer.peekAll());
    }

    @Test
    public void testSetCapacityShrinkingToSize() throws Exception {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        //test shrinking without dropping bytes
        buffer.setCapacity(10);

        assertEquals(10, buffer.getCapacity());
        assertEquals(10, buffer.getSize());

        assertArrayEquals(bytes, buffer.peekAll());
    }

    @Test
    public void testSetCapacityShrinkingBelowSize() throws Exception {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        //test shrinking with dropping bytes
        buffer.setCapacity(8);

        assertEquals(8, buffer.getCapacity());
        assertEquals(8, buffer.getSize());

        assertArrayEquals("cdefghij".getBytes(), buffer.peekAll());
    }
}