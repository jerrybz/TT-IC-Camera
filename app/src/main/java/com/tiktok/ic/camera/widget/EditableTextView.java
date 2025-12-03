package com.tiktok.ic.camera.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.tiktok.ic.camera.R;

public class EditableTextView extends FrameLayout {
    
    public EditText editText;
    private ImageButton deleteButton;
    private ImageButton rotateButton;
    private FrameLayout textContainer; // 文字容器（内层）
    
    private float rotationAngle = 0; // 旋转角度（0-360°）
    private float scaleFactor = 1.0f; // 缩放因子
    private PointF pivotPoint = new PointF(); // 旋转和缩放的中心点
    
    private boolean isSelected = false;
    private OnTextDeleteListener deleteListener;
    private OnTextSelectedListener selectedListener;
    
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    private static final float BUTTON_SIZE = 50; // 增大按钮尺寸，与StickerView一致
    private static final float BUTTON_PADDING = BUTTON_SIZE / 2; // 按钮padding，确保按钮完全显示
    private static final float BORDER_WIDTH = 3; // 边框宽度，与StickerView一致
    private static final float TEXT_CONTAINER_PADDING = 20; // 输入框到边框的padding
    
    // 边框绘制相关
    private Paint borderPaint;
    
    private int touchMode = NONE;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ROTATE_BUTTON = 2;
    private static final int SCALE = 3;
    
    private PointF lastTouchPoint = new PointF();
    private PointF lastScreenPoint = new PointF(); // 屏幕坐标，用于旋转后平移
    private float initialDistance = 0;
    private float initialRotation = 0;
    private float initialScale = 1.0f;
    private PointF initialPivot = new PointF();
    private PointF initialTouchPoint = new PointF();
    
    private ScaleGestureDetector scaleGestureDetector;
    private boolean isDragging = false;
    
    public interface OnTextDeleteListener {
        void onDelete(EditableTextView view);
    }
    
    public interface OnTextSelectedListener {
        void onSelected(EditableTextView view);
    }
    
    public EditableTextView(Context context) {
        super(context);
        init();
    }
    
    public EditableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public EditableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        // 设置padding，为按钮留出空间
        setPadding((int) BUTTON_PADDING, (int) BUTTON_PADDING, (int) BUTTON_PADDING, (int) BUTTON_PADDING);
        
        // 允许绘制（FrameLayout默认不绘制）
        setWillNotDraw(false);
        
        // 允许子视图超出边界显示（这样按钮可以显示在边框外）
        setClipChildren(false);
        setClipToPadding(false);
        
        // 初始化边框画笔
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0xFF4488FF); // 蓝色边框，与StickerView一致
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_WIDTH);
        
        // 创建文字容器（内层）
        textContainer = new FrameLayout(getContext());
        textContainer.setBackgroundColor(Color.TRANSPARENT);
        // 设置padding，让输入框和边框之间有间距
        textContainer.setPadding((int) TEXT_CONTAINER_PADDING, (int) TEXT_CONTAINER_PADDING, 
                                 (int) TEXT_CONTAINER_PADDING, (int) TEXT_CONTAINER_PADDING);
        
        // 创建可编辑的文本视图
        editText = new EditText(getContext());
        editText.setBackground(null);
        editText.setGravity(Gravity.CENTER);
        editText.setHint("点击输入文字");
        editText.setHintTextColor(0x80FFFFFF);
        editText.setTextColor(Color.WHITE);
        editText.setTextSize(24);
        editText.setSingleLine(false);
        editText.setMinLines(1);
        editText.setMaxLines(10);
        editText.setPadding(20, 20, 20, 20);
        
        // 添加虚线边框效果
        editText.setBackgroundResource(R.drawable.text_border);
        
        // 监听焦点变化，确保按钮始终显示
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (isSelected) {
                updateButtonVisibility();
            }
        });
        
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = Gravity.CENTER;
        textContainer.addView(editText, textParams);
        
        // 将文字容器添加到主容器
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParams.gravity = Gravity.CENTER;
        addView(textContainer, containerParams);
        
        // 创建删除按钮 - 位置在外框的右上角
        deleteButton = new ImageButton(getContext());
        deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
        deleteButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        deleteButton.setPadding(8, 8, 8, 8);
        deleteButton.setLayoutParams(new FrameLayout.LayoutParams(
            (int) BUTTON_SIZE,
            (int) BUTTON_SIZE
        ));
        deleteButton.setClickable(true);
        deleteButton.setFocusable(true);
        deleteButton.setElevation(20); // 提升按钮层级，确保可见
        // 设置白色圆形背景，与StickerView一致
        android.graphics.drawable.GradientDrawable deleteBg = new android.graphics.drawable.GradientDrawable();
        deleteBg.setColor(0xFFFFFFFF);
        deleteBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            deleteButton.setBackground(deleteBg);
        } else {
            deleteButton.setBackgroundDrawable(deleteBg);
        }
        deleteButton.setOnClickListener(v -> {
            if (deleteListener != null && isSelected) {
                deleteListener.onDelete(this);
            }
        });
        addView(deleteButton);
        
        // 创建旋转按钮 - 位置在外框的右下角
        rotateButton = new ImageButton(getContext());
        rotateButton.setImageResource(R.drawable.ic_rotate);
        rotateButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        rotateButton.setPadding(8, 8, 8, 8);
        rotateButton.setLayoutParams(new FrameLayout.LayoutParams(
            (int) BUTTON_SIZE,
            (int) BUTTON_SIZE
        ));
        rotateButton.setElevation(20); // 提升按钮层级，确保可见
        // 设置白色圆形背景，与StickerView一致
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
                initialRotation = rotationAngle;
                initialTouchPoint.set(event.getRawX(), event.getRawY());
                initialPivot.set(getX() + getWidth() / 2f, getY() + getHeight() / 2f);
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && touchMode == ROTATE_BUTTON) {
                // 计算视图中心在屏幕上的位置
                float centerX = getX() + getWidth() / 2f;
                float centerY = getY() + getHeight() / 2f;
                
                // 计算当前触摸点相对于视图中心的角度
                float currentDx = event.getRawX() - centerX;
                float currentDy = event.getRawY() - centerY;
                float currentAngle = (float) Math.toDegrees(Math.atan2(currentDy, currentDx));
                
                // 计算初始触摸点相对于视图中心的角度
                float initialDx = initialTouchPoint.x - centerX;
                float initialDy = initialTouchPoint.y - centerY;
                float initialAngle = (float) Math.toDegrees(Math.atan2(initialDy, initialDx));
                
                // 计算角度差
                float deltaAngle = currentAngle - initialAngle;
                
                // 处理角度跨越180度的情况
                if (deltaAngle > 180) deltaAngle -= 360;
                if (deltaAngle < -180) deltaAngle += 360;
                
                // 更新旋转角度
                rotationAngle = (initialRotation + deltaAngle) % 360;
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
        
        // 初始化缩放手势检测器（用于双指缩放）
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (touchMode == SCALE) {
                    float scale = detector.getScaleFactor();
                    float newScale = initialScale * scale;
                    if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
                        scaleFactor = newScale;
                        applyTransform();
                    }
                }
                return true;
            }
        });
        
        // 设置点击监听 - 点击文字框时选中（但不在EditText区域）
        setOnClickListener(v -> {
            // 这个监听器主要用于点击外框区域时选中
            if (selectedListener != null) {
                selectedListener.onSelected(this);
            }
            setSelected(true);
        });
        
        updateButtonVisibility();
    }
    
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        updateButtonVisibility();
        // 刷新视图以重绘边框
        invalidate();
    }
    
    public boolean isSelected() {
        return isSelected;
    }
    
    private void updateButtonVisibility() {
        // 选中时始终显示按钮，无论是否有焦点
        deleteButton.setVisibility(isSelected ? VISIBLE : GONE);
        rotateButton.setVisibility(isSelected ? VISIBLE : GONE);
    }
    
    public void setOnTextDeleteListener(OnTextDeleteListener listener) {
        this.deleteListener = listener;
    }
    
    public void setOnTextSelectedListener(OnTextSelectedListener listener) {
        this.selectedListener = listener;
    }
    
    // 文字内容相关方法
    public String getText() {
        return editText.getText().toString();
    }
    
    public void setText(String text) {
        editText.setText(text);
    }
    
    // 字体相关方法
    public void setTypeface(android.graphics.Typeface typeface) {
        editText.setTypeface(typeface);
    }
    
    public android.graphics.Typeface getTypeface() {
        return editText.getTypeface() != null ? editText.getTypeface() : android.graphics.Typeface.DEFAULT;
    }
    
    // 字号相关方法
    public void setTextSize(float size) {
        editText.setTextSize(size);
    }
    
    public float getTextSize() {
        return editText.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
    }
    
    // 颜色相关方法
    public void setTextColor(int color) {
        editText.setTextColor(color);
    }
    
    public int getTextColor() {
        return editText.getCurrentTextColor();
    }
    
    // 透明度相关方法
    public void setTextAlpha(int alpha) {
        int color = editText.getCurrentTextColor();
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        editText.setTextColor(Color.argb(alpha, r, g, b));
    }
    
    public int getTextAlpha() {
        return Color.alpha(editText.getCurrentTextColor());
    }
    
    // 旋转相关方法
    public void setRotationAngle(float angle) {
        this.rotationAngle = angle % 360;
        if (rotationAngle < 0) rotationAngle += 360;
        applyTransform();
    }
    
    public float getRotationAngle() {
        return rotationAngle;
    }
    
    // 缩放相关方法
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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            pivotPoint.set(getWidth() / 2f, getHeight() / 2f);
            setPivotX(pivotPoint.x);
            setPivotY(pivotPoint.y);
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
        deleteButton.setX(width - padding - halfButtonSize);
        deleteButton.setY(padding - halfButtonSize);
        deleteButton.bringToFront();
        
        // 旋转按钮 - 边框右下角，按钮中心对齐到角上
        rotateButton.setX(width - padding - halfButtonSize);
        rotateButton.setY(height - padding - halfButtonSize);
        rotateButton.bringToFront();
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
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        // 检查是否点击在按钮区域，如果是则让按钮处理
        if (isSelected) {
            // 检查删除按钮
            if (deleteButton.getVisibility() == VISIBLE) {
                float deleteX = deleteButton.getX();
                float deleteY = deleteButton.getY();
                float buttonSize = BUTTON_SIZE;
                if (x >= deleteX - 10 && x <= deleteX + buttonSize + 10 &&
                    y >= deleteY - 10 && y <= deleteY + buttonSize + 10) {
                    // 不拦截，让按钮处理
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
                    // 不拦截，让按钮处理
                    return false;
                }
            }
        }
        
        // 检查是否点击在EditText的实际内容区域（排除padding区域）
        // 计算EditText在父容器中的实际位置（考虑textContainer的padding）
        float editTextLeft = textContainer.getLeft() + TEXT_CONTAINER_PADDING + editText.getLeft();
        float editTextTop = textContainer.getTop() + TEXT_CONTAINER_PADDING + editText.getTop();
        float editTextRight = editTextLeft + editText.getWidth();
        float editTextBottom = editTextTop + editText.getHeight();
        
        if (x >= editTextLeft && x <= editTextRight &&
            y >= editTextTop && y <= editTextBottom) {
            // 不拦截，让EditText处理
            return false;
        }
        
        // 其他情况拦截（用于拖动，包括padding区域）
        return true;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isSelected) {
            return super.onTouchEvent(event);
        }
        
        float x = event.getX();
        float y = event.getY();
        
        // 检查是否点击在按钮上 - 使用本地坐标
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // 检查删除按钮
            if (deleteButton.getVisibility() == VISIBLE) {
                float deleteX = deleteButton.getX();
                float deleteY = deleteButton.getY();
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
        }
        
        // 检查是否点击在EditText的实际内容区域（排除padding区域）
        // 计算EditText在父容器中的实际位置（考虑textContainer的padding）
        float editTextLeft = textContainer.getLeft() + TEXT_CONTAINER_PADDING + editText.getLeft();
        float editTextTop = textContainer.getTop() + TEXT_CONTAINER_PADDING + editText.getTop();
        float editTextRight = editTextLeft + editText.getWidth();
        float editTextBottom = editTextTop + editText.getHeight();
        
        if (x >= editTextLeft && x <= editTextRight &&
            y >= editTextTop && y <= editTextBottom) {

            // 创建新的事件对象，坐标相对于editText
            MotionEvent editTextEvent = MotionEvent.obtain(event);
            editTextEvent.offsetLocation(-editTextLeft, -editTextTop);
            
            // 让EditText处理事件
            boolean handled = editText.onTouchEvent(editTextEvent);
            editTextEvent.recycle();
            
            // 如果EditText处理了事件（比如点击获得焦点），就不继续处理拖动
            if (handled) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // 确保选中状态
                    if (selectedListener != null) {
                        selectedListener.onSelected(this);
                    }
                    setSelected(true);
                    // 请求焦点并显示键盘
                    editText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
                return true;
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
                }
                break;
                
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2 && touchMode == DRAG) {
                    touchMode = SCALE;
                    initialDistance = getDistance(event);
                    initialRotation = rotationAngle;
                    initialScale = scaleFactor;
                    initialPivot.set(pivotPoint.x, pivotPoint.y);
                    getMidPoint(lastTouchPoint, event);
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (touchMode == DRAG && event.getPointerCount() == 1) {
                    // 单指拖动 - 使用屏幕坐标计算移动距离，避免旋转影响
                    // 直接使用屏幕坐标的差值，这样无论文字框如何旋转，移动方向都是正确的
                    float dx = event.getRawX() - lastScreenPoint.x;
                    float dy = event.getRawY() - lastScreenPoint.y;
                    
                    // 计算移动距离
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    
                    if (distance > 5) { // 拖动阈值
                        isDragging = true;
                        // 获取父容器
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            float newX = getX() + dx;
                            float newY = getY() + dy;
                            
                            // 边界检查 - 允许文字框部分超出边界，但中心点不能太远
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
                    }
                    
                    // 更新坐标
                    lastTouchPoint.set(event.getX(), event.getY());
                    lastScreenPoint.set(event.getRawX(), event.getRawY());
                } else if (touchMode == SCALE && event.getPointerCount() == 2) {
                    // 双指缩放 - 只缩放，不旋转
                    float newDistance = getDistance(event);
                    float scale = newDistance / initialDistance;
                    float newScale = initialScale * scale;
                    
                    if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
                        scaleFactor = newScale;
                        applyTransform();
                    }
                    
                    // 更新中点位置
                    getMidPoint(lastTouchPoint, event);
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
                break;
        }
        
        return true;
    }
    
    private float getDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    private void getMidPoint(PointF point, MotionEvent event) {
        point.set(
            (event.getX(0) + event.getX(1)) / 2,
            (event.getY(0) + event.getY(1)) / 2
        );
    }
    
    // 获取文字在图片上的绘制信息（用于保存时绘制到图片上）
    public TextDrawInfo getTextDrawInfo(float imageWidth, float imageHeight, float viewWidth, float viewHeight) {
        TextDrawInfo info = new TextDrawInfo();
        info.text = getText();
        
        // 计算文字视图中心点在容器中的位置
        float centerX = getX() + getWidth() / 2f;
        float centerY = getY() + getHeight() / 2f;
        
        // 转换为图片坐标
        info.x = (centerX / viewWidth) * imageWidth;
        info.y = (centerY / viewHeight) * imageHeight;
        info.rotation = rotationAngle;
        info.scale = scaleFactor;
        info.textSize = getTextSize() * scaleFactor;
        info.color = getTextColor();
        info.typeface = getTypeface();
        return info;
    }
    
    public static class TextDrawInfo {
        public String text;
        public float x;
        public float y;
        public float rotation;
        public float scale;
        public float textSize;
        public int color;
        public android.graphics.Typeface typeface;
    }
}
