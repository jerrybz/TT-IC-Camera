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
    private static final float BUTTON_SIZE = 40;
    private static final float BUTTON_PADDING = BUTTON_SIZE / 2; // 按钮padding，确保按钮完全显示
    
    private int touchMode = NONE;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ROTATE_BUTTON = 2;
    private static final int SCALE = 3;
    
    private PointF lastTouchPoint = new PointF();
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
        
        // 创建文字容器（内层）
        textContainer = new FrameLayout(getContext());
        textContainer.setBackgroundColor(Color.TRANSPARENT);
        
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
        deleteButton.setBackgroundColor(0xFFFFFFFF);
        deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
        deleteButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        deleteButton.setPadding(8, 8, 8, 8);
        deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(this);
            }
        });
        
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(
            (int) BUTTON_SIZE,
            (int) BUTTON_SIZE
        );
        // 按钮中心对应外框右上角
        deleteParams.gravity = Gravity.TOP | Gravity.END;
        deleteParams.setMargins(0, 0, 0, 0);
        addView(deleteButton, deleteParams);
        
        // 创建旋转按钮 - 位置在外框的右下角
        rotateButton = new ImageButton(getContext());
        rotateButton.setBackgroundColor(0xFFFFFFFF);
        rotateButton.setImageResource(R.drawable.ic_rotate);
        rotateButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        rotateButton.setPadding(8, 8, 8, 8);
        
        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(
            (int) BUTTON_SIZE,
            (int) BUTTON_SIZE
        );
        // 按钮中心对应外框右下角
        rotateParams.gravity = Gravity.BOTTOM | Gravity.END;
        rotateParams.setMargins(0, 0, 0, 0);
        addView(rotateButton, rotateParams);
        
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
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            pivotPoint.set(getWidth() / 2f, getHeight() / 2f);
            setPivotX(pivotPoint.x);
            setPivotY(pivotPoint.y);
        }
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        // 检查是否点击了按钮区域，如果是则拦截
        if (isSelected) {
            float deleteX = getWidth();
            float deleteY = 0;
            float rotateX = getWidth();
            float rotateY = getHeight();
            
            float deleteDist = (float) Math.sqrt(
                (x - deleteX) * (x - deleteX) + (y - deleteY) * (y - deleteY)
            );
            float rotateDist = (float) Math.sqrt(
                (x - rotateX) * (x - rotateX) + (y - rotateY) * (y - rotateY)
            );
            
            if (deleteDist <= BUTTON_SIZE / 2 || rotateDist <= BUTTON_SIZE / 2) {
                return true; // 拦截按钮点击
            }
        }
        
        // 检查是否点击在EditText区域
        if (textContainer.getLeft() <= x && x <= textContainer.getRight() &&
            textContainer.getTop() <= y && y <= textContainer.getBottom()) {
            // 不拦截，让EditText处理
            return false;
        }
        
        // 其他情况拦截（用于拖动）
        return true;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        
        float x = event.getX();
        float y = event.getY();
        
        // 检查是否点击了按钮区域
        if (isSelected) {
            // 删除按钮位置（右上角）
            float deleteX = getWidth();
            float deleteY = 0;
            
            // 旋转按钮位置（右下角）
            float rotateX = getWidth();
            float rotateY = getHeight();
            
            // 计算到按钮中心的距离
            float deleteDist = (float) Math.sqrt(
                (x - deleteX) * (x - deleteX) + (y - deleteY) * (y - deleteY)
            );
            float rotateDist = (float) Math.sqrt(
                (x - rotateX) * (x - rotateX) + (y - rotateY) * (y - rotateY)
            );
            
            if (deleteDist <= BUTTON_SIZE / 2) {
                // 点击删除按钮
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    deleteButton.performClick();
                }
                return true;
            } else if (rotateDist <= BUTTON_SIZE / 2) {
                // 点击旋转按钮，开始旋转模式
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchMode = ROTATE_BUTTON;
                    initialRotation = rotationAngle;
                    initialTouchPoint.set(x, y);
                    return true;
                } else if (touchMode == ROTATE_BUTTON) {
                    // 继续旋转
                    handleRotateButton(event, x, y);
                    return true;
                }
            }
        }
        
        // 检查是否点击在EditText区域
        if (textContainer.getLeft() <= x && x <= textContainer.getRight() &&
            textContainer.getTop() <= y && y <= textContainer.getBottom()) {
            // 将坐标转换为相对于editText的坐标
            float editTextX = x - textContainer.getLeft() - editText.getLeft();
            float editTextY = y - textContainer.getTop() - editText.getTop();
            
            // 创建新的事件对象，坐标相对于editText
            MotionEvent editTextEvent = MotionEvent.obtain(event);
            editTextEvent.offsetLocation(-textContainer.getLeft() - editText.getLeft(), 
                                        -textContainer.getTop() - editText.getTop());
            
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
                if (event.getPointerCount() == 1) {
                    // 准备拖动
                    touchMode = DRAG;
                    lastTouchPoint.set(x, y);
                    isDragging = false;
                    if (selectedListener != null) {
                        selectedListener.onSelected(this);
                    }
                    setSelected(true);
                } else if (event.getPointerCount() == 2) {
                    // 双指缩放
                    touchMode = SCALE;
                    initialDistance = getDistance(event);
                    initialRotation = rotationAngle;
                    initialScale = scaleFactor;
                    initialPivot.set(pivotPoint.x, pivotPoint.y);
                    getMidPoint(lastTouchPoint, event);
                }
                break;
                
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2) {
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
                    float dx = x - lastTouchPoint.x;
                    float dy = y - lastTouchPoint.y;
                    
                    // 计算移动距离
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    
                    if (distance > 5) { // 拖动阈值
                        isDragging = true;
                        // 获取父容器
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            // 直接使用父容器坐标系进行拖动
                            float newX = getX() + dx;
                            float newY = getY() + dy;
                            
                            // 限制在父容器内（考虑旋转后的边界）
                            float viewWidth = getWidth();
                            float viewHeight = getHeight();
                            
                            // 由于有旋转，需要计算旋转后的实际占用空间
                            float cos = (float) Math.abs(Math.cos(Math.toRadians(rotationAngle)));
                            float sin = (float) Math.abs(Math.sin(Math.toRadians(rotationAngle)));
                            float rotatedWidth = viewWidth * cos + viewHeight * sin;
                            float rotatedHeight = viewWidth * sin + viewHeight * cos;
                            
                            float minX = -rotatedWidth / 2;
                            float minY = -rotatedHeight / 2;
                            float maxX = parent.getWidth() - rotatedWidth / 2;
                            float maxY = parent.getHeight() - rotatedHeight / 2;
                            
                            // 限制位置，但允许部分超出边界
                            newX = Math.max(minX, Math.min(newX, maxX));
                            newY = Math.max(minY, Math.min(newY, maxY));
                            
                            setX(newX);
                            setY(newY);
                        }
                    }
                    
                    lastTouchPoint.set(x, y);
                } else if (touchMode == SCALE && event.getPointerCount() == 2) {
                    // 双指缩放 - 只缩放，不旋转
                    float newDistance = getDistance(event);
                    float scale = newDistance / initialDistance;
                    float newScale = initialScale * scale;
                    
                    if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
                        scaleFactor = newScale;
                        applyTransform();
                    }
                    
                    // 更新中点位置，但不进行旋转计算
                    getMidPoint(lastTouchPoint, event);
                } else if (touchMode == ROTATE_BUTTON) {
                    handleRotateButton(event, x, y);
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (touchMode == DRAG) {
                    isDragging = false;
                }
                touchMode = NONE;
                break;
        }
        
        return true;
    }
    
    private void handleRotateButton(MotionEvent event, float x, float y) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        
        // 计算当前触摸点相对于中心的角度
        float dx = x - centerX;
        float dy = y - centerY;
        float currentAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        
        // 计算初始触摸点相对于中心的角度
        float initialDx = initialTouchPoint.x - centerX;
        float initialDy = initialTouchPoint.y - centerY;
        float initialAngle = (float) Math.toDegrees(Math.atan2(initialDy, initialDx));
        
        // 计算角度差
        float deltaAngle = currentAngle - initialAngle;
        
        // 更新旋转角度
        rotationAngle = (initialRotation + deltaAngle) % 360;
        if (rotationAngle < 0) rotationAngle += 360;
        
        applyTransform();
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
