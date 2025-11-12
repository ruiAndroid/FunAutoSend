package com.funshion.funautosend.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.funshion.funautosend.R;

import java.util.List;
import java.util.Map;

/**
 * RecyclerView适配器，用于显示API返回结果
 */
public class ApiResultRecyclerAdapter extends RecyclerView.Adapter<ApiResultRecyclerAdapter.ViewHolder> {
    private Context context;
    private List<Map<String, Object>> data;

    public ApiResultRecyclerAdapter(Context context, List<Map<String, Object>> data) {
        this.context = context;
        this.data = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_api_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
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

        // 清空之前的字段视图
        holder.fieldContainer.removeAllViews();

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

            // 创建值文本视图
            TextView valueTextView = new TextView(context);
            valueTextView.setText(value);
            valueTextView.setTextSize(14);
            valueTextView.setTextColor(context.getResources().getColor(android.R.color.black));

            // 如果是匹配项，高亮显示
            if (isMatch && matchedWorkPhone != null && (key.equals("workPhone") || key.contains("手机号码")) && value.equals(matchedWorkPhone)) {
                valueTextView.setTextColor(context.getResources().getColor(android.R.color.holo_red_light));
                valueTextView.setTypeface(null, Typeface.BOLD);
            }

            // 添加到字段布局
            fieldLayout.addView(keyTextView);
            fieldLayout.addView(valueTextView);

            // 添加到容器
            holder.fieldContainer.addView(fieldLayout);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        LinearLayout fieldContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_item_title);
            fieldContainer = itemView.findViewById(R.id.ll_field_container);
        }
    }
}