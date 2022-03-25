package com.tencent.ncnnbodyseg.media;

import android.content.res.AssetManager;

public abstract class CameraNativeContext {
    public static final int IMAGE_FORMAT_RGBA = 0x01;
    public static final int IMAGE_FORMAT_NV21 = 0x02;
    public static final int IMAGE_FORMAT_NV12 = 0x03;
    public static final int IMAGE_FORMAT_I420 = 0x04;

    static {
        System.loadLibrary("video_matting");
    }

    private long mNativeContextHandle;

    protected native void native_CreateContext();

    protected native void native_DestroyContext();

    protected native int native_Init(AssetManager mgr);

    protected native int native_UnInit();

    protected native void native_OnPreviewFrame(int format, byte[] data, int width, int height, byte[] outputData);

    protected native void native_SetTransformMatrix(float translateX, float translateY, float scaleX, float scaleY, int degree, int mirror);

    protected native void native_OnSurfaceCreated();

    protected native void native_OnSurfaceChanged(int width, int height);

    protected native void native_OnDrawFrame();

    protected native void native_SetFilterData(int index, int format, int width, int height, byte[] bytes);

    protected native void native_SetFragShader(int index, String str);
}
