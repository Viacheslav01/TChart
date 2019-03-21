package ru.smityukh.tchart.view;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.view.View;
import ru.smityukh.tchart.R;

class PeriodSelectionFrameRender {

    private final int mFrameHorizontalLineHeight;

    @NonNull
    private View mView;
    @NonNull
    private final Paint mUnselectedPaint;
    @NonNull
    private final Paint mFramePaint;

    @NonNull
    private final Rect mUnselectedHeadRect = new Rect();
    @NonNull
    private final Rect mUnselectedTailRect = new Rect();

    @NonNull
    private final Rect mLeftFrameRect = new Rect();
    @NonNull
    private final Rect mTopFrameRect = new Rect();
    @NonNull
    private final Rect mRightFrameRect = new Rect();
    @NonNull
    private final Rect mBottomFrameRect = new Rect();

    private boolean mHasDrawData;

    PeriodSelectionFrameRender(@NonNull View view) {
        mView = view;
        Resources resources = view.getContext().getResources();

        mUnselectedPaint = new Paint();
        mUnselectedPaint.setColor(resources.getColor(R.color.colorUnselectedPeriodYashmak));

        mFramePaint = new Paint();
        mFramePaint.setColor(resources.getColor(R.color.colorSelectedPeriodFrame));

        mFrameHorizontalLineHeight = resources.getDimensionPixelSize(R.dimen.period_selector_view_frame_horizontal_line_height);
    }

    void prepareDrawData(int viewWidth, int viewHeight, @NonNull Rect startBarRect, @NonNull Rect endBarRect) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            mHasDrawData = false;
            invalidate();
            return;
        }

        if (startBarRect.isEmpty() || endBarRect.isEmpty()) {
            mHasDrawData = false;
            invalidate();
            return;
        }

        mLeftFrameRect.set(startBarRect);
        mRightFrameRect.set(endBarRect);
        mTopFrameRect.set(mLeftFrameRect.right, 0, mRightFrameRect.left, mFrameHorizontalLineHeight);
        mBottomFrameRect.set(mLeftFrameRect.right, viewHeight - mFrameHorizontalLineHeight, mRightFrameRect.left, viewHeight);

        if (startBarRect.left > 0) {
            mUnselectedHeadRect.set(0, 0, mLeftFrameRect.left - 1, viewHeight);
        } else {
            mUnselectedHeadRect.setEmpty();
        }

        if (endBarRect.right < viewWidth) {
            mUnselectedTailRect.set(mRightFrameRect.right + 1, 0, viewWidth, viewHeight);
        } else {
            mUnselectedTailRect.setEmpty();
        }

        mHasDrawData = true;
        invalidate();
    }

    void draw(@NonNull Canvas canvas) {
        if (!mHasDrawData) {
            return;
        }

        // Draw frame
        canvas.drawRect(mLeftFrameRect, mFramePaint);
        canvas.drawRect(mTopFrameRect, mFramePaint);
        canvas.drawRect(mRightFrameRect, mFramePaint);
        canvas.drawRect(mBottomFrameRect, mFramePaint);

        // Draw yashmak
        if (!mUnselectedHeadRect.isEmpty()) {
            canvas.drawRect(mUnselectedHeadRect, mUnselectedPaint);
        }

        if (!mUnselectedTailRect.isEmpty()) {
            canvas.drawRect(mUnselectedTailRect, mUnselectedPaint);
        }
    }

    private void invalidate() {
        mView.invalidate();
    }
}
