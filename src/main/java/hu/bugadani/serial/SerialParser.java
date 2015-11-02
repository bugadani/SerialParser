package hu.bugadani.serial;

import java.nio.BufferUnderflowException;
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

    public static class Builder {
        private int mBufferSize       = 0;
        private int mLongestFrameSize = 0;

        private final List<FrameDefinition> mFrameDefinitionList = new ArrayList<FrameDefinition>();

        public Builder setBufferSize(int bufferSize) {
            mBufferSize = bufferSize;

            return this;
        }

        public Builder addFrameDefinition(FrameDefinition frameDefinition) {
            mLongestFrameSize = Math.max(mLongestFrameSize, frameDefinition.getFrameLength());
            mFrameDefinitionList.add(frameDefinition);
            frameDefinition.setInited();

            return this;
        }

        public SerialParser build() {
            final SerialParser object = new SerialParser();

            object.mSyncBuffer =
                    new CircularByteArray(Math.max(5 * mLongestFrameSize, mBufferSize));
            object.mFrameDefinitionList = mFrameDefinitionList;
            object.mLongestFrameSize = mLongestFrameSize;

            return object;
        }
    }

    public static class FrameDefinition {

        public enum MatchResult {
            No,
            Maybe,
            Yes
        }

        public static final int VARIABLE_LENGTH = 0;

        private final int mFrameId;

        private byte[] mHeader;
        private int     mDataLength         = VARIABLE_LENGTH;
        private boolean mHasTerminatingByte = false;
        private byte    mTerminatingByte    = 0;
        private boolean mInitialized        = false;

        private final FrameMatchListener.Aggregator listeners = new FrameMatchListener.Aggregator();

        public FrameDefinition(int frameId, byte[] header) {
            mFrameId = frameId;
            mHeader = header;
        }

        public boolean isFrame(int frameId) {
            return mFrameId == frameId;
        }

        private void initGuard() {
            if (mInitialized) {
                throw new IllegalStateException("FrameDefinition is already initialized");
            }
        }

        public FrameDefinition setDataLength(int length) {
            initGuard();
            mDataLength = length;
            return this;
        }

        public FrameDefinition setTerminatingByte(byte terminatingByte) {
            initGuard();
            mTerminatingByte = terminatingByte;
            mHasTerminatingByte = true;
            return this;
        }

        public FrameDefinition addListener(FrameMatchListener listener) {
            listeners.add(listener);
            return this;
        }

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

        public MatchResult match(CircularByteArray syncBuffer) {

            byte[] headerBytes;
            try {
                headerBytes = syncBuffer.peekMultiple(mHeader.length);
            } catch (BufferUnderflowException e) {
                return MatchResult.Maybe;
            }

            if (!Arrays.equals(mHeader, headerBytes)) {
                return MatchResult.No;
            }

            if (mDataLength == VARIABLE_LENGTH) {
                for (int index = mHeader.length; index < syncBuffer.getSize(); index++) {
                    if (syncBuffer.peek(index) == mTerminatingByte) {
                        return matched(syncBuffer, index - mHeader.length);
                    }
                }
            } else {
                int frameLength = getFrameLength();
                if (syncBuffer.getSize() >= frameLength) {
                    if (!mHasTerminatingByte || mTerminatingByte == syncBuffer.peek(frameLength - 1)) {
                        return matched(syncBuffer, mDataLength);
                    }

                    return MatchResult.No;
                }
            }

            return MatchResult.Maybe;
        }

        public int getFrameLength() {
            return mHeader.length + mDataLength + (mHasTerminatingByte ? 1 : 0);
        }

        private MatchResult matched(CircularByteArray syncBuffer, int length) {

            syncBuffer.remove(mHeader.length);

            byte[] data = syncBuffer.remove(length);

            if (mHasTerminatingByte) {
                syncBuffer.remove();
            }

            listeners.onFrameMatched(this, data);

            return MatchResult.Yes;
        }
    }

    private CircularByteArray     mSyncBuffer;
    private List<FrameDefinition> mFrameDefinitionList;
    private int                   mLongestFrameSize;

    protected SerialParser() {
    }

    public void add(byte[] bytes) {
        if (bytes.length > mLongestFrameSize) {
            byte[] b = new byte[mLongestFrameSize];
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.remaining() >= mLongestFrameSize) {
                buffer.get(b, 0, mLongestFrameSize);
                addInternal(b);
            }
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes, 0, buffer.remaining());
        }
        addInternal(bytes);
    }

    private void addInternal(byte[] bytes) {
        if (bytes.length != 0) {
            mSyncBuffer.add(bytes);

            int size = mSyncBuffer.getSize();
            while (!mSyncBuffer.isEmpty()) {
                if (!step()) {
                    break;
                }
                if (mSyncBuffer.getSize() == size) {
                    return;
                }
                size = mSyncBuffer.getSize();
            }
        }
    }

    /**
     * @return bool Whether the processing can continue
     */
    private boolean step() {
        boolean removeByte = true;
        for (FrameDefinition fd : mFrameDefinitionList) {
            final FrameDefinition.MatchResult matchResult = fd.match(mSyncBuffer);
            switch (matchResult) {
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
        if (!removeByte && !mSyncBuffer.isFull()) {
            //wait for next input
            return false;
        }
        mSyncBuffer.remove();
        return true;
    }
}
