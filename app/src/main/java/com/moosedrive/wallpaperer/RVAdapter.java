package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.ListPreloader;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.card.MaterialCardView;
import com.moosedrive.wallpaperer.data.ImageObject;
import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.utils.BackgroundExecutor;
import com.moosedrive.wallpaperer.utils.PreferenceHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.zhanghai.android.fastscroll.PopupTextProvider;

public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ImageHolder> implements PopupTextProvider, ListPreloader.PreloadModelProvider<ImageObject> {

    final ImageStore store;
    final Context context;
    private ItemClickListener clickListener;
    private final int columns;

    public RVAdapter(Context context, ImageStore store, int columns) {
        this.store = store;
        this.context = context;
        this.columns = columns;
    }

    /**
     * Get the square dimension of the card given the desired number of columns.
     * This method returns R.dimen.card_size_min or greater. Because of this minimum, the returned
     * size may not fit in the desired screen space depending on column count.
     * @param context the context
     * @return largest of width of card based on desired columns or R.dimen.card_size_min
     */
    public static int getCardSize(Context context, int columns) {
        //int width = Math.round(Resources.getSystem().getDisplayMetrics().widthPixels);
        int height = Math.round(Resources.getSystem().getDisplayMetrics().heightPixels);
        //int columns = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("preference_columns", "2"));
        int width = Math.round(Resources.getSystem().getDisplayMetrics().widthPixels);
        // Set lower limit on thumbnail size (need space for buttons and metadata text) based on display size
        if (width / columns < (int) context.getResources().getDimension(R.dimen.card_size_min))
            columns = (int) (width / context.getResources().getDimension(R.dimen.card_size_min));
        //Create a thumbnail that will be big enough for both portrait and landscape
        return Math.min(width / columns, Math.min(width, height));
    }

    @NonNull
    @Override
    public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.cardlayout, parent, false);
        return new ImageHolder(v);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
        holder.ivBlocker.setOnClickListener(v -> {
        });
        final ImageObject img = store.getImageObject(position);
        int color = img.getColor();
        if (!img.isColorSet()) {
            // Color isn't generated. Don't wait, but do it for next time.
            color = context.getColor(androidx.cardview.R.color.cardview_dark_background);
            BackgroundExecutor.getExecutor().execute(() -> img.setColor(img.getColorFromBitmap(context)));
        }
        if (store.getLastWallpaperId().equals(img.getId()))
            holder.cv.setStrokeColor(context.getColor(R.color.gray_400));
        else
            holder.cv.setStrokeColor(context.getColor(R.color.transparent));
        holder.ivImage.setBackgroundColor(color);
        String type = img.getType();
        String name = img.getName();
        Date date = img.getCreationDate();
        boolean showStats = PreferenceHelper.showStats(context);
        holder.tvFileName.setVisibility((showStats) ? View.VISIBLE : View.INVISIBLE);
        holder.flowStats.setVisibility((showStats) ? View.VISIBLE : View.INVISIBLE);
        holder.flowStats.setBackgroundColor(color);
        holder.tvFileName.setText(name.toUpperCase());
        String sDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
        holder.tvDate.setText(sDate);
        holder.tvType.setText(Html.fromHtml(type, Html.FROM_HTML_MODE_COMPACT));
        holder.tvSize.setText(String.format(Locale.US, context.getString(R.string.file_size), img.getSize() / (1024.0 * 1024.0)));
        int squareDimen = getCardSize(context,columns);
        holder.itemView.getLayoutParams().width = squareDimen;
        holder.itemView.getLayoutParams().height = squareDimen;
        Glide
                .with(context)
                .load(img.getThumbUri(context))
                .centerCrop()
                .override(squareDimen)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(holder.ivImage);


    }

    @Override
    public long getItemId(int position) {
        return store.getImageObject(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return store.size();
    }

    @Override
    public void onViewRecycled(@NonNull ImageHolder holder) {
        super.onViewRecycled(holder);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    public void setClickListener(ItemClickListener itemClickListener) {
        this.clickListener = itemClickListener;
    }

    public void removeItem(int position) {
        notifyItemRemoved(position);
    }

    public ArrayList<ImageObject> getData() {
        return new ArrayList<>(Arrays.asList(store.getImageObjectArray()));
    }

    @NonNull
    @Override
    public String getPopupText(int position) {
        return (position + 1) + " of " + store.size();
    }

    @NonNull
    @Override
    public List<ImageObject> getPreloadItems(int position) {
        return Collections.singletonList(store.getImageObject(position));
    }

    @Nullable
    @Override
    public RequestBuilder<Drawable> getPreloadRequestBuilder(@NonNull ImageObject img) {
        int width = getCardSize(context, columns);
        //This needs to be identical (except "into") to the onBind glide builder
        return Glide.with(context)
                .load(img.getThumbUri(context))
                .centerCrop()
                .override(width)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE);
    }

    public class ImageHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView tvDate;
        final ImageView ivImage;
        final ImageView ivShare;
        final TextView tvFileName;
        final TextView tvType;
        final ImageView ivSetWp;
        final TextView tvSize;
        final Flow flowStats;
        final View ivBlocker;
        final MaterialCardView cv;


        ImageHolder(View itemView) {
            super(itemView);
            cv = (MaterialCardView)itemView;
            flowStats = itemView.findViewById(R.id.card_stats);
            ivImage = itemView.findViewById(R.id.iv_image);
            ivShare = itemView.findViewById(R.id.iv_share);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvType = itemView.findViewById(R.id.tv_type);
            tvFileName = itemView.findViewById(R.id.textFileName);
            ivSetWp = itemView.findViewById(R.id.iv_setWp);
            ivBlocker = itemView.findViewById(R.id.touch_blocker);
            tvSize = itemView.findViewById(R.id.tv_size);
            ivSetWp.setOnClickListener(this);
            ivImage.setOnClickListener(this);
            ivShare.setOnClickListener(this);

        }

        @Override
        public void onClick(View view) {
            PackageManager packageManager = context.getPackageManager();
            ImageObject img = store.getImageObject(getAbsoluteAdapterPosition());
            if (clickListener != null && view == ivSetWp) {
                clickListener.onSetWpClick(getAbsoluteAdapterPosition());
                ivSetWp.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_change_wallpaper));
            }
            if (clickListener != null && view == ivImage) {
                clickListener.onImageClick(getAbsoluteAdapterPosition(), view);
            }
            if (clickListener != null && view == ivShare) {
                ivShare.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_change_wallpaper));
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", new File(img.getUri().getPath()));
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setType(img.getType());
                if (intent.resolveActivity(packageManager) != null)
                    context.startActivity(intent);
                else
                    Toast.makeText(context, context.getString(R.string.action_share_no_apps_configured), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public interface ItemClickListener {
        void onSetWpClick(int position);

        void onImageClick(int pos, View view);
    }
}
