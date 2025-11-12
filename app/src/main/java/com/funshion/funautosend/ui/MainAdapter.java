package com.funshion.funautosend.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.funshion.funautosend.R;
import com.funshion.funautosend.model.PhoneNumberData;

import java.util.List;
import java.util.Map;

/**
 * 统一的主界面适配器，支持多种类型的item
 */
public class MainAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    // 定义item类型
    public enum ItemType {
        PHONE_DISPLAY,
        SETTING,
        API_RESULT,
        EMPTY
    }

    // 数据项类
    public static class ItemData {
        public ItemType type;
        public PhoneNumberData phoneData;
        public String title;
        public String status;
        public String buttonText;
        public View.OnClickListener buttonClickListener;
        public Map<String, Object> apiResult;

        public ItemData(ItemType type) {
            this.type = type;
        }
    }

    private Context context;
    private List<ItemData> data;

    public MainAdapter(Context context, List<ItemData> data) {
        this.context = context;
        this.data = data;
    }

    @Override
    public int getItemViewType(int position) {
        ItemData item = data.get(position);
        switch (item.type) {
            case PHONE_DISPLAY:
                return 0;
            case SETTING:
                return 1;
            case API_RESULT:
                return 2;
            case EMPTY:
                return 3;
            default:
                return 0;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case 0: // PHONE_DISPLAY
                View phoneView = LayoutInflater.from(context).inflate(R.layout.item_phone_display, parent, false);
                return new PhoneDisplayViewHolder(phoneView);
            case 1: // SETTING
                View settingView = LayoutInflater.from(context).inflate(R.layout.item_setting, parent, false);
                return new SettingViewHolder(settingView);
            case 3: // EMPTY
                View emptyView = LayoutInflater.from(context).inflate(R.layout.item_setting, parent, false);
                return new EmptyViewHolder(emptyView);
            case 2: // API_RESULT
            default:
                View apiResultView = LayoutInflater.from(context).inflate(R.layout.list_item_api_result, parent, false);
                return new ApiResultViewHolder(apiResultView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ItemData item = data.get(position);
        int viewType = getItemViewType(position);

        switch (viewType) {
            case 0: // PHONE_DISPLAY
                bindPhoneDisplayViewHolder((PhoneDisplayViewHolder) holder, item);
                break;
            case 1: // SETTING
                bindSettingViewHolder((SettingViewHolder) holder, item);
                break;
            case 2: // API_RESULT
                bindApiResultViewHolder((ApiResultViewHolder) holder, item);
                break;
            case 3: // EMPTY
                bindEmptyViewHolder((EmptyViewHolder) holder, item);
                break;
        }
    }

    private void bindPhoneDisplayViewHolder(PhoneDisplayViewHolder holder, ItemData item) {
        if (item.phoneData != null) {
            holder.tvPhone1.setText(item.phoneData.getPhoneNumber1());
            holder.tvPhone2.setText(item.phoneData.getPhoneNumber2());
        }
    }

    private void bindSettingViewHolder(SettingViewHolder holder, ItemData item) {
        holder.tvTitle.setText(item.title);
        holder.tvStatus.setText(item.status);
        holder.btnAction.setText(item.buttonText);
        holder.btnAction.setVisibility(View.VISIBLE);

        // 设置按钮点击事件
        if (item.buttonClickListener != null) {
            holder.btnAction.setOnClickListener(item.buttonClickListener);
        }
    }

    private void bindApiResultViewHolder(ApiResultViewHolder holder, ItemData item) {
        if (item.apiResult == null) return;
        
        // 设置标题
        String title = (String) item.apiResult.get("title");
        holder.tvTitle.setText(title);

        // 获取字段映射
        Map<String, String> fields = (Map<String, String>) item.apiResult.get("fields");

        // 检查是否是匹配项
        boolean isMatch = item.apiResult.containsKey("isMatch") && (boolean) item.apiResult.get("isMatch");
        String matchedWorkPhone = isMatch ? (String) item.apiResult.get("matchedWorkPhone") : null;

        // 清空之前的字段视图
        holder.fieldContainer.removeAllViews();

        // 动态创建和添加每个字段的视图
        if (fields != null) {
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
    }

    private void bindEmptyViewHolder(EmptyViewHolder holder, ItemData item) {
        holder.tvTitle.setText(item.title);
        holder.tvStatus.setText(item.status);
        holder.btnAction.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    // 手机号显示ViewHolder
    static class PhoneDisplayViewHolder extends RecyclerView.ViewHolder {
        TextView tvPhone1;
        TextView tvPhone2;

        PhoneDisplayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPhone1 = itemView.findViewById(R.id.tv_phone1);
            tvPhone2 = itemView.findViewById(R.id.tv_phone2);
        }
    }

    // 设置项ViewHolder
    static class SettingViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvStatus;
        Button btnAction;

        SettingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_setting_title);
            tvStatus = itemView.findViewById(R.id.tv_setting_status);
            btnAction = itemView.findViewById(R.id.btn_setting_action);
        }
    }

    // 空数据项ViewHolder
    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvStatus;
        Button btnAction;

        EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_setting_title);
            tvStatus = itemView.findViewById(R.id.tv_setting_status);
            btnAction = itemView.findViewById(R.id.btn_setting_action);
        }
    }

    // API结果ViewHolder
    static class ApiResultViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        LinearLayout fieldContainer;

        ApiResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_item_title);
            fieldContainer = itemView.findViewById(R.id.ll_field_container);
        }
    }

    // 为了保持兼容性而保留的接口，但不再使用
    public interface OnItemClickListener {
        void onSettingButtonClick(int viewType);
    }
}