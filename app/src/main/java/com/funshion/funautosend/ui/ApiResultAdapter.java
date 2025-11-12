package com.funshion.funautosend.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.funshion.funautosend.R;

import java.util.List;
import java.util.Map;

/**
 * 自定义适配器，用于显示API返回结果
 */
public class ApiResultAdapter extends BaseAdapter {
    private Activity context;
    private List<Map<String, Object>> data;

    public ApiResultAdapter(Activity context, List<Map<String, Object>> data) {
        this.context = context;
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewHolder holder;

        if (view == null) {
            // 加载列表项布局
            view = context.getLayoutInflater().inflate(R.layout.list_item_api_result, parent, false);

            // 创建ViewHolder
            holder = new ViewHolder();
            holder.tvTitle = view.findViewById(R.id.tv_item_title);
            holder.fieldContainer = view.findViewById(R.id.ll_field_container);

            // 将ViewHolder存储在view中
            view.setTag(holder);
        } else {
            // 重用现有的view
            holder = (ViewHolder) view.getTag();

            // 清空之前的字段视图
            holder.fieldContainer.removeAllViews();
        }

        // 设置数据
        Map<String, Object> item = data.get(position);

        // 设置标题
        String title = (String) item.get("title");
        holder.tvTitle.setText(title);

        // 获取字段映射
        Map<String, String> fields = (Map<String, String>) item.get("fields");

        // 检查是否是匹配项
        boolean isMatch = item.containsKey("isMatch") && (boolean) item.get("isMatch");
        String matchedWorkPhone = isMatch ? (String) item.get("matchedWorkPhone") : null;

        // 动态创建和添加每个字段的视图
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // 创建字段布局
            LinearLayout fieldLayout = new LinearLayout(context);
            fieldLayout.setOrientation(LinearLayout.HORIZONTAL);
            fieldLayout.setPadding(0, 4, 0, 4);

            // 创建键文本视图
            TextView keyTextView = new TextView(context);
            keyTextView.setText(key + ": ");
            keyTextView.setTextSize(14);
            keyTextView.setTextColor(context.getResources().getColor(android.R.color.holo_blue_light));
            keyTextView.setTypeface(null, Typeface.BOLD);
            keyTextView.setPadding(0, 0, 8, 0);

            // 创建值文本视图
            TextView valueTextView = new TextView(context);
            valueTextView.setTextSize(14);
            valueTextView.setTextColor(context.getResources().getColor(android.R.color.black));
            valueTextView.setSingleLine(false);
            valueTextView.setMaxWidth(parent.getWidth() - 100); // 限制宽度以避免显示问题

            // 如果是匹配的workPhone字段，标红显示
            if (isMatch && "workPhone".equals(key) && matchedWorkPhone != null && matchedWorkPhone.equals(value)) {
                valueTextView.setText(Html.fromHtml("<font color='#FF0000'>" + value + "</font>"));
            } else {
                valueTextView.setText(value);
            }

            // 添加到字段布局
            fieldLayout.addView(keyTextView);
            fieldLayout.addView(valueTextView);

            // 添加到容器
            holder.fieldContainer.addView(fieldLayout);
        }

        return view;
    }

    // ViewHolder类，用于优化ListView性能
    private static class ViewHolder {
        TextView tvTitle;
        LinearLayout fieldContainer;
    }
}