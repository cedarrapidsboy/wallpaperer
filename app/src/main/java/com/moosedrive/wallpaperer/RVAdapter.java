package com.moosedrive.wallpaperer;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
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
import androidx.annotation.RequiresApi;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ImageHolder> {

    final ImageStore store;
    final Context context;
    private ItemClickListener clickListener;

    public RVAdapter(Context context) {
        this.store = ImageStore.getInstance();
        this.context = context;
    }

    public static int getCardSize(Context context) {
        //int width = Math.round(Resources.getSystem().getDisplayMetrics().widthPixels);
        int height = Math.round(Resources.getSystem().getDisplayMetrics().heightPixels);
        //int columns = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("preference_columns", "2"));
        int columns = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.preference_columns), "2"));
        int width = Math.round(Resources.getSystem().getDisplayMetrics().widthPixels);
        if (width / columns < (int) context.getResources().getDimension(R.dimen.card_size_min))
            columns = (int) (width / context.getResources().getDimension(R.dimen.card_size_min));
        //Create a thumbnail that will be big enough for both portrait and landscape (without evicting the thumbcache to resize)
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
        holder.ivBlocker.setOnClickListener(v -> {});
        ImageObject img = store.getImageObject(position);
        String type = img.getType();
        String name = img.getName();
        Date date = img.getDate();
        boolean showStats = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.preference_card_stats), false);
        holder.tvFileName.setVisibility((showStats) ? View.VISIBLE : View.INVISIBLE);
        holder.flowStats.setVisibility((showStats) ? View.VISIBLE : View.INVISIBLE);
        holder.tvFileName.setText(name.toUpperCase());
        String sDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
        holder.tvDate.setText(sDate);
        holder.tvType.setText(Html.fromHtml(type, Html.FROM_HTML_MODE_COMPACT));
        holder.tvSize.setText(String.format(Locale.US, context.getString(R.string.file_size), img.getSize() / (1024.0 * 1024.0)));
        //holder.ivImage.requestLayout();
        int width = getCardSize(context);
        holder.itemView.getLayoutParams().width = width;
        //noinspection SuspiciousNameCombination
        holder.itemView.getLayoutParams().height = width;
        //Set the thumbnail in a different thread (long running resize operation)
        Glide
                .with(context)
                .load(img.getUri())
                .centerCrop()
                .priority(Priority.NORMAL)
                .transition(withCrossFade())
                .into(holder.ivImage);


    }

    @Override
    public long getItemId(int position) {
        return store.getImageObject(position).hashCode();
        //return super.getItemId(position);
    }

    @Override
    public int getItemCount() {
        return store.size();
    }

    @Override
    public void onViewRecycled(@NonNull ImageHolder holder) {
        Glide.with(context).clear(holder.ivImage);
        holder.ivImage.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_pending_24));
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
        //notifyDataSetChanged();
    }

    public void saveToPrefs(){
        store.saveToPrefs(context);
    }

    public void addItem(ImageObject item) {
        addItem(item, store.getPosition(item.getId()));
    }

    public void addItem(ImageObject item, int position) {
        store.addImageObject(item, position);
        notifyItemInserted(store.getPosition(item.getId()));
    }

    public ArrayList<ImageObject> getData() {
        return new ArrayList<>(Arrays.asList(store.getImageObjectArray()));
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


        ImageHolder(View itemView) {
            super(itemView);
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
            ImageObject img = store.getImageObject(getAdapterPosition());
            if (clickListener != null && view == ivSetWp) {
                clickListener.onSetWpClick(getAdapterPosition());
                ivSetWp.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_change_wallpaper));
            }
            if (clickListener != null && view == ivImage) {
                //TODO Fullscreen view
                clickListener.onImageClick(getAdapterPosition(), view);
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
                    Toast.makeText(context, "No apps configured to view this item.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
