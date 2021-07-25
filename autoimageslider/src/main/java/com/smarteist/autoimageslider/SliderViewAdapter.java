package com.smarteist.autoimageslider;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;
import java.util.Queue;

public abstract class SliderViewAdapter<VH extends SliderViewAdapter.ViewHolder> extends PagerAdapter {

    private DataSetListener dataSetListener;

    //Default View holder class
    public static abstract class ViewHolder {
        public final View itemView;

        public ViewHolder(View itemView) {
            this.itemView = itemView;
        }
    }

    private Queue<VH> destroyedItems = new LinkedList<>();

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        VH viewHolder = destroyedItems.poll();
        if (viewHolder == null) {
            viewHolder = onCreateViewHolder(container);
        }
        // Re-add existing view before rendering so that we can make change inside getView()
        container.addView(viewHolder.itemView);
        onBindViewHolder(viewHolder, position);

        return viewHolder;
    }

    @Override
    public final void destroyItem(ViewGroup container, int position, @NonNull Object object) {
        VH viewHolder = (VH)object;
        onDestroyViewHolder(viewHolder);
        container.removeView(viewHolder.itemView);
        destroyedItems.add(viewHolder);
    }

    @Override
    public final boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return ((VH) object).itemView == view;
    }

    @Override
    public int getItemPosition(Object object) {
        return POSITION_NONE;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        if (this.dataSetListener != null) {
            dataSetListener.dataSetChanged();
        }
    }

    /**
     * Create a new view holder
     *
     * @param parent wrapper view
     * @return view holder
     */
    public abstract VH onCreateViewHolder(ViewGroup parent);

    /**
     * Bind data at position into viewHolder
     *
     * @param viewHolder item view holder
     * @param position   item position
     */
    public abstract void onBindViewHolder(VH viewHolder, int position);

    public abstract void onDestroyViewHolder(VH viewHolder);

    void dataSetChangedListener(SliderViewAdapter.DataSetListener dataSetListener) {
        this.dataSetListener = dataSetListener;
    }

    interface DataSetListener {
        void dataSetChanged();
    }

}