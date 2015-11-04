package hu.bugadani.serial;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

/**
 * A circular byte buffer class
 */
public class ByteRingBuffer {
    enum CopyAlignment {
        Left,
        Right
    }

    private byte[] mArray;
    private int mHead;
    private int mTail;
    private int mSize;

    public ByteRingBuffer(int cap) {
        setCapacity(cap);
    }

    /**
     * Set the capacity of the buffer. If the new capacity is less than the current buffer size,
     * the differing number of bytes from the beginning of the buffer will be dropped.
     *
     * @param capacity The new capacity
     */
    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        byte[] array = new byte[capacity];
        if (isEmpty()) {
            //No copying is necessary
            mSize = 0;
        } else {
            copyBufferContents(array, CopyAlignment.Right);
            mSize = Math.min(mSize, capacity);
        }
        mTail = 0;
        mHead = mSize;
        mArray = array;
    }

    private void copyBufferContents(byte[] dest, CopyAlignment alignment) {
        int size = mSize;
        int copyLength = Math.min(dest.length, size);
        if (copyLength == 0) {
            //Don't copy if there is nothing to copy
            return;
        }

        int tail = mTail;
        //If buffer is smaller than the current data size...
        if (copyLength < size) {
            if (alignment == CopyAlignment.Right) {//... drop bytes from beginning
                int dropBytes = size - copyLength;
                tail = wrap(tail + dropBytes);
            }
        } else if (alignment == CopyAlignment.Left) {
            copyLength = size;
        }

        int distToEnd = mArray.length - tail;
        //If the source data wraps around the end of the array...
        if (copyLength <= distToEnd) {
            System.arraycopy(mArray, tail, dest, 0, copyLength);
        } else {
            //.. copy in two steps

            //copy the second part to the beginning
            System.arraycopy(mArray, tail, dest, 0, distToEnd);
            //copy the first part to the end
            System.arraycopy(mArray, 0, dest, distToEnd, copyLength - distToEnd);
        }
    }

    private int wrap(int i) {
        int mod = i % mArray.length;
        if (i < 0) {
            mod += mArray.length;
        }
        return mod;
    }

    private void stepTail(int i) {
        mTail = wrap(mTail + i);
        mSize -= i;
    }

    private void stepHead(int i) {
        mHead = wrap(mHead + i);
        mSize += i;
    }

    /**
     * @return The maximum number of bytes in the buffer
     */
    public int getCapacity() {
        return mArray.length;
    }

    /**
     * @return The current number of bytes in the buffer
     */
    public int getSize() {
        return mSize;
    }

    /**
     * @return The current free space in the buffer
     */
    public int getSpace() {
        return mArray.length - mSize;
    }

    /**
     * @return True if the buffer is empty
     */
    public boolean isEmpty() {
        return mSize == 0;
    }

    /**
     * @return True if the buffer is full
     */
    public boolean isFull() {
        return mSize == mArray.length;
    }

    /**
     * Add a byte to the buffer's end.
     *
     * @param b The byte to add
     * @throws BufferOverflowException
     */
    public void add(byte b) throws BufferOverflowException {
        if (isFull()) {
            throw new BufferOverflowException();
        }
        mArray[mHead] = b;
        stepHead(1);
    }

    /**
     * Add a number of bytes to the buffer's end.
     *
     * @param list The bytes to add
     * @throws BufferOverflowException
     */
    public void add(byte[] list) {
        add(list, list.length);
    }

    /**
     * Add a number of bytes to the buffer's end.
     *
     * @param list The bytes to add
     * @throws BufferOverflowException
     */
    public void add(byte[] list, int length) {
        if (length > list.length) {
            throw new IllegalArgumentException("length > list.length");
        }
        if (length > getSpace()) {
            throw new BufferOverflowException();
        }

        // For efficiency, the bytes are copied in blocks
        // instead of one at a time.

        int ptr = 0;
        while (ptr < length) {
            int space = getSpace();
            int distToEnd = mArray.length - mHead;
            int blockLen = Math.min(space, distToEnd);

            int bytesRemaining = length - ptr;
            int copyLen = Math.min(blockLen, bytesRemaining);

            System.arraycopy(list, ptr, mArray, mHead, copyLen);
            stepHead(copyLen);
            ptr += copyLen;
        }
    }

    /**
     * Remove a byte from the array
     *
     * @return The removed byte
     * @throws BufferOverflowException
     */
    public byte remove() throws BufferOverflowException {
        if (isEmpty()) {
            throw new BufferUnderflowException();
        }
        byte b = mArray[mTail];
        stepTail(1);

        return b;
    }

    /**
     * Remove a number of bytes from the buffer.
     *
     * @return The removed bytes
     * @throws BufferOverflowException
     */
    public byte[] remove(int n) throws BufferOverflowException {
        byte[] bytes = peekMultiple(n);

        stepTail(bytes.length);

        return bytes;
    }

    /**
     * Remove all bytes from the buffer.
     *
     * @return The removed bytes
     */
    public byte[] removeAll() {
        return remove(mSize);
    }

    /**
     * Returns the current byte if the buffer is not empty, without removing it
     *
     * @return The current byte
     */
    public byte peek() throws BufferUnderflowException {
        return peek(0);
    }

    /**
     * Returns the byte that is n positions ahead in the buffer, without removing it.
     *
     * @param n The number of positions, where 0 is the current byte.
     *          Must be less than the current buffer size.
     * @return The byte n positions ahead
     * @throws BufferUnderflowException
     */
    public byte peek(int n) throws BufferUnderflowException {
        if (n >= mSize) {
            throw new BufferUnderflowException();
        }
        return mArray[wrap(mTail + n)];
    }

    /**
     * Return a number of bytes without removing them from the buffer.
     *
     * @return The bytes
     * @throws BufferUnderflowException
     */
    public byte[] peekMultiple(int n) throws BufferUnderflowException {
        // For efficiency, the bytes are copied in blocks
        // instead of one at a time.
        if (n > mSize) {
            throw new BufferUnderflowException();
        }

        byte[] bytes = new byte[n];
        copyBufferContents(bytes, CopyAlignment.Left);
        return bytes;
    }

    /**
     * Return a copy of the buffer contents.
     *
     * @return The copied bytes
     */
    public byte[] peekAll() {
        return peekMultiple(mSize);
    }
}
