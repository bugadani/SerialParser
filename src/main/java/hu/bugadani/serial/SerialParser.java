package hu.bugadani.serial;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SerialParser {

    public interface FrameMatchListener {

        class Aggregator implements FrameMatchListener {

            private final List<FrameMatchListener> mListenerList = new ArrayList<FrameMatchListener>();

            public void add(FrameMatchListener listener) {
                if (!mListenerList.contains(listener)) {
                    mListenerList.add(listener);
                }
            }

            public void remove(FrameMatchListener listener) {
                mListenerList.remove(listener);
            }

            public List<FrameMatchListener> getListeners() {
                return mListenerList;
            }

            public void onFrameMatched(FrameDefinition frame, byte[] data) {
                for (FrameMatchListener listener : getListeners()) {
                    listener.onFrameMatched(frame, data);
                }
            }
        }

        void onFrameMatched(FrameDefinition frame, byte[] data);
    }

    /**
     * This class is used to initialize a SerialParser instance.
     */
    public static class Builder {
        private int mBufferSize = 0;
        private int mLongestFrameSize = 0;

        private final List<FrameDefinition> mFrameDefinitionList = new ArrayList<FrameDefinition>();
        private final List<Integer> mFrameIds = new ArrayList<Integer>();

        /**
         * Sets an explicit buffer size
         * <p>
         * Note: this is only a hint and may be overridden if a fixed length frame is longer than bufferSize.
         *
         * @param bufferSize The buffer size
         * @return Fluent interface
         */
        public Builder setBufferSize(int bufferSize) {
            mBufferSize = bufferSize;

            return this;
        }

        /**
         * @param frameDefinition The frame definition to be added
         * @return Fluent interface
         */
        public Builder addFrameDefinition(FrameDefinition frameDefinition) {

            if (mFrameIds.contains(frameDefinition.mFrameId)) {
                throw new IllegalArgumentException("Duplicate frame id: " + frameDefinition.mFrameId);
            }

            frameDefinition.setInited();
            mLongestFrameSize = Math.max(mLongestFrameSize, frameDefinition.getFrameLength());
            mFrameDefinitionList.add(frameDefinition);
            mFrameIds.add(frameDefinition.mFrameId);

            return this;
        }

        /**
         * Construct the SerialParser object
         *
         * @return The created object
         */
        public SerialParser build() {
            if (mBufferSize == 0) {
                for (FrameDefinition def : mFrameDefinitionList) {
                    if (def.mDataLength == FrameDefinition.VARIABLE_LENGTH) {
                        throw new IllegalStateException("Variable length frames require a specified buffer size");
                    }
                }
            }

            ByteRingBuffer byteRingBuffer = new ByteRingBuffer(Math.max(mLongestFrameSize, mBufferSize));
            FrameDefinition[] frameDefinitions = mFrameDefinitionList.toArray(new FrameDefinition[0]);

            return new SerialParser(byteRingBuffer, frameDefinitions, mLongestFrameSize);
        }
    }

    /**
     * This class holds frame definition data, like header bytes, length specification, terminating byte.
     */
    public static class FrameDefinition {

        enum MatchResult {
            No,
            Maybe,
            Yes
        }

        public static final int VARIABLE_LENGTH = 0;

        private final int mFrameId;

        private byte[] mHeader;
        private int mDataLength = VARIABLE_LENGTH;
        private boolean mHasTerminatingByte = false;
        private byte mTerminatingByte = 0;
        private boolean mInitialized = false;

        private final FrameMatchListener.Aggregator listeners = new FrameMatchListener.Aggregator();

        /**
         * Construct a FrameDefinition instance
         *
         * @param frameId
         * @param header
         */
        public FrameDefinition(int frameId, String header) {
            this(frameId, header.getBytes());
        }

        /**
         * Construct a FrameDefinition instance
         *
         * @param frameId
         * @param header
         */
        public FrameDefinition(int frameId, char header) {
            this(frameId, new byte[]{(byte) header});
        }

        /**
         * Construct a FrameDefinition instance
         *
         * @param frameId
         * @param header
         */
        public FrameDefinition(int frameId, byte header) {
            this(frameId, new byte[]{header});
        }

        /**
         * Construct a FrameDefinition instance
         *
         * @param frameId
         * @param header
         */
        public FrameDefinition(int frameId, byte[] header) {
            mFrameId = frameId;
            mHeader = header;
        }

        /**
         * Check if frameId matched the frame's ID
         *
         * @param frameId
         * @return true if the ids are equal
         */
        public boolean isFrame(int frameId) {
            return mFrameId == frameId;
        }

        private void initGuard() {
            if (mInitialized) {
                throw new IllegalStateException("FrameDefinition is already initialized");
            }
        }

        /**
         * Set the length of data inside the frame.
         *
         * @param length A positive integer length or VARIABLE_LENGTH constant
         * @return Fluent interface
         */
        public FrameDefinition setDataLength(int length) {
            initGuard();
            mDataLength = length;
            return this;
        }

        /**
         * @param terminatingByte
         * @return Fluent interface
         */
        public FrameDefinition setTerminatingByte(byte terminatingByte) {
            initGuard();
            mTerminatingByte = terminatingByte;
            mHasTerminatingByte = true;
            return this;
        }

        /**
         * Add a listener that will be called when the frame is matched
         *
         * @param listener
         * @return Fluent interface
         */
        public FrameDefinition addListener(FrameMatchListener listener) {
            listeners.add(listener);
            return this;
        }

        /**
         * Remove a listener
         *
         * @param listener
         * @return Fluent interface
         */
        public FrameDefinition removeListener(FrameMatchListener listener) {
            listeners.remove(listener);
            return this;
        }

        private void setInited() {
            initGuard();
            if (mDataLength == VARIABLE_LENGTH && !mHasTerminatingByte) {
                throw new IllegalStateException("Variable length frames require a terminating byte");
            }
            mInitialized = true;
        }

        private MatchResult match(ByteRingBuffer syncBuffer) {

            if (syncBuffer.getSize() <= mHeader.length) {
                //return Maybe if buffer does not have enough data
                return MatchResult.Maybe;
            }

            //Get header bytes
            byte[] headerBytes = syncBuffer.peekMultiple(mHeader.length);

            //Check header bytes
            if (!Arrays.equals(mHeader, headerBytes)) {
                return MatchResult.No;
            }

            int bufferSize = syncBuffer.getSize();
            if (mDataLength == VARIABLE_LENGTH) {
                //Find the offset of mTerminatingByte in the buffer
                for (int index = mHeader.length; index < bufferSize; index++) {
                    if (syncBuffer.peek(index) == mTerminatingByte) {
                        int matchedDataLength = index - mHeader.length;
                        return matched(syncBuffer, matchedDataLength);
                    }
                }
            } else {
                int frameLength = getFrameLength();
                if (bufferSize >= frameLength) {
                    //There is enough data to process - look for the terminating byte
                    if (!mHasTerminatingByte || mTerminatingByte == syncBuffer.peek(frameLength - 1)) {
                        return matched(syncBuffer, mDataLength);
                    }

                    //Terminating byte did not match
                    return MatchResult.No;
                }
            }

            return MatchResult.Maybe;
        }

        private int getFrameLength() {
            return mHeader.length + mDataLength + (mHasTerminatingByte ? 1 : 0);
        }

        private MatchResult matched(ByteRingBuffer syncBuffer, int length) {

            //remove the header bytes - no longer needed
            syncBuffer.remove(mHeader.length);

            //remove the data bytes
            byte[] data = syncBuffer.remove(length);

            //remove terminating byte, if there is one
            if (mHasTerminatingByte) {
                syncBuffer.remove();
            }

            //trigger event
            listeners.onFrameMatched(this, data);

            return MatchResult.Yes;
        }
    }

    private final ByteRingBuffer mSyncBuffer;
    private final FrameDefinition[] mFrameDefinitions;
    private final int mLongestFrameSize;

    protected SerialParser(ByteRingBuffer byteRingBuffer, FrameDefinition[] frameDefinitions, int longestFrameSize) {
        mSyncBuffer = byteRingBuffer;
        mFrameDefinitions = frameDefinitions;
        mLongestFrameSize = longestFrameSize;
    }

    /**
     * Adds one byte to the internal buffer and tries to match frames.
     *
     * @param b
     */
    public void add(byte b) {
        mSyncBuffer.add(b);
        process();
    }

    /**
     * Adds a number of bytes to the internal buffer and tries to match frames.
     *
     * @param bytes
     */
    public void add(byte[] bytes) {
        if (bytes.length > mLongestFrameSize) {
            //Add in longest frame length blocks
            byte[] b = new byte[mLongestFrameSize];
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.remaining() >= mLongestFrameSize) {
                buffer.get(b, 0, mLongestFrameSize);
                addInternal(b);
            }
            //Set the bytes array to the remaining bytes
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes, 0, bytes.length);
        }
        //Add (remaining) bytes
        addInternal(bytes);
    }

    private void addInternal(byte[] bytes) {
        if (bytes.length == 0) {
            return;
        }

        if (bytes.length == 1) {
            add(bytes[0]);
        } else if (mSyncBuffer.getSpace() >= bytes.length) {
            addBytesInternal(bytes, bytes.length);
        } else {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] b = new byte[Math.min(mLongestFrameSize, bytes.length)];

            while (buffer.remaining() > 0) {
                int copyLength = Math.min(mSyncBuffer.getSpace(), buffer.remaining());
                buffer.get(b, 0, copyLength);

                addBytesInternal(b, copyLength);
            }
        }
    }

    private void addBytesInternal(byte[] bytes, int length) {
        mSyncBuffer.add(bytes, length);
        process();
    }

    private void process() {
        while (!mSyncBuffer.isEmpty()) {
            if (!step()) {
                break;
            }
        }
    }

    /**
     * @return bool Whether the processing can continue
     */
    private boolean step() {
        boolean removeByte = true;
        for (FrameDefinition fd : mFrameDefinitions) {
            switch (fd.match(mSyncBuffer)) {
                case No:
                    //Empty; match next frame definition
                    break;
                case Maybe:
                    //Match next frame definition, but don't remove a byte if none is matching
                    removeByte = false;
                    break;
                case Yes:
                    //Stop matching
                    return true;
            }
        }
        //There was at least one 'Maybe'
        if (!removeByte && !mSyncBuffer.isFull()) {
            //wait for next input
            return false;
        }
        mSyncBuffer.remove();
        return true;
    }
}
