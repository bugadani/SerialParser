package hu.bugadani.serial;

import org.junit.Before;
import org.junit.Test;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

import static org.junit.Assert.*;

public class ByteRingBufferTest {

    private ByteRingBuffer buffer;

    @Before
    public void setUp() {
        buffer = new ByteRingBuffer(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetZeroCapacity() {
        buffer.setCapacity(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetNegativeCapacity() {
        buffer.setCapacity(-1);
    }

    @Test
    public void testEmpty() {
        assertTrue(buffer.isEmpty());
        buffer.add((byte) 'a');
        assertFalse(buffer.isEmpty());
        buffer.remove();
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void testFull() {
        buffer.add(new byte[9]);
        assertFalse(buffer.isFull());
        buffer.add((byte) 'a');
        assertTrue(buffer.isFull());
        buffer.remove();
        assertFalse(buffer.isFull());
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
        assertEquals((byte) 'a', b);
    }

    @Test
    public void testPeekOne() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte b = buffer.peek();

        assertEquals(10, buffer.getSize());
        assertEquals((byte) 'a', b);
    }

    @Test
    public void testPeekOffset() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte b = buffer.peek(2);

        assertEquals(10, buffer.getSize());
        assertEquals((byte) 'c', b);
    }

    @Test(expected = BufferUnderflowException.class)
    public void testPeekOffsetUnderflow() {
        buffer.add((byte)'a');
        buffer.peek(2);
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
    public void testPeekAll() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte[] b = buffer.peekAll();

        assertEquals(10, buffer.getSize());
        assertArrayEquals(bytes, b);
    }

    @Test(expected = BufferUnderflowException.class)
    public void testRemoveFromEmpty() {
        buffer.remove();
    }

    @Test(expected = BufferUnderflowException.class)
    public void testRemoveMoreFromEmpty() {
        buffer.remove(2);
    }

    public void testRemoveAllFromEmpty() {
        byte[] bytes = buffer.removeAll();
        assertArrayEquals(new byte[0], bytes);
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
    public void testRemoveAll() {
        byte[] bytes = "abcdefghij".getBytes();
        buffer.add(bytes);
        assertEquals(10, buffer.getSize());

        byte[] b = buffer.removeAll();

        assertEquals(0, buffer.getSize());
        assertArrayEquals(bytes, b);
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

    @Test
    public void testSetCapacityGrowingWhenWrapped() throws Exception {
        buffer.add(new byte[5]);
        buffer.remove(5);
        testSetCapacityGrowing();
    }

    @Test
    public void testSetCapacityShrinkingToSizeWhenWrapped() throws Exception {
        buffer.add(new byte[5]);
        buffer.remove(5);
        testSetCapacityShrinkingToSize();
    }

    @Test
    public void testSetCapacityShrinkingBelowSizeWhenWrapped() throws Exception {
        buffer.add(new byte[5]);
        buffer.remove(5);
        testSetCapacityShrinkingBelowSize();
    }
}