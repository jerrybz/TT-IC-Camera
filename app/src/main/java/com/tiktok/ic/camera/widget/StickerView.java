package com.tiktok.ic.camera.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.tiktok.ic.camera.R;

/**
 * 可编辑的贴纸视图，支持拖动、缩放、旋转、删除
 */
public class StickerView extends FrameLayout {
    
    private ImageView stickerImageView;
    private ImageButton deleteButton;
    private ImageButton rotateButton;
    private ImageButton bringToFrontButton; // 图层调整按钮
    
    private float rotationAngle = 0; // 旋转角度（0-360°）
    private float scaleFactor = 1.0f; // 缩放因子
    
    private boolean isSelected = false;
    private OnStickerDeleteListener deleteListener;
    private OnStickerSelectedListener selectedListener;
    private OnStickerBringToFrontListener bringToFrontListener;
    
    private static final float MIN_SCALE = 0.3f;
    private static final float MAX_SCALE = 5.0f;
    private static final float BUTTON_SIZE = 50; // 增大按钮尺寸
    private static final float BUTTON_PADDING = BUTTON_SIZE / 2;
    private static final float BORDER_WIDTH = 3; // 边框宽度
    
    // 边框绘制相关
    private Paint borderPaint;
    
    private int touchMode = NONE;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ROTATE_BUTTON = 2;
    private static final int SCALE = 3;
    private static final int ROTATE = 4;
    
    private PointF lastTouchPoint = new PointF();
    private PointF lastScreenPoint = new PointF(); // 屏幕坐标，用于旋转后平移
    private float initialDistance = 0;
    private float initialRotationAngle = 0; // 初始旋转角度
    private float initialScale = 1.0f;
    private PointF initialPivot = new PointF();
    private PointF initialTouchPoint = new PointF();
    private float initialAngle = 0; // 初始双指角度
    
    private ScaleGestureDetector scaleGestureDetector;
    private boolean isDragging = false;
    private boolean isScaling = false;
    private boolean isRotating = false;
    
    public interface OnStickerDeleteListener {
        void onDelete(StickerView view);
    }
    
    public interface OnStickerSelectedListener {
        void onSelected(StickerView view);
    }
    
    public interface OnStickerBringToFrontListener {
        void onBringToFront(StickerView view);
    }
    
    public StickerView(Context context) {
        super(context);
        init();
    }
    
    public StickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public StickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setPadding((int) BUTTON_PADDING, (int) BUTTON_PADDING, (int) BUTTON_PADDING, (int) BUTTON_PADDING);
        
        // 允许绘制（FrameLayout默认不绘制）
        setWillNotDraw(false);
        
        // 允许子视图超出边界显示（这样按钮可以显示在边框外）
        setClipChildren(false);
        setClipToPadding(false);
        
        // 初始化边框画笔
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0xFF4488FF); // 蓝色边框
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_WIDTH);
        
        // 创建贴纸图片视图
        stickerImageView = new ImageView(getContext());
        stickerImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stickerImageView.setAdjustViewBounds(true);
        
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        imageParams.gravity = Gravity.CENTER;
        addView(stickerImageView, imageParams);
        
        // 创建删除按钮 - 放在边框右上角（内部）
        deleteButton = new ImageButton(getContext());
        deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
        deleteButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        deleteButton.setPadding(8, 8, 8, 8);
        deleteButton.setLayoutParams(new FrameLayout.LayoutParams(
            (int) BUTTON_SIZE,
            (int) BUTTON_SIZE
        ));
        // 按钮位置会在onLayout中设置
        deleteButton.setOnClickListener(v -> {
            if (deleteListener != null && isSelected) {
                deleteListener.onDelete(this);
            }
        });
        
        // 确保删除按钮可以接收触摸事件
        deleteButton.setClickable(true);
        deleteButton.setFocusable(true);
        deleteButton.setElevation(20); // 提升按钮层级，确保可见
        // 设置白色圆形背景
        android.graphics.drawable.GradientDrawable deleteBg = new android.graphics.drawable.GradientDrawable();
        deleteBg.setColor(0xFFFFFFFF);
        deleteBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            deleteButton.setBackground(deleteBg);
        } else {
            deleteButton.setBackgroundDrawable(deleteBg);
        }
        addView(deleteButton);
        
        // 创建旋转按钮 - 放在边框右下角（内部）
        rotateButton = new ImageButton(getContext());
        rotateButton.setImageResource(R.drawable.ic_rotate);
        rotateButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        rotateButton.setPadding(8, 8, 8, 8);
        rotateButton.setLayoutParams(new FrameLayout.LayoutParams(
            (int) BUTTON_SIZE,
            (int) BUTTON_SIZE
        ));
        // 按钮位置会在onLayout中设置
        rotateButton.setElevation(20); // 提升按钮层级，确保可见
        // 设置白色圆形背景
        android.graphics.drawable.GradientDrawable rotateBg = new android.graphics.drawable.GradientDrawable();
        rotateBg.setColor(0xFFFFFFFF);
        rotateBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            rotateButton.setBackground(rotateBg);
        } else {
            rotateButton.setBackgroundDrawable(rotateBg);
        }
        rotateButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchMode = ROTATE_BUTTON;
                initialRotationAngle = rotationAngle;
                initialTouchPoint.set(event.getRawX(), event.getRawY());
                initialPivot.set(getX() + getWidth() / 2f, getY() + getHeight() / 2f);
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && touchMode == ROTATE_BUTTON) {
                float dx = event.getRawX() - initialTouchPoint.x;
                float dy = event.getRawY() - initialTouchPoint.y;
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                rotationAngle = (initialRotationAngle + angle) % 360;
                if (rotationAngle < 0) rotationAngle += 360;
                applyTransform();
                updateRotateButtonPosition();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || 
                       event.getAction() == MotionEvent.ACTION_CANCEL) {
                touchMode = NONE;
                v.getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return false;
        });
        addView(rotateButton);
        
        // 创建图层调整按钮 - 放在边框左下角（内部）
        bringToFrontButton = new ImageButton(getContext());
        bringToFrontButton.setImageResource(R.drawable.ic_layer_up);
        bringToFrontButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bringToFrontButton.setPadding(8, 8, 8, 8);
        bringToFrontButton.setLayoutParams(new FrameLayout.LayoutParams(
            (int) BUTTON_SIZE,
            (int) BUTTON_SIZE
        ));
        // 初始位置会在onLayout中设置
        bringToFrontButton.setOnClickListener(v -> {
            if (bringToFrontListener != null && isSelected) {
                bringToFrontListener.onBringToFront(this);
            }
        });
        
        // 确保图层调整按钮可以接收触摸事件
        bringToFrontButton.setClickable(true);
        bringToFrontButton.setFocusable(true);
        bringToFrontButton.setElevation(20); // 提升按钮层级，确保可见
        // 设置白色圆形背景
        android.graphics.drawable.GradientDrawable frontBg = new android.graphics.drawable.GradientDrawable();
        frontBg.setColor(0xFFFFFFFF);
        frontBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            bringToFrontButton.setBackground(frontBg);
        } else {
            bringToFrontButton.setBackgroundDrawable(frontBg);
        }
        addView(bringToFrontButton);
        
        // 初始化手势检测器 - 禁用，我们手动处理双指操作
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                // 不使用 ScaleGestureDetector，手动处理以避免冲突
                return false;
            }
        });
        
        setOnClickListener(v -> {
            if (selectedListener != null) {
                selectedListener.onSelected(this);
            }
            setSelected(true);
        });
        
        updateButtonVisibility();
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            updateButtonPositions();
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 如果选中，绘制边框
        if (isSelected) {
            float padding = BUTTON_PADDING;
            float left = padding;
            float top = padding;
            float right = getWidth() - padding;
            float bottom = getHeight() - padding;
            
            // 绘制矩形边框
            canvas.drawRect(left, top, right, bottom, borderPaint);
        }
    }
    
    private void updateButtonPositions() {
        float width = getWidth();
        float height = getHeight();
        float padding = BUTTON_PADDING;
        float halfButtonSize = BUTTON_SIZE / 2f;
        
        // 删除按钮 - 边框右上角，按钮中心对齐到角上
        // 边框右上角位置：(width - padding, padding)
        // 按钮左上角位置 = 中心位置 - 按钮半径
        deleteButton.setX(width - padding - halfButtonSize);
        deleteButton.setY(padding - halfButtonSize);
        deleteButton.bringToFront();
        
        // 旋转按钮 - 边框右下角，按钮中心对齐到角上
        // 边框右下角位置：(width - padding, height - padding)
        rotateButton.setX(width - padding - halfButtonSize);
        rotateButton.setY(height - padding - halfButtonSize);
        rotateButton.bringToFront();
        
        // 图层调整按钮 - 边框左下角，按钮中心对齐到角上
        // 边框左下角位置：(padding, height - padding)
        bringToFrontButton.setX(padding - halfButtonSize);
        bringToFrontButton.setY(height - padding - halfButtonSize);
        bringToFrontButton.bringToFront();
    }
    
    private void updateRotateButtonPosition() {
        float width = getWidth();
        float height = getHeight();
        float padding = BUTTON_PADDING;
        float halfButtonSize = BUTTON_SIZE / 2f;
        
        // 更新旋转按钮位置 - 边框右下角，按钮中心对齐到角上
        rotateButton.setX(width - padding - halfButtonSize);
        rotateButton.setY(height - padding - halfButtonSize);
        rotateButton.bringToFront();
        // 确保按钮在旋转后仍然可见
        deleteButton.bringToFront();
        bringToFrontButton.bringToFront();
    }
    
    public void setStickerResource(int resId) {
        stickerImageView.setImageResource(resId);
    }
    
    public android.graphics.drawable.Drawable getStickerDrawable() {
        return stickerImageView.getDrawable();
    }
    
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        updateButtonVisibility();
        invalidate();
    }
    
    public boolean isSelected() {
        return isSelected;
    }
    
    private void updateButtonVisibility() {
        deleteButton.setVisibility(isSelected ? VISIBLE : GONE);
        rotateButton.setVisibility(isSelected ? VISIBLE : GONE);
        bringToFrontButton.setVisibility(isSelected ? VISIBLE : GONE);
    }
    
    public void setOnStickerDeleteListener(OnStickerDeleteListener listener) {
        this.deleteListener = listener;
    }
    
    public void setOnStickerSelectedListener(OnStickerSelectedListener listener) {
        this.selectedListener = listener;
    }
    
    public void setOnStickerBringToFrontListener(OnStickerBringToFrontListener listener) {
        this.bringToFrontListener = listener;
    }
    
    public void setRotationAngle(float angle) {
        this.rotationAngle = angle % 360;
        if (rotationAngle < 0) rotationAngle += 360;
        applyTransform();
    }
    
    public float getRotationAngle() {
        return rotationAngle;
    }
    
    public void setScaleFactor(float scale) {
        this.scaleFactor = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        applyTransform();
    }
    
    public float getScaleFactor() {
        return scaleFactor;
    }
    
    private void applyTransform() {
        setRotation(rotationAngle);
        setScaleX(scaleFactor);
        setScaleY(scaleFactor);
        // 更新按钮位置，确保在旋转后仍然可见
        updateButtonPositions();
        // 刷新视图以重绘边框
        invalidate();
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isSelected) {
            return super.onTouchEvent(event);
        }
        
        // 检查是否点击在按钮上 - 使用全局坐标
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            
            // 检查删除按钮
            if (deleteButton.getVisibility() == VISIBLE) {
                float deleteX = deleteButton.getX();
                float deleteY = deleteButton.getY();
                // 考虑按钮的 padding
                float buttonSize = BUTTON_SIZE;
                if (x >= deleteX - 10 && x <= deleteX + buttonSize + 10 &&
                    y >= deleteY - 10 && y <= deleteY + buttonSize + 10) {
                    // 让按钮处理点击事件
                    return false;
                }
            }
            
            // 检查旋转按钮
            if (rotateButton.getVisibility() == VISIBLE) {
                float rotateX = rotateButton.getX();
                float rotateY = rotateButton.getY();
                float buttonSize = BUTTON_SIZE;
                if (x >= rotateX - 10 && x <= rotateX + buttonSize + 10 &&
                    y >= rotateY - 10 && y <= rotateY + buttonSize + 10) {
                    // 让按钮处理点击事件
                    return false;
                }
            }
            
            // 检查置顶按钮
            if (bringToFrontButton.getVisibility() == VISIBLE) {
                float frontX = bringToFrontButton.getX();
                float frontY = bringToFrontButton.getY();
                float buttonSize = BUTTON_SIZE;
                if (x >= frontX - 10 && x <= frontX + buttonSize + 10 &&
                    y >= frontY - 10 && y <= frontY + buttonSize + 10) {
                    // 让按钮处理点击事件
                    return false;
                }
            }
        }
        
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (touchMode == NONE) {
                    touchMode = DRAG;
                    lastTouchPoint.set(event.getX(), event.getY());
                    // 保存屏幕坐标，用于旋转后平移
                    lastScreenPoint.set(event.getRawX(), event.getRawY());
                    isDragging = false;
                    isScaling = false;
                    isRotating = false;
                }
                break;
                
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2 && touchMode == DRAG) {
                    touchMode = SCALE;
                    initialDistance = getDistance(event);
                    initialScale = scaleFactor;
                    initialRotationAngle = rotationAngle;
                    initialAngle = (float) Math.toDegrees(Math.atan2(
                        event.getY(1) - event.getY(0),
                        event.getX(1) - event.getX(0)
                    ));
                    initialPivot.set(getX() + getWidth() / 2f, getY() + getHeight() / 2f);
                    isScaling = false;
                    isRotating = false;
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (touchMode == DRAG && event.getPointerCount() == 1) {
                    // 单指拖动 - 使用屏幕坐标计算移动距离，避免旋转影响
                    // 直接使用屏幕坐标的差值，这样无论贴纸如何旋转，移动方向都是正确的
                    float dx = event.getRawX() - lastScreenPoint.x;
                    float dy = event.getRawY() - lastScreenPoint.y;
                    
                    // 限制在父容器内
                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) {
                        float newX = getX() + dx;
                        float newY = getY() + dy;
                        
                        // 边界检查 - 允许贴纸部分超出边界，但中心点不能太远
                        float minX = -getWidth() * 0.8f;
                        float maxX = parent.getWidth() - getWidth() * 0.2f;
                        float minY = -getHeight() * 0.8f;
                        float maxY = parent.getHeight() - getHeight() * 0.2f;
                        
                        newX = Math.max(minX, Math.min(newX, maxX));
                        newY = Math.max(minY, Math.min(newY, maxY));
                        
                        setX(newX);
                        setY(newY);
                    } else {
                        setX(getX() + dx);
                        setY(getY() + dy);
                    }
                    
                    // 更新坐标
                    lastTouchPoint.set(event.getX(), event.getY());
                    lastScreenPoint.set(event.getRawX(), event.getRawY());
                    isDragging = true;
                } else if (touchMode == SCALE && event.getPointerCount() == 2) {
                    // 双指操作：同时支持缩放和旋转，但优先判断主要操作
                    float newDistance = getDistance(event);
                    float currentAngle = (float) Math.toDegrees(Math.atan2(
                        event.getY(1) - event.getY(0),
                        event.getX(1) - event.getX(0)
                    ));
                    
                    // 计算距离变化和角度变化
                    float distanceChange = Math.abs(newDistance - initialDistance) / initialDistance;
                    float angleChange = Math.abs(currentAngle - initialAngle);
                    if (angleChange > 180) angleChange = 360 - angleChange;
                    
                    // 如果已经确定是缩放操作，继续缩放
                    if (isScaling) {
                        float scale = newDistance / initialDistance;
                        float newScale = initialScale * scale;
                        if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
                            scaleFactor = newScale;
                            applyTransform();
                        }
                    }
                    // 如果已经确定是旋转操作，继续旋转
                    else if (isRotating) {
                        float deltaAngle = currentAngle - initialAngle;
                        // 处理角度跨越180度的情况
                        if (deltaAngle > 180) deltaAngle -= 360;
                        if (deltaAngle < -180) deltaAngle += 360;
                        rotationAngle = (initialRotationAngle + deltaAngle) % 360;
                        if (rotationAngle < 0) rotationAngle += 360;
                        applyTransform();
                    }
                    // 如果还没确定操作类型，根据变化量判断
                    else {
                        // 距离变化更明显，执行缩放
                        if (distanceChange > 0.08f && distanceChange > angleChange / 30f) {
                            isScaling = true;
                            float scale = newDistance / initialDistance;
                            float newScale = initialScale * scale;
                            if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
                                scaleFactor = newScale;
                                applyTransform();
                            }
                        }
                        // 角度变化更明显，执行旋转
                        else if (angleChange > 8f && angleChange / 30f > distanceChange) {
                            isRotating = true;
                            float deltaAngle = currentAngle - initialAngle;
                            // 处理角度跨越180度的情况
                            if (deltaAngle > 180) deltaAngle -= 360;
                            if (deltaAngle < -180) deltaAngle += 360;
                            rotationAngle = (initialRotationAngle + deltaAngle) % 360;
                            if (rotationAngle < 0) rotationAngle += 360;
                            applyTransform();
                        }
                    }
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (touchMode == DRAG && !isDragging) {
                    // 点击选中
                    if (selectedListener != null) {
                        selectedListener.onSelected(this);
                    }
                    setSelected(true);
                }
                touchMode = NONE;
                isDragging = false;
                isScaling = false;
                isRotating = false;
                break;
        }
        
        return true;
    }
    
    private float getDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
