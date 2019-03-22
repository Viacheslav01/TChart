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

    PeriodChartsRender(@NonNull ChartData data, @NonNull ChartPeriodView view) {
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

    @Nullable
    private VisibilityChangeAnimation mVisibilityAnimation;

    void setChartVisibility(int position, boolean visible) {
        if (position < 0 || position >= mChartsCount) {
            throw new IllegalArgumentException("Position is out of range");
        }

        if (mVisibilityAnimation != null) {
            mVisibilityAnimation = mVisibilityAnimation.createSuccessor(position, visible);
            mVisibilityAnimation.start();
            return;
        }

        mVisibilityAnimation = new VisibilityChangeAnimation(position, visible);
        mVisibilityAnimation.start();
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

        if (mColumnsCount <= 0) {
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

    private class VisibilityChangeAnimation extends FloatAnimationWrapper {

        private static final long DURATION_MS = 2500;

        private final int mPosition;
        private final boolean mVisible;

        private final AnimationBlock[] mBlocks;

        private float mLastAnimatedvalue;

        VisibilityChangeAnimation(int position, boolean visible) {
            super(0.0f, 1.0f);

            setDuration(DURATION_MS);
            setInterpolator(new AccelerateDecelerateInterpolator());

            mPosition = position;
            mVisible = visible;

            AnimationBlock visibilityBlock = new VisibilityAnimationBlock(position, visible);
            AnimationBlock rangeBlock = createRangeBlock(position, visible);

            if (rangeBlock != null) {
                mBlocks = new AnimationBlock[]{visibilityBlock, rangeBlock};
            } else {
                mBlocks = new AnimationBlock[]{visibilityBlock};
            }
        }

        private VisibilityChangeAnimation(long duration, int position, boolean visible, @NonNull AnimationBlock[] blocks) {
            super(0.0f, 1.0f);

            setDuration(duration);
            setInterpolator(new AccelerateDecelerateInterpolator());

            mPosition = position;
            mVisible = visible;

            mBlocks = blocks;
        }

        public VisibilityChangeAnimation createSuccessor(int position, boolean visible) {
            if (!isStarted() && !isRunning()) {
                // Create the new animation if the current is not playing
                return new VisibilityChangeAnimation(position, visible);
            }

            if (mPosition == position) {
                if (mVisible == visible) {
                    return this;
                }

                cancel();

                float currentValue = ((float) mChartPaints[position].getAlpha()) / 255;

                AnimationBlock visibilityBlock = new ContinueVisibilityAnimationBlock(position, visible, currentValue);
                AnimationBlock rangeBlock = createRangeBlock(position, visible);

                AnimationBlock[] blocks;
                if (rangeBlock != null) {
                    blocks = new AnimationBlock[]{visibilityBlock, rangeBlock};
                } else {
                    blocks = new AnimationBlock[]{visibilityBlock};
                }

                long duration = (long) (visible ? DURATION_MS * (1 - currentValue) : DURATION_MS * currentValue);
                return new VisibilityChangeAnimation(duration, position, visible, blocks);
            }

            cancel();
            return new VisibilityChangeAnimation(position, visible);
        }

        private AnimationBlock createRangeBlock(int position, boolean visible) {
            boolean currentVisible = mChartVisible[position];
            mChartVisible[position] = visible;

            float minValue = getMinValue();
            float maxValue = getMaxValue();

            mChartVisible[position] = currentVisible;

            if (Float.compare(minValue, 0.0f) == 0 && Float.compare(maxValue, 0.0f) == 0) {
                // Min and max value have to be changed to zero so we can do nothing
                return null;
            }

            if (Float.compare(mLastMinValue, 0.0f) == 0 && Float.compare(mLastMaxValue, 0.0f) == 0) {
                // Last min and max values was zero so we can change it without a smooth animation
                return new JumpRangeAnimationBlock(minValue, maxValue);
            }

            return new RangeAnimationBlock(mLastMinValue, minValue, mLastMaxValue, maxValue);
        }

        @Override
        protected void onAnimationStart() {
            mLastAnimatedvalue = 0.0f;

            for (AnimationBlock block : mBlocks) {
                block.onAnimationStart();
            }
        }

        @Override
        protected void onAnimationFinished(boolean canceled) {
            for (AnimationBlock block : mBlocks) {
                block.onAnimationFinished(canceled);
            }
        }

        @Override
        protected void onAnimationUpdate(float animatedValue) {
            mLastAnimatedvalue = animatedValue;

            for (AnimationBlock block : mBlocks) {
                block.onAnimationUpdate(animatedValue);
            }
        }
    }

    private class AnimationBlock {
        void onAnimationStart() {
        }

        void onAnimationFinished(boolean canceled) {
        }

        void onAnimationUpdate(float value) {

        }
    }

    private class VisibilityAnimationBlock extends AnimationBlock {
        private final int mPosition;
        private final boolean mVisible;

        VisibilityAnimationBlock(int position, boolean visible) {
            mPosition = position;
            mVisible = visible;
        }

        @Override
        void onAnimationStart() {
            mChartVisible[mPosition] = true;
            mChartPaints[mPosition].setAlpha(mVisible ? 0 : 255);
        }

        @Override
        void onAnimationFinished(boolean canceled) {
            if (!canceled) {
                mChartVisible[mPosition] = mVisible;
                mChartPaints[mPosition].setAlpha(255);

                prepareDrawData();
            }
        }

        @Override
        void onAnimationUpdate(float value) {
            int alpha = 255;
            if (mVisible) {
                alpha *= value;
            } else {
                alpha *= 1 - value;
            }

            mChartPaints[mPosition].setAlpha(alpha);

            invalidate();
        }
    }

    private class ContinueVisibilityAnimationBlock extends AnimationBlock {
        private final int mPosition;
        private final boolean mVisible;
        private final float mInitValue;

        ContinueVisibilityAnimationBlock(int position, boolean visible, @FloatRange(from = 0.0f, to = 1.0f) float initValue) {
            mPosition = position;
            mVisible = visible;
            mInitValue = initValue;
        }

        @Override
        void onAnimationStart() {
            mChartVisible[mPosition] = true;
            mChartPaints[mPosition].setAlpha((int) (255 * mInitValue));
        }

        @Override
        void onAnimationFinished(boolean canceled) {
            if (!canceled) {
                mChartVisible[mPosition] = mVisible;
                mChartPaints[mPosition].setAlpha(255);

                prepareDrawData();
            }
        }

        @Override
        void onAnimationUpdate(float value) {
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

    private class RangeAnimationBlock extends AnimationBlock {

        private final float mFromMinValue;
        private final float mToMinValue;
        private final float mFromMaxValue;
        private final float mToMaxValue;

        RangeAnimationBlock(float fromMinValue, float toMinValue, float fromMaxValue, float toMaxValue) {
            mFromMinValue = fromMinValue;
            mToMinValue = toMinValue;
            mFromMaxValue = fromMaxValue;
            mToMaxValue = toMaxValue;
        }

        @Override
        void onAnimationUpdate(float value) {
            float minValue = mFromMinValue + (mToMinValue - mFromMinValue) * value;
            float maxValue = mFromMaxValue + (mToMaxValue - mFromMaxValue) * value;

            prepareDrawData(minValue, maxValue);
        }
    }

    private class JumpRangeAnimationBlock extends AnimationBlock {

        private final float mMinValue;
        private final float mMaxValue;

        JumpRangeAnimationBlock(float minValue, float maxValue) {
            mMinValue = minValue;
            mMaxValue = maxValue;
        }

        @Override
        void onAnimationStart() {
            prepareDrawData(mMinValue, mMaxValue);
        }
    }
}