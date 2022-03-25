package com.tencent.ncnnbodyseg.media;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.util.Log;

import com.tencent.ncnnbodyseg.R;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyNativeContext extends CameraNativeContext implements GLSurfaceView.Renderer{
    private static final String TAG = "MyNativeContext";
    private GLSurfaceView mGLSurfaceView;
    private int mOESTextureId;
    private Context mContext;


    public MyNativeContext() {

    }

    public void init(Context context, AssetManager mgr, GLSurfaceView surfaceView) {
        mContext = context;
        mGLSurfaceView = surfaceView;
        mGLSurfaceView.setEGLContextClientVersion(2);
        mGLSurfaceView.setRenderer(this);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        native_CreateContext();
        native_Init(mgr);
    }

    public void unInit() {
        native_UnInit();
        native_DestroyContext();
    }

    public void requestRender() {
        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    public void setTransformMatrix(int degree, int mirror) {
        Log.d(TAG, "setTransformMatrix() called with: degree = [" + degree + "], mirror = [" + mirror + "]");
        native_SetTransformMatrix(0, 0, 1, 1, degree, mirror);
    }

    private byte[] mSegOutputData = null; //用于保存输出的分割结果 YUV（I420）格式
    private ByteBuffer yBuffer = null, uBuffer = null, vBuffer = null;

    public void onPreviewFrame(int format, byte[] data, int width, int height) {
        Log.d(TAG, "onPreviewFrame() called with: data = [" + data + "], width = [" + width + "], height = [" + height + "]");
        if(mSegOutputData == null) {
            mSegOutputData = new byte[width * height * 3 / 2]; //用于保存输出的分割结果 YUV（I420）格式
        }
        native_OnPreviewFrame(format, data, width, height, mSegOutputData);
        //分割结果传回 Java 层，保存至 /sdcard/IMG_SegResult_480x640.I420 准备推流
        //saveBytes2File(mSegOutputData, String.format("IMG_SegResult_%dx%d.I420", height, width));
        if(yBuffer == null) {
            yBuffer = ByteBuffer.allocate(width * height);
            uBuffer = ByteBuffer.allocate(width * height / 4);
            vBuffer = ByteBuffer.allocate(width * height / 4);
        }
        yBuffer = ByteBuffer.wrap(getSubBytes(mSegOutputData, 0, width * height));
        yBuffer.rewind();

        uBuffer = ByteBuffer.wrap(getSubBytes(mSegOutputData, width * height, width * height / 4));
        uBuffer.rewind();

        vBuffer = ByteBuffer.wrap(getSubBytes(mSegOutputData, width * height * 5 / 4, width * height / 4));
        vBuffer.rewind();

//        检查数据有没有问题
//        byte[] arr = new byte[uBuffer.remaining()];
//        uBuffer.get(arr);
//        saveBytes2File(arr, String.format("IMG_SegResult_%dx%d_U.Gray", height / 2, width / 2));
//
//        arr = new byte[vBuffer.remaining()];
//        vBuffer.get(arr);
//        saveBytes2File(arr, String.format("IMG_SegResult_%dx%d_V.Gray", height / 2, width / 2));

        //推流
        pushFramePlane(yBuffer, height, uBuffer, height / 2, vBuffer, height / 2, 1);
    }

    public void pushFramePlane(ByteBuffer yBuffer, int yRowStride, ByteBuffer uBuffer, int uRawStride, ByteBuffer vBuffer, int vRawStride, long uPixelStride) {

    }

    public byte[] getSubBytes(byte[] bytes, int offset, int size) {
        byte[] subBytes = new byte[size];
        System.arraycopy(bytes, offset, subBytes, 0, size);
        return subBytes;
    }

    public void loadShaderFromAssetsFile(int shaderIndex, Resources r) {
        String result = null;
        try {
            InputStream in = r.getAssets().open("shaders/fshader_" + shaderIndex + ".glsl");
            int ch = 0;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((ch = in.read()) != -1) {
                baos.write(ch);
            }
            byte[] buff = baos.toByteArray();
            baos.close();
            in.close();
            result = new String(buff, "UTF-8");
            result = result.replaceAll("\\r\\n", "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (result != null) {
            setFragShader(shaderIndex, result);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated() called with: gl = [" + gl + "], config = [" + config + "]");
        mock();
        native_OnSurfaceCreated();

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged() called with: gl = [" + gl + "], width = [" + width + "], height = [" + height + "]");
        native_OnSurfaceChanged(width, height);

    }

    @Override
    public void onDrawFrame(GL10 gl) {
        Log.d(TAG, "onDrawFrame() called with: gl = [" + gl + "]");
        native_OnDrawFrame();
    }

    public void setFilterData(int index, int format, int width, int height, byte[] bytes) {
        native_SetFilterData(index, format, width, height, bytes);
    }

    public void setFragShader(int index, String str) {
        native_SetFragShader(index, str);
    }

    private void createOESTexture() {
        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        mOESTextureId = textureIds[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mOESTextureId);
        //环绕（超出纹理坐标范围）  （s==x t==y GL_REPEAT 重复）
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        //过滤（纹理像素映射到坐标点）  （缩小、放大：GL_LINEAR线性）
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexImage2D(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0, GLES11Ext.GL_RGB8_OES, 640, 480,
        0, GLES11Ext.GL_RGB8_OES, GLES20.GL_UNSIGNED_BYTE, null);
        // 解绑扩展纹理
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    private void saveBytes2File(byte[] bytes, String fileName) {
        File file = new File(Environment.getExternalStorageDirectory(), fileName);
        FileOutputStream outputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            outputStream = new FileOutputStream(file);
            bufferedOutputStream = new BufferedOutputStream(outputStream);
            bufferedOutputStream.write(bytes);
            bufferedOutputStream.flush();
        } catch (Exception e) {
            // 打印异常信息
            e.printStackTrace();
        } finally {
            // 关闭创建的流对象
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bufferedOutputStream != null) {
                try {
                    bufferedOutputStream.close();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    /**
     * 模拟你那边从纹理中读取图像，做分割，最后获取分割结果推流的过程
     * */
    void mock() {
        createOESTexture();

        int width = 640, height = 480; //默认相机预览宽高 640x480
        if(mSegOutputData == null) {
            mSegOutputData = new byte[width * height * 3 / 2]; //用于保存输出的分割结果 YUV（I420）格式
        }

        native_ReadDataFromTextureId(mOESTextureId, 640, 480, mSegOutputData);

        if(yBuffer == null) {
            yBuffer = ByteBuffer.allocate(width * height);
            uBuffer = ByteBuffer.allocate(width * height / 4);
            vBuffer = ByteBuffer.allocate(width * height / 4);
        }
        yBuffer = ByteBuffer.wrap(getSubBytes(mSegOutputData, 0, width * height));
        yBuffer.rewind();

        uBuffer = ByteBuffer.wrap(getSubBytes(mSegOutputData, width * height, width * height / 4));
        uBuffer.rewind();

        vBuffer = ByteBuffer.wrap(getSubBytes(mSegOutputData, width * height * 5 / 4, width * height / 4));
        vBuffer.rewind();

//        检查数据有没有问题
//        byte[] arr = new byte[uBuffer.remaining()];
//        uBuffer.get(arr);
//        saveBytes2File(arr, String.format("IMG_SegResult_%dx%d_U.Gray", height / 2, width / 2));
//
//        arr = new byte[vBuffer.remaining()];
//        vBuffer.get(arr);
//        saveBytes2File(arr, String.format("IMG_SegResult_%dx%d_V.Gray", height / 2, width / 2));

        //推流
        pushFramePlane(yBuffer, height, uBuffer, height / 2, vBuffer, height / 2, 1);
    }
}