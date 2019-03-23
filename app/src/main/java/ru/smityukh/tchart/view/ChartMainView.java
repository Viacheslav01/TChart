package ru.smityukh.tchart.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
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
    @Nullable
    private MainChartsRender mChartsRender;

    private Rect mTmpRect = new Rect();

    @NonNull
    private AxisRender mAxisRender;

    private float mVisibleColumns;
    private float mPixelPerColumn;

    private int mFirstVisibleColumn;
    private int mLastVisibleColumn;
    private float mOffsetX;

    public ChartMainView(Context context) {
        this(context, null, 0);
    }

    public ChartMainView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartMainView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mAxisRender = new AxisRender(context);
    }

    public void setChartData(@NonNull ChartData data) {
        mChartData = data;

        mChartsRender = new MainChartsRender(data, this);
        mChartsRender.setLineWidth(6);
        mChartsRender.setVerticalChartOffset(0);
        mChartsRender.setViewSize(getWidth(), getHeight());

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

        if (mChartsRender != null) {
            mChartsRender.setViewSize(width, height);
        }

        mAxisRender.setViewHeight(height);

        onSelectionLengthChanged();
    }

    private float mSelectionStart = 0.0f;
    private float mSelectionEnd = 1.0f;
    private float mSelectionLength = 1.0f;

    private static final double MIN_SELECTION_CHANGE_STEP = 0.001;

    private float[] mColumnPositions;

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

        invalidate();
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

    }

    private void drawCharts(Canvas canvas) {
        if (mChartsRender != null) {
            //mChartsRender.render(canvas);
        }
    }

    private void drawSelection(Canvas canvas) {
    }

    public void setChartVisibility(int position, boolean checked) {
        if (mChartsRender != null) {
            mChartsRender.setChartVisibility(position, checked);
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
}
