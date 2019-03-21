package ru.smityukh.tchart.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import android.view.ViewConfiguration;
import ru.smityukh.tchart.R;
import ru.smityukh.tchart.data.ChartData;

import java.security.InvalidParameterException;

class ChartPeriodView extends View {

    private static final String TAG = "ChartPeriodView";

    private int mLineWidth;
    private int mSetVerticalChartOffset;

    @Nullable
    private ChartsRender mChartsRender;
    @NonNull
    private final PeriodSelectionFrameRender mPeriodSelectionFrameRender = new PeriodSelectionFrameRender(this);

    @NonNull
    private final SelectionController mSelectionController;
    @Nullable
    private ChartData mChartData;

    public ChartPeriodView(Context context) {
        this(context, null, 0);
    }

    public ChartPeriodView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartPeriodView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLineWidth = context.getResources().getDimensionPixelSize(R.dimen.period_selector_view_line_width);
        mSetVerticalChartOffset = context.getResources().getDimensionPixelSize(R.dimen.period_selector_view_vertical_chart_offset);

        mSelectionController = new SelectionController(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mSelectionController.onTouchEvent(event);
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

        mPeriodSelectionFrameRender.draw(canvas);
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

        float mSelectionStart = .45f;
        float mSelectionEnd = .9f;

        private int mViewWidth;
        private int mViewHeight;

        private final int mTouchSlop;
        private final int mFrameVerticalLineWidth;

        @NonNull
        private final Rect mStartBarRect = new Rect();
        @NonNull
        private final Rect mEndBarRect = new Rect();

        @NonNull
        private final Rect mStartBarHitTestRect = new Rect();
        @NonNull
        private final Rect mEndBarHitTestRect = new Rect();

        SelectionController(@NonNull Context context) {
            ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
            mTouchSlop = viewConfiguration.getScaledTouchSlop();

            mFrameVerticalLineWidth = context.getResources().getDimensionPixelSize(R.dimen.period_selector_view_frame_vertical_line_width);
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

                fillHitTest(mStartBarRect, mStartBarHitTestRect);
                fillHitTest(mEndBarRect, mEndBarHitTestRect);

                mPeriodSelectionFrameRender.prepareDrawData(0, 0, mStartBarRect, mEndBarRect);
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

            fillHitTest(mStartBarRect, mStartBarHitTestRect);
            fillHitTest(mEndBarRect, mEndBarHitTestRect);

            // TODO: I have to decide is a safe copy of the rect required here or not.
            mPeriodSelectionFrameRender.prepareDrawData(mViewWidth, mViewHeight, mStartBarRect, mEndBarRect);
        }

        private void fillHitTest(@NonNull Rect sourceRect, @NonNull Rect hitTestRect) {
            if (sourceRect.isEmpty()) {
                hitTestRect.setEmpty();
                return;
            }

            hitTestRect.set(
                    Math.max(sourceRect.left - mFrameVerticalLineWidth, 0),
                    sourceRect.top,
                    Math.min(sourceRect.right + mFrameVerticalLineWidth, mViewWidth),
                    sourceRect.bottom);
        }

        private TouchState mTouchState = new NoTouchState();

        boolean onTouchEvent(@NonNull MotionEvent event) {
            return mTouchState.onTouchEvent(event);
        }

        private abstract class TouchState {
            abstract boolean onTouchEvent(@NonNull MotionEvent event);
        }

        private class NoTouchState extends TouchState {
            @Override
            boolean onTouchEvent(@NonNull MotionEvent event) {
                if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    return false;
                }

                Log.e("FUCK", "" + event.getSize());

                float x = event.getX();
                float y = event.getY();

                if (mStartBarHitTestRect.contains((int) x, (int) y)) {
                    mTouchState = new StartSeparatorDownState(event);
                    return true;
                }

                if (mEndBarHitTestRect.contains((int) x, (int) y)) {
                    mTouchState = new EndSeparatorDownState(event);
                    return true;
                }

                return false;
            }
        }

        private abstract class XScrollState extends TouchState {

            @NonNull
            private final MotionEvent mDownEvent;
            @Nullable
            private MotionEvent mLastMotionEvent;

            private boolean mInScroll;

            XScrollState(@NonNull MotionEvent downEvent) {
                if (downEvent.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    throw new InvalidParameterException("downEvent is not a down one");
                }

                mDownEvent = MotionEvent.obtain(downEvent);
            }

            private void releaseEvents() {
                mDownEvent.recycle();
                if (mLastMotionEvent != null) {
                    mLastMotionEvent.recycle();
                }
            }

            @Override
            boolean onTouchEvent(@NonNull MotionEvent event) {
                int action = event.getActionMasked();

                Log.w("FUCK", "move: " + event);

                switch (action) {
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        releaseEvents();
                        mTouchState = new NoTouchState();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        handleMove(event);
                        return true;
                }

                return false;
            }

            private void handleMove(@NonNull MotionEvent event) {
                int downPointerIndex = mDownEvent.getActionIndex();
                int downPointerId = mDownEvent.getPointerId(downPointerIndex);

                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);

                if (pointerId != downPointerId) {
                    return;
                }

                float x = event.getX(pointerIndex);
                float downX = mDownEvent.getX(downPointerIndex);

                float totalX = x - downX;
                if (!mInScroll) {
                    mInScroll = Math.abs(totalX) >= mTouchSlop;
                }

                if (!mInScroll) {
                    return;
                }

                float deltaX = totalX;

                if (mLastMotionEvent != null) {
                    float lastMotionX = mLastMotionEvent.getX(mLastMotionEvent.getActionIndex());
                    deltaX = x - lastMotionX;
                }

                if (Math.abs(deltaX) < 1) {
                    return;
                }

                if (mLastMotionEvent != null) {
                    mLastMotionEvent.recycle();
                }

                mLastMotionEvent = MotionEvent.obtain(event);

                onScroll(x, deltaX, totalX);
            }

            abstract void onScroll(float x, float deltaX, float totalX);
        }

        private class StartSeparatorDownState extends XScrollState {

            private final Rect mOriginalRect;
            private final float mMaxX;

            StartSeparatorDownState(@NonNull MotionEvent downEvent) {
                super(downEvent);

                mOriginalRect = new Rect(mStartBarRect);
                mMaxX = mEndBarRect.left - mEndBarRect.width() * 5;
            }

            @Override
            void onScroll(float x, float deltaX, float totalX) {
                if (mViewWidth <= 0) {
                    return;
                }

                float newX = Math.min(mOriginalRect.left + totalX, mMaxX);

                float newStart = newX / mViewWidth;
                if (Math.abs(mSelectionStart - newStart) < 0.01) {
                    return;
                }

                mSelectionStart = newStart;
                updatePixelData();
            }
        }

        private class EndSeparatorDownState extends XScrollState {

            private final Rect mOriginalRect;
            private final float mMinX;

            EndSeparatorDownState(@NonNull MotionEvent downEvent) {
                super(downEvent);

                mOriginalRect = new Rect(mEndBarRect);
                mMinX = mStartBarRect.right + mStartBarRect.width() * 5;
            }

            @Override
            void onScroll(float x, float deltaX, float totalX) {
                if (mViewWidth <= 0) {
                    return;
                }

                float newX = Math.max(mOriginalRect.right + totalX, mMinX);

                float newEnd = newX / mViewWidth;
                if (Math.abs(mSelectionEnd - newEnd) < 0.01) {
                    return;
                }

                mSelectionEnd = newEnd;
                updatePixelData();
            }
        }
    }
}
