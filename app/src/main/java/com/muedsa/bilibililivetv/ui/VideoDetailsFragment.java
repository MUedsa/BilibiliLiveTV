package com.muedsa.bilibililivetv.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.app.DetailsSupportFragmentBackgroundController;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.common.base.Strings;
import com.muedsa.bilibililiveapiclient.BilibiliLiveApiClient;
import com.muedsa.bilibililiveapiclient.model.BilibiliResponse;
import com.muedsa.bilibililiveapiclient.model.DanmuInfo;
import com.muedsa.bilibililiveapiclient.model.Durl;
import com.muedsa.bilibililiveapiclient.model.LargeInfo;
import com.muedsa.bilibililiveapiclient.model.PlayUrlData;
import com.muedsa.bilibililiveapiclient.model.Qn;
import com.muedsa.bilibililivetv.R;
import com.muedsa.bilibililivetv.model.LiveRoom;
import com.muedsa.bilibililivetv.model.LiveRoomConvert;
import com.muedsa.bilibililivetv.model.LiveRoomHistoryHolder;
import com.muedsa.bilibililivetv.task.TaskRunner;

/*
 * LeanbackDetailsFragment extends DetailsFragment, a Wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its meta plus related videos.
 */
public class VideoDetailsFragment extends DetailsSupportFragment {
    private static final String TAG = "VideoDetailsFragment";

    private static final int ACTION_NOTHING = -1;
    private static final int ACTION_WATCH_TRAILER = 1;

    private static final int DETAIL_THUMB_WIDTH = 216;
    private static final int DETAIL_THUMB_HEIGHT = 136;

    private LiveRoom mSelectedLiveRoom;

    private Action playAction;
    private Action liveStatusAction;
    private Action onlineNumAction;
    private DetailsOverviewRow detailsOverviewRow;

    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;

    private DetailsSupportFragmentBackgroundController mDetailsBackground;

    private TaskRunner taskRunner;
    private BilibiliLiveApiClient bilibiliLiveApiClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate DetailsFragment");
        super.onCreate(savedInstanceState);

        mDetailsBackground = new DetailsSupportFragmentBackgroundController(this);

        mSelectedLiveRoom = (LiveRoom) getActivity().getIntent()
                .getSerializableExtra(DetailsActivity.LIVE_ROOM);

        if (mSelectedLiveRoom != null && mSelectedLiveRoom.getId() > 0) {

            taskRunner = TaskRunner.getInstance();
            bilibiliLiveApiClient = new BilibiliLiveApiClient();

            mPresenterSelector = new ClassPresenterSelector();
            mAdapter = new ArrayObjectAdapter(mPresenterSelector);
            setupDetailsOverviewRow();
            setupDetailsOverviewRowPresenter();
            //setupRelatedMovieListRow();
            setAdapter(mAdapter);
            initializeBackground();
            setOnItemViewClickedListener(new ItemViewClickedListener());

            mSelectedLiveRoom.setLiveStatus(0);
            mSelectedLiveRoom.setOnlineNum(0);
            mSelectedLiveRoom.setPlayUrlArr(new String[0]);
            initializePlayUrl();
            initializeLiveRoomInfo();
        } else {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            startActivity(intent);
        }
    }


    private void initializeLiveRoomInfo(){
        taskRunner.executeAsync(
                () ->{
                    String errorMsg = getResources().getString(R.string.live_room_info_failure);
                    BilibiliResponse<LargeInfo> response = bilibiliLiveApiClient.getLargeInfo(mSelectedLiveRoom.getId());
                    if(response != null){
                        if(response.getCode() == 0){
                            if(response.getData() != null){
                                LiveRoomConvert.updateRoomInfo(mSelectedLiveRoom, response.getData());
                                LiveRoomHistoryHolder.addHistory(mSelectedLiveRoom, getContext());
                                errorMsg = null;
                            }
                        }else if(response.getMessage() != null){
                            errorMsg = response.getMessage();
                        }
                    }
                    return errorMsg;
                },
                errorMsg -> {
                    if(errorMsg == null){
                        updateCardImage();
                        updateBackground();
                        liveStatusAction.setLabel2(mSelectedLiveRoom.getLiveStatusDesc(getResources()));
                        onlineNumAction.setLabel2(String.valueOf(mSelectedLiveRoom.getOnlineNum()));
                    }else{
                        Toast.makeText(getActivity(), errorMsg, Toast.LENGTH_LONG)
                                .show();
                    }
                });
        taskRunner.executeAsync(
                () ->{
                    String errorMsg = getResources().getString(R.string.live_danmu_ws_token_failure);
                    BilibiliResponse<DanmuInfo> response = bilibiliLiveApiClient.getDanmuInfo(mSelectedLiveRoom.getId());
                    if(response != null){
                        if(response.getCode() == 0){
                            if(response.getData() != null){
                                mSelectedLiveRoom.setDanmuWsToken(response.getData().getToken());
                                errorMsg = null;
                            }
                        }else if(response.getMessage() != null){
                            errorMsg = response.getMessage();
                        }
                    }
                    return errorMsg;
                },
                errorMsg -> {
                    if(errorMsg != null){
                        Toast.makeText(getActivity(), errorMsg, Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }

    private void initializePlayUrl(){
        playAction.setLabel2(getResources().getString(R.string.watch_trailer_loading));
        taskRunner.executeAsync(
                () -> {
                    String errorMsg = getResources().getString(R.string.live_play_failure);
                    BilibiliResponse<PlayUrlData> response = bilibiliLiveApiClient.getPlayUrlMessage(mSelectedLiveRoom.getId(), Qn.RAW);
                    if(response != null){
                        if(response.getCode() == 0){
                            if(response.getData() != null && response.getData().getDurl() != null && response.getData().getDurl().size() > 0){
                                mSelectedLiveRoom.setPlayUrlArr(response.getData().getDurl().stream().map(Durl::getUrl).toArray(String[]::new));
                                errorMsg = null;
                            }
                        }else if(response.getMessage() != null){
                            errorMsg = response.getMessage();
                        }
                    }
                    return errorMsg;
                },
                errorMsg -> {
                    if(errorMsg == null){
                        playAction.setLabel2(getResources().getString(R.string.watch_trailer_play));
                    }else{
                        Toast.makeText(getActivity(), errorMsg, Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }

    private void initializeBackground() {
        mDetailsBackground.enableParallax();
        updateBackground();
    }
    private void updateBackground(){
        String backgroundUrl = Strings.isNullOrEmpty(mSelectedLiveRoom.getBackgroundImageUrl()) ?
                mSelectedLiveRoom.getSystemCoverImageUrl() : mSelectedLiveRoom.getBackgroundImageUrl();
        Glide.with(getActivity())
                .asBitmap()
                .centerCrop()
                .error(R.drawable.default_background)
                .load(backgroundUrl)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap,
                                                @Nullable Transition<? super Bitmap> transition) {
                        Log.d(TAG, "details overview background image url ready: " + bitmap);
                        mDetailsBackground.setCoverBitmap(bitmap);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }
                });
    }

    private void setupDetailsOverviewRow() {
        Log.d(TAG, "setupDetailsOverviewRow: " + mSelectedLiveRoom.getId());
        detailsOverviewRow = new DetailsOverviewRow(mSelectedLiveRoom);
        detailsOverviewRow.setImageDrawable(
                ContextCompat.getDrawable(getContext(), R.drawable.no_cover));
        updateCardImage();

        ArrayObjectAdapter actionAdapter = new ArrayObjectAdapter();
        playAction = new Action(ACTION_WATCH_TRAILER, getResources().getString(R.string.watch_trailer_title), getResources().getString(R.string.watch_trailer_loading));
        actionAdapter.add(playAction);
        liveStatusAction = new Action(ACTION_NOTHING, getResources().getString(R.string.room_live_status), mSelectedLiveRoom.getLiveStatusDesc(getResources()));
        actionAdapter.add(liveStatusAction);
        onlineNumAction = new Action(ACTION_NOTHING, getResources().getString(R.string.room_online_num), String.valueOf(mSelectedLiveRoom.getOnlineNum()));
        actionAdapter.add(onlineNumAction);
        detailsOverviewRow.setActionsAdapter(actionAdapter);

        mAdapter.add(detailsOverviewRow);
    }

    private void updateCardImage(){
        int width = convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_WIDTH);
        int height = convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_HEIGHT);
        Glide.with(getActivity())
                .load(mSelectedLiveRoom.getCoverImageUrl())
                .centerCrop()
                .error(R.drawable.no_cover)
                .into(new CustomTarget<Drawable>(width, height) {
                    @Override
                    public void onResourceReady(@NonNull Drawable drawable,
                                                @Nullable Transition<? super Drawable> transition) {
                        Log.d(TAG, "details overview card image url ready: " + drawable);
                        detailsOverviewRow.setImageDrawable(drawable);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }
                });
    }

    private void setupDetailsOverviewRowPresenter() {
        // Set detail background.
        FullWidthDetailsOverviewRowPresenter detailsPresenter =
                new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        detailsPresenter.setBackgroundColor(
                ContextCompat.getColor(getContext(), R.color.selected_background));

        // Hook up transition element.
        FullWidthDetailsOverviewSharedElementHelper sharedElementHelper =
                new FullWidthDetailsOverviewSharedElementHelper();
        sharedElementHelper.setSharedElementEnterTransition(
                getActivity(), DetailsActivity.SHARED_ELEMENT_NAME);
        detailsPresenter.setListener(sharedElementHelper);
        detailsPresenter.setParticipatingEntranceTransition(true);

        detailsPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_WATCH_TRAILER) {
                    if(mSelectedLiveRoom.getPlayUrlArr() == null || mSelectedLiveRoom.getPlayUrlArr().length == 0){
                        Toast.makeText(getActivity(), getResources().getString(R.string.live_play_failure), Toast.LENGTH_SHORT).show();
                    }else{
                        Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                        intent.putExtra(DetailsActivity.LIVE_ROOM, mSelectedLiveRoom);
                        startActivity(intent);
                    }
                }
            }
        });
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void setupRelatedMovieListRow() {
//        String subcategories[] = {getString(R.string.related_movies)};
//        List<LiveRoom> list = MovieList.getList();
//
//        Collections.shuffle(list);
//        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
//        for (int j = 0; j < NUM_COLS; j++) {
//            listRowAdapter.add(list.get(j % 5));
//        }
//
//        HeaderItem header = new HeaderItem(0, subcategories[0]);
//        mAdapter.add(new ListRow(header, listRowAdapter));
//        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
    }

    private int convertDpToPixel(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof LiveRoom) {
                Log.d(TAG, "Item: " + item.toString());
                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                intent.putExtra(getResources().getString(R.string.liveRoom), mSelectedLiveRoom);

                Bundle bundle =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                getActivity(),
                                ((ImageCardView) itemViewHolder.view).getMainImageView(),
                                DetailsActivity.SHARED_ELEMENT_NAME)
                                .toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }
}