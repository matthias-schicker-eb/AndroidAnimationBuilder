package com.komaxx.androidanimationbuilder;

import android.animation.Animator;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * <p>
 * Provides a simple, promise-like, interface to create complex
 * animations, easily extensible with callbacks and hooks all
 * along the way.
 * </p>
 *
 * Exemplary usage to create a simple wiggle animation
 * <pre>
 *      AnimationBuilder builder = new AnimationBuilder(v);
 *      builder.setDefaultStepLength(60)
 *      .rotateBy(2)
 *      .then().rotateBy(-6)
 *      .then().rotateBy(7)
 *      .then().rotateBy(-3).ms(120)
 *      .execute();
 * </pre>
 *
 * @author  Created by Matthias Schicker (KoMaXX) on 28/02/2017.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class AndroidAnimationBuilder {
    private static final boolean DEBUG_LOGGING = false;

    private final WeakReference<View> viewRef;

    private boolean allowLayerAdjustmentForAnimation = true;
    private int defaultStepDurationMS = 300;

    private final ArrayList<AnimationStep> steps = new ArrayList<>();

    // not yet added to steps!
    private AnimationStep currentStep = new AnimationStep();

    private boolean executionTriggered = false;


    /**
     * Starting point for all the animation fun. Will take any ol' view.
     *
     * @param v The view on which the animations will be executed. WEAKLY held!
     */
    public AndroidAnimationBuilder(View v) {
        viewRef = new WeakReference<>(v);
    }

    /**
     * Set this to define the default duration of all steps, i.e., the
     * duration of all steps that did not receive explicit duration
     * definitions.<br/>
     * Defaults to 300ms.
     */
    public AndroidAnimationBuilder setDefaultStepDuration(int ms) {
        if (alreadyExecuted()) return this;

        defaultStepDurationMS = ms;
        return this;
    }

    /**
     * If <code>true</code>, the AnimationBuilder will attempt to set the
     * layer type of the view for the duration of the animation.
     * Default: <code>true</code>.
     */
    public AndroidAnimationBuilder setAllowLayerAdjustmentForAnimation(boolean allowLayerAdjustmentForAnimation) {
        if (alreadyExecuted()) return this;

        this.allowLayerAdjustmentForAnimation = allowLayerAdjustmentForAnimation;
        return this;
    }

    /**
     * Add a rotation animation to the current step. Will replace previously
     * set rotation animations for the current step.
     */
    public AndroidAnimationBuilder rotateBy(float degrees) {
        if (alreadyExecuted()) return this;

        currentStep.setRotateBy(degrees);
        return this;
    }

    /**
     * Add a translation on the xAxis to the current animation step.
     * Will replace current x-translation definitions for the current step.
     */
    public AndroidAnimationBuilder translateX(float xTransPx){
        if (alreadyExecuted()) return this;

        currentStep.setTranslateX(xTransPx);
        return this;
    }

    /**
     * Add a translation on the yAxis to the current animation step.
     * Will replace current y-translation definitions for the current step.
     */
    public AndroidAnimationBuilder translateY(float yTransPx){
        if (alreadyExecuted()) return this;

        currentStep.setTranslateY(yTransPx);
        return this;
    }

    /**
     * Add a translation on the zAxis to the current animation step.
     * Will replace current z-translation definitions for the current step.
     *
     * ONLY available in API level >=21 environments. No-op otherwise.
     */
    public AndroidAnimationBuilder translateZ(float zTransPx){
        if (alreadyExecuted()) return this;

        currentStep.setTranslateZ(zTransPx);
        return this;
    }

    /**
     * Add scaling in x direction to the current animation step.
     * Will replace current x-scale definitions for the current step.
     */
    public AndroidAnimationBuilder scaleX(float scaleX){
        if (alreadyExecuted()) return this;

        currentStep.setScaleX(scaleX);
        return this;
    }

    /**
     * Add scaling in y direction to the current animation step.
     * Will replace current y-scale definitions for the current step.
     */
    public AndroidAnimationBuilder scaleY(float scaleY){
        if (alreadyExecuted()) return this;

        currentStep.setScaleY(scaleY);
        return this;
    }

    /**
     * Add alpha animation to the current step.
     */
    public AndroidAnimationBuilder alpha(float alpha){
        if (alreadyExecuted()) return this;

        currentStep.setAlpha(alpha);
        return this;
    }

    /**
     * Finishes the current animation step definition and starts the next one.
     * Unless given a specific duration it will have the default duration.
     * An empty step will simply appear as a pause. An empty step at the end
     * of the sequence will be ignored.
     */
    public AndroidAnimationBuilder then() {
        if (alreadyExecuted()) return this;

        steps.add(currentStep);
        currentStep = new AnimationStep();
        return this;
    }

    private boolean alreadyExecuted() {
        if (executionTriggered){
            Log.w("AndroidAnimationBuilder", "Further animation definitions ignored: Execution already started!");
        }
        return executionTriggered;
    }

    /**
     * MUST be the final call to the builder. Compiles the actual animations
     * out of the definitions.
     * All following calls to the build will have no effect;
     */
    public void execute() {
        if (alreadyExecuted()) return;
        executionTriggered = true;

        // prepare the chain:
        // add the current step
        if (!currentStep.isEmpty()) steps.add(currentStep);
        if (steps.size() < 1){
            if (DEBUG_LOGGING){
                Log.w("AndroidAnimationBuilder", "No animation defined.");
            }
            return;
        }


        FinalStep finalStep = new FinalStep();
        finalStep.viewRef = viewRef;
        steps.add(finalStep);

        // build chain out of animation steps. Set defaults if not done yet
        for (int i = 0; i < steps.size()-1; i++){
            AnimationStep step = steps.get(i);
            step.viewRef = viewRef;
            step.nextStep = steps.get(i+1);
            step.setDurationIfUnset(defaultStepDurationMS);
        }

        View view = viewRef.get();
        if (view != null && allowLayerAdjustmentForAnimation) {
            finalStep.setEndLayerType(view.getLayerType());

            // speed up the animation if available
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        steps.get(0).execute();
    }

    /**
     * Set the duration for the current step. If not set, the step will use the
     * default duration. Values <1 unset any previously set duration.
     */
    public AndroidAnimationBuilder ms(int ms) {
        currentStep.durationMs = ms;
        return this;
    }

    static class AnimationStep implements Animator.AnimatorListener {
        Float rotateByDegrees;

        Float translationX;
        Float translationY;
        Float translationZ;

        Float scaleX;
        Float scaleY;

        Float alpha;

        Runnable preStep;

        WeakReference<View> viewRef;
        AnimationStep nextStep;
        int durationMs;


        void setRotateBy(float degrees) {
            rotateByDegrees = degrees;
        }

        void setTranslateX(float translateX) {
            this.translationX = translateX;
        }
        void setTranslateY(float translateY) {
            this.translationY = translateY;
        }
        void setTranslateZ(float translateZ) {
            this.translationZ = translateZ;
        }

        void setScaleX(float scaleX) {
            this.scaleX = scaleX;
        }
        void setScaleY(float scaleY) {
            this.scaleY = scaleY;
        }

        void setAlpha(float alpha){
            this.alpha = alpha;
        }

        /**
         * Decides if the step is at least minimally defined. Empty steps
         * will simply be ignored.
         */
        boolean isEmpty() {
            return !hasAnimation() && preStep==null;
        }

        boolean hasAnimation() {
            return     rotateByDegrees!=null
                    || translationX!=null
                    || translationY!=null
                    || translationZ!=null
                    || scaleX!=null
                    || scaleY!=null
                    || alpha!=null;
        }

        void setDurationIfUnset(int ms) {
            if (durationMs <= 0) durationMs = ms;
        }

        void execute() {
            View view = viewRef.get();
            if (view == null){
                Log.w("AndroidAnimationBuilder", "Aborting animation step: View was cleaned up");
                return;
            }

            if (preStep != null){
                preStep.run();
            }

            if (hasAnimation()){
                ViewPropertyAnimator animate = view.animate();

                if (rotateByDegrees != null) animate.rotationBy(rotateByDegrees);

                if (translationX != null) animate.translationX(translationX);
                if (translationY != null) animate.translationY(translationY);

                if (translationZ != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    animate.translationZ(translationZ);
                }

                if (scaleX != null) animate.scaleX(scaleX);
                if (scaleY != null) animate.scaleY(scaleY);

                if (alpha != null) animate.alpha(alpha);

                // add other animation types here.
                animate.setDuration(durationMs);
                animate.setListener(this);
                animate.start();
            } else {
                view.postDelayed(new Runnable() {
                    @Override  public void run() {
                        stepFinished();
                    }
                }, durationMs);
            }
        }

        /**
         * Called when the step was finished.
         */
        private void stepFinished() {
            View view = viewRef.get();
            if (view == null){
                if (DEBUG_LOGGING){
                    Log.i("AndroidAnimationBuilder", "Aborting animation step when scheduling next step: View was cleaned up");
                }
                return;
            }

            view.postDelayed(new Runnable() {
                @Override public void run() {
                    nextStep.execute();
                }
            }, 1);
        }


        @Override  public void onAnimationEnd(Animator animator) {
            stepFinished();
        }

        @Override  public void onAnimationStart(Animator animator) {}
        @Override  public void onAnimationCancel(Animator animator) {}
        @Override  public void onAnimationRepeat(Animator animator) {}
    }

    /**
     * Special no-op final step of the animation. Undoes any changes
     * to layer settings done to make the animation smooth.
     */
    private static class FinalStep extends AnimationStep {
        private Integer endLayerType;

        @Override
        public void execute() {
            if (DEBUG_LOGGING){
                Log.d("AndroidAnimationBuilder","Animation done!");
            }

            View view = viewRef.get();
            if (view != null && endLayerType != null){
                view.setLayerType(endLayerType, null);
            }
        }

        public void setEndLayerType(int layerType) {
            this.endLayerType = layerType;
        }
    }
}
