package com.example.barberlink.Manager;

import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.barberlink.Helper.StartSnapHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VegaLayoutManager extends RecyclerView.LayoutManager {

    private int scroll = 0;
    private SparseArray<Rect> locationRects = new SparseArray<>();
    private SparseBooleanArray attachedItems = new SparseBooleanArray();
    private ArrayMap<String, Boolean> expandedStateItems = new ArrayMap<>();
    private List<String> itemUID = new ArrayList<>();

    private int expandedHeight = -1;
    private int collapseHeight = -1;
    private boolean needSnap = false;
    private int lastDy = 0;
    private int maxScroll = -1;
    private RecyclerView.Adapter adapter;
    private RecyclerView.Recycler recycler;


    public VegaLayoutManager() {
        setAutoMeasureEnabled(true);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void updateExpandedState(String uid, boolean isExpanded, int newHeight) {
        Log.d("TestCLickMore", "position: " + uid + " isExpanded: " + isExpanded + " newHeight: " + newHeight);
        Log.d("VegaManage", "123");
        expandedStateItems.put(uid, isExpanded);
        // Simpan tinggi baru
//        if (isExpanded && expandedHeight == -1) {
//            expandedHeight = newHeight;
//        }
        // height = newHeight;

        // Perbarui tata letak lokasi item
        // buildLocationRects();
        requestLayout();
    }

    public void setExpandedState(
        List<String> itemUID,
        Map<String, Boolean> expandedState,
        boolean requestLayoutNow
    ) {
        this.itemUID = new ArrayList<>(itemUID);
        this.expandedStateItems = new ArrayMap<>();
        this.expandedStateItems.putAll(expandedState);

        if (requestLayoutNow) {
//            requestLayout();
        }
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        this.adapter = newAdapter;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        this.recycler = recycler; // 二话不说，先把recycler保存了
        if (state.isPreLayout()) {
            return;
        }

        buildLocationRects();

        // 先回收放到缓存，后面会再次统一layout
        detachAndScrapAttachedViews(recycler);
        layoutItemsOnCreate(recycler);
    }

    private void buildLocationRects() {
        locationRects.clear();
        attachedItems.clear();

        Log.d("VegaManage", "456");
        int tempPosition = getPaddingTop();
        int itemCount = itemUID.size();
        Log.d("VegaManage", "count: " + itemCount + " size: " + itemUID.size());
        for (int i = 0; i < itemCount; i++) {
            // 1. 先计算出itemWidth和itemHeight
            int itemHeight;

            // Tambahkan margin ekstra jika item di-expand
            Log.d("VegaManage", "numberItem: " + i + " expand: "+ expandedStateItems.get(itemUID.get(i)) + " expandedHeight: "+ expandedHeight);
            if (Boolean.TRUE.equals(expandedStateItems.get(itemUID.get(i)))) {
                if (expandedHeight != -1) {
                    itemHeight = expandedHeight;
                    Log.d("VegaManage", "itemHeight A: "+ itemHeight);
                } else {
                    View itemView = recycler.getViewForPosition(i);
                    addView(itemView);
                    measureChildWithMargins(itemView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    itemHeight = getDecoratedMeasuredHeight(itemView);
                    expandedHeight = itemHeight;
                    Log.d("VegaManage", "itemHeight B: "+ itemHeight);
                }
            } else {
                if (collapseHeight != -1) {
                    itemHeight = collapseHeight;
                    Log.d("VegaManage", "itemHeight A: "+ itemHeight);
                } else {
                    View itemView = recycler.getViewForPosition(i);
                    addView(itemView);
                    measureChildWithMargins(itemView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    itemHeight = getDecoratedMeasuredHeight(itemView);
                    collapseHeight = itemHeight;
                    Log.d("VegaManage", "itemHeight D: "+ itemHeight);
                }
            }

            // 2. 组装Rect并保存
            Rect rect = new Rect();
            rect.left = getPaddingLeft();
            rect.top = tempPosition;
            rect.right = getWidth() - getPaddingRight();
            rect.bottom = rect.top + itemHeight;
            locationRects.put(i, rect);
            attachedItems.put(i, false);
            tempPosition = tempPosition + itemHeight;
        }

        if (itemCount == 0) {
            maxScroll = 0;
        } else {
            computeMaxScroll();
        }
    }

    /**
     * 对外提供接口，找到第一个可视view的index
     */
    public int findFirstVisibleItemPosition() {
        int count = locationRects.size();
        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        for (int i = 0; i < count; i++) {
            if (Rect.intersects(displayRect, locationRects.get(i)) &&
                    attachedItems.get(i)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 计算可滑动的最大值
     */
    private void computeMaxScroll() {
        maxScroll = locationRects.get(locationRects.size() - 1).bottom - getHeight();
        if (maxScroll < 0) {
            maxScroll = 0;
            return;
        }

        int itemCount = itemUID.size();
        int screenFilledHeight = 0;
        for (int i = itemCount - 1; i >= 0; i--) {
            Rect rect = locationRects.get(i);
            int itemHeight = rect.bottom - rect.top;
            screenFilledHeight = screenFilledHeight + itemHeight;
            if (screenFilledHeight > getHeight()) {
                if (i == itemCount - 1) {
                    // Item terakhir sudah lebih besar dari layar, 
                    // tidak perlu tambah extraSnapHeight agar tidak scroll off.
                    break;
                }
                int extraSnapHeight = getHeight() - (screenFilledHeight - itemHeight);
                maxScroll = maxScroll + extraSnapHeight;
                break;
            }
        }
    }

    /**
     * 初始化的时候，layout子View
     */
    private void layoutItemsOnCreate(RecyclerView.Recycler recycler) {
        int itemCount = itemUID.size();
        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        for (int i = 0; i < itemCount; i++) {
            Rect thisRect = locationRects.get(i);
            if (Rect.intersects(displayRect, thisRect)) {
                View childView = recycler.getViewForPosition(i);
                addView(childView);
                measureChildWithMargins(childView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                layoutItem(childView, locationRects.get(i));
                attachedItems.put(i, true);
                childView.setPivotY(0);
                childView.setPivotX(childView.getMeasuredWidth() / 2);
                if (thisRect.top - scroll > getHeight()) {
                    break;
                }
            }
        }
    }


    /**
     * 初始化的时候，layout子View
     */
    private void layoutItemsOnScroll() {
        int childCount = getChildCount();
        // 1. 已经在屏幕上显示的child
        int itemCount = itemUID.size();
        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        int firstVisiblePosition = -1;
        int lastVisiblePosition = -1;
        for (int i = childCount - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            int position = getPosition(child);
            if (!Rect.intersects(displayRect, locationRects.get(position))) {
                // 回收滑出屏幕的View
                removeAndRecycleView(child, recycler);
                attachedItems.put(position, false);
            } else {
                // Item还在显示区域内，更新滑动后Item的位置
                if (lastVisiblePosition < 0) {
                    lastVisiblePosition = position;
                }

                if (firstVisiblePosition < 0) {
                    firstVisiblePosition = position;
                } else {
                    firstVisiblePosition = Math.min(firstVisiblePosition, position);
                }

                layoutItem(child, locationRects.get(position)); //更新Item位置
            }
        }

        // 2. 复用View处理
        if (firstVisiblePosition > 0) {
            // 往前搜索复用
            for (int i = firstVisiblePosition - 1; i >= 0; i--) {
                if (Rect.intersects(displayRect, locationRects.get(i)) &&
                        !attachedItems.get(i)) {
                    reuseItemOnSroll(i, true);
                } else {
                    break;
                }
            }
        }
        // 往后搜索复用
        for (int i = lastVisiblePosition + 1; i < itemCount; i++) {
            if (Rect.intersects(displayRect, locationRects.get(i)) &&
                    !attachedItems.get(i)) {
                reuseItemOnSroll(i, false);
            } else {
                break;
            }
        }
    }

    /**
     * 复用position对应的View
     */
    private void reuseItemOnSroll(int position, boolean addViewFromTop) {
        View scrap = recycler.getViewForPosition(position);
        measureChildWithMargins(scrap, 0, 0);
        scrap.setPivotY(0);
        scrap.setPivotX(scrap.getMeasuredWidth() / 2);

        if (addViewFromTop) {
            addView(scrap, 0);
        } else {
            addView(scrap);
        }
        // 将这个Item布局出来
        layoutItem(scrap, locationRects.get(position));
        attachedItems.put(position, true);
    }


    private void layoutItem(View child, Rect rect) {
        int topDistance = scroll - rect.top;
        int layoutTop, layoutBottom;
        int itemHeight = rect.bottom - rect.top;

        Log.d("OverScroll", "topDistance: " + topDistance + " itemHeight: " + itemHeight + " scroll: " + scroll);
        if (topDistance < itemHeight && topDistance > 0) {
            float rate1 = (float) topDistance / itemHeight;
            float rate2 = 1 - rate1 * rate1 / 3;
            float rate3 = 1 - rate1 * rate1;
            child.setScaleX(rate2);
            child.setScaleY(rate2);
            child.setAlpha(rate3);
            layoutTop = 0;
            layoutBottom = itemHeight;
        } else {
            child.setScaleX(1);
            child.setScaleY(1);
            child.setAlpha(1);

            layoutTop = rect.top - scroll;
            layoutBottom = rect.bottom - scroll;
        }
        layoutDecorated(child, rect.left, layoutTop, rect.right, layoutBottom);
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (itemUID.size() == 0 || dy == 0) {
            return 0;
        }
        int travel = dy;
        if (dy + scroll < 0) {
            travel = -scroll;
        } else if (dy + scroll > maxScroll) {
            travel = maxScroll - scroll;
        }
        scroll += travel; //累计偏移量
        lastDy = dy;
        if (!state.isPreLayout() && getChildCount() > 0) {
            layoutItemsOnScroll();
        }

        return travel;
    }

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        new StartSnapHelper().attachToRecyclerView(view);
    }

    @Override
    public void onScrollStateChanged(int state) {
        if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
            needSnap = true;
        }
        super.onScrollStateChanged(state);
    }

    public int getSnapHeight() {
        if (!needSnap) {
            return 0;
        }
        needSnap = false;

        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        int itemCount = itemUID.size();
        for (int i = 0; i < itemCount; i++) {
            Rect itemRect = locationRects.get(i);
            if (displayRect.intersect(itemRect)) {

                if (lastDy > 0) {
                    // scroll变大，属于列表往下走，往下找下一个为snapView
                    if (i < itemCount - 1) {
                        Rect nextRect = locationRects.get(i + 1);
                        return nextRect.top - displayRect.top;
                    }
                }
                return itemRect.top - displayRect.top;
            }
        }
        return 0;
    }

    public View findSnapView() {
        if (getChildCount() > 0) {
            return getChildAt(0);
        }
        return null;
    }
}
