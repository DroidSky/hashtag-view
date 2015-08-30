package com.greenfrvr.hashtagview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.support.annotation.ColorRes;
import android.support.annotation.DimenRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import timber.log.Timber;

/**
 * Created by greenfrvr
 */
public class HashtagView<T> extends LinearLayout {

    public static final int GRAVITY_LEFT = Gravity.LEFT;
    public static final int GRAVITY_RIGHT = Gravity.RIGHT;
    public static final int GRAVITY_CENTER = Gravity.CENTER;

    public static final int MODE_STRETCH = 1;
    public static final int MODE_WRAP = 0;

    private final LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    private TagsClickListener listener;

    private List<ItemData> data;
    private Multimap<Integer, ItemData> viewMap;

    private int itemMargin;
    private int itemPaddingLeft;
    private int itemPaddingRight;
    private int itemPaddingTop;
    private int itemPaddingBottom;
    private int minItemWidth;
    private int itemTextColor;
    private int itemTextGravity;
    private float itemTextSize;

    private int rowMargin;
    private int rowGravity;
    private int rowMode;
    private int backgroundDrawable;
    private int foregroundDrawable;

    private float totalItemsWidth = 0;

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            Timber.i("Pre draw listener shoots");
            wrap();
            sort();
            draw();
            getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            return true;
        }
    };

    public HashtagView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        extractAttributes(attrs);

        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    private void extractAttributes(AttributeSet attrs) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.HashtagView, 0, 0);
        try {
            itemMargin = a.getDimensionPixelOffset(R.styleable.HashtagView_itemMargin, getResources().getDimensionPixelOffset(R.dimen.default_item_margin));
            itemPaddingLeft = a.getDimensionPixelOffset(R.styleable.HashtagView_itemPaddingLeft, getResources().getDimensionPixelOffset(R.dimen.default_item_margin));
            itemPaddingRight = a.getDimensionPixelOffset(R.styleable.HashtagView_itemPaddingLeft, getResources().getDimensionPixelOffset(R.dimen.default_item_margin));
            itemPaddingTop = a.getDimensionPixelOffset(R.styleable.HashtagView_itemPaddingLeft, getResources().getDimensionPixelOffset(R.dimen.default_item_margin));
            itemPaddingBottom = a.getDimensionPixelOffset(R.styleable.HashtagView_itemPaddingLeft, getResources().getDimensionPixelOffset(R.dimen.default_item_margin));
            minItemWidth = a.getDimensionPixelOffset(R.styleable.HashtagView_itemMinWidth, getResources().getDimensionPixelOffset(R.dimen.min_item_width));
            rowMargin = a.getDimensionPixelOffset(R.styleable.HashtagView_rowMargin, getResources().getDimensionPixelOffset(R.dimen.default_row_margin));
            itemTextSize = a.getDimension(R.styleable.HashtagView_itemTextSize, getResources().getDimension(R.dimen.default_text_size));

            itemTextGravity = a.getInt(R.styleable.HashtagView_itemTextGravity, Gravity.CENTER);
            rowMode = a.getInt(R.styleable.HashtagView_rowMode, 0);
            rowGravity = a.getInt(R.styleable.HashtagView_rowGravity, Gravity.CENTER);

            backgroundDrawable = a.getResourceId(R.styleable.HashtagView_itemBackground, 0);
            foregroundDrawable = a.getResourceId(R.styleable.HashtagView_itemForeground, 0);

            itemTextColor = a.getColor(R.styleable.HashtagView_itemTextColor, Color.BLACK);
        } finally {
            a.recycle();
        }
    }

    public void setData(List<String> list) {
        data = new ArrayList<>(list.size());
        for (String item : list) {
            data.add(new ItemData<>(item));
        }
        Collections.sort(data, new Comparator<ItemData>() {
            @Override
            public int compare(ItemData lhs, ItemData rhs) {
                return lhs.title.compareTo(rhs.title);
            }
        });
    }

    public void setData(List<T> list, DataTransform<T> transformer) {
        data = new ArrayList<>(list.size());
        for (T item : list) {
            data.add(new ItemData<>(item, transformer.prepare(item)));
        }
        Collections.sort(data, new Comparator<ItemData>() {
            @Override
            public int compare(ItemData lhs, ItemData rhs) {
                return lhs.title.compareTo(rhs.title);
            }
        });
    }

    public void setOnTagClickListener(TagsClickListener listener) {
        this.listener = listener;
    }

    public void wrap() {
        if (data == null || data.isEmpty()) return;

        int itemTotalOffset = itemPaddingLeft + itemPaddingRight + 2 * itemMargin;

        for (ItemData item : data) {
            View view = inflateTagView(item);

            TextView textView = (TextView) view.findViewById(R.id.text);
            textView.setText(item.title);
            textView.setTextColor(itemTextColor);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = itemTextGravity;
            textView.setLayoutParams(params);

            float width = textView.getPaint().measureText(item.title) + itemTotalOffset;
            width = Math.max(width, minItemWidth);
            item.view = view;
            item.width = width;

            totalItemsWidth += width;
        }
    }

    public void sort() {
        int rowsCount = (int) Math.ceil(totalItemsWidth / (getWidth() - getPaddingLeft() - getPaddingRight()));
        final int[] rowsWidth = new int[rowsCount];
        Timber.i("Total items width is %f, and width of widget is %d", totalItemsWidth, (getWidth() - getPaddingLeft() - getPaddingRight()));
        Timber.i("Rows count - %d", rowsCount);

        viewMap = ArrayListMultimap.create(rowsCount, data.size());
        while (!data.isEmpty()) {
            rowIteration:
            for (int i = 0; i < rowsCount; i++) {
                Iterator<ItemData> iterator = data.iterator();

                while (iterator.hasNext()) {
                    ItemData item = iterator.next();
                    Timber.i("Row width %1$f, total width - %2$d", rowsWidth[i] + item.width, getWidth());

                    if (rowsWidth[i] + item.width < getWidth()) {
                        Timber.i("Tag with width %f fits", item.width);
                        rowsWidth[i] += item.width;
                        viewMap.put(i, item);
                        data.remove(item);
                        continue rowIteration;
                    }
                }
            }
        }
    }

    public void draw() {
        if (viewMap == null || viewMap.isEmpty()) return;
        removeAllViews();

        for (Integer key : viewMap.keySet()) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setOrientation(HORIZONTAL);
            rowLayout.setLayoutParams(rowParams);
            rowLayout.setGravity(rowGravity);
            rowLayout.setWeightSum(viewMap.get(key).size());
            addView(rowLayout);

            List<ItemData> itemsList = new ArrayList<>(viewMap.get(key));
            Collections.shuffle(itemsList);
            for (ItemData item : itemsList) {
                rowLayout.addView(item.view, getItemLayoutParams());
            }
        }
    }

    private View inflateTagView(final ItemData item) {
        ViewGroup itemLayout = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.layout_item, this, false);
        if(foregroundDrawable != 0) ((FrameLayout) itemLayout).setForeground(ContextCompat.getDrawable(getContext(), foregroundDrawable));
        itemLayout.setBackgroundResource(backgroundDrawable);
        itemLayout.setPadding(itemPaddingLeft, itemPaddingTop, itemPaddingRight, itemPaddingBottom);
        itemLayout.setMinimumWidth(minItemWidth);

        itemLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.i("Item clicked");
                if (listener != null) {
                    if (item.data == null) {
                        listener.onItemClicked(item.title);
                    } else {
                        listener.onItemClicked(item.data);
                    }
                }
            }
        });
        return itemLayout;
    }

    private LayoutParams getItemLayoutParams() {
        LayoutParams itemParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.bottomMargin = itemMargin;
        itemParams.topMargin = itemMargin;
        itemParams.leftMargin = rowMargin;
        itemParams.rightMargin = rowMargin;
        itemParams.weight = rowMode;
        return itemParams;
    }

    public void setItemMargin(int itemMargin) {
        this.itemMargin = itemMargin;
    }

    public void setItemMarginRes(@DimenRes int marginRes) {
        this.itemMargin = getResources().getDimensionPixelOffset(marginRes);
    }

    public void setItemPadding(int left, int right, int top, int bottom) {
        this.itemPaddingLeft = left;
        this.itemPaddingRight = right;
        this.itemPaddingTop = top;
        this.itemPaddingBottom = bottom;
    }

    public void setItemPaddingRes(@DimenRes int left, @DimenRes int right, @DimenRes int top, @DimenRes int bottom) {
        this.itemPaddingLeft = getResources().getDimensionPixelOffset(left);
        this.itemPaddingRight = getResources().getDimensionPixelOffset(right);
        this.itemPaddingTop = getResources().getDimensionPixelOffset(top);
        this.itemPaddingBottom = getResources().getDimensionPixelOffset(bottom);
    }

    public void setMinItemWidth(int minWidth) {
        this.minItemWidth = minWidth;
    }

    public void setMinItemWidthRes(@DimenRes int minWidth) {
        this.minItemWidth = getResources().getDimensionPixelOffset(minWidth);
    }

    public void setItemTextColor(int textColor) {
        this.itemTextColor = textColor;
    }

    public void setItemTextColorRes(@ColorRes int textColor) {
        this.itemTextColor = getResources().getColor(textColor);
    }

    public void setItemTextGravity(int itemTextGravity) {
        this.itemTextGravity = itemTextGravity;
    }

    public void setItemTextSize(float textSize) {
        this.itemTextSize = textSize;
    }

    public void setItemTextSizeRes(@DimenRes int textSize) {
        this.itemTextSize = getResources().getDimension(textSize);
    }

    public void setRowMargin(int rowMargin) {
        this.rowMargin = rowMargin;
    }

    public void setRowMarginRes(@DimenRes int rowMargin) {
        this.rowMargin = getResources().getDimensionPixelOffset(rowMargin);
    }

    public void setRowGravity(int rowGravity) {
        this.rowGravity = rowGravity;
    }

    public void setRowMode(int rowMode) {
        this.rowMode = rowMode;
    }

    public void setBackgroundDrawable(@DrawableRes int backgroundDrawable) {
        this.backgroundDrawable = backgroundDrawable;
    }

    public void setBackgroundColor(@ColorRes int backgroundDrawable) {
        this.backgroundDrawable = backgroundDrawable;
    }

    public void setForegroundDrawable(@DrawableRes int foregroundDrawable) {
        this.foregroundDrawable = foregroundDrawable;
    }

    public class ItemData<T> {
        protected T data;
        protected String title;

        protected View view;
        protected float width;

        public ItemData(String title) {
            this.title = title;
        }

        public ItemData(T data, String title) {
            this.data = data;
            this.title = title;
        }
    }

    public interface TagsClickListener {
        void onItemClicked(Object item);
    }

    public interface DataTransform<T> {
        String prepare(T item);
    }
}