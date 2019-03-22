package ru.smityukh.tchart.animation;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import ru.smityukh.tchart.animation.FinishAnimatorListener;

public class FloatAnimationWrapper {
    @NonNull
    private ValueAnimator mValueAnimator;

    public FloatAnimationWrapper(float from, float to) {
        mValueAnimator = ValueAnimator.ofFloat(from, to);

        mValueAnimator.addListener(new FinishAnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                FloatAnimationWrapper.this.onAnimationStart();
            }

            @Override
            protected void onAnimationFinished(boolean canceled) {
                FloatAnimationWrapper.this.onAnimationFinished(canceled);
            }
        });

        mValueAnimator.addUpdateListener(animation -> FloatAnimationWrapper.this.onAnimationUpdate((float) animation.getAnimatedValue()));
    }

    public void setDuration(long duration) {
        mValueAnimator.setDuration(duration);
    }

    public void setInterpolator(@NonNull TimeInterpolator timeInterpolator) {
        mValueAnimator.setInterpolator(timeInterpolator);
    }

    public boolean isStarted() {
        return mValueAnimator.isStarted();
    }

    public boolean isRunning() {
        return mValueAnimator.isRunning();
    }

    public void start() {
        mValueAnimator.start();
    }

    public void cancel() {
        mValueAnimator.cancel();
    }

    public void end() {
        mValueAnimator.end();
    }

    protected void onAnimationStart() {

    }

    protected void onAnimationFinished(boolean canceled) {

    }

    protected void onAnimationUpdate(float animatedValue) {

    }
}
