package haven.demo.webrtc.rtcdemo.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;

import haven.demo.webrtc.rtcdemo.R;
import haven.demo.webrtc.rtcdemo.listener.OnUserListInteractionListener;
import haven.demo.webrtc.rtcdemo.model.User;

public class ContactRecyclerViewAdapter extends RecyclerView.Adapter<ContactRecyclerViewAdapter.ViewHolder> {

    private final List<User> users;
    private final OnUserListInteractionListener mListener;

    public ContactRecyclerViewAdapter(List<User> users, OnUserListInteractionListener listener) {
        this.users = users;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.contact_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.user = users.get(position);
        holder.tvName.setText(users.get(position).getUserName());

        holder.btCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    mListener.onClickCallUser(holder.user);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView tvName;
        public final ImageButton btCall;
        public User user;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            tvName = (TextView) view.findViewById(R.id.tvName);
            btCall = (ImageButton) view.findViewById(R.id.btCall);
        }
    }
}
