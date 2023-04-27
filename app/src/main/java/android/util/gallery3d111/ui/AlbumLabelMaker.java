/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util.gallery3d111.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.gallery3d111.data.DataSourceType;
import android.util.gallery3d111.util.ThreadPool;

import com.android.gallery3d111.R;
import com.android.photos.data.GalleryBitmapPool;

public class AlbumLabelMaker {
    private static final int BORDER_SIZE = 0;

    private final AlbumSetSlotRenderer.LabelSpec mSpec;
    private final TextPaint mTitlePaint;
    private final TextPaint mCountPaint;
    private final Context mContext;

    private int mLabelWidth;
    private int mBitmapWidth;
    private int mBitmapHeight;

    private final LazyLoadedBitmap mLocalSetIcon;
    private final LazyLoadedBitmap mPicasaIcon;
    private final LazyLoadedBitmap mCameraIcon;

    public AlbumLabelMaker(Context context, AlbumSetSlotRenderer.LabelSpec spec) {
        mContext = context;
        mSpec = spec;
        mTitlePaint = getTextPaint(spec.titleFontSize, spec.titleColor, false);
        mCountPaint = getTextPaint(spec.countFontSize, spec.countColor, false);

        mLocalSetIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_folder);
        mPicasaIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_picasa);
        mCameraIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_camera);
    }

    public static int getBorderSize() {
        return BORDER_SIZE;
    }

    private Bitmap getOverlayAlbumIcon(int sourceType) {
        switch (sourceType) {
            case DataSourceType.TYPE_CAMERA:
                return mCameraIcon.get();
            case DataSourceType.TYPE_LOCAL:
                return mLocalSetIcon.get();
            case DataSourceType.TYPE_PICASA:
                return mPicasaIcon.get();
        }
        return null;
    }

    private static TextPaint getTextPaint(int textSize, int color, boolean isBold) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        //paint.setShadowLayer(2f, 0f, 0f, Color.LTGRAY);
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }
        return paint;
    }

    private class LazyLoadedBitmap {
        private Bitmap mBitmap;
        private int mResId;

        public LazyLoadedBitmap(int resId) {
            mResId = resId;
        }

        public synchronized Bitmap get() {
            if (mBitmap == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Config.ARGB_8888;
                mBitmap = BitmapFactory.decodeResource(
                        mContext.getResources(), mResId, options);
            }
            return mBitmap;
        }
    }

    public synchronized void setLabelWidth(int width) {
        if (mLabelWidth == width) return;
        mLabelWidth = width;
        int borders = 2 * BORDER_SIZE;
        mBitmapWidth = width + borders;
        mBitmapHeight = mSpec.labelBackgroundHeight + borders;
    }

    public ThreadPool.Job<Bitmap> requestLabel(
            String title, String count, int sourceType) {
        return new AlbumLabelJob(title, count, sourceType);
    }

    static void drawText(Canvas canvas,
            int x, int y, String text, int lengthLimit, TextPaint p) {
        // The TextPaint cannot be used concurrently
        synchronized (p) {
            text = TextUtils.ellipsize(
                    text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
        }
    }

    private class AlbumLabelJob implements ThreadPool.Job<Bitmap> {
        private final String mTitle;
        private final String mCount;
        private final int mSourceType;

        public AlbumLabelJob(String title, String count, int sourceType) {
            mTitle = title;
            mCount = count;
            mSourceType = sourceType;
        }

        @Override
        public Bitmap run(ThreadPool.JobContext jc) {
            AlbumSetSlotRenderer.LabelSpec s = mSpec;

            String title = mTitle;
            String count = mCount;
            Bitmap icon = getOverlayAlbumIcon(mSourceType);

            Bitmap bitmap;
            int labelWidth;

            //内存池里面选择，防止过度消耗
            synchronized (this) {
                labelWidth = mLabelWidth;
                bitmap = GalleryBitmapPool.getInstance().get(mBitmapWidth, mBitmapHeight);
            }

            //bitmap本质上就是一块内存
            if (bitmap == null) {
                int borders = 2 * BORDER_SIZE;
                bitmap = Bitmap.createBitmap(labelWidth + borders,
                        s.labelBackgroundHeight + borders, Config.ARGB_8888);
            }

            //画布生成：将bitmap传入，new了一个Canvas实例cancas。
            Canvas canvas = new Canvas(bitmap);
            canvas.clipRect(BORDER_SIZE, BORDER_SIZE,
                    bitmap.getWidth() - BORDER_SIZE,
                    bitmap.getHeight() - BORDER_SIZE);
            canvas.drawColor(mSpec.backgroundColor, PorterDuff.Mode.SRC);

            canvas.translate(BORDER_SIZE, BORDER_SIZE);

            // draw title
            if (jc.isCancelled()) return null;
            int x = s.leftMargin + s.iconSize;
            // TODO: is the offset relevant in new reskin?
            // int y = s.titleOffset;
            int y = (s.labelBackgroundHeight - s.titleFontSize) / 2;
            drawText(canvas, x, y, title, labelWidth - s.leftMargin - x - 
                    s.titleRightMargin, mTitlePaint);

            // draw count
            if (jc.isCancelled()) return null;
            x = labelWidth - s.titleRightMargin;
            y = (s.labelBackgroundHeight - s.countFontSize) / 2;
            drawText(canvas, x, y, count,
                    labelWidth - x , mCountPaint);

            // draw the icon
            if (icon != null) {
                if (jc.isCancelled()) return null;
                float scale = (float) s.iconSize / icon.getWidth();
                canvas.translate(s.leftMargin, (s.labelBackgroundHeight -
                        Math.round(scale * icon.getHeight()))/2f);
                canvas.scale(scale, scale);
                //框架层的drawBitmap
                canvas.drawBitmap(icon, 0, 0, null);
            }
            return bitmap;
            //返回一个bitmap图像，在这里进行绘制。
            //绘制：把一块内存写入了一些图像数据。
        }
        //上层应用上的绘制实际上就是指：把一块内存写了一些图像数据进去。
        //bitmap：可以看作是创建了一块内存
        //画布canvas：是把bitmap传进去，创建了一个canvas实例。
    }

    public void recycleLabel(Bitmap label) {
        GalleryBitmapPool.getInstance().put(label);
    }
}
