package ru.smityukh.tchart.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import ru.smityukh.tchart.R;
import ru.smityukh.tchart.data.ChartData;

class ChartPeriodView extends View {

    private static final String TAG = "ChartPeriodView";

    private int mLineWidth;
    private int mSetVerticalChartOffset;

    @Nullable
    private ChartsRender mChartsRender;
    @NonNull
    private SelectionRender mSelectionRender = new SelectionRender();

    @NonNull
    private final SelectionController mSelectionController = new SelectionController();
    @Nullable
    private ChartData mChartData;

    public ChartPeriodView(Context context) {
        super(context);

        init(context);
    }

    public ChartPeriodView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ChartPeriodView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mLineWidth = context.getResources().getDimensionPixelSize(R.dimen.period_selector_view_line_width);
        mSetVerticalChartOffset = context.getResources().getDimensionPixelSize(R.dimen.period_selector_view_vertical_chart_offset);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.e("FUCK", "onTouchEvent: " + event.toString());

//        if (mSelectionController.mGestureDetector.onTouchEvent(event)) {
//            //Log.e("FUCK", "onTouchEvent: handled");
//            return true;
//        }

        //Log.e("FUCK", "onTouchEvent: NOT handled");

        return true;//super.onTouchEvent(event);
    }

    void setData(@NonNull ChartData data) {
        mChartData = data;

        mChartsRender = new ChartsRender(data, this);
        mChartsRender.setLineWidth(mLineWidth);
        mChartsRender.setVerticalChartOffset(mSetVerticalChartOffset);
        mChartsRender.setViewSize(getWidth(), getHeight());

        mSelectionController.setViewSize(getWidth(), getHeight());
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);

        if (mChartsRender != null) {
            mChartsRender.setViewSize(width, height);
        }

        mSelectionController.setViewSize(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawCharts(canvas);

        mSelectionRender.draw(canvas);
    }

    private void drawCharts(@NonNull Canvas canvas) {
        if (mChartsRender == null) {
            return;
        }

        mChartsRender.render(canvas);
    }

    public void setChartVisibility(int position, boolean visible) {
        if (mChartsRender == null) {
            return;
        }

        mChartsRender.setChartVisibility(position, visible);
    }

    private static class ChartsRender {

        @NonNull
        private final ChartData mChartData;
        @NonNull
        private ChartPeriodView mView;

        private int mChartsCount;
        private int mColumnsCount;
        private int mLinesCount;

        private int mSetVerticalChartOffset;

        private Paint[] mChartPaints;

        private long[] mMinValue;
        private long[] mMaxValue;

        private boolean mChartVisible[];

        // Store the all lines of the chart as a sequence of (x1, y1, x2, y2)
        private float[] mLines;

        private int mViewWidth;
        private int mViewHeight;

        private float mYOffset;

        private boolean mHasDrawData;

        ChartsRender(@NonNull ChartData data, @NonNull ChartPeriodView view) {
            mChartData = data;
            mView = view;

            mChartsCount = data.mValues.length;
            if (mChartsCount == 0) {
                return;
            }

            mColumnsCount = data.mAxis.length;
            if (mColumnsCount == 0) {
                return;
            }
            mLinesCount = mColumnsCount - 1;

            // Full size lines buffer to avoid an unnecessary GC work
            mLines = new float[mLinesCount * mChartsCount * 4];

            mChartPaints = new Paint[mChartsCount];

            mMinValue = new long[mChartsCount];
            mMaxValue = new long[mChartsCount];

            mChartVisible = new boolean[mChartsCount];

            for (int chart = 0; chart < mChartsCount; chart++) {
                mChartPaints[chart] = createChartPaint(data.mColors[chart]);
                mChartVisible[chart] = true;

                long[] chartValues = data.mValues[chart];

                long minValue = chartValues[0];
                long maxValue = chartValues[0];

                for (int jndex = 1; jndex < mColumnsCount; jndex++) {
                    long value = chartValues[jndex];

                    if (minValue > value) {
                        minValue = value;
                    }

                    if (maxValue < value) {
                        maxValue = value;
                    }
                }

                mMinValue[chart] = minValue;
                mMaxValue[chart] = maxValue;
            }
        }

        void setLineWidth(int lineWidth) {
            for (int chart = 0; chart < mChartsCount; chart++) {
                mChartPaints[chart].setStrokeWidth(lineWidth);
            }
        }

        void setChartVisibility(int position, boolean visible) {
            if (position < 0 || position >= mChartsCount) {
                throw new IllegalArgumentException("Position is out of range");
            }

            if (mChartVisible[position] == visible) {
                return;
            }

            mChartVisible[position] = visible;

            // TODO: I have to implement incremental update here is not necessary to recalculate the whole data
            prepareDrawData();
        }

        void setVerticalChartOffset(int setVerticalChartOffset) {
            mSetVerticalChartOffset = setVerticalChartOffset;
            prepareDrawData();
        }

        void setViewSize(int width, int height) {
            if (mViewWidth == width && mViewHeight == height) {
                return;
            }

            mViewWidth = width;
            mViewHeight = height;

            prepareDrawData();
        }

        private void prepareDrawData() {
            if (mViewWidth <= 0 || mViewHeight <= 0) {
                mHasDrawData = false;
                mView.invalidate();
                return;
            }

            if (mColumnsCount <= 0) {
                mHasDrawData = false;
                mView.invalidate();
                return;
            }

            mHasDrawData = true;

            long minValue = getMinValue();
            long maxValue = getMaxValue();

            long range = maxValue - minValue;
            float yScale = ((float) mViewHeight - mSetVerticalChartOffset * 2) / range;
            float xStepSize = ((float) mViewWidth) / mLinesCount;

            mYOffset = maxValue * yScale + mSetVerticalChartOffset;

            int linePosition = 0;

            for (int chart = 0; chart < mChartsCount; chart++) {
                long[] values = mChartData.mValues[chart];
                float x = 0;

                // Extract  the first line to remove float a multiplication from cycle
                mLines[linePosition] = x;
                x += xStepSize;
                mLines[linePosition + 1] = values[0] * yScale;
                mLines[linePosition + 2] = x;
                mLines[linePosition + 3] = values[1] * yScale;

                linePosition += 4;

                for (int column = 1; column < mLinesCount; column++) {
                    mLines[linePosition] = x;
                    x += xStepSize;
                    mLines[linePosition + 1] = mLines[linePosition - 1];
                    mLines[linePosition + 2] = x;
                    mLines[linePosition + 3] = values[column + 1] * yScale;

                    linePosition += 4;
                }
            }

            mView.invalidate();
        }

        void render(@NonNull Canvas canvas) {
            if (!mHasDrawData) {
                return;
            }

            canvas.save();

            canvas.translate(0, mYOffset);
            canvas.scale(1, -1);

            int lineOffset = 0;
            for (int index = 0; index < mChartsCount; index++) {
                if (mChartVisible[index]) {
                    canvas.drawLines(mLines, lineOffset << 2, mLinesCount << 2, mChartPaints[index]);
                }

                lineOffset += mLinesCount;
            }

            canvas.restore();
        }

        private long getMinValue() {
            if (mChartsCount <= 0) {
                return 0;
            }

            long min = Long.MAX_VALUE;
            for (int i = 0; i < mChartsCount; i++) {
                if (!mChartVisible[i]) {
                    continue;
                }

                if (min > mMinValue[i]) {
                    min = mMinValue[i];
                }
            }

            return min == Long.MAX_VALUE ? 0 : min;
        }

        private long getMaxValue() {
            if (mChartsCount <= 0) {
                return 0;
            }

            long max = Long.MIN_VALUE;
            for (int i = 0; i < mChartsCount; i++) {
                if (!mChartVisible[i]) {
                    continue;
                }

                if (max < mMaxValue[i]) {
                    max = mMaxValue[i];
                }
            }

            return max == Long.MIN_VALUE ? 0 : max;
        }

        @NonNull
        private static Paint createChartPaint(int color) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);

            return paint;
        }
    }

    private class SelectionController {

        float mSelectionStart = 0.0f;
        float mSelectionEnd = 1.0f;

        private int mViewWidth;
        private int mViewHeight;

        private final int mFrameVerticalLineWidth;

        @NonNull
        private final Rect mStartBarRect = new Rect();
        @NonNull
        private final Rect mEndBarRect = new Rect();

        SelectionController() {
            Resources resources = getContext().getResources();

            mFrameVerticalLineWidth = resources.getDimensionPixelSize(R.dimen.period_selector_view_frame_vertical_line_width);
        }

        void setViewSize(int width, int height) {
            if (mViewWidth == width && mViewHeight == height) {
                return;
            }

            mViewWidth = width;
            mViewHeight = height;

            updatePixelData();
        }

        private void updatePixelData() {
            if (mViewWidth <= 0 || mViewHeight <= 0) {
                mStartBarRect.setEmpty();
                mEndBarRect.setEmpty();
                mSelectionRender.prepareDrawData(0, 0);
                return;
            }

            int startX;
            int endX;

            if (mSelectionController.mSelectionStart <= 0.01) {
                startX = 0;
            } else {
                startX = (int) (mViewWidth * mSelectionController.mSelectionStart);
            }

            if (mSelectionController.mSelectionEnd >= 0.99) {
                endX = mViewWidth;
            } else {
                endX = (int) (mViewWidth * mSelectionController.mSelectionEnd);
            }

            mStartBarRect.set(startX, 0, startX + mFrameVerticalLineWidth, mViewHeight);
            mEndBarRect.set(endX - mFrameVerticalLineWidth, 0, endX, mViewHeight);

            mSelectionRender.prepareDrawData(mViewWidth, mViewHeight);
        }
    }

    private class SelectionRender {
        private final int mFrameHorizontalLineHeight;

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

        SelectionRender() {
            Resources resources = getContext().getResources();

            mUnselectedPaint = new Paint();
            mUnselectedPaint.setColor(resources.getColor(R.color.colorUnselectedPeriodYashmak));

            mFramePaint = new Paint();
            mFramePaint.setColor(resources.getColor(R.color.colorSelectedPeriodFrame));

            mFrameHorizontalLineHeight = resources.getDimensionPixelSize(R.dimen.period_selector_view_frame_horizontal_line_height);
        }

        void prepareDrawData(int viewWidth, int viewHeight) {
            if (viewWidth <= 0 || viewHeight <= 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            if (mSelectionController.mStartBarRect.isEmpty() || mSelectionController.mEndBarRect.isEmpty()) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            if (mChartData == null || mChartData.mAxis.length == 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            mLeftFrameRect.set(mSelectionController.mStartBarRect);
            mRightFrameRect.set(mSelectionController.mEndBarRect);
            mTopFrameRect.set(mLeftFrameRect.right, 0, mRightFrameRect.left, mFrameHorizontalLineHeight);
            mBottomFrameRect.set(mLeftFrameRect.right, viewHeight - mFrameHorizontalLineHeight, mRightFrameRect.left, viewHeight);

            if (mSelectionController.mStartBarRect.left > 0) {
                mUnselectedHeadRect.set(0, 0, mLeftFrameRect.left - 1, viewHeight);
            } else {
                mUnselectedHeadRect.setEmpty();
            }

            if (mSelectionController.mEndBarRect.right < viewWidth) {
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
    }
}
