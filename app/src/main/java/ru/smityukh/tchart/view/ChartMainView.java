package ru.smityukh.tchart.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import ru.smityukh.tchart.R;
import ru.smityukh.tchart.data.ChartData;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

class ChartMainView extends View {

    @Nullable
    private ChartData mChartData;

    private Rect mTmpRect = new Rect();

    @NonNull
    private AxisRender mAxisRender;
    @NonNull
    private ChartsRender mChartsRender;
    @NonNull
    private RulersRenrer mRulersRenrer;

    private float mVisibleColumns;
    private float mPixelPerColumn;

    private int mFirstVisibleColumn;
    private int mLastVisibleColumn;
    private float mOffsetX;

    private float mSelectionStart = 0.0f;
    private float mSelectionEnd = 1.0f;
    private float mSelectionLength = 1.0f;

    private boolean[] mChartVisible;

    private static final double MIN_SELECTION_CHANGE_STEP = 0.001;

    private float[] mColumnPositions;

    public ChartMainView(Context context) {
        this(context, null, 0);
    }

    public ChartMainView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartMainView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mAxisRender = new AxisRender(context);
        mChartsRender = new ChartsRender(context);
        mRulersRenrer = new RulersRenrer(context);
    }

    public void setChartData(@NonNull ChartData data) {
        mChartData = data;

        mChartVisible = new boolean[mChartData.mValues.length];
        for (int chartIndex = 0; chartIndex < mChartVisible.length; chartIndex++) {
            mChartVisible[chartIndex] = true;
        }

        mChartsRender.setData(data);

        mSelectionStart = -1f;
        mSelectionEnd = -1f;
        mSelectionLength = -1f;
        setSelection(0f, 1f);

        mAxisRender.setViewHeight(getHeight());

        onSelectionLengthChanged();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);

        mAxisRender.setViewHeight(height);
        mChartsRender.setViewPort(0, width, height - mAxisRender.mAxisHeight);
        mRulersRenrer.setViewPort(0, width, height - mAxisRender.mAxisHeight);

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
            long minValue = getMinValue();
            long maxValue = getMaxValue();
            mChartsRender.prepareDrawData(minValue, maxValue, mSelectionLength);
            mRulersRenrer.prepareDrawData(minValue, maxValue);
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

        long minValue = getMinValue();
        long maxValue = getMaxValue();
        mChartsRender.prepareDrawData(minValue, maxValue, mSelectionLength);
        mRulersRenrer.prepareDrawData(minValue, maxValue);

        invalidate();
    }

    private long getMinValue() {
        if (mChartData == null) {
            return 0;
        }

        return 0;
        // Disabled in case to show 0 line everywhere
//        long minValue = Long.MAX_VALUE;
//        for (int chartIndex = 0; chartIndex < mChartData.mValues.length; chartIndex++) {
//            if (!mChartVisible[chartIndex]) {
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
            if (!mChartVisible[chartIndex]) {
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
        mRulersRenrer.draw(canvas);
    }

    private void drawCharts(Canvas canvas) {
        mChartsRender.draw(canvas);
    }

    private void drawSelection(Canvas canvas) {
    }

    public void setChartVisibility(int position, boolean checked) {
        if (mChartVisible != null) {
            mChartVisible[position] = checked;
            mChartsRender.prepareDrawData(getMinValue(), getMaxValue(), mSelectionLength);
            mRulersRenrer.prepareDrawData(getMinValue(), getMaxValue());
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

        @Nullable
        private SortedMap<Integer, String> mColumnLabels;

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

            mColumnLabels = new TreeMap<>();

            int lastLabeledColumn = columnsCount - 1 - firstLabeledColumn;

            int columnRange = lastLabeledColumn - firstLabeledColumn;
            int labelsCount = columnRange / labelStep;
            float columnsPerLabel = labelsCount != 0
                    ? ((float) columnRange) / labelsCount
                    : 1;

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

            SortedMap<Integer, String> visibleColumnsMap = mColumnLabels.subMap(firstLabeledColumn, lastLabeledColumn);
            for (Map.Entry<Integer, String> item : visibleColumnsMap.entrySet()) {
                float columnx = mColumnPositions[item.getKey()];
                canvas.drawText(item.getValue(), columnx, mLabelY, mAxisTextPaint);
            }
        }
    }

    private class ChartsRender {

        private final int mShartLineWidth;
        @Nullable
        private ChartData mChartData;

        private float[] mLines;
        private Paint[] mChartPaints;

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
        }

        private void prepareDrawData(float minValue, float maxValue, float selectionLength) {
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
                if (mChartVisible[index]) {
                    int offset = (lineOffset + mFirstVisibleColumn) << 2;
                    int count = (mLastVisibleColumn - mFirstVisibleColumn) << 2;

                    canvas.drawLines(mLines, offset, count, mChartPaints[index]);
                    //canvas.drawLines(mLines, lineOffset << 2, mLinesCount << 2, mChartPaints[index]);
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
    }

    private class RulersRenrer {

        private final Paint mRulerPaint;
        private final int mBaselineOffset;

        private boolean mHasDrawData;

        private long mCurrentStep;

        Map<Long, Long> mRulers;

        private int mViewportTop;
        private int mViewportWidth;
        private int mViewportHeigth;

        private float mYOffset;

        RulersRenrer(@NonNull Context context) {

            Resources resources = context.getResources();
            int color = resources.getColor(R.color.colorAxisTextColor);
            int rulerStrokeWidth = resources.getDimensionPixelSize(R.dimen.chart_main_view_chart_ruler_width);
            int textSize = resources.getDimensionPixelSize(R.dimen.chart_main_view_axis_text_size);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(rulerStrokeWidth);
            paint.setTextSize(textSize);

            mBaselineOffset = textSize / 2;
            mRulerPaint = paint;
        }

        void setViewPort(int top, int width, int heigth) {
            if (mViewportTop == top && mViewportWidth == width && mViewportHeigth == heigth) {
                return;
            }

            mViewportTop = top;
            mViewportWidth = width;
            mViewportHeigth = heigth;
        }

        void prepareDrawData(long minValue, long maxValue) {
            long range = maxValue - minValue;
            if (range == 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            long step = (long) Math.ceil((range * 0.8) / 5);
            if (step == 0) {
                mHasDrawData = false;
                invalidate();
                return;
            }

            mHasDrawData = true;

            // In case of time I have a search a nice step
            if (Math.abs(((float) mCurrentStep) / step - 1) > 0.05) {
                mRulers = new ArrayMap<>();
                long value = 0;
                for (int i = 0; i <= 5; i++) {
                    mRulers.put(value, 0l);
                    value += step;
                }

                mCurrentStep = step;
            }

            float yScale = ((float) mViewportHeigth) / range;

            mYOffset = maxValue * yScale + mViewportTop;

            for (long value : mRulers.keySet()) {
                mRulers.put(value, (long) (value * yScale));
            }
        }

        void draw(@NonNull Canvas canvas) {
            if (!mHasDrawData) {
                return;
            }

            canvas.save();

            canvas.translate(mOffsetX, mYOffset);

            for (Map.Entry<Long, Long> item : mRulers.entrySet()) {
                canvas.scale(1, -1);
                canvas.drawLine(0, item.getValue(), mViewportWidth, item.getValue(), mRulerPaint);

                canvas.scale(1, -1);
                canvas.drawText(item.getKey().toString(), 0, -item.getValue() - mBaselineOffset, mRulerPaint);
            }

            canvas.restore();
        }
    }
}
