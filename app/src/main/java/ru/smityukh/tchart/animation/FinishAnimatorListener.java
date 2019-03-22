package ru.smityukh.tchart.animation;

import android.animation.Animator;
import android.support.annotation.NonNull;

public class FinishAnimatorListener extends SimpleAnimatorListener {
    private boolean mCanceled;

    @Override
    public void onAnimationCancel(@NonNull Animator animation) {
        mCanceled = true;
    }

    @Override
    public void onAnimationEnd(@NonNull Animator animation) {
        onAnimationFinished(mCanceled);
    }

    protected void onAnimationFinished(boolean canceled) {
    }
}
