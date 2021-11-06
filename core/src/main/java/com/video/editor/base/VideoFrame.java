package com.video.editor.base;

import android.graphics.Matrix;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.ByteBuffer;

public class VideoFrame implements RefCounted {


    /**
     * Implements image storage medium. Might be for example an OpenGL texture or a memory region
     * containing I420-data.
     *
     * <p>Reference counting is needed since a video buffer can be shared between multiple VideoSinks,
     * and the buffer needs to be returned to the VideoSource as soon as all references are gone.
     */
    public interface Buffer extends RefCounted {
        /**
         * Representation of the underlying buffer. Currently, only NATIVE and I420 are supported.
         */
        @CalledByNative("Buffer")
        default int getBufferType() {
            return VideoFrameBufferType.NATIVE;
        }

        /**
         * Resolution of the buffer in pixels.
         */
        @CalledByNative("Buffer") int getWidth();
        @CalledByNative("Buffer") int getHeight();

        /**
         * Returns a memory-backed frame in I420 format. If the pixel data is in another format, a
         * conversion will take place. All implementations must provide a fallback to I420 for
         * compatibility with e.g. the internal WebRTC software encoders.
         */
        @CalledByNative("Buffer") I420Buffer toI420();

        @Override @CalledByNative("Buffer") void retain();
        @Override @CalledByNative("Buffer") void release();

        /**
         * Crops a region defined by `cropx`, `cropY`, `cropWidth` and `cropHeight`. Scales it to size
         * `scaleWidth` x `scaleHeight`.
         */
        @CalledByNative("Buffer")
        Buffer cropAndScale(
                int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight);
    }

    /**
     * Interface for I420 buffers.
     */
    public interface I420Buffer extends Buffer {
        @Override
        default int getBufferType() {
            return VideoFrameBufferType.I420;
        }

        /**
         * Returns a direct ByteBuffer containing Y-plane data. The buffer capacity is at least
         * getStrideY() * getHeight() bytes. The position of the returned buffer is ignored and must
         * be 0. Callers may mutate the ByteBuffer (eg. through relative-read operations), so
         * implementations must return a new ByteBuffer or slice for each call.
         */
        @CalledByNative("I420Buffer") ByteBuffer getDataY();
        /**
         * Returns a direct ByteBuffer containing U-plane data. The buffer capacity is at least
         * getStrideU() * ((getHeight() + 1) / 2) bytes. The position of the returned buffer is ignored
         * and must be 0. Callers may mutate the ByteBuffer (eg. through relative-read operations), so
         * implementations must return a new ByteBuffer or slice for each call.
         */
        @CalledByNative("I420Buffer") ByteBuffer getDataU();
        /**
         * Returns a direct ByteBuffer containing V-plane data. The buffer capacity is at least
         * getStrideV() * ((getHeight() + 1) / 2) bytes. The position of the returned buffer is ignored
         * and must be 0. Callers may mutate the ByteBuffer (eg. through relative-read operations), so
         * implementations must return a new ByteBuffer or slice for each call.
         */
        @CalledByNative("I420Buffer")
        ByteBuffer getDataV();

        @CalledByNative("I420Buffer") int getStrideY();
        @CalledByNative("I420Buffer") int getStrideU();
        @CalledByNative("I420Buffer") int getStrideV();
    }

    /**
     * Interface for buffers that are stored as a single texture, either in OES or RGB format.
     */
    public interface TextureBuffer extends Buffer {
        enum Type {
            OES(GLES11Ext.GL_TEXTURE_EXTERNAL_OES),
            RGB(GLES20.GL_TEXTURE_2D);

            private final int glTarget;

            private Type(final int glTarget) {
                this.glTarget = glTarget;
            }

            public int getGlTarget() {
                return glTarget;
            }
        }

        Type getType();
        int getTextureId();

        /**
         * Retrieve the transform matrix associated with the frame. This transform matrix maps 2D
         * homogeneous coordinates of the form (s, t, 1) with s and t in the inclusive range [0, 1] to
         * the coordinate that should be used to sample that location from the buffer.
         */
        Matrix getTransformMatrix();
    }

    private final Buffer buffer;
    private final int rotation;
    private final long timestampNs;

    /**
     * Constructs a new VideoFrame backed by the given {@code buffer}.
     *
     * @note Ownership of the buffer object is tranferred to the new VideoFrame.
     */
    @CalledByNative
    public VideoFrame(Buffer buffer, int rotation, long timestampNs) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer not allowed to be null");
        }
        if (rotation % 90 != 0) {
            throw new IllegalArgumentException("rotation must be a multiple of 90");
        }
        this.buffer = buffer;
        this.rotation = rotation;
        this.timestampNs = timestampNs;
    }

    @CalledByNative
    public Buffer getBuffer() {
        return buffer;
    }

    /**
     * Rotation of the frame in degrees.
     */
    @CalledByNative
    public int getRotation() {
        return rotation;
    }

    /**
     * Timestamp of the frame in nano seconds.
     */
    @CalledByNative
    public long getTimestampNs() {
        return timestampNs;
    }

    public int getRotatedWidth() {
        if (rotation % 180 == 0) {
            return buffer.getWidth();
        }
        return buffer.getHeight();
    }

    public int getRotatedHeight() {
        if (rotation % 180 == 0) {
            return buffer.getHeight();
        }
        return buffer.getWidth();
    }

    @Override
    public void retain() {
        buffer.retain();
    }

    @Override
    @CalledByNative
    public void release() {
        buffer.release();
    }
}
