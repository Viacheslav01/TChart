package ru.smityukh.tchart.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
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

    void setData(@NonNull ChartData data) {
        mChartsRender = new ChartsRender(data, this);
        mChartsRender.setLineWidth(mLineWidth);
        mChartsRender.setVerticalChartOffset(mSetVerticalChartOffset);
        mChartsRender.setViewSize(getWidth(), getHeight());
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);

        if (mChartsRender != null) {
            mChartsRender.setViewSize(width, height);
        }
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
                mLines[linePosition + 0] = x;
                x += xStepSize;
                mLines[linePosition + 1] = values[0] * yScale;
                mLines[linePosition + 2] = x;
                mLines[linePosition + 3] = values[1] * yScale;

                linePosition += 4;

                for (int column = 1; column < mLinesCount; column++) {
                    mLines[linePosition + 0] = x;
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

        public void setVerticalChartOffset(int setVerticalChartOffset) {
            mSetVerticalChartOffset = setVerticalChartOffset;
            prepareDrawData();
        }
    }

    private class SelectionController {
        float mSelectionStart = 0.3f;
        float mSelectionEnd = 0.6f;
    }

    private class SelectionRender {

        public void draw(@NonNull Canvas canvas) {
            int width = getWidth();
            int height = getHeight();

            boolean hasUnselectedHead = false;
            boolean hasUnselectedTail = false;

            float startX;
            float endX;

            if (mSelectionController.mSelectionStart <= 0.01) {
                startX = 0;
            } else {
                hasUnselectedHead = true;
                startX = width * mSelectionController.mSelectionStart;
            }

            if (mSelectionController.mSelectionEnd >= 0.99) {
                endX = width;
            } else {
                hasUnselectedTail = true;
                endX = width * mSelectionController.mSelectionEnd;
            }

            Paint unselectedPaint = new Paint();
            unselectedPaint.setColor(0xB2F0F3F4);

            Paint framePaint = new Paint();
            framePaint.setColor(0x26325A96);

            canvas.drawRect(startX, 0, startX + 15, height, framePaint);
            canvas.drawRect(startX + 15, 0, endX - 15, 3, framePaint);
            canvas.drawRect(startX + 15, height - 3, endX - 15, height, framePaint);
            canvas.drawRect(endX - 15, 0, endX, height, framePaint);

            if (hasUnselectedHead) {
                canvas.drawRect(0, 0, startX -1, height, unselectedPaint);
            }

            if (hasUnselectedTail) {
                canvas.drawRect(endX + 1, 0, width, height, unselectedPaint);
            }
        }
    }
}

//  Draw test
//        int min = -30;
//        int max = 10;
//
//        int height = getHeight();
//        int range = max - min;
//
//        float scale = ((float) height) / range;
//
//        int value = -15;
//
//        canvas.translate(0, max * scale);
//        canvas.scale(1, -1);
//
//        canvas.drawLine(0, value * scale, 300, value * scale, paint);