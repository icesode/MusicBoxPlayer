package com.example.music_box;


import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

class ViewHolder{
        public ImageView itemicon;
        public TextView itemMusicName;
        public TextView itemMusicSinger;
        public int defaultTextColor;

        View itemView;
        public ViewHolder(View itemView){
            if (itemView==null){
                throw new IllegalArgumentException("itemView can not be null!");
            }
            this.itemView=itemView;
            itemicon=(ImageView) itemView.findViewById(R.id.rand_icon);
            itemMusicName=(TextView) itemView.findViewById(R.id.item_music_name);
            itemMusicSinger=(TextView) itemView.findViewById(R.id.item_music_singer);
            defaultTextColor=itemMusicName.getCurrentTextColor();
        }
}
public class ListAdapter extends BaseAdapter {

    private List<MusicInfo> musicList;
    private LayoutInflater layoutInflater;
    private Context context;
    private int currentPos=-1;

    private ViewHolder holder=null;

    public ListAdapter(Context context,List<MusicInfo> musicList){
        this.musicList=musicList;
        this.context=context;
        layoutInflater=LayoutInflater.from(context);
    }

    public void setFocusItemPos(int pos){
        currentPos=pos;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return musicList.size();
    }

    @Override
    public Object getItem(int position) {
        return musicList.get(position).getMusic_title();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
    public void remove(int index){
        musicList.remove(index);
    }

    public void refreshDataSet(){
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView==null){
            convertView=layoutInflater.inflate(R.layout.item_layout,null);
            holder=new ViewHolder(convertView);
            convertView.setTag(holder);
        }else{
            holder=(ViewHolder) convertView.getTag();
        }
        if (position==currentPos){
            holder.itemicon.setImageResource(R.drawable.music_playing);
            holder.itemMusicName.setTextColor(Color.RED);
            holder.itemMusicSinger.setTextColor(Color.RED);
        }else {
            holder.itemicon.setImageResource(R.drawable.music);
            holder.itemMusicName.setTextColor(holder.defaultTextColor);
            holder.itemMusicSinger.setTextColor(holder.defaultTextColor);
        }
        holder.itemMusicName.setText(musicList.get(position).getMusic_title());
        holder.itemMusicSinger.setText(musicList.get(position).getMusic_artist());

        return convertView;
    }
}
