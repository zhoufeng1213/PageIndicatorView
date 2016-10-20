package com.rd;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import com.rd.pageindicatorview.R;
import com.rd.utils.DensityUtils;
import com.rd.animation.AbsAnimation;
import com.rd.animation.AnimationType;
import com.rd.animation.ScaleAnimation;
import com.rd.animation.ValueAnimation;

public class PageIndicatorView extends View implements ViewPager.OnPageChangeListener {

    private static final String DEFAULT_UNSELECTED_COLOR = "#33ffffff";
    private static final String DEFAULT_SELECTED_COLOR = "#ffffff";

    private static final int DEFAULT_CIRCLES_COUNT = 3;
    private static final int DEFAULT_RADIUS_DP = 6;
    private static final int DEFAULT_PADDING_DP = 8;

    private int radiusPx = DensityUtils.dpToPx(DEFAULT_RADIUS_DP);
    private int paddingPx = DensityUtils.dpToPx(DEFAULT_PADDING_DP);

    private int count = DEFAULT_CIRCLES_COUNT;
    private boolean isCountSet;

    //Color
    private int unselectedColor = Color.parseColor(DEFAULT_UNSELECTED_COLOR);
    private int selectedColor = Color.parseColor(DEFAULT_SELECTED_COLOR);

    private int frameColor;
    private int frameColorReverse;

    //Scale
    private int frameRadiusPx;
    private int frameRadiusReversePx;
    private float scaleFactor;

    //Worm
    private int frameLeftX;
    private int frameRightX;

    //Slide
    private int frameXCoordinate;

    private int selectedPosition;
    private int selectingPosition;
    private int lastSelectedPosition;

    private boolean interactiveAnimation;
    private long animationDuration;

    private boolean dynamicCount = true;
    private DataSetObserver setObserver;

    private Paint paint = new Paint();
    private RectF rect = new RectF();

    private AnimationType animationType = AnimationType.NONE;
    private ValueAnimation animation;
    private ViewPager viewPager;

    public PageIndicatorView(Context context) {
        super(context);
        init(null);
    }

    public PageIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PageIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PageIndicatorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    @Override
    protected void onDetachedFromWindow() {
        unRegisterSetObserver();
        super.onDetachedFromWindow();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int circleDiameterPx = radiusPx * 2;
        int desiredHeight = circleDiameterPx;
        int desiredWidth = 0;

        if (count != 0) {
            desiredWidth = (circleDiameterPx * count) + (paddingPx * (count - 1));
        }

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        } else {
            width = desiredWidth;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }

        if (width < 0) {
            width = 0;
        }

        if (height < 0) {
            height = 0;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateFrameValues();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawIndicatorView(canvas);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (interactiveAnimation) {
            onPageScroll(position, positionOffset);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (!interactiveAnimation || animationType == AnimationType.NONE) {
            setSelection(position);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {/*empty*/}

    /**
     * Set static number of circle indicators to be displayed.
     * Will automatically disable updating count dynamically. See {@link #setDynamicCount(boolean)}
     *
     * @param count total count of indicators.
     */
    public void setCount(int count) {
        if (this.count != count) {
            this.count = count;
            this.isCountSet = true;

            setDynamicCount(false);
            requestLayout();
        }
    }

    /**
     * Return number of circle indicators
     */
    public int getCount() {
        return count;
    }

    /**
     * Default is true. Dynamic count will automatically update count of circle indicators while {@link ViewPager} set.
     * See {@link #setViewPager(ViewPager)}
     *
     * @param dynamicCount boolean value to add/remove indicators dynamically.
     */
    public void setDynamicCount(boolean dynamicCount) {
        this.dynamicCount = dynamicCount;
        if (dynamicCount) {
            setCount(getViewPagerCount());
            registerSetObserver();
        } else {
            unRegisterSetObserver();
        }
    }

    /**
     * Set radius in dp of each circle indicator. Default value is {@link PageIndicatorView#DEFAULT_RADIUS_DP}.
     * Note: make sure you set circle Radius, not a Diameter.
     *
     * @param radiusDp radius of circle in dp.
     */
    public void setRadius(int radiusDp) {
        if (radiusDp < 0) {
            radiusDp = 0;
        }

        radiusPx = DensityUtils.dpToPx(radiusDp);
        invalidate();
    }

    /**
     * Return radius of each circle indicators in dp. If custom radius is not set, return
     * default value {@link PageIndicatorView#DEFAULT_RADIUS_DP}.
     */
    public int getRadius() {
        return DensityUtils.dpToPx(radiusPx);
    }

    /**
     * Set padding in dp between each circle indicator. Default value is {@link PageIndicatorView#DEFAULT_PADDING_DP}.
     *
     * @param paddingDp padding between circles.
     */
    public void setPadding(int paddingDp) {
        paddingPx = DensityUtils.dpToPx(paddingDp);
        invalidate();
    }

    /**
     * Return padding in dp between each circle indicator. If custom padding is not set,
     * return default value {@link PageIndicatorView#DEFAULT_PADDING_DP}.
     */
    public int getPadding() {
        return DensityUtils.dpToPx(paddingPx);
    }

    /**
     * Set color of unselected state to each circle indicator. Default color {@link PageIndicatorView#DEFAULT_UNSELECTED_COLOR}.
     *
     * @param color color of each unselected circle.
     */
    public void setUnselectedColor(int color) {
        unselectedColor = color;
        invalidate();
    }

    /**
     * Return color of unselected state of each circle indicator. If custom unselected color
     * is not set, return default color {@link PageIndicatorView#DEFAULT_UNSELECTED_COLOR}.
     */
    public int getUnselectedColor() {
        return unselectedColor;
    }

    /**
     * Set color of selected state to circle indicator. Default color is white {@link PageIndicatorView#DEFAULT_SELECTED_COLOR}.
     *
     * @param color color selected circle.
     */
    public void setSelectedColor(int color) {
        selectedColor = color;
        updateFrameValues();

        invalidate();
    }

    /**
     * Return color of selected circle indicator. If custom unselected color.
     * is not set, return default color {@link PageIndicatorView#DEFAULT_SELECTED_COLOR}.
     */
    public int getSelectedColor() {
        return selectedColor;
    }

    /**
     * Set animation duration time in millisecond. Default animation duration time is {@link AbsAnimation#DEFAULT_ANIMATION_TIME}.
     * (Won't affect on anything unless {@link #setAnimationType(AnimationType type)} is specified
     * and {@link #setInteractiveAnimation(boolean isInteractive)} is false).
     *
     * @param duration animation duration time.
     */
    public void setAnimationDuration(long duration) {
        animationDuration = duration;
    }

    /**
     * Return animation duration time in milliseconds. If custom duration is not set,
     * return default duration time {@link AbsAnimation#DEFAULT_ANIMATION_TIME}.
     */
    public long getAnimationDuration() {
        return animationDuration;
    }

    /**
     * Set animation type to perform while selecting new circle indicator.
     * Default animation type is {@link AnimationType#NONE}.
     *
     * @param type type of animation, one of {@link AnimationType}
     */
    public void setAnimationType(@Nullable AnimationType type) {
        if (type != null) {
            animationType = type;
        } else {
            animationType = AnimationType.NONE;
        }
    }

    /**
     * Set boolean value to perform interactive animation while selecting new indicator.
     *
     * @param isInteractive value of animation to be interactive or not.
     */
    public void setInteractiveAnimation(boolean isInteractive) {
        interactiveAnimation = isInteractive;
    }

    /**
     * Set progress value in range [0 - 1] to specify state of animation while selecting new circle indicator.
     * (Won't affect on anything unless {@link #setInteractiveAnimation(boolean isInteractive)} is false).
     *
     * @param selectingPosition selecting position with specific progress value.
     * @param progress          float value of progress.
     */
    public void setProgress(int selectingPosition, float progress) {
        if (interactiveAnimation) {

            if (selectingPosition < 0) {
                selectingPosition = 0;

            } else if (selectingPosition > count - 1) {
                selectingPosition = count - 1;
            }

            if (progress < 0) {
                progress = 0;

            } else if (progress > 1) {
                progress = 1;
            }

            this.selectingPosition = selectingPosition;
            AbsAnimation animator = getSelectedAnimation();

            if (animator != null) {
                animator.progress(progress);
            }
        }
    }

    /**
     * Set specific circle indicator position to be selected. If position < or > total count,
     * accordingly first or last circle indicator will be selected.
     *
     * @param position position of indicator to select.
     */
    public void setSelection(int position) {
        if (position < 0) {
            position = 0;

        } else if (position > count - 1) {
            position = count - 1;
        }

        lastSelectedPosition = selectedPosition;
        selectedPosition = position;

        switch (animationType) {
            case NONE:
                invalidate();
                break;

            case COLOR:
                startColorAnimation();
                break;

            case SCALE:
                startScaleAnimation();
                break;

            case WORM:
                startWormAnimation();
                break;

            case SLIDE:
                startSlideAnimation();
                break;
        }
    }

    /**
     * Return position of currently selected circle indicator.
     */
    public int getSelection() {
        return selectedPosition;
    }

    /**
     * Set {@link ViewPager} to add {@link ViewPager.OnPageChangeListener} to automatically
     * handle selecting new indicators events (and interactive animation effect in case
     * interactive animation is enabled).
     *
     * @param pager instance of {@link ViewPager} to work with
     */
    public void setViewPager(@Nullable ViewPager pager) {
        if (pager != null) {
            viewPager = pager;
            viewPager.addOnPageChangeListener(this);

            if (!isCountSet) {
                setCount(getViewPagerCount());
            }
        }
    }

    /**
     * Release {@link ViewPager} and stop handling events of {@link ViewPager.OnPageChangeListener}.
     */
    public void releaseViewPager() {
        if (viewPager != null) {
            viewPager.removeOnPageChangeListener(this);
            viewPager = null;
        }
    }

    private void onPageScroll(int position, float positionOffset) {
        Pair<Integer, Float> progressPair = getProgress(position, positionOffset);
        int selectingPosition = progressPair.first;
        float selectingProgress = progressPair.second;

        if (selectingProgress == 1) {
            lastSelectedPosition = selectedPosition;
            selectedPosition = selectingPosition;
        }

        setProgress(selectingPosition, selectingProgress);
    }

    private void drawIndicatorView(@NonNull Canvas canvas) {
        int y = getHeight() / 2;

        for (int i = 0; i < count; i++) {
            int x = getXCoordinate(i);
            drawCircle(canvas, i, x, y);
        }
    }

    private void drawCircle(@NonNull Canvas canvas, int position, int x, int y) {
        boolean selectedItem = !interactiveAnimation && (position == selectedPosition || position == lastSelectedPosition);
        boolean selectingItem = interactiveAnimation && (position == selectingPosition || position == selectedPosition);
        boolean isSelectedItem = selectedItem | selectingItem;

        if (isSelectedItem) {
            drawWithAnimationEffect(canvas, position, x, y);
        } else {
            drawWithNoEffect(canvas, position, x, y);
        }
    }

    private void drawWithAnimationEffect(@NonNull Canvas canvas, int position, int x, int y) {
        switch (animationType) {
            case COLOR:
                drawWithColorAnimation(canvas, position, x, y);
                break;

            case SCALE:
                drawWithScaleAnimation(canvas, position, x, y);
                break;

            case WORM:
                drawWithWormAnimation(canvas, x, y);
                break;

            case SLIDE:
                drawWithSlideAnimation(canvas, position, x, y);
                break;

            case NONE:
                drawWithNoEffect(canvas, position, x, y);
                break;
        }
    }

    private void drawWithColorAnimation(@NonNull Canvas canvas, int position, int x, int y) {
        int color = unselectedColor;

        if (interactiveAnimation) {
            if (position == selectingPosition) {
                color = frameColor;

            } else if (position == selectedPosition) {
                color = frameColorReverse;
            }

        } else {
            if (position == selectedPosition) {
                color = frameColor;

            } else if (position == lastSelectedPosition) {
                color = frameColorReverse;
            }
        }

        paint.setColor(color);
        canvas.drawCircle(x, y, radiusPx, paint);
    }

    private void drawWithScaleAnimation(@NonNull Canvas canvas, int position, int x, int y) {
        int color = unselectedColor;
        int radius = radiusPx;

        if (interactiveAnimation) {
            if (position == selectingPosition) {
                radius = frameRadiusPx;
                color = frameColor;

            } else if (position == selectedPosition) {
                radius = frameRadiusReversePx;
                color = frameColorReverse;
            }

        } else {
            if (position == selectedPosition) {
                radius = frameRadiusPx;
                color = frameColor;

            } else if (position == lastSelectedPosition) {
                radius = frameRadiusReversePx;
                color = frameColorReverse;
            }
        }

        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    private void drawWithWormAnimation(@NonNull Canvas canvas, int x, int y) {
        int radius = radiusPx;

        int left = frameLeftX;
        int right = frameRightX;
        int top = y - radius;
        int bot = y + radius;

        rect.left = left;
        rect.right = right;
        rect.top = top;
        rect.bottom = bot;

        paint.setColor(unselectedColor);
        canvas.drawCircle(x, y, radius, paint);

        paint.setColor(selectedColor);
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);
    }

    private void drawWithSlideAnimation(@NonNull Canvas canvas, int position, int x, int y) {
        paint.setColor(unselectedColor);
        canvas.drawCircle(x, y, radiusPx, paint);

        if (interactiveAnimation && (position == selectingPosition || position == selectedPosition)) {
            paint.setColor(selectedColor);
            canvas.drawCircle(frameXCoordinate, y, radiusPx, paint);

        } else if (!interactiveAnimation && (position == selectedPosition || position == lastSelectedPosition)) {
            paint.setColor(selectedColor);
            canvas.drawCircle(frameXCoordinate, y, radiusPx, paint);
        }
    }

    private void drawWithNoEffect(@NonNull Canvas canvas, int position, int x, int y) {
        int radius = radiusPx;
        int color = unselectedColor;

        if (animationType == AnimationType.SCALE) {
            radius /= scaleFactor;
        }

        if (position == selectedPosition) {
            color = selectedColor;
        }

        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    private void init(@Nullable AttributeSet attrs) {
        initAttributes(attrs);
        initAnimation();

        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
    }

    private void initAttributes(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PageIndicatorView, 0, 0);
        initCountAttribute(typedArray);
        initSizeAttribute(typedArray);
        initColorAttribute(typedArray);
        initAnimationAttribute(typedArray);
    }

    private void initAnimation() {
        animation = new ValueAnimation(new ValueAnimation.UpdateListener() {
            @Override
            public void onColorAnimationUpdated(int color, int colorReverse) {
                frameColor = color;
                frameColorReverse = colorReverse;
                invalidate();
            }

            @Override
            public void onScaleAnimationUpdated(int color, int colorReverse, int radius, int radiusReverse) {
                frameColor = color;
                frameColorReverse = colorReverse;

                frameRadiusPx = radius;
                frameRadiusReversePx = radiusReverse;
                invalidate();
            }

            @Override
            public void onWormAnimationUpdated(int leftX, int rightX) {
                frameLeftX = leftX;
                frameRightX = rightX;
                invalidate();
            }

            @Override
            public void onSlideAnimationUpdated(int xCoordinate) {
                frameXCoordinate = xCoordinate;
                invalidate();
            }
        });
    }

    private void initCountAttribute(@NonNull TypedArray typedArray) {
        count = typedArray.getInt(R.styleable.PageIndicatorView_count, DEFAULT_CIRCLES_COUNT);
        int position = typedArray.getInt(R.styleable.PageIndicatorView_select, 0);

        if (position < 0) {
            position = 0;
        } else if (count > 0 && position > count - 1) {
            position = count - 1;
        }

        selectedPosition = position;
        selectingPosition = position;
    }

    private void initSizeAttribute(@NonNull TypedArray typedArray) {
        paddingPx = (int) typedArray.getDimension(R.styleable.PageIndicatorView_padding, paddingPx);
        radiusPx = (int) typedArray.getDimension(R.styleable.PageIndicatorView_radius, radiusPx);

        scaleFactor = typedArray.getFloat(R.styleable.PageIndicatorView_scaleFactor, ScaleAnimation.DEFAULT_SCALE_FACTOR);
        if (scaleFactor < ScaleAnimation.MIN_SCALE_FACTOR) {
            scaleFactor = ScaleAnimation.MIN_SCALE_FACTOR;
        } else if (scaleFactor > ScaleAnimation.MAX_SCALE_FACTOR) {
            scaleFactor = ScaleAnimation.MAX_SCALE_FACTOR;
        }
    }

    private void initColorAttribute(@NonNull TypedArray typedArray) {
        unselectedColor = typedArray.getColor(R.styleable.PageIndicatorView_unselectedColor, unselectedColor);
        selectedColor = typedArray.getColor(R.styleable.PageIndicatorView_selectedColor, selectedColor);
    }

    private void initAnimationAttribute(@NonNull TypedArray typedArray) {
        animationDuration = typedArray.getInt(R.styleable.PageIndicatorView_animationDuration, AbsAnimation.DEFAULT_ANIMATION_TIME);
        interactiveAnimation = typedArray.getBoolean(R.styleable.PageIndicatorView_interactiveAnimation, false);

        int index = typedArray.getInt(R.styleable.PageIndicatorView_animationType, AnimationType.NONE.ordinal());
        animationType = getAnimationType(index);
    }

    private AnimationType getAnimationType(int index) {
        switch (index) {
            case 0:
                return AnimationType.NONE;
            case 1:
                return AnimationType.COLOR;
            case 2:
                return AnimationType.SCALE;
            case 3:
                return AnimationType.WORM;
            case 4:
                return AnimationType.SLIDE;
        }

        return AnimationType.NONE;
    }

    private void updateFrameValues() {
        //color
        frameColor = selectedColor;
        frameColorReverse = unselectedColor;

        //scale
        frameRadiusPx = radiusPx;
        frameRadiusReversePx = radiusPx;

        //worm
        int xCoordinate = getXCoordinate(selectedPosition);
        if (xCoordinate - radiusPx >= 0) {
            frameLeftX = xCoordinate - radiusPx;
            frameRightX = xCoordinate + radiusPx;

        } else {
            frameLeftX = xCoordinate;
            frameRightX = xCoordinate + (radiusPx * 2);
        }

        //slide
        frameXCoordinate = xCoordinate;
    }

    private void startColorAnimation() {
        animation.color().with(unselectedColor, selectedColor).duration(animationDuration).start();
    }

    private void startScaleAnimation() {
        animation.scale().with(unselectedColor, selectedColor, radiusPx, scaleFactor).duration(animationDuration).start();
    }

    private void startWormAnimation() {
        int fromX = getXCoordinate(lastSelectedPosition);
        int toX = getXCoordinate(selectedPosition);
        boolean isRightSide = selectedPosition > lastSelectedPosition;

        animation.worm().end();
        animation.worm().with(fromX, toX, radiusPx, isRightSide).duration(animationDuration).start();
    }

    private void startSlideAnimation() {
        int fromX = getXCoordinate(lastSelectedPosition);
        int toX = getXCoordinate(selectedPosition);

        animation.slide().with(fromX, toX).duration(animationDuration).start();
    }

    @Nullable
    private AbsAnimation getSelectedAnimation() {
        switch (animationType) {
            case COLOR:
                return animation.color().with(unselectedColor, selectedColor);

            case SCALE:
                return animation.scale().with(unselectedColor, selectedColor, radiusPx, scaleFactor);

            case WORM:
            case SLIDE:
                int fromX = getXCoordinate(selectedPosition);
                int toX = getXCoordinate(selectingPosition);

                if (animationType == AnimationType.WORM) {
                    boolean isRightSide = selectingPosition > selectedPosition;
                    return animation.worm().with(fromX, toX, radiusPx, isRightSide);

                } else if (animationType == AnimationType.SLIDE) {
                    return animation.slide().with(fromX, toX);
                }
        }

        return null;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private int getXCoordinate(int position) {
        int actualViewWidth = calculateActualViewWidth();
        int viewCenter = (getWidth() - actualViewWidth) / 2;
        int x = viewCenter;

        if (x < 0) {
            x = 0;
        }

        for (int i = 0; i < count; i++) {
            x += radiusPx;
            if (position == i) {
                return x;
            }

            x += radiusPx + paddingPx;
        }

        return x;
    }

    private Pair<Integer, Float> getProgress(int position, float positionOffset) {
        boolean isRightOverScrolled = position > selectedPosition;
        boolean isLeftOverScrolled = position + 1 < selectedPosition;

        if (isRightOverScrolled || isLeftOverScrolled) {
            selectedPosition = position;
        }

        boolean isSlideToRightSide = selectedPosition == position && positionOffset != 0;
        int selectingPosition;
        float selectingProgress;

        if (isSlideToRightSide) {
            selectingPosition = position + 1;
            selectingProgress = positionOffset;

        } else {
            selectingPosition = position;
            selectingProgress = 1 - positionOffset;
        }

        if (selectingProgress > 1) {
            selectingProgress = 1;

        } else if (selectingProgress < 0) {
            selectingProgress = 0;
        }

        return new Pair<>(selectingPosition, selectingProgress);
    }

    private int calculateActualViewWidth() {
        int width = 0;
        int diameter = radiusPx * 2;

        for (int i = 0; i < count; i++) {
            width += diameter;

            if (i < count - 1) {
                width += paddingPx;
            }
        }

        return width;
    }

    private void registerSetObserver() {
        if (setObserver == null && viewPager != null && viewPager.getAdapter() != null) {
            setObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    super.onChanged();
                    if (viewPager != null && viewPager.getAdapter() != null) {
                        int count = viewPager.getAdapter().getCount();
                        setCount(count);
                    }
                }
            };

            viewPager.getAdapter().registerDataSetObserver(setObserver);
        }
    }

    private void unRegisterSetObserver() {
        if (setObserver != null && viewPager != null && viewPager.getAdapter() != null) {
            viewPager.getAdapter().unregisterDataSetObserver(setObserver);
            setObserver = null;
        }
    }

    private int getViewPagerCount() {
        if (viewPager != null && viewPager.getAdapter() != null) {
            return viewPager.getAdapter().getCount();
        } else {
            return count;
        }
    }
}
