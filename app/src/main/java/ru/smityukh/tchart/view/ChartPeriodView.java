package ru.smityukh.tchart.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.math.MathUtils;
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
    private PeriodChartsRender mChartsRender;
    @NonNull
    private final PeriodSelectionFrameRender mPeriodSelectionFrameRender = new PeriodSelectionFrameRender(this);

    @NonNull
    private final SelectionController mSelectionController;
    @Nullable
    private ChartData mChartData;
    @Nullable
    private OnSelectionChangedCallback mSelectionChangedCallback;

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

    public void setOnSelectionChangedCallback(@Nullable OnSelectionChangedCallback callback) {
        mSelectionChangedCallback = callback;
    }

    private void notifySelectionChanged() {
        if (mSelectionChangedCallback != null) {
            mSelectionChangedCallback.onSelectionChanged(mSelectionController.getStart(), mSelectionController.getEnd());
        }
    }

    void setData(@NonNull ChartData data) {
        mChartData = data;

        mChartsRender = new PeriodChartsRender(data, this);
        mChartsRender.setLineWidth(mLineWidth);
        mChartsRender.setVerticalChartOffset(mSetVerticalChartOffset);
        mChartsRender.setViewSize(getWidth(), getHeight());

        mSelectionController.setSelection(0f, 1f);
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

    private class SelectionController {

        private static final double MIN_SELECTION_CHANGE_STEP = 0.001;

        float mSelectionStartV = 0;
        float mSelectionEndV = 1f;

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
        @NonNull
        private final Rect mSelectedHitTestRect = new Rect();

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

        private void setSelection(float start, float end) {
            start = MathUtils.clamp(start, 0.0f, 1.0f);
            end = MathUtils.clamp(end, 0.0f, 1.0f);

            if (end < start) {
                end = start;
            }

            if (Float.compare(mSelectionStartV, start) == 0 && Float.compare(mSelectionEndV, end) == 0) {
                return;
            }

            mSelectionStartV = start;
            mSelectionEndV = end;

            notifySelectionChanged();
            updatePixelData();
        }

        private void setSelectionStart(float start) {
            start = MathUtils.clamp(start, 0.0f, 1.0f);

            if (mSelectionEndV < start) {
                start = mSelectionEndV;
            }

            if (Float.compare(mSelectionStartV, start) == 0) {
                return;
            }

            mSelectionStartV = start;

            notifySelectionChanged();
            updatePixelData();
        }

        private void setSelectionEnd(float end) {
            end = MathUtils.clamp(end, 0.0f, 1.0f);

            if (end < mSelectionStartV) {
                end = mSelectionStartV;
            }

            if (Float.compare(mSelectionEndV, end) == 0) {
                return;
            }

            mSelectionEndV = end;

            notifySelectionChanged();
            updatePixelData();
        }

        private void updatePixelData() {
            if (mViewWidth <= 0 || mViewHeight <= 0) {
                mStartBarRect.setEmpty();
                mEndBarRect.setEmpty();

                fillBarHitTest(mStartBarRect, mStartBarHitTestRect);
                fillBarHitTest(mEndBarRect, mEndBarHitTestRect);
                mSelectedHitTestRect.setEmpty();

                mPeriodSelectionFrameRender.prepareDrawData(0, 0, mStartBarRect, mEndBarRect);
                return;
            }

            int startX;
            int endX;

            if (mSelectionController.getStart() <= 0.01) {
                startX = 0;
            } else {
                startX = (int) (mViewWidth * mSelectionController.getStart());
            }

            if (mSelectionController.getEnd() >= 0.99) {
                endX = mViewWidth;
            } else {
                endX = (int) (mViewWidth * mSelectionController.getEnd());
            }

            mStartBarRect.set(startX, 0, startX + mFrameVerticalLineWidth, mViewHeight);
            mEndBarRect.set(endX - mFrameVerticalLineWidth, 0, endX, mViewHeight);

            fillBarHitTest(mStartBarRect, mStartBarHitTestRect);
            fillBarHitTest(mEndBarRect, mEndBarHitTestRect);
            mSelectedHitTestRect.set(mStartBarHitTestRect.right, mStartBarHitTestRect.top, mEndBarHitTestRect.left, mEndBarHitTestRect.bottom);

            // TODO: I have to decide is a safe copy of the rect required here or not.
            mPeriodSelectionFrameRender.prepareDrawData(mViewWidth, mViewHeight, mStartBarRect, mEndBarRect);
        }

        private void fillBarHitTest(@NonNull Rect sourceRect, @NonNull Rect hitTestRect) {
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

        float getStart() {
            return mSelectionStartV;
        }

        float getEnd() {
            return mSelectionEndV;
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

                if (mSelectedHitTestRect.contains((int) x, (int) y)) {
                    if (mSelectionStartV >= 0.01 || mSelectionEndV <= 0.99) {
                        mTouchState = new SelectedRegionDownState(event);
                        return true;
                    }
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
                if (Math.abs(mSelectionStartV - newStart) < MIN_SELECTION_CHANGE_STEP) {
                    return;
                }

                setSelectionStart(newStart);
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
                if (Math.abs(mSelectionEndV - newEnd) < MIN_SELECTION_CHANGE_STEP) {
                    return;
                }

                setSelectionEnd(newEnd);
            }
        }

        private class SelectedRegionDownState extends XScrollState {

            private final Rect mOriginalStartRect;
            private final Rect mOriginalEndRect;
            private final float mSelectionLenght;

            SelectedRegionDownState(@NonNull MotionEvent downEvent) {
                super(downEvent);

                mOriginalStartRect = new Rect(mStartBarRect);
                mOriginalEndRect = new Rect(mEndBarRect);

                mSelectionLenght = mSelectionEndV - mSelectionStartV;
            }

            @Override
            void onScroll(float x, float deltaX, float totalX) {
                if (mViewWidth <= 0) {
                    return;
                }

                float startX = mOriginalStartRect.left + totalX;
                float endX = mOriginalEndRect.right + totalX;

                float start;
                float end;

                if (startX <= 0) {
                    start = 0.0f;
                    end = mSelectionLenght;
                } else if (endX >= mViewWidth) {
                    end = 1.0f;
                    start = 1.0f - mSelectionLenght;
                } else {
                    start = startX / mViewWidth;
                    end = start + mSelectionLenght;
                }

                if (Math.abs(mSelectionStartV - start) < MIN_SELECTION_CHANGE_STEP) {
                    return;
                }

                setSelection(start, end);
            }
        }
    }

    public interface OnSelectionChangedCallback {
        void onSelectionChanged(float start, float end);
    }
}
