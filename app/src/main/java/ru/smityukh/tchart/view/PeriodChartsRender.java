package ru.smityukh.tchart.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.animation.AccelerateDecelerateInterpolator;
import ru.smityukh.tchart.animation.FloatAnimationWrapper;
import ru.smityukh.tchart.data.ChartData;

class PeriodChartsRender {

    private static final long ANIMATION_DURATION_MS = 2500;

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

    private float mLastMinValue;
    private float mLastMaxValue;

    private final AnimationManager mAnimationManager;

    PeriodChartsRender(@NonNull ChartData data, @NonNull ChartPeriodView view) {
        mChartData = data;
        mView = view;

        mChartsCount = data.mValues.length;
        mAnimationManager = new AnimationManager(mChartsCount);

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
        mAnimationManager.animateVisibilityChnaged(position, visible);
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
        long minValue = getMinValue();
        long maxValue = getMaxValue();

        prepareDrawData(minValue, maxValue);
    }

    private void prepareDrawData(float minValue, float maxValue) {
        if (mViewWidth <= 0 || mViewHeight <= 0) {
            mHasDrawData = false;
            mView.invalidate();
            return;
        }

        if (mColumnsCount <= 1) {
            mHasDrawData = false;
            mView.invalidate();
            return;
        }

        if (mLastMinValue == minValue && mLastMaxValue == maxValue) {
            // Nothing changed
            mView.invalidate();
            return;
        }

        mLastMinValue = minValue;
        mLastMaxValue = maxValue;

        float range = maxValue - minValue;
        if (Float.compare(range, 0.0f) == 0) {
            mHasDrawData = false;
            mView.invalidate();
            return;
        }

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

        mHasDrawData = true;
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
            if (mChartVisible[index] || mAnimationManager.isVisibleForRender(index)) {
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

    private void invalidate() {
        mView.invalidate();
    }

    @NonNull
    private static Paint createChartPaint(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);

        return paint;
    }

    private class AnimationManager {
        @NonNull
        private final AlphaAnimation[] mAlphaAnimations;
        @Nullable
        private RangeAnimation mRangeAnimation;

        AnimationManager(int chartsCount) {
            mAlphaAnimations = new AlphaAnimation[chartsCount];
        }

        boolean isVisibleForRender(int position) {
            return mAlphaAnimations[position] != null;
        }

        void animateVisibilityChnaged(int position, boolean visible) {
            AlphaAnimation alphaAnimation = createAlphaAnimation(position, visible);
            RangeAnimation rangeAnimation = createRangeAnimation();

            alphaAnimation.start();
            if (rangeAnimation != null) {
                rangeAnimation.start();
            }
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

        @Nullable
        private RangeAnimation createRangeAnimation() {
            float minValue = getMinValue();
            float maxValue = getMaxValue();

            if (mRangeAnimation != null) {
                mRangeAnimation.cancel();
            }

            if (Float.compare(minValue, 0.0f) == 0 && Float.compare(maxValue, 0.0f) == 0) {
                // Min and max value have to be changed to zero so we can change it without a smooth animation
                mLastMinValue = 0;
                mLastMaxValue = 0;
                return null;
            }

            if (Float.compare(mLastMinValue, 0.0f) == 0 && Float.compare(mLastMaxValue, 0.0f) == 0) {
                // Last min and max values was zero so we can change it without a smooth animation
                prepareDrawData(minValue, maxValue);
                return null;
            }

            mRangeAnimation = new RangeAnimation(mLastMinValue, minValue, mLastMaxValue, maxValue);
            return mRangeAnimation;
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
                mAnimationManager.mAlphaAnimations[mPosition] = null;

                if (!canceled) {
                    mChartVisible[mPosition] = mVisible;
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
                setInterpolator(new AccelerateDecelerateInterpolator());
            }

            @Override
            protected void onAnimationFinished(boolean canceled) {
                mAnimationManager.mRangeAnimation = null;

                if (!canceled) {
                    prepareDrawData();
                }
            }

            @Override
            protected void onAnimationUpdate(float value) {
                float minValue = mFromMinValue + (mToMinValue - mFromMinValue) * value;
                float maxValue = mFromMaxValue + (mToMaxValue - mFromMaxValue) * value;

                prepareDrawData(minValue, maxValue);
            }
        }
    }
}
