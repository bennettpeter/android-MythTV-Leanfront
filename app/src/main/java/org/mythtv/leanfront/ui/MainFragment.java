/*
 * Copyright (c) 2014 The Android Open Source Project
 * Copyright (c) 2019-2020 Peter Bennett
 *
 * Incorporates code from "Android TV Samples"
 * <https://github.com/android/tv-samples>
 * Modified by Peter Bennett
 *
 * This file is part of MythTV-leanfront.
 *
 * MythTV-leanfront is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * MythTV-leanfront is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.mythtv.leanfront.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.app.ProgressBarManager;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.leanback.widget.TitleViewAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.core.content.ContextCompat;

import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.mythtv.leanfront.MyApplication;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.FetchVideoService;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.VideoDbHelper;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.ListItem;
import org.mythtv.leanfront.model.MyHeaderItem;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.presenter.CardPresenter;
import org.mythtv.leanfront.presenter.IconHeaderItemPresenter;
import org.mythtv.leanfront.recommendation.UpdateRecommendationsService;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseSupportFragment
        implements AsyncBackendCall.OnBackendCallListener {

    private static final String TAG = "lfe";
    private static final String CLASS = "MainFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mCategoryRowAdapter;
    private Drawable mDefaultBackground;
    private Uri mDefaultBackgroundURI;
    private DisplayMetrics mMetrics;
    private Runnable mBackgroundTask;
    private Uri mBackgroundURI;
    private BackgroundManager mBackgroundManager;
    int mType;
    public static final String KEY_TYPE = "LEANFRONT_TYPE";
    // Type applicable to main screen
    public static final int TYPE_TOPLEVEL = 1;
    // Types applicable to main screen or row
    public static final int TYPE_RECGROUP = 2;
    // Types applicable to main screen row, or cell
    public static final int TYPE_VIDEODIR = 3;
    // Types applicable to row or cell
    public static final int TYPE_SERIES = 4;
    // Types applicable to cell
    public static final int TYPE_EPISODE = 5;
    public static final int TYPE_VIDEO = 6;
    public static final int TYPE_CHANNEL = 7;
    // Types of rows
    public static final int TYPE_TOP_ALL = 8;
    public static final int TYPE_RECGROUP_ALL = 9;
    public static final int TYPE_VIDEODIR_ALL = 10;
    public static final int TYPE_RECENTS = 11;
    // Type applicable to row or cell
    public static final int TYPE_CHANNEL_ALL = 12;
    // Special row type
    public static final int TYPE_TOOLS = 20;
    // Special Item Type
    public static final int TYPE_SETTINGS = 21;
    public static final int TYPE_REFRESH = 22;
    public static final int TYPE_INFO = 23;
    public static final int TYPE_MANAGE = 24;

    public static final String KEY_BASENAME = "LEANFRONT_BASENAME";
    public static final String KEY_ROWNAME = "LEANFRONT_ROWNAME";
    public static final String KEY_ITEMNAME = "LEANFRONT_ITEMNAME";
    // mBase is the current recgroup or directory being displayed.
    String mBaseName;
    private TextView mUsageView;

    private static ScheduledExecutorService executor = null;
    private static MythTask mythTask = new MythTask();
    private static boolean scheduledTaskRunning;
    public static volatile long mFetchTime = 0;
    // Keep track of the fragment currently showing, if any.
    private static MainFragment mActiveFragment = null;
    private static boolean mWasInBackground = true;
    // Not final so I can change it during debug
    private static int TASK_INTERVAL = 240;
    private ItemViewClickedListener mItemViewClickedListener;
    private ScrollSupport scrollSupport;
    volatile boolean isLoaderRunning;
    // This flag will be set true during refresh if it is found that we are on a
    // backend that supports the LastPlayPos APIs (V32 or later).
    public static boolean supportLastPlayPos;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scrollSupport = new ScrollSupport((getContext()));
        Intent intent = getActivity().getIntent();
        mType = intent.getIntExtra(KEY_TYPE, TYPE_TOPLEVEL);
        if (mType == TYPE_TOPLEVEL) {
            // Fire TV starts a new main activity if the application goes into
            // the background and is then restarted. This shuts down that new
            // activity so the previously active one will show.
            // I believe this is a bug in Fire TV. NVidia shield does not create
            // a new activity that way.
            if (MainActivity.getContext() != null) {
                getActivity().finish();
                return;
            }
            // Clear ip address cache
            XmlNode.clearCache();
            VideoDbHelper dbh = VideoDbHelper.getInstance(getContext());
            SQLiteDatabase db = dbh.getWritableDatabase();
            // delete stale entries from bookmark table
            String where = VideoContract.StatusEntry.COLUMN_LAST_USED + " < ? ";
            // 60 days in milliseconds
            String[] selectionArgs = {String.valueOf(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)};
            // https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html
            db.delete(VideoContract.StatusEntry.TABLE_NAME, where, selectionArgs);
            // Initialize startup members
            if (executor != null)
                executor.shutdown();
            executor = null;
            mFetchTime = 0;
            mActiveFragment = null;
            mWasInBackground = true;
            showNotes();
        } else
            mBaseName = intent.getStringExtra(KEY_BASENAME);
    }

    private void setProgressBar(boolean show) {
        ProgressBarManager manager = getProgressBarManager();
        // Initial delay defaults to 1000 (1 second)
        if (show)
            manager.show();
        else
            manager.hide();
    }

    private void setUsage(int used) {
        if (getContext() == null)
            return;
        if (mUsageView == null) {
            View mainView = getView();
            if (mainView == null)
                return;
            ViewGroup grp = mainView.findViewById(R.id.browse_title_group);
            int height = grp.getHeight();
            int width = grp.getWidth();
            mUsageView = new TextView(getContext());
            mUsageView.setTextSize(16.0f);
            mUsageView.setPadding(width / 15, height / 3, 0, 0);
            grp.addView(mUsageView, new FrameLayout.LayoutParams(width / 5, height,
                    Gravity.TOP + Gravity.LEFT));
            TextClock clock = new TextClock(getContext());
            clock.setGravity(Gravity.BOTTOM + Gravity.RIGHT);
            grp.addView(clock, new FrameLayout.LayoutParams(width / 10, height / 5,
                    Gravity.BOTTOM + Gravity.RIGHT));
        }
        mUsageView.setText(getContext().getResources().getString(R.string.title_disk_usage, used));
    }

    /**
     * Fetch video list
     *
     * @param rectype    Set to -1 to fetch all, or to either
     *                   VideoContract.VideoEntry.RECTYPE_RECORDING or
     *                   VideoContract.VideoEntry.RECTYPE_VIDEO
     * @param recordedId Set to null, or recordedId if only one to be refreshed
     * @param recGroup   Set to a recordimng group if only that one is to
     *                   be refreshed
     */
    public void startFetch(int rectype, String recordedId, String recGroup) {
        if (rectype == -1)
            mFetchTime = System.currentTimeMillis();
        // Start an Intent to fetch the videos.
        Intent serviceIntent = new Intent(getActivity(), FetchVideoService.class);
        serviceIntent.putExtra(FetchVideoService.RECTYPE, rectype);
        serviceIntent.putExtra(FetchVideoService.RECORDEDID, recordedId);
        serviceIntent.putExtra(FetchVideoService.RECGROUP, recGroup);
        getActivity().startService(serviceIntent);
    }

    // Replacement for StartLoader. This needs to be called after any database update.
    // Must be called on UI Thread
    public void startAsyncLoader() {
        if (!isLoaderRunning) {
            new AsyncMainLoader().execute(this);
            isLoaderRunning = true;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState);

        // Prepare the manager that maintains the same background image between activities.
        prepareBackgroundManager();

        setupUIElements();
        setupEventListeners();
        prepareEntranceTransition();

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        updateRecommendations();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mBackgroundTask);
        mBackgroundManager = null;
        if (mType == TYPE_TOPLEVEL) {
            if (executor != null)
                executor.shutdown();
            executor = null;
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mActiveFragment = this;
        startBackgroundTimer();
        if (mWasInBackground || executor == null)
            restartMythTask();
        mWasInBackground = false;
        // If it's been more than an hour, refresh
        if (mFetchTime < System.currentTimeMillis() - 60*60*1000) {
            startFetch(-1, null, null);
        }
        startAsyncLoader();
    }

    // Notes dialog that comes up when you start for the first time.
    // To advise users of new features etc.
    // Currently no messages are displayed but an array of strings can be provided
    // in sNotes in the parens, e.g. {R.string.notes_audio}
    void showNotes() {
        final int[] sNotes = {};
        int notesVersion = Settings.getInt("pref_notes_version");
        if (notesVersion >= sNotes.length)
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.notes_title);
        StringBuilder msg = new StringBuilder();
        for (int ix = notesVersion ; ix < sNotes.length ; ix++) {
            msg.append(getContext().getString(sNotes[ix]));
        }
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.notes_seen, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor editor = Settings.getEditor();
                Settings.putString(editor,"pref_notes_version",String.valueOf(sNotes.length));
                editor.commit();
                dialog.cancel();
            }
        });
        builder.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public static void restartMythTask() {
        if (!scheduledTaskRunning) {
            if (executor != null)
                executor.shutdown();
            executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(mythTask, 0, TASK_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onPause() {
        mActiveFragment = null;
        super.onPause();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    /*
        Get current selection
        Returns array:
            [0]: Selected row
            [1]: Selected item
        Either or both can be -1 to indicate no selection.
     */
    // TODO: Other places duplicate this code. Call this instead
    int [] getSelection() {
        int selectedRowNum = getSelectedPosition();
        int selectedItemNum = -1;
        if (selectedRowNum >= 0) {
            if (!isShowingHeaders()) {
                ListRowPresenter.ViewHolder selectedViewHolder
                        = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
                        .getRowViewHolder(selectedRowNum);
                if (selectedViewHolder != null)
                    selectedItemNum = selectedViewHolder.getSelectedPosition();
            }
        }
        return new int[]{selectedRowNum, selectedItemNum};
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        Context context = getContext();
        if (taskRunner == null)
            return;
        int [] tasks = taskRunner.getTasks();
        Intent intent;
        switch (tasks[0]) {
            case Video.ACTION_BACKEND_INFO_HTML:
                String result = taskRunner.getStringResult();
                if (result == null)
                    break;
                // Get rid of span elements, which are pop=ups and should not be displayed here
                String fix = result.replaceAll("<span>.+</span>","");
                Spanned spanned;
                if (android.os.Build.VERSION.SDK_INT >= 24)
                    spanned = Html.fromHtml(fix,Html.FROM_HTML_MODE_COMPACT);
                else
                    spanned =  Html.fromHtml(fix);
                AlertDialog.Builder builder = new AlertDialog.Builder(context,
                        R.style.Theme_AppCompat);
                builder.setMessage(spanned);
                builder.show();
                break;
            case Video.ACTION_BACKEND_INFO:
                XmlNode xml = taskRunner.getXmlResult();
                if (xml != null) {
                    XmlNode mach = xml.getNode("MachineInfo");
                    if (mach == null)
                        break;
                    XmlNode stg = mach.getNode("Storage");
                    if (stg == null)
                        break;
                    XmlNode grp = null;
                    for (int ix = 0;;ix++) {
                        grp = stg.getNode("Group",ix);
                        if (grp == null)
                            break;
                        String dir = grp.getAttribute("dir");
                        if ("TotalDiskSpace".equals(dir))
                            break;
                    }
                    if (grp == null)
                        break;
                    String usedStr = grp.getAttribute("used");
                    String totalStr = grp.getAttribute("total");
                    long used = Long.parseLong(usedStr);
                    long total = Long.parseLong(totalStr);
                    if (total > 0)
                        setUsage((int)(used * 100 / total));
                    else
                        // If the total storage is 0 set usage as 100%
                        setUsage(100);
                }
                break;

        }
    }


    public static MainFragment getActiveFragment() {
        return mActiveFragment;
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        int resourceId = R.drawable.background;
        Resources resources = getResources();
        mDefaultBackgroundURI = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(resourceId))
                .appendPath(resources.getResourceTypeName(resourceId))
                .appendPath(resources.getResourceEntryName(resourceId))
                .build();
        mDefaultBackground = resources.getDrawable(R.drawable.background, null);
        mBackgroundTask = new UpdateBackgroundTask();
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        if (mType == TYPE_TOPLEVEL)
            setBadgeDrawable(
                    getActivity().getResources().getDrawable(R.drawable.mythtv_320x180_icon, null));
        setTitle(mBaseName);
        showTitle(TitleViewAdapter.FULL_VIEW_VISIBLE);
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.fastlane_background));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.search_opaque));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter(MainFragment.this);
            }
        });
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        });

        setOnItemViewClickedListener(mItemViewClickedListener = new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        HeadersSupportFragment header = getHeadersSupportFragment();
        if (header != null)
            header.setOnHeaderClickedListener(new HeaderClickedListener());
    }

    private void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;

        if (uri == null)
            return;

        RequestOptions options = new RequestOptions()
                .centerCrop()
                .error(mDefaultBackground);

        Glide.with(this)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(
                            Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
    }

    private void startBackgroundTimer() {
        mHandler.removeCallbacks(mBackgroundTask);
        mHandler.postDelayed(mBackgroundTask, BACKGROUND_UPDATE_DELAY);
    }

    private void updateRecommendations() {
        Intent recommendationIntent = new Intent(getActivity(), UpdateRecommendationsService.class);
        getActivity().startService(recommendationIntent);
    }

    static final String[] articles = MyApplication.getAppContext().getResources().getStringArray(R.array.title_sort_articles);
    /**
     * Create the Sql to sort with excluding articles "the" "a" etc at the front
     * or at the front of directory names
     * @param columnName Column for sorting on
     * @param delim Delimiter to use - ^ for title and / for directory
     * @return StringBuilder with resulting phrase for "order by"
     */
    public static StringBuilder makeTitleSort(String columnName, char delim) {
        // Sort uppercase title
        StringBuilder titleSort = new StringBuilder();
        titleSort.append("'").append(delim).append("'||UPPER(")
                .append(columnName).append(")");
        for (String article : articles) {
            if (article != null && article.length() > 0) {
                titleSort.insert(0, "REPLACE(");
                titleSort.append(",'").append(delim).append(article)
                        .append(" ','").append(delim).append("')");
            }
        }
        return titleSort;
    }

    // replacement for onLoadFinished
    // ArrayList return as follows
    // Each entry is an ArrayList describing one row
    // Each row arraylist has
    //   [0] is a MyHeaderItem
    //   [1] onwards are each a Video

    public void onAsyncLoadFinished(AsyncMainLoader loader, ArrayList<ArrayList<ListItem>> list) {
        isLoaderRunning = false;
        MainActivity main = (MainActivity) getActivity();
        if (main == null)
            return;
        setProgressBar(false);

        // To avoid crash if scrolling when refresh occurs
        // Do not reload if less than 1 second after key access
        if (System.currentTimeMillis() - main.mMultipleKeyTime < 1000)
            return;

        int [] selection = getSelection();
        // Fill in disk usage
        new AsyncBackendCall(null, this).execute(Video.ACTION_BACKEND_INFO);
        // Every time we have to re-get the category loader, we must re-create the sidebar.
        mCategoryRowAdapter.clear();
        ListRow row;
        for (int rownum = 0 ; rownum < list.size() ; rownum++) {
            ArrayList<ListItem> rowList = list.get(rownum);
            MyHeaderItem header = (MyHeaderItem) rowList.get(0);
            ArrayObjectAdapter rowObjectAdapter = new ArrayObjectAdapter(new CardPresenter());
            rowList.remove(0);
            if (rowList.size() > 0)
                rowObjectAdapter.addAll(0, rowList);
            row = new ListRow(header, rowObjectAdapter);
            mCategoryRowAdapter.add(row);
        }

        // Create a row for tools.
        MyHeaderItem gridHeader = new MyHeaderItem(getString(R.string.row_header_tools),
                TYPE_TOOLS,mBaseName);
        CardPresenter presenter = new CardPresenter();
        ArrayObjectAdapter toolsRowAdapter = new ArrayObjectAdapter(presenter);
        row = new ListRow(gridHeader, toolsRowAdapter);
        mCategoryRowAdapter.add(row);

        Video video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_settings))
                .subtitle("")
                .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                .progflags("0")
                .build();
        video.type = TYPE_SETTINGS;
        toolsRowAdapter.add(video);

        video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_refresh_lists))
                .subtitle("")
                .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                .progflags("0")
                .build();
        video.type = TYPE_REFRESH;
        toolsRowAdapter.add(video);

        video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_backend_status))
                .subtitle("")
                .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                .progflags("0")
                .build();
        video.type = TYPE_INFO;
        toolsRowAdapter.add(video);

        video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_manage_recordings))
                .subtitle("")
                .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                .progflags("0")
                .build();
        video.type = TYPE_MANAGE;
        toolsRowAdapter.add(video);

        SelectionSetter setter = new SelectionSetter(selection[0], selection[1]);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(setter, 100);
    }

    public int getType() {
        return mType;
    }

    public static ScheduledExecutorService getExecutor() {
        return executor;
    }

    private class UpdateBackgroundTask implements Runnable {
        @Override
        public void run() {
            if (mBackgroundURI != null) {
                updateBackground(mBackgroundURI.toString());
            }
        }
    }
    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

            ListItem li = (ListItem) item;
            int liType = li.getItemType();
            Activity context = getActivity();
            Bundle bundle;
            MyHeaderItem headerItem = (MyHeaderItem) row.getHeaderItem();
            if (headerItem.getItemType() == TYPE_RECENTS)
                liType = TYPE_EPISODE;
            switch (liType) {
                case TYPE_EPISODE:
                case TYPE_VIDEO:
                case TYPE_CHANNEL:
                    Video video = (Video) item;
                    Intent intent = new Intent(context, VideoDetailsActivity.class);
                    intent.putExtra(VideoDetailsActivity.VIDEO, video);

                    bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            context,
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    context.startActivity(intent, bundle);
                    break;
                case TYPE_SERIES:
                case TYPE_CHANNEL_ALL:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(KEY_TYPE,MainFragment.TYPE_RECGROUP);
                    intent.putExtra(KEY_BASENAME,headerItem.getName());
                    intent.putExtra(KEY_ROWNAME,((Video)li).title);
                    bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(context)
                                    .toBundle();
                    context.startActivity(intent, bundle);
                    break;
                case TYPE_VIDEODIR:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    String baseName = mBaseName;
                    if (mType == TYPE_TOPLEVEL)
                        baseName = "";
                    else {
                        if (baseName != null && baseName.length() > 0)
                            baseName = baseName + "/" + headerItem.getName();
                        else
                            baseName = headerItem.getName();
                    }
                    intent.putExtra(KEY_BASENAME,baseName);
                    intent.putExtra(KEY_ROWNAME,((Video)li).title);
                    bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(context)
                                    .toBundle();
                    context.startActivity(intent, bundle);
                    break;
                case TYPE_SETTINGS:
                    intent = new Intent(context, SettingsActivity.class);
                    startActivity(intent);
                    break;
                case TYPE_REFRESH:
                    setProgressBar(true);
                    int recType = -1;
                    String recGroup = null;
                    if (mType == TYPE_RECGROUP) {
                        recType = VideoContract.VideoEntry.RECTYPE_RECORDING;
                        if (!mBaseName.endsWith("\t"))
                            recGroup = mBaseName;
                    }
                    if (mType == TYPE_VIDEODIR)
                        recType = VideoContract.VideoEntry.RECTYPE_VIDEO;
                    startFetch(recType, null, recGroup);
                    break;
                case TYPE_INFO:
                    new AsyncBackendCall(null,
                            MainFragment.this).execute(Video.ACTION_BACKEND_INFO_HTML);
                    break;
                case TYPE_MANAGE:
                    intent = new Intent(context, ManageRecordingsActivity.class);
                    startActivity(intent);
                    break;
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video && ((Video) item).bgImageUrl != null)
                mBackgroundURI = Uri.parse(((Video) item).bgImageUrl);
            else
                mBackgroundURI = mDefaultBackgroundURI;

            startBackgroundTimer();
            scrollSupport.onItemSelected(itemViewHolder,rowViewHolder, getRowsSupportFragment());
        }
    }

    private final class HeaderClickedListener implements HeadersSupportFragment.OnHeaderClickedListener {
        @Override
        public void onHeaderClicked(RowHeaderPresenter.ViewHolder viewHolder, Row row) {
            Context context = getActivity();
            MyHeaderItem headerItem = (MyHeaderItem) row.getHeaderItem();

            Intent intent;
            int type = headerItem.getItemType();
            switch (type) {
                case MainFragment.TYPE_RECGROUP:
                case MainFragment.TYPE_TOP_ALL:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_RECGROUP);
                    intent.putExtra(MainFragment.KEY_BASENAME,headerItem.getName());
                    break;
                case MainFragment.TYPE_VIDEODIR_ALL:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    intent.putExtra(MainFragment.KEY_BASENAME,"");
                    break;
                case MainFragment.TYPE_VIDEODIR:
                    String name = headerItem.getName();
                    // All and Root entries
                    if (name.endsWith("\t")) {
                        int rownum = mCategoryRowAdapter.indexOf(row);
                        if (rownum == -1)
                            return;
                        if (rownum == getSelectedPosition())
                            startHeadersTransition(false);
                        else
                            setSelectedPosition(rownum);
                        return;
                    }
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    String baseName = headerItem.getBaseName();
                    if (baseName != null && baseName.length() > 0)
                        baseName = baseName + "/" + name;
                    else
                        baseName = name;
                    intent.putExtra(MainFragment.KEY_BASENAME,baseName);
                    break;
                default:
                    int rownum = mCategoryRowAdapter.indexOf(row);
                    if (rownum == -1)
                        return;
                    if (rownum == getSelectedPosition())
                        startHeadersTransition(false);
                    else
                        setSelectedPosition(rownum);
                    return;
            }
            Bundle bundle =
                    ActivityOptionsCompat.makeSceneTransitionAnimation((Activity)context)
                            .toBundle();
            context.startActivity(intent, bundle);

        }
    }

    public boolean onHeaderMenu(MyHeaderItem headerItem) {
        int type = headerItem.getItemType();
        ArrayList<String> prompts = new ArrayList<>();
        ArrayList<Action> actions = new ArrayList<>();
        switch (type) {
            case MainFragment.TYPE_SERIES:
            case MainFragment.TYPE_VIDEODIR:
                Row row = null;
                ObjectAdapter rowsAdapter = getAdapter();
                int size = rowsAdapter.size();
                for (int ix = 0 ; ix < size ; ix++) {
                    row = (Row)rowsAdapter.get(ix);
                    if (row.getHeaderItem() == headerItem)
                        break;
                }
                Row selectedRow = row;
                if (((ListRow)row).getAdapter().size() == 0)
                    break;
                String alertTitle;
                if (type == MainFragment.TYPE_SERIES) {
                    alertTitle = getContext().getString(R.string.title_menu_series,
                            headerItem.getName(),headerItem.getBaseName());
                    if ("Deleted".equals(headerItem.getBaseName())) {
                        prompts.add(getString(R.string.menu_undelete));
                        actions.add(new Action(Video.ACTION_UNDELETE));
                    }
                    else {
                        prompts.add(getString(R.string.menu_delete));
                        actions.add(new Action(Video.ACTION_DELETE));
                        prompts.add(getString(R.string.menu_delete_rerecord));
                        actions.add(new Action(Video.ACTION_DELETE_AND_RERECORD));
                    }
                    prompts.add(getString(R.string.menu_rerecord));
                    actions.add(new Action(Video.ACTION_ALLOW_RERECORD));
                }
                else {
                    String baseName = headerItem.getBaseName();
                    if (baseName.length() > 0)
                        baseName = baseName + "/";
                    alertTitle = getContext().getString(R.string.title_menu_videodir,
                            baseName + headerItem.getName());
                }
                prompts.add(getString(R.string.menu_mark_unwatched));
                actions.add(new Action(Video.ACTION_SET_UNWATCHED));
                prompts.add(getString(R.string.menu_mark_watched));
                actions.add(new Action(Video.ACTION_SET_WATCHED));
                prompts.add(getString(R.string.menu_remove_bookmark));
                actions.add(new Action(Video.ACTION_REMOVE_BOOKMARK));
                prompts.add(getString(R.string.menu_remove_from_recent));
                actions.add(new Action(Video.ACTION_REMOVE_RECENT));

                if (prompts != null && actions != null) {
                    final ArrayList<Action> finalActions = actions; // needed because used in inner class
                    // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                            R.style.Theme_AppCompat_Dialog_Alert);
                    builder
                            .setTitle(alertTitle)
                            .setItems(prompts.toArray(new String[0]),
                                    new DialogInterface.OnClickListener() {
                                        ArrayList<Action> mActions = finalActions;
                                        MainFragment mParent = MainFragment.this;

                                        public void onClick(DialogInterface dialog, int which) {
                                            // The 'which' argument contains the index position
                                            // of the selected item
                                            if (which < mActions.size()) {
                                                mParent.onMenuClicked(mActions.get(which), selectedRow);
                                            }
                                        }
                                    });
                    builder.show();
                }

                return true;
        }
        return true; // Do not treat long press as a short press
    }

    public void onMenuClicked(Action action, Row row) {
        ListRow listRow = (ListRow) row;
        ObjectAdapter rowAdapter = listRow.getAdapter();
        AsyncBackendCall call = new AsyncBackendCall(
                new AsyncBackendCall.OnBackendCallListener() {
                    @Override
                    public void onPostExecute(AsyncBackendCall taskRunner) {
                        if (getContext() == null)
                            return;
                        ArrayList<XmlNode> results = taskRunner.getXmlResults();
                        int nSuccess = 0;
                        int nFail = 0;
                        XmlNode xmlResult;
                        // only look at every alternate result, others are
                        // refresh or dummy
                        for (int ix = 1; ix < results.size(); ix+=2) {
                            xmlResult = results.get(ix);
                            String result = null;
                            if (xmlResult != null)
                                result = xmlResult.getString();
                            if ("true".equals(result))
                                nSuccess++;
                            else
                                nFail++;
                        }
                        if (nFail > 0) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                                    R.style.Theme_AppCompat_Dialog_Alert);
                            builder.setTitle(R.string.title_alert_rowresults);
                            String msg = getContext().getString(R.string.alert_rowresults, nSuccess, nFail);
                            builder.setMessage(msg);
                            builder.show();
                        }
                    }
        });
        call.setBookmark(0);
        call.setPosBookmark(0);
        call.setRowAdapter(rowAdapter);
        Integer [] tasks;
        int task = (int)action.getId();

        switch (task) {
            case Video.ACTION_DELETE:
            case Video.ACTION_DELETE_AND_RERECORD:
                tasks = new Integer [] {Video.ACTION_REFRESH, task};
                break;
            case Video.ACTION_SET_UNWATCHED:
            case Video.ACTION_SET_WATCHED:
                call.setWatched(task == Video.ACTION_SET_WATCHED);
                // Set the task since both watched and unwatched are done with
                // ACTION_SET_WATCHED in AsyncBackend
                task = Video.ACTION_SET_WATCHED;
                // Fall Through to default
            default:
                tasks = new Integer [] {Video.ACTION_DUMMY, task};
                break;
        }
        call.execute(tasks);
        setProgressBar(true);
    }

    private static class MythTask implements Runnable {
        boolean mVersionMessageShown = false;

        @Override
        public void run() {
            try {
                scheduledTaskRunning = true;
                boolean connection = false;
                boolean connectionfail = false;
                if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                        == Lifecycle.State.CREATED) {
                    // process is now in the background
                    mWasInBackground = true;
                    if (executor != null)
                        executor.shutdown();
                    executor = null;
                    return;
                }
                String backendIP = Settings.getString("pref_backend");
                if (backendIP == null || backendIP.length() == 0)
                    return;
                while (!connection) {
                    if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                            == Lifecycle.State.CREATED) {
                        // process is now in the background
                        mWasInBackground = true;
                        if (executor != null)
                            executor.shutdown();
                        executor = null;
                        return;
                    }
                    int toastMsg = 0;
                    int toastLeng = 0;
                    try {
                        String result = null;
                        String url = XmlNode.mythApiUrl(null,
                                "/Myth/DelayShutdown");
                        if (url == null)
                            return;
                        XmlNode bkmrkData = XmlNode.fetch(url, "POST");
                        result = bkmrkData.getString();
                        connection = true;
                    } catch (FileNotFoundException e) {
                        if (!mVersionMessageShown) {
                            toastMsg = R.string.msg_no_delayshutdown;
                            toastLeng = Toast.LENGTH_LONG;
                            mVersionMessageShown = true;
                        }
                        connection = true;
                    } catch (IOException e) {
                        toastMsg = R.string.msg_no_connection;
                        toastLeng = Toast.LENGTH_LONG;
                        connectionfail = true;
                        mFetchTime = 0; // Force a fetch when it comes back
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    if (connectionfail)
                        if (wakeBackend())
                            toastMsg = R.string.msg_wake_backend;

                    if (toastMsg != 0) {
                        Activity activity = MainActivity.getContext();
                        if (activity == null)
                            return;
                        ToastShower toastShower = new ToastShower(activity, toastMsg, toastLeng);
                        activity.runOnUiThread(toastShower);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                if (mFetchTime < System.currentTimeMillis() - 60 * 60 * 1000) {
                    Activity activity = MainActivity.getContext();
                    if (activity == null)
                        return;
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            MainActivity.getContext().getMainFragment().startFetch(-1, null, null);
                        }
                    });
                }
            } finally {
                scheduledTaskRunning = false;
            }
        }

        public boolean wakeBackend() {
            MainActivity main = MainActivity.getContext();
            if (main == null)
                return false;
            String backendMac = Settings.getString("pref_backend_mac");
            if (backendMac.length() == 0)
                return false;

            // The magic packet is a broadcast frame containing anywhere within its payload
            // 6 bytes of all 255 (FF FF FF FF FF FF in hexadecimal), followed by sixteen
            // repetitions of the target computer's 48-bit MAC address, for a total of 102 bytes.

            byte [] msg = new byte[102];
            int ix;
            for (ix=0; ix < 6; ix++)
                msg[ix] = (byte)0xff;

            int  msglen = 6;
            String[] tokens = backendMac.split(":");
            byte[] macaddr = new byte[6];

            if (tokens.length != 6) {
                Log.e(TAG, CLASS + " wakeBackend WakeOnLan("+backendMac+"): Incorrect MAC length");
                return false;
            }

            for (int y = 0; y < 6; y++)
            {
                try {
                    macaddr[y] = (byte) Integer.parseInt(tokens[y], 16);
                } catch (NumberFormatException e) {
                    Log.e(TAG, CLASS +" wakeBackend WakeOnLan("+backendMac+"): Invalid MAC address");
                    return false;
                }

            }

            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 6; y++)
                    msg[msglen++] = macaddr[y];

            Log.i(TAG, CLASS + " wakeBackend WakeOnLan(): Sending WOL packet to "+backendMac);

            try {
                DatagramPacket DpSend = new DatagramPacket(msg, msg.length, InetAddress.getByName("255.255.255.255"), 9);
                DatagramSocket ds = new DatagramSocket();
                ds.send(DpSend);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    }


    private static class ToastShower implements Runnable {

        private Activity activity;
        private int toastMsg;
        private int toastLeng;
        private static Toast mToast;

        public ToastShower(Activity activity, int toastMsg, int toastLeng) {
            this.activity = activity;
            this.toastMsg = toastMsg;
            this.toastLeng = toastLeng;
        }
        public void run() {
            // show toast here
            if (mToast != null)
                mToast.cancel();
            mToast = Toast.makeText(activity,
                    activity.getString(toastMsg), toastLeng);
            mToast.show();
        }
    }

    private class SelectionSetter implements Runnable {

        private int selectedRowNum;
        private int selectedItemNum;

        public SelectionSetter(int selectedRowNum, int selectedItemNum) {
            this.selectedRowNum = selectedRowNum;
            this.selectedItemNum = selectedItemNum;
        }
        public void run() {
            RowsSupportFragment frag = getRowsSupportFragment();
            if (frag != null) {
                // Note we do not need to check selectedRowNum or
                // selectedItemNum, if either is more than the maximum
                // there is no exception - it just selects the last item.
                ListRowPresenter.SelectItemViewHolderTask task
                        = new ListRowPresenter.SelectItemViewHolderTask(selectedItemNum);
                task.setSmoothScroll(false);
                frag.setSelectedPosition(selectedRowNum, false, task);
                if (selectedItemNum == -1)
                    getHeadersSupportFragment().getView().requestFocus();
            }
        }
    }

}
