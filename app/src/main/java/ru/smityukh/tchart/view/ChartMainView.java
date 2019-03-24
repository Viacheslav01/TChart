package ru.smityukh.tchart.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import ru.smityukh.tchart.R;
import ru.smityukh.tchart.animation.FloatAnimationWrapper;
import ru.smityukh.tchart.data.ChartData;

import java.text.SimpleDateFormat;
import java.util.*;

class ChartMainView extends View {

    private static final double MIN_SELECTION_CHANGE_STEP = 0.001;
    private static final long ANIMATION_DURATION_MS = 250;

    @Nullable
    private ChartData mChartData;

    private Rect mTmpRect = new Rect();

    @NonNull
    private AxisRender mAxisRender;
    @NonNull
    private ChartsRender mChartsRender;
    @NonNull
    private RulersRender mRulersRender;
    @NonNull
    private SelectionRender mSelectionRender;

    private float mVisibleColumns;
    private float mPixelPerColumn;

    private int mFirstVisibleColumn;
    private int mLastVisibleColumn;

    private float mSelectionStart = 0.0f;
    private float mSelectionEnd = 1.0f;
    private float mSelectionLength = 1.0f;

    private boolean[] mChartsVisibility;

    private float mOffsetX;
    private float[] mColumnPositions;
    private int mTopPadding;

    private float mLastMinValue;
    private float mLastMaxValue;

    private int mSelectedColumn = -1;

    @Nullable
    private RangeAnimation mRangeAnimation;

    public ChartMainView(Context context) {
        this(context, null, 0);
    }

    public ChartMainView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartMainView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        Resources resources = context.getResources();
        mTopPadding = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_top_padding);

        mAxisRender = new AxisRender(context);
        mChartsRender = new ChartsRender(context);
        mRulersRender = new RulersRender(context);
        mSelectionRender = new SelectionRender(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                updateSelectedColumn(event.getX() + mOffsetX);
                return true;
            case MotionEvent.ACTION_MOVE:
                updateSelectedColumn(event.getX() + mOffsetX);
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateSelectedColumn(float x) {
        if (mColumnPositions == null || mColumnPositions.length < 2) {
            return;
        }

        int selectedColumn = -1;

        // TODO: Replace by binary search
        for (int columnIndex = 0; columnIndex < mColumnPositions.length - 1; columnIndex++) {
            if (mColumnPositions[columnIndex] <= x && mColumnPositions[columnIndex + 1] >= x) {
                if (Math.abs(x - mColumnPositions[columnIndex]) <= Math.abs(x - mColumnPositions[columnIndex + 1])) {
                    selectedColumn = columnIndex;
                } else {
                    selectedColumn = columnIndex + 1;
                }

                break;
            }
        }

        mSelectedColumn = selectedColumn;
        mSelectionRender.prepareDraw(mSelectedColumn, getMinValue(), getMaxValue());
    }

    public void setChartData(@NonNull ChartData data) {
        mChartData = data;

        mChartsVisibility = new boolean[mChartData.mValues.length];
        for (int chartIndex = 0; chartIndex < mChartsVisibility.length; chartIndex++) {
            mChartsVisibility[chartIndex] = true;
        }

        mChartsRender.setData(data);
        mSelectionRender.setData(data);

        int width = getWidth();
        int height = getHeight();

        mAxisRender.setViewHeight(height);
        mChartsRender.setViewPort(mTopPadding, width, height - mAxisRender.mAxisHeight - mTopPadding);
        mRulersRender.setViewPort(mTopPadding, width, height - mAxisRender.mAxisHeight - mTopPadding);
        mSelectionRender.setViewPort(mTopPadding, width, height - mAxisRender.mAxisHeight - mTopPadding);

        mSelectionStart = -1f;
        mSelectionEnd = -1f;
        mSelectionLength = -1f;

        setSelection(0f, 1f);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);

        mAxisRender.setViewHeight(height);
        mChartsRender.setViewPort(mTopPadding, width, height - mAxisRender.mAxisHeight - mTopPadding);
        mRulersRender.setViewPort(mTopPadding, width, height - mAxisRender.mAxisHeight - mTopPadding);
        mSelectionRender.setViewPort(mTopPadding, width, height - mAxisRender.mAxisHeight - mTopPadding);

        onSelectionLengthChanged();
    }

    public void setSelection(float start, float end) {
        if (Float.compare(mSelectionStart, start) == 0 && Float.compare(mSelectionEnd, end) == 0) {
            return;
        }

        if (mChartData == null) {
            return;
        }

        mSelectionStart = start;
        mSelectionEnd = end;

        float length = end - start;
        if (Math.abs(mSelectionLength - length) >= MIN_SELECTION_CHANGE_STEP) {
            mSelectionLength = length;
            onSelectionLengthChanged();
        } else {
            updateVisibleColumnsInfo();

            mSelectedColumn = -1;

            RangeAnimation animation = createRangeAnimation();
            if (animation != null) {
                animation.start();
            }
        }

        invalidate();
    }

    private void onSelectionLengthChanged() {
        if (mChartData == null) {
            return;
        }

        if (getWidth() <= 0) {
            return;
        }

        updateVisibleColumnsInfo();

        float columnPosition = 0.0f;
        mColumnPositions = new float[mChartData.mAxis.length];
        for (int column = 0; column < mChartData.mAxis.length; column++) {
            mColumnPositions[column] = columnPosition;
            columnPosition += mPixelPerColumn;
        }

        mAxisRender.updateDrawData(mVisibleColumns, mPixelPerColumn);

        mSelectedColumn = -1;

        RangeAnimation animation = createRangeAnimation();
        if (animation != null) {
            animation.start();
        }

        invalidate();
    }

    private void updateVisibleColumnsInfo() {
        if (mChartData == null || getWidth() <= 0) {
            return;
        }

        mVisibleColumns = mChartData.mAxis.length * mSelectionLength;
        mPixelPerColumn = getWidth() / mVisibleColumns;

        mFirstVisibleColumn = (int) Math.ceil(mChartData.mAxis.length * mSelectionStart);
        mLastVisibleColumn = (int) Math.ceil(mFirstVisibleColumn + mVisibleColumns);
        mLastVisibleColumn = Math.min(mLastVisibleColumn, mChartData.mAxis.length - 1);

        mOffsetX = mPixelPerColumn * mChartData.mAxis.length * mSelectionStart;
    }

    private long getMinValue() {
        if (mChartData == null) {
            return 0;
        }

        return 0;
        // Disabled in case to show 0 line everywhere
//        long minValue = Long.MAX_VALUE;
//        for (int chartIndex = 0; chartIndex < mChartData.mValues.length; chartIndex++) {
//            if (!mChartsVisibility[chartIndex]) {
//                continue;
//            }
//
//            long[] values = mChartData.mValues[chartIndex];
//
//            for (int column = mFirstVisibleColumn; column <= mLastVisibleColumn; column++) {
//                if (values[column] < minValue) {
//                    minValue = values[column];
//                }
//            }
//        }
//
//        return minValue != Long.MAX_VALUE ? minValue : 0;
    }

    // TODO: replace by tree to enhance O(N) to O(log N)?
    // For current data set the current version is quite quick
    private long getMaxValue() {
        if (mChartData == null) {
            return 0;
        }

        long maxValue = Long.MIN_VALUE;
        for (int chartIndex = 0; chartIndex < mChartData.mValues.length; chartIndex++) {
            if (!mChartsVisibility[chartIndex]) {
                continue;
            }

            long[] values = mChartData.mValues[chartIndex];

            for (int column = mFirstVisibleColumn; column <= mLastVisibleColumn; column++) {
                if (values[column] > maxValue) {
                    maxValue = values[column];
                }
            }
        }

        return maxValue != Long.MIN_VALUE ? maxValue : 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.translate(-mOffsetX, 0);

        drawAxis(canvas);
        drawRullers(canvas);
        drawCharts(canvas);
        drawSelection(canvas);

        canvas.restore();
    }

    private void drawAxis(Canvas canvas) {
        mAxisRender.draw(canvas);
    }

    private void drawRullers(Canvas canvas) {
        mRulersRender.draw(canvas);
    }

    private void drawCharts(Canvas canvas) {
        mChartsRender.draw(canvas);
    }

    private void drawSelection(Canvas canvas) {
        mSelectionRender.draw(canvas);
    }

    public void setChartVisibility(int position, boolean checked) {
        if (mChartsVisibility != null) {
            mChartsVisibility[position] = checked;
            mChartsRender.setChartVisibility(position, checked);

            RangeAnimation animation = createRangeAnimation();
            if (animation != null) {
                animation.start();
            }
        }
    }

    private void onPreMinMaxChanged(float minValue, float maxValue) {
        mRulersRender.updateRulers(minValue, maxValue);
    }

    private void onMinMaxChanged(float minValue, float maxValue) {
        mLastMinValue = minValue;
        mLastMaxValue = maxValue;

        mChartsRender.prepareDrawData(minValue, maxValue, mSelectionLength);
        mRulersRender.updateDrawData(minValue, maxValue);
        mSelectionRender.prepareDraw(mSelectedColumn, (int) minValue, (int) maxValue);
    }

    @Nullable
    private RangeAnimation createRangeAnimation() {
        float minValue = getMinValue();
        float maxValue = getMaxValue();

        if (mRangeAnimation != null) {
            if (mRangeAnimation.mToMinValue == minValue && mRangeAnimation.mToMaxValue == maxValue) {
                return null;
            }

            mRangeAnimation.cancel();
        }

        if (Float.compare(minValue, 0.0f) == 0 && Float.compare(maxValue, 0.0f) == 0) {
            // Min and max value have to be changed to zero so we can change it without a smooth animation
            onPreMinMaxChanged(minValue, maxValue);
            onMinMaxChanged(minValue, maxValue);
            return null;
        }

        if (Float.compare(mLastMinValue, 0.0f) == 0 && Float.compare(mLastMaxValue, 0.0f) == 0) {
            // Last min and max values was zero so we can change it without a smooth animation
            onPreMinMaxChanged(minValue, maxValue);
            onMinMaxChanged(minValue, maxValue);
            return null;
        }

        mRangeAnimation = new RangeAnimation(mLastMinValue, minValue, mLastMaxValue, maxValue);
        return mRangeAnimation;
    }

    private class RangeAnimation extends FloatAnimationWrapper {
        private final float mFromMinValue;
        private final float mToMinValue;
        private final float mFromMaxValue;
        private final float mToMaxValue;

        RangeAnimation(float fromMinValue, float toMinValue, float fromMaxValue, float toMaxValue) {
            super(0.0f, 1.0f);

            mFromMinValue = fromMinValue;
            mToMinValue = toMinValue;
            mFromMaxValue = fromMaxValue;
            mToMaxValue = toMaxValue;

            setDuration(ANIMATION_DURATION_MS);
            setInterpolator(new LinearInterpolator());
        }

        @Override
        protected void onAnimationStart() {
            onPreMinMaxChanged(mToMinValue, mToMaxValue);
        }

        @Override
        protected void onAnimationFinished(boolean canceled) {
            mRangeAnimation = null;

            if (!canceled) {
                onMinMaxChanged(getMinValue(), getMaxValue());
            }
        }

        @Override
        protected void onAnimationUpdate(float value) {
            float minValue = mFromMinValue + (mToMinValue - mFromMinValue) * value;
            float maxValue = mFromMaxValue + (mToMaxValue - mFromMaxValue) * value;

            onMinMaxChanged(minValue, maxValue);
        }
    }

    private class AxisRender {

        private int mLabelY;

        private int mAxisFirstLabeledColumn;
        private int mAxisLabelStep;

        private int mAxisTextColor;
        private int mAxisTextSize;
        private Paint mAxisTextPaint;
        private int mAxisLabelWidth;
        private int mAxisTextVerticalPadding;
        private int mAxisHeight;

        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.US);

        private int mCurrentLabelsCount;

        @Nullable
        private ShowLabelsAnimation mShowLabelsAnimation;

        @Nullable
        private SortedMap<Integer, String> mColumnLabels;
        @NonNull
        private List<HideLabelsAnimation> mRetiredLabels = new ArrayList<>();

        AxisRender(@NonNull Context context) {
            Resources resources = context.getResources();

            mAxisTextColor = resources.getColor(R.color.colorAxisTextColor);
            mAxisTextSize = resources.getDimensionPixelSize(R.dimen.chart_main_view_axis_text_size);
            mAxisTextVerticalPadding = resources.getDimensionPixelSize(R.dimen.chart_main_view_axis_text_vertical_padding);

            mAxisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mAxisTextPaint.setTextSize(mAxisTextSize);
            mAxisTextPaint.setColor(mAxisTextColor);
            mAxisTextPaint.setTextAlign(Paint.Align.CENTER);
            mAxisTextPaint.setLinearText(true);

            mAxisTextPaint.getTextBounds("MMM 00", 0, 6, mTmpRect);

            mAxisLabelWidth = mTmpRect.width();
            mAxisHeight = mTmpRect.height() + mAxisTextVerticalPadding * 2;
        }

        void updateDrawData(float visibleColumns, float pixelPerColumn) {
            if (visibleColumns <= 0.0f || pixelPerColumn <= 0.0f) {
                mColumnLabels = null;
                invalidate();
                return;
            }

            if (mChartData == null || mChartData.mAxis.length < 2) {
                mColumnLabels = null;
                invalidate();
                return;
            }

            long[] axisData = mChartData.mAxis;
            int columnsCount = axisData.length;

            int firstLabeledColumn = (int) Math.ceil(((float) mAxisLabelWidth) / pixelPerColumn / 2);
            int labelStep = (int) Math.ceil(mAxisLabelWidth * 1.5 / pixelPerColumn);

            if (labelStep == 0) {
                return;
            }

            if (mAxisFirstLabeledColumn == firstLabeledColumn && mAxisLabelStep == labelStep) {
                return;
            }

            mAxisFirstLabeledColumn = firstLabeledColumn;
            mAxisLabelStep = labelStep;


            int lastLabeledColumn = columnsCount - 1 - firstLabeledColumn;

            int columnRange = lastLabeledColumn - firstLabeledColumn;
            int labelsCount = columnRange / labelStep;

            if (mCurrentLabelsCount == labelsCount) {
                return;
            }
            mCurrentLabelsCount = labelsCount;

            float columnsPerLabel = labelsCount != 0
                    ? ((float) columnRange) / labelsCount
                    : 1;

            if (mShowLabelsAnimation != null) {
                mShowLabelsAnimation.end();
            }

            if (mColumnLabels != null) {
                HideLabelsAnimation hideAnimation = new HideLabelsAnimation(mColumnLabels);
                mRetiredLabels.add(hideAnimation);
                hideAnimation.start();

                mShowLabelsAnimation = new ShowLabelsAnimation();
                mShowLabelsAnimation.start();
            }

            mColumnLabels = new TreeMap<>();

            for (float labelColumn = firstLabeledColumn + columnsPerLabel; labelColumn < lastLabeledColumn; labelColumn += columnsPerLabel) {
                mColumnLabels.put(Math.round(labelColumn), dateFormat.format(axisData[Math.round(labelColumn)]));
            }

            mColumnLabels.put(firstLabeledColumn, dateFormat.format(axisData[firstLabeledColumn]));
            mColumnLabels.put(lastLabeledColumn, dateFormat.format(axisData[lastLabeledColumn]));
        }

        void setViewHeight(int height) {
            if (height <= 0) {
                return;
            }

            mLabelY = height - mAxisTextVerticalPadding;
        }

        void draw(@NonNull Canvas canvas) {
            if (mColumnLabels == null || mChartData == null) {
                return;
            }

            int firstLabeledColumn = mFirstVisibleColumn - mAxisLabelStep;
            int lastLabeledColumn = mLastVisibleColumn + mAxisLabelStep;

            int alpha = mShowLabelsAnimation != null ? mShowLabelsAnimation.mAlpha : 255;
            mAxisTextPaint.setAlpha(alpha);

            SortedMap<Integer, String> visibleColumnsMap = mColumnLabels.subMap(firstLabeledColumn, lastLabeledColumn);
            for (Map.Entry<Integer, String> item : visibleColumnsMap.entrySet()) {
                float columnx = mColumnPositions[item.getKey()];
                canvas.drawText(item.getValue(), columnx, mLabelY, mAxisTextPaint);
            }

            for (int index = 0; index < mRetiredLabels.size(); index++) {
                HideLabelsAnimation animation = mRetiredLabels.get(index);

                mAxisTextPaint.setAlpha(animation.mAlpha);

                visibleColumnsMap = animation.mColumnLabels.subMap(firstLabeledColumn, lastLabeledColumn);
                for (Map.Entry<Integer, String> item : visibleColumnsMap.entrySet()) {
                    float columnx = mColumnPositions[item.getKey()];
                    canvas.drawText(item.getValue(), columnx, mLabelY, mAxisTextPaint);
                }
            }
        }

        private class ShowLabelsAnimation extends FloatAnimationWrapper {

            int mAlpha;

            ShowLabelsAnimation() {
                super(0, 1);
                setDuration(ANIMATION_DURATION_MS);
                setInterpolator(new AccelerateInterpolator());
            }

            @Override
            protected void onAnimationStart() {
                mAlpha = 0;
            }

            @Override
            protected void onAnimationFinished(boolean canceled) {
                mShowLabelsAnimation = null;
                invalidate();
            }

            @Override
            protected void onAnimationUpdate(float animatedValue) {
                mAlpha = (int) (255 * animatedValue);
                invalidate();
            }
        }

        private class HideLabelsAnimation extends FloatAnimationWrapper {

            @NonNull
            SortedMap<Integer, String> mColumnLabels;
            int mAlpha;

            HideLabelsAnimation(@NonNull SortedMap<Integer, String> labels) {
                super(1, 0);
                setDuration(ANIMATION_DURATION_MS);
                setInterpolator(new DecelerateInterpolator());

                mColumnLabels = labels;
            }

            @Override
            protected void onAnimationStart() {
                mAlpha = 255;
            }

            @Override
            protected void onAnimationFinished(boolean canceled) {
                mRetiredLabels.remove(this);
                invalidate();
            }

            @Override
            protected void onAnimationUpdate(float animatedValue) {
                mAlpha = (int) (255 * animatedValue);
                invalidate();
            }
        }
    }

    private class ChartsRender {

        private final int mShartLineWidth;
        @Nullable
        private ChartData mChartData;

        private float[] mLines;
        private Paint[] mChartPaints;
        private AlphaAnimation[] mAlphaAnimations;

        private int mChartsCount;
        private int mColumnsCount;
        private int mLinesCount;

        private int mViewportTop;
        private int mViewportWidth;
        private int mViewportHeigth;

        private float mYOffset;

        private boolean mHasDrawData;

        private float mLastMinValue;
        private float mLastMaxValue;
        private float mLastSelectionLength;

        ChartsRender(@NonNull Context context) {
            Resources resources = context.getResources();

            mShartLineWidth = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_line_width);
        }

        void setViewPort(int top, int width, int heigth) {
            if (mViewportTop == top && mViewportWidth == width && mViewportHeigth == heigth) {
                return;
            }

            mViewportTop = top;
            mViewportWidth = width;
            mViewportHeigth = heigth;
        }

        void setData(@NonNull ChartData data) {
            mChartData = data;

            mLastMinValue = 0;
            mLastMaxValue = 0;
            mLastSelectionLength = 0;

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
            for (int chart = 0; chart < mChartsCount; chart++) {
                mChartPaints[chart] = createChartPaint(data.mColors[chart]);
            }

            mAlphaAnimations = new AlphaAnimation[mChartsCount];
        }

        void setChartVisibility(int chartIndex, boolean visible) {
            createAlphaAnimation(chartIndex, visible).start();
        }

        void prepareDrawData(float minValue, float maxValue, float selectionLength) {
            if (mViewportWidth <= 0 || mViewportHeigth <= 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            if (mChartData == null || mChartsCount < 1 || mColumnsCount < 2) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            if (mLastMinValue == minValue && mLastMaxValue == maxValue && mLastSelectionLength == selectionLength) {
                // Nothing changed
                invalidate();
                return;
            }

            mLastMinValue = minValue;
            mLastMaxValue = maxValue;
            mLastSelectionLength = selectionLength;

            float range = maxValue - minValue;
            if (Float.compare(range, 0.0f) == 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            float yScale = ((float) mViewportHeigth) / range;

            mYOffset = maxValue * yScale + mViewportTop;

            int linePosition = 0;

            for (int chart = 0; chart < mChartsCount; chart++) {
                long[] values = mChartData.mValues[chart];

                // Extract  the first line to remove float a multiplication from cycle
                mLines[linePosition] = mColumnPositions[0];
                mLines[linePosition + 1] = values[0] * yScale;
                mLines[linePosition + 2] = mColumnPositions[1];
                mLines[linePosition + 3] = values[1] * yScale;

                linePosition += 4;

                for (int column = 1; column < mLinesCount; column++) {
                    mLines[linePosition] = mColumnPositions[column];
                    mLines[linePosition + 1] = mLines[linePosition - 1];
                    mLines[linePosition + 2] = mColumnPositions[column + 1];
                    mLines[linePosition + 3] = values[column + 1] * yScale;

                    linePosition += 4;
                }
            }

            mHasDrawData = true;
            invalidate();
        }

        void draw(@NonNull Canvas canvas) {
            if (!mHasDrawData) {
                return;
            }

            canvas.save();

            canvas.translate(0, mYOffset);
            canvas.scale(1, -1);

            int lineOffset = 0;
            for (int index = 0; index < mChartsCount; index++) {
                if (mChartsVisibility[index] || mAlphaAnimations[index] != null) {
                    int offset = (lineOffset + mFirstVisibleColumn) << 2;
                    int count = (mLastVisibleColumn - mFirstVisibleColumn) << 2;

                    canvas.drawLines(mLines, offset, count, mChartPaints[index]);
                }

                lineOffset += mLinesCount;
            }

            canvas.restore();
        }

        @NonNull
        private Paint createChartPaint(int color) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(mShartLineWidth);

            return paint;
        }

        @NonNull
        private AlphaAnimation createAlphaAnimation(int position, boolean visible) {
            AlphaAnimation alphaAnimation = mAlphaAnimations[position];
            if (alphaAnimation == null || (!alphaAnimation.isStarted() && !alphaAnimation.isRunning())) {
                alphaAnimation = new AlphaAnimation(position, visible);
            } else {
                alphaAnimation.cancel();

                float currentValue = ((float) mChartPaints[position].getAlpha()) / 255;
                alphaAnimation = new AlphaAnimation(position, visible, currentValue);
            }

            mAlphaAnimations[position] = alphaAnimation;
            return alphaAnimation;
        }

        private class AlphaAnimation extends FloatAnimationWrapper {
            private final int mPosition;
            private final boolean mVisible;
            private final float mInitValue;

            AlphaAnimation(int position, boolean visible) {
                this(position, visible, visible ? 0.0f : 1.0f);
            }

            AlphaAnimation(int position, boolean visible, @FloatRange(from = 0.0f, to = 1.0f) float initValue) {
                super(0.0f, 1.0f);

                mPosition = position;
                mVisible = visible;
                mInitValue = initValue;

                long duration = (long) (visible ? ANIMATION_DURATION_MS * (1 - initValue) : ANIMATION_DURATION_MS * initValue);
                setDuration(duration);
                setInterpolator(new AccelerateDecelerateInterpolator());
            }

            @Override
            protected void onAnimationStart() {
                mChartPaints[mPosition].setAlpha((int) (255 * mInitValue));
            }

            @Override
            protected void onAnimationFinished(boolean canceled) {
                mAlphaAnimations[mPosition] = null;

                if (!canceled) {
                    mChartPaints[mPosition].setAlpha(255);
                    invalidate();
                }
            }

            @Override
            protected void onAnimationUpdate(float value) {
                float piece = mVisible ? 1.0f - mInitValue : mInitValue;

                int alpha = 255;
                if (mVisible) {
                    alpha *= mInitValue + piece * value;
                } else {
                    alpha *= mInitValue - piece * value;
                }

                mChartPaints[mPosition].setAlpha(alpha);
                invalidate();
            }
        }
    }

    private class RulersRender {

        private final Paint mRulerPaint;
        private final Paint mTextPaint;

        private final int mBaselineOffset;

        private boolean mHasDrawData;

        private long mCurrentStep;

        Map<Long, Long> mRulers;

        private int mViewportTop;
        private int mViewportWidth;
        private int mViewportHeigth;

        private float mYOffset;

        @Nullable
        private ShowRulersAnimation mShowRulersAnimation;
        @NonNull
        private List<HideRulersAnimation> mRetiredRullers = new ArrayList<>();

        RulersRender(@NonNull Context context) {

            Resources resources = context.getResources();
            int textColor = resources.getColor(R.color.colorAxisTextColor);
            int lineColor = resources.getColor(R.color.colorAxisRulerColor);
            int rulerStrokeWidth = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_ruler_width);
            int textSize = resources.getDimensionPixelSize(R.dimen.chart_main_view_axis_text_size);

            mRulerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mRulerPaint.setColor(lineColor);
            mRulerPaint.setStrokeCap(Paint.Cap.ROUND);
            mRulerPaint.setStrokeWidth(rulerStrokeWidth);

            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setColor(textColor);
            mTextPaint.setTextSize(textSize);

            mBaselineOffset = textSize / 2;
        }

        void setViewPort(int top, int width, int heigth) {
            if (mViewportTop == top && mViewportWidth == width && mViewportHeigth == heigth) {
                return;
            }

            mViewportTop = top;
            mViewportWidth = width;
            mViewportHeigth = heigth;
        }

        void updateRulers(float minValue, float maxValue) {
            float range = maxValue - minValue;
            if (Float.compare(range, 0f) == 0) {
                mRulers = null;
                return;
            }

            long step = (long) Math.ceil((range * 0.8) / 5);
            if (step == 0) {
                mRulers = null;
                return;
            }

            if (!(Math.abs(((float) mCurrentStep) / step - 1) > 0.05)) {
                return;
            }

            // In case of time I have a search a nice step

            if (mShowRulersAnimation != null) {
                mShowRulersAnimation.cancel();
            }

            if (mRulers != null) {
                HideRulersAnimation hideAnimation = new HideRulersAnimation(mRulers);
                mRetiredRullers.add(hideAnimation);
                hideAnimation.start();

                mShowRulersAnimation = new ShowRulersAnimation();
                mShowRulersAnimation.start();
            }

            mRulers = new ArrayMap<>();
            long value = 0;
            for (int i = 0; i <= 5; i++) {
                mRulers.put(value, 0L);
                value += step;
            }

            mCurrentStep = step;
        }

        void updateDrawData(float minValue, float maxValue) {
            if (mRulers == null) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            float range = maxValue - minValue;
            if (Float.compare(range, 0f) == 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            mHasDrawData = true;

            float yScale = ((float) mViewportHeigth) / range;
            mYOffset = maxValue * yScale + mViewportTop;

            for (long value : mRulers.keySet()) {
                mRulers.put(value, (long) (value * yScale));
            }

            invalidate();
        }

        void draw(@NonNull Canvas canvas) {
            if (!mHasDrawData) {
                return;
            }

            int alpha = mShowRulersAnimation != null ? mShowRulersAnimation.mAlpha : 255;
            mRulerPaint.setAlpha(alpha);
            mTextPaint.setAlpha(alpha);

            canvas.save();

            canvas.translate(mOffsetX, mYOffset);

            for (Map.Entry<Long, Long> item : mRulers.entrySet()) {
                canvas.scale(1, -1);
                canvas.drawLine(0, item.getValue(), mViewportWidth, item.getValue(), mRulerPaint);

                canvas.scale(1, -1);
                canvas.drawText(item.getKey().toString(), 0, -item.getValue() - mBaselineOffset, mTextPaint);
            }

            for (int index = 0; index < mRetiredRullers.size(); index++) {
                HideRulersAnimation animation = mRetiredRullers.get(index);

                mRulerPaint.setAlpha(animation.mAlpha);
                mTextPaint.setAlpha(animation.mAlpha);

                for (Map.Entry<Long, Long> item : animation.mRulers.entrySet()) {
                    canvas.scale(1, -1);
                    canvas.drawLine(0, item.getValue(), mViewportWidth, item.getValue(), mRulerPaint);

                    canvas.scale(1, -1);
                    canvas.drawText(item.getKey().toString(), 0, -item.getValue() - mBaselineOffset, mTextPaint);
                }
            }

            canvas.restore();
        }

        private class ShowRulersAnimation extends FloatAnimationWrapper {

            int mAlpha;

            ShowRulersAnimation() {
                super(0, 1);
                setDuration(ANIMATION_DURATION_MS);
                setInterpolator(new AccelerateInterpolator());
            }

            @Override
            protected void onAnimationStart() {
                mAlpha = 0;
            }

            @Override
            protected void onAnimationFinished(boolean canceled) {
                mShowRulersAnimation = null;
                invalidate();
            }

            @Override
            protected void onAnimationUpdate(float animatedValue) {
                mAlpha = (int) (255 * animatedValue);
                invalidate();
            }
        }

        private class HideRulersAnimation extends FloatAnimationWrapper {

            @NonNull
            Map<Long, Long> mRulers;
            int mAlpha;

            HideRulersAnimation(@NonNull Map<Long, Long> rulers) {
                super(1, 0);
                setDuration(ANIMATION_DURATION_MS);
                setInterpolator(new DecelerateInterpolator());

                mRulers = rulers;
            }

            @Override
            protected void onAnimationStart() {
                mAlpha = 255;
            }

            @Override
            protected void onAnimationFinished(boolean canceled) {
                mRetiredRullers.remove(this);
                invalidate();
            }

            @Override
            protected void onAnimationUpdate(float animatedValue) {
                mAlpha = (int) (255 * animatedValue);
                invalidate();
            }
        }
    }

    private class SelectionRender {

        private final Paint mStrokePaint;
        private final Paint mInternalCirclePaint;
        private final Paint mInfoDatePaint;

        private final int mCircleRadius;
        private final int mCircleInternalRadius;
        private final int mDateTextSize;
        private final int mValueTextSize;
        private final int mNameTextSize;
        private final int mInfoHorizontalPadding;
        private final int mInfoVerticalPadding;
        private final Drawable mInfoBoxBackground;

        private boolean mHasDrawData;

        private int mViewportTop;
        private int mViewportWidth;
        private int mViewportHeigth;

        private float mYOffset;

        private float mCircles[];

        @Nullable
        private ChartData mChartData;
        private Paint[] mChartPaints;
        private int mChartsCount;
        private int mSelectedColumn;

        private SimpleDateFormat mInfoDateFormat = new SimpleDateFormat("EEE, MMM dd", Locale.US);
        private int mInfoBoxLeft;
        private int mInfoBoxTop;
        private int mInfoBoxRight;
        private int mInfoBoxBottom;
        private int mInfoBoxHeaderOffsetY;
        private int mInfoBoxValueOffsetY;
        private int mInfoBoxNameOffsetY;

        SelectionRender(Context context) {

            Resources resources = context.getResources();

            int color = resources.getColor(R.color.colorAxisRulerColor);
            int rulerStrokeWidth = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_ruler_width);

            mCircleRadius = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_selector_circle_radius);
            mCircleInternalRadius = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_selector_circle_internal_radius);

            mDateTextSize = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_selector_info_date_text_size);
            mValueTextSize = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_selector_info_value_text_size);
            mNameTextSize = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_selector_info_name_text_size);

            mInfoHorizontalPadding = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_selector_info_horizontal_padding);
            mInfoVerticalPadding = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_selector_info_vertical_padding);

            mInfoBoxBackground = resources.getDrawable(R.drawable.info_box_background);

            mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mStrokePaint.setColor(color);
            mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            mStrokePaint.setStrokeWidth(rulerStrokeWidth);

            mInternalCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mInternalCirclePaint.setColor(resources.getColor(R.color.columnInfoBoxInternalCircle));

            mInfoDatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mInfoDatePaint.setColor(resources.getColor(R.color.columnInfoBoxHeader));
            mInfoDatePaint.setTextSize(mDateTextSize);
        }

        void setViewPort(int top, int width, int heigth) {
            if (mViewportTop == top && mViewportWidth == width && mViewportHeigth == heigth) {
                return;
            }

            mViewportTop = top;
            mViewportWidth = width;
            mViewportHeigth = heigth;
        }

        void setData(@NonNull ChartData data) {
            mChartData = data;

            mChartsCount = data.mValues.length;
            if (mChartsCount == 0) {
                return;
            }

            mCircles = new float[mChartsCount];
            mValueText = new String[mChartsCount];
            mInfoBoxColumnOffset = new int[mChartsCount];

            mChartPaints = new Paint[mChartsCount];
            for (int chart = 0; chart < mChartsCount; chart++) {
                mChartPaints[chart] = createPaint(data.mColors[chart]);
            }
        }

        private String mInfoDateText;
        private String mValueText[];
        private int mInfoBoxColumnOffset[];
        private int mInfoBoxWidth;
        private int mInfoBoxHeight;

        void prepareDraw(int selectedColumn, long minValue, long maxValue) {
            if (mChartData == null) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            mSelectedColumn = selectedColumn;

            if (selectedColumn < 0 || selectedColumn >= mChartData.mAxis.length) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            long range = maxValue - minValue;
            if (range == 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            mHasDrawData = true;

            float yScale = ((float) mViewportHeigth) / range;
            mYOffset = maxValue * yScale + mViewportTop;

            int boxWidth = mInfoHorizontalPadding;

            for (int chartIndex = 0; chartIndex < mChartsCount; chartIndex++) {
                if (!mChartsVisibility[chartIndex]) {
                    continue;
                }

                long value = mChartData.mValues[chartIndex][selectedColumn];
                mCircles[chartIndex] = value * yScale;
                mValueText[chartIndex] = Long.toString(value);

                Paint paint = mChartPaints[chartIndex];

                paint.setTextSize(mValueTextSize);
                paint.getTextBounds(mValueText[chartIndex], 0, mValueText[chartIndex].length(), mTmpRect);
                int valueWidth = mTmpRect.width();

                paint.setTextSize(mNameTextSize);
                paint.getTextBounds(mChartData.mNames[chartIndex], 0, mChartData.mNames[chartIndex].length(), mTmpRect);
                int nameWidth = mTmpRect.width();

                mInfoBoxColumnOffset[chartIndex] = boxWidth;

                boxWidth += Math.max(valueWidth, nameWidth);
                boxWidth += mInfoHorizontalPadding;
            }

            mInfoDateText = mInfoDateFormat.format(mChartData.mAxis[mSelectedColumn]);
            mInfoDatePaint.getTextBounds(mInfoDateText, 0, mInfoDateText.length(), mTmpRect);
            int widthRequiredForHeader = mTmpRect.width() + mInfoHorizontalPadding * 2;

            boxWidth = Math.max(widthRequiredForHeader, boxWidth);
            mInfoBoxWidth = boxWidth;

            float columnX = mColumnPositions[mSelectedColumn];
            float x = columnX - mOffsetX;

            int infoBoxX = (int) (x - mInfoHorizontalPadding);
            if (mViewportWidth - infoBoxX < mInfoBoxWidth) {
                infoBoxX -= mInfoBoxWidth - (mViewportWidth - infoBoxX) - 1;
            }

            mInfoBoxHeaderOffsetY = mInfoBoxTop + mInfoVerticalPadding + mDateTextSize;
            mInfoBoxValueOffsetY = mInfoBoxHeaderOffsetY + mInfoVerticalPadding + mValueTextSize;
            mInfoBoxNameOffsetY = mInfoBoxValueOffsetY + mNameTextSize / 2 + mNameTextSize;

            mInfoBoxHeight = mInfoBoxNameOffsetY + mInfoVerticalPadding;

            mInfoBoxLeft = Math.max(infoBoxX, 1);
            mInfoBoxTop = mViewportTop;
            mInfoBoxRight = mInfoBoxLeft + mInfoBoxWidth;
            mInfoBoxBottom = mInfoBoxTop + mInfoBoxHeight;

            invalidate();
        }

        void draw(@NonNull Canvas canvas) {
            if (!mHasDrawData) {
                return;
            }

            float columnX = mColumnPositions[mSelectedColumn];

            // Draw vertical lines
            canvas.drawLine(columnX, mInfoBoxBottom, columnX, mViewportTop + mViewportHeigth, mStrokePaint);

            // Draw circles
            canvas.save();

            canvas.translate(0, mYOffset);
            canvas.scale(1, -1);

            for (int chartIndex = 0; chartIndex < mChartsCount; chartIndex++) {
                if (!mChartsVisibility[chartIndex]) {
                    continue;
                }

                float y = mCircles[chartIndex];

                canvas.drawCircle(columnX, y, mCircleRadius, mChartPaints[chartIndex]);
                canvas.drawCircle(columnX, y, mCircleInternalRadius, mInternalCirclePaint);
            }

            canvas.restore();

            // Draw info box
            canvas.save();

            canvas.translate(mOffsetX, 0);

            mInfoBoxBackground.setBounds(mInfoBoxLeft, mInfoBoxTop, mInfoBoxRight, mInfoBoxBottom);
            mInfoBoxBackground.draw(canvas);

            canvas.drawText(mInfoDateText, mInfoBoxLeft + mInfoHorizontalPadding, mInfoBoxHeaderOffsetY, mInfoDatePaint);

            for (int chartIndex = 0; chartIndex < mChartsCount; chartIndex++) {
                if (!mChartsVisibility[chartIndex]) {
                    continue;
                }

                Paint paint = mChartPaints[chartIndex];

                paint.setTextSize(mValueTextSize);
                canvas.drawText(mValueText[chartIndex], mInfoBoxLeft + mInfoBoxColumnOffset[chartIndex], mInfoBoxValueOffsetY, paint);

                paint.setTextSize(mNameTextSize);
                canvas.drawText(mChartData.mNames[chartIndex], mInfoBoxLeft + mInfoBoxColumnOffset[chartIndex], mInfoBoxNameOffsetY, paint);
            }

            canvas.restore();
        }

        @NonNull
        private Paint createPaint(int color) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(3);

            return paint;
        }
    }
}
