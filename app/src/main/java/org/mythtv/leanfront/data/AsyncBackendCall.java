/*
 * Copyright (c) 2019-2020 Peter Bennett
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

package org.mythtv.leanfront.data;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.leanback.widget.ObjectAdapter;

import org.mythtv.leanfront.MyApplication;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.RecordRule;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.model.VideoCursorMapper;
import org.mythtv.leanfront.ui.MainFragment;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncBackendCall implements Runnable {

    public interface OnBackendCallListener {
        void onPostExecute(AsyncBackendCall taskRunner);
    }

    private Video mVideo;
    private long mValue; // Used for recordid or file length
    // if posBookmark is >=0 use that, else use mBookmark for bookmark
    private long mBookmark = -1;
    private long mPosBookmark = -1;
    private long mLastPlay = -1;
    private long mPosLastPlay = -1;
    private OnBackendCallListener listener;
    private Activity activity;
    private View view;
    private boolean mWatched;
    private int [] mTasks;
    private Integer [] inTasks;
    private long mFileLength = -1;
    private long mRecordId = -1;
    private long mRecordedId = -1;
    private String mStringResult = null;
    private ArrayList<XmlNode> mXmlResults = new ArrayList<>();
    private Date mStartTime;
    private Date mEndTime;
    private int mId;
    private String mName;
    private RecordRule mRecordRule;
    private String mStringParameter;
    private ObjectAdapter rowAdapter;
    private CommBreakTable commBreakTable;
    private final static ExecutorService executor = Executors.newSingleThreadExecutor();

    // Parsing results of GetRecorded
    private static final String[] XMLTAGS_RECGROUP = {"Recording","RecGroup"};
    private static final String[] XMLTAGS_PROGRAMFLAGS = {"ProgramFlags"};
    private static final String[] XMLTAGS_RECORDID = {"Recording", "RecordId"};
    private static final String[] XMLTAGS_RECORDEDID = {"Recording", "RecordedId"};
    private static final String[] XMLTAGS_ENDTIME = {"Recording", "EndTs"};
    private static final String XMLTAG_WATCHED = "Watched";
    private static final String VALUE_WATCHED = (new Integer(Video.FL_WATCHED)).toString();

    private static final String TAG = "lfe";
    private static final String CLASS = "AsyncBackendCall";

    private static long mTimeAdjustment = 0;
    private static int mythTvVersion;

    public AsyncBackendCall(@Nullable Activity activity, @Nullable OnBackendCallListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public long getBookmark() {
        return mBookmark;
    }

    public void setBookmark(long bookmark) {
        this.mBookmark = bookmark;
    }

    public long getPosBookmark() {
        return mPosBookmark;
    }

    public void setPosBookmark(long posBookmark) {
        this.mPosBookmark = posBookmark;
    }

    public long getLastPlay() {
        return mLastPlay;
    }

    public void setLastPlay(long LastPlay) {
        this.mLastPlay = LastPlay;
    }

    public long getPosLastPlay() {
        return mPosLastPlay;
    }

    public void setPosLastPlay(long posLastPlay) {
        this.mPosLastPlay = posLastPlay;
    }

    public long getFileLength() {
        return mFileLength;
    }

    public Video getVideo() {
        return mVideo;
    }

    public int[] getTasks() {
        return mTasks;
    }

    public long getRecordId() {
        return mRecordId;
    }

    public long getRecordedId() {
        return mRecordedId;
    }

    public String getStringResult() {
        return mStringResult;
    }

    public XmlNode getXmlResult()
    {
        if (mXmlResults.size() > 0)
            return mXmlResults.get(0);
        else
            return null;
    }

    public ArrayList<XmlNode> getXmlResults() {
        return mXmlResults;
    }

    public void setStartTime(Date mStartTime) {
        this.mStartTime = mStartTime;
    }

    public void setEndTime(Date mEndTime) {
        this.mEndTime = mEndTime;
    }

    public void setId(int id) {
        this.mId = id;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setRecordRule(RecordRule recordRule) {
        this.mRecordRule = recordRule;
    }

    public void setStringParameter(String stringParameter) {
        this.mStringParameter = stringParameter;
    }

    public void setWatched(boolean watched) {
        this.mWatched = watched;
    }

    public String getStringParameter() {
        return mStringParameter;
    }

    public void setRowAdapter(ObjectAdapter rowAdapter) {
        this.rowAdapter = rowAdapter;
    }

    public static int getMythTvVersion() {
        return mythTvVersion;
    }

    public void setmValue(long mValue) {
        this.mValue = mValue;
    }

    public void setCommBreakTable(CommBreakTable commBreakTable) {
        this.commBreakTable = commBreakTable;
    }

    public void setVideo(Video mVideo) {
        this.mVideo = mVideo;
    }

    public void setView(View view) {
        this.view = view;
    }

    public void execute(Integer ... tasks) {
        inTasks = tasks;
        executor.submit(this);
    }

    @Override
    public void run() {
        try {
            runTasks();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        finally {
            if (listener != null) {
                if (activity != null)
                    activity.runOnUiThread(() -> listener.onPostExecute(this));
                else if (view != null)
                    view.post(() -> listener.onPostExecute(this));
                else
                    listener.onPostExecute(this);
            }
        }
    }

    private void runTasks() {
        mTasks = new int[inTasks.length];
        Context context = MyApplication.getAppContext();
        HttpURLConnection urlConnection = null;
        int videoIndex = 0;
        int taskIndex = -1;
        for(;;) {
            // If there is a rowAdapter, take each video in the adapter and run
            // all tasks on it.
            taskIndex++;
            if (taskIndex >= inTasks.length) {
                if (rowAdapter == null)
                    break;
                taskIndex = 0;
                videoIndex++;
            }
            if (rowAdapter != null && videoIndex >= rowAdapter.size())
                break;

            if (rowAdapter != null) {
                mVideo = (Video) rowAdapter.get(videoIndex);
                // in row adapter only process videos and series.
                if ( ! (mVideo.type == MainFragment.TYPE_VIDEO
                        || mVideo.type == MainFragment.TYPE_EPISODE))
                    continue;
            }
            int task = inTasks[taskIndex];
            mTasks[taskIndex] = task;
            boolean found;
            boolean isRecording = false;
            if (mVideo != null)
                isRecording = (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
            String urlString = null;
            String urlMethod = null;
            boolean allowRerecord = false;
            XmlNode xmlResult = null;
            String paramValue = null;
            switch (task) {
                case Video.ACTION_REFRESH:
                    mBookmark = 0;
                    mPosBookmark = -1;
                    mLastPlay = 0;
                    mPosLastPlay = -1;
                    try {
                        if (context == null)
                            return;
                        // If there is a local bookmark always use it before checking
                        // for a MythTV bookmark.

                        // Look for a local bookmark
                        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
                        SQLiteDatabase db = dbh.getReadableDatabase();

                        // Define a projection that specifies which columns from the database
                        // you will actually use after this query.
                        String[] projection = {
                                VideoContract.StatusEntry._ID,
                                VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH,
                                VideoContract.StatusEntry.COLUMN_LAST_USED,
                                VideoContract.StatusEntry.COLUMN_BOOKMARK,
                                VideoContract.StatusEntry.COLUMN_SHOW_RECENT
                        };

                        // Filter results
                        String selection = VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH + " = ?";
                        String[] selectionArgs = {mVideo.videoUrlPath};

                        Cursor cursor = db.query(
                                VideoContract.StatusEntry.TABLE_NAME,   // The table to query
                                projection,             // The array of columns to return (pass null to get all)
                                selection,              // The columns for the WHERE clause
                                selectionArgs,          // The values for the WHERE clause
                                null,                   // don't group the rows
                                null,                   // don't filter by row groups
                                null               // The sort order
                        );

                        // We expect one or zero results, never more than one.
                        if (cursor.moveToNext()) {
                            int colno = cursor.getColumnIndex(VideoContract.StatusEntry.COLUMN_BOOKMARK);
                            mLastPlay = cursor.getLong(colno);
                            colno = cursor.getColumnIndex(VideoContract.StatusEntry.COLUMN_SHOW_RECENT);
                            mVideo.showRecent = cursor.getInt(colno) != 0;
                        }
                        cursor.close();

                        // This is actually last play pos but stored as pref_bookmark
                        // for backward compatibility
                        String pref = Settings.getString("pref_bookmark");
                        // If no local bookmark was found look for one on MythTV
                        if (mLastPlay <= 0 && ("mythtv".equals(pref) || "auto".equals(pref))
                                && MainFragment.supportLastPlayPos) {
                            long[] playNPos = fetchBookmark("GetLastPlayPos");
                            mLastPlay = playNPos[0];
                            mPosLastPlay = playNPos[1];
                        }
                        long[] bookNPos = fetchBookmark("GetSavedBookmark");
                        mBookmark = bookNPos[0];
                        mPosBookmark = bookNPos[1];
                        if (isRecording) {
                            // Find out rec group
                            urlString = XmlNode.mythApiUrl(mVideo.hostname,
                                    "/Dvr/GetRecorded?RecordedId="
                                            + mVideo.recordedid);
                            xmlResult = XmlNode.fetch(urlString, null);
                            mVideo.recGroup = xmlResult.getString(XMLTAGS_RECGROUP);
                            mVideo.progflags = xmlResult.getString(XMLTAGS_PROGRAMFLAGS);
                            String newEndtime = xmlResult.getString(XMLTAGS_ENDTIME);

                            if (context != null && !Objects.equals(mVideo.endtime, newEndtime)) {
                                mVideo.endtime = newEndtime;
                                MainFragment.startFetch(VideoContract.VideoEntry.RECTYPE_RECORDING,
                                        mVideo.recordedid, null);
                            }
                        }
                        else {
                            urlString = XmlNode.mythApiUrl(mVideo.hostname,
                                    "/Video/GetVideo?Id="
                                            + mVideo.recordedid);
                            xmlResult = XmlNode.fetch(urlString, null);
                            String watched = xmlResult.getString(XMLTAG_WATCHED);
                            if ("true".equals(watched))
                                watched = VALUE_WATCHED;
                            else
                                watched = "0";
                            mVideo.progflags = watched;
                            if (context != null) {
                                MainFragment.startFetch(VideoContract.VideoEntry.RECTYPE_VIDEO,
                                        mVideo.recordedid, null);
                            }
                        }
                    } catch(IOException | XmlPullParserException e){
                        mBookmark = 0;
                        mLastPlay = 0;
                        e.printStackTrace();
                    }
                    break;
                case Video.ACTION_DELETE_AND_RERECORD:
                    allowRerecord = true;
                case Video.ACTION_DELETE:
                    // Delete recording
                    // If already deleted do not delete again.
                    if (!isRecording || "Deleted".equals(mVideo.recGroup))
                        break;
                    try {
                        urlString = XmlNode.mythApiUrl(mVideo.hostname,
                                "/Dvr/DeleteRecording?RecordedId="
                                        + mVideo.recordedid
                                        + "&AllowRerecord=" + allowRerecord);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        if (context != null)
                            MainFragment.startFetch(VideoContract.VideoEntry.RECTYPE_RECORDING,
                                    mVideo.recordedid, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Video.ACTION_UNDELETE:
                    // UnDelete recording
                    if (!isRecording)
                        break;
                    try {
                        urlString = XmlNode.mythApiUrl(mVideo.hostname,
                                "/Dvr/UnDeleteRecording?RecordedId="
                                        + mVideo.recordedid);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        if (context != null)
                            MainFragment.startFetch(VideoContract.VideoEntry.RECTYPE_RECORDING,
                                    mVideo.recordedid, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Video.ACTION_REMOVE_BOOKMARK:
                    mBookmark = 0;
                    mPosBookmark = 0;
                case Video.ACTION_SET_BOOKMARK:
                    // when using this method, mBookmark and mPosBookmark must both be set.
                    try {
                        xmlResult = updateBookmark("SetSavedBookmark", mBookmark, mPosBookmark);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Video.ACTION_REMOVE_LASTPLAYPOS:
                    mLastPlay = 0;
                    mPosLastPlay = 0;
                    // fall through
                case Video.ACTION_SET_LASTPLAYPOS:
                    // when using this method, mLastPlay and mPosLastPlay must both be set.
                    try {
                        found = false;
                        String method;
                        if (MainFragment.supportLastPlayPos)
                            method = "SetLastPlayPos";
                        else
                            method = "SetSavedBookmark";
                        // This is actually last play pos but stored as pref_bookmark
                        // for backward compatibility
                        String pref = Settings.getString("pref_bookmark");
                        if ("mythtv".equals(pref) || "auto".equals(pref)) {
                            xmlResult = updateBookmark(method, mLastPlay, mPosLastPlay);
                            String result = xmlResult.getString();
                            if ("true".equals(result))
                                found = true;
                        }

                        // If bookmark was updated on MythTV, reset local one to 0.
                        long localBkmark = 0;
                        if (!found) {
                            // We need this to force local update for mythbackend V30 or older
                            // where bookmarks are not supported by the api.
                            localBkmark = mLastPlay;
                        }

                        // Update local bookmark

                        // Gets the data repository in write mode
                        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
                        SQLiteDatabase db = dbh.getWritableDatabase();

                        // Create a new map of values, where column names are the keys
                        ContentValues values = new ContentValues();
                        values.put(VideoContract.StatusEntry.COLUMN_LAST_USED, System.currentTimeMillis());
                        values.put(VideoContract.StatusEntry.COLUMN_BOOKMARK, localBkmark);
                        values.put(VideoContract.StatusEntry.COLUMN_SHOW_RECENT, 1);

                        // First try an update
                        String selection = VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH + " = ?";
                        String[] selectionArgs = {mVideo.videoUrlPath};

                        int sqlCount = db.update(
                                VideoContract.StatusEntry.TABLE_NAME,
                                values,
                                selection,
                                selectionArgs);

                        if (sqlCount == 0) {
                            // Try an insert instead
                            values.put(VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH, mVideo.videoUrlPath);
                            // Insert the new row, returning the primary key value of the new row
                            long newRowId = db.insert(VideoContract.StatusEntry.TABLE_NAME,
                                    null, values);
                        }
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Video.ACTION_SET_WATCHED:
                    // This handles both set watched and set unwatched, depending on your setting for
                    // mWatched.
                    try {
                        int type;
                        if (isRecording) {
                            // set recording watched
                            urlString = XmlNode.mythApiUrl(mVideo.hostname,
                                    "/Dvr/UpdateRecordedWatchedStatus?RecordedId="
                                            + mVideo.recordedid + "&Watched=" + mWatched);
                            type = VideoContract.VideoEntry.RECTYPE_RECORDING;
                        }
                        else {
                            // set video watched
                            urlString = XmlNode.mythApiUrl(mVideo.hostname,
                                    "/Video/UpdateVideoWatchedStatus?Id="
                                            + mVideo.recordedid + "&Watched=" + mWatched);
                            type = VideoContract.VideoEntry.RECTYPE_VIDEO;
                        }
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        if (context != null)
                            MainFragment.startFetch(type, mVideo.recordedid, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Video.ACTION_REMOVE_RECENT: {
                    // Gets the data repository in write mode
                    VideoDbHelper dbh = VideoDbHelper.getInstance(context);
                    SQLiteDatabase db = dbh.getWritableDatabase();
                    // Create a new map of values, where column names are the keys
                    ContentValues values = new ContentValues();
                    values.put(VideoContract.StatusEntry.COLUMN_SHOW_RECENT, 0);

                    // First try an update
                    String selection = VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH + " = ?";
                    String[] selectionArgs = {mVideo.videoUrlPath};

                    db.update(
                            VideoContract.StatusEntry.TABLE_NAME,
                            values,
                            selection,
                            selectionArgs);

                    if (context != null)
                        MainFragment.startFetch(mVideo.rectype, mVideo.recordedid, null);
                    // Fake out an xml node with true to pass back success status
                    xmlResult = new XmlNode();
                    xmlResult.setString("true");
                    break;
                }
                case Video.ACTION_FILELENGTH:
                    // mValue is prior file length to be checked against
                    // Try 10 times until file length increases.
                    urlString = mVideo.videoUrl;
                    mFileLength = -1;
                    for (int counter = 0 ; counter < 5 ; counter++) {
                        try {
                            // pause 1 second between attempts
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                        try {
                            URL url = new URL(urlString);
                            urlConnection = (HttpURLConnection) url.openConnection();
                            urlConnection.addRequestProperty("Cache-Control", "no-cache");
                            urlConnection.addRequestProperty("Accept-Encoding", "identity");
                            urlConnection.setConnectTimeout(1000);
                            urlConnection.setReadTimeout(1000);
                            urlConnection.setRequestMethod("HEAD");
                            Log.i(TAG, CLASS + " URL: " + urlString);
                            urlConnection.connect();
                            try {
                                Log.d(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                        + " " + urlConnection.getResponseMessage());
                            } catch(Exception ignored) {
                                // Sometimes there is a ProtocolException in the urlConnection.getResponseCode
                                // Ignore the error so that we can continue
                            }
                            String strContentLeng = urlConnection.getHeaderField("Content-Length");
                            if (strContentLeng != null)
                                mFileLength = Long.parseLong(strContentLeng);
                            if (mFileLength > mValue)
                                break;
                        } catch (Exception e) {
                            try {
                                Log.i(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                        + " " + urlConnection.getResponseMessage());
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                            Log.e(TAG, CLASS + " Exception getting file length.",e);
                        } finally {
                            if (urlConnection != null)
                                urlConnection.disconnect();
                        }
                    }
                    break;
                case Video.ACTION_LIVETV:
                    // Schedule a recording for 3 hours starting now
                    // Wait for recording to be ready
                    // Play recording, passing in the info needed for cancelling it on exit.

                    // Replace the video (channel dummy video) in this object with the recording
                    Video channel = mVideo;
                    mVideo = null;
                    try {
                        // Get values needed to set up recording
                        Date startTime = new Date(System.currentTimeMillis() + mTimeAdjustment);
                        // 3 hours
                        String pref = Settings.getString("pref_livetv_duration");
                        int duration = 60;
                        try {
                            duration = Integer.parseInt(pref, 10);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            duration = 60;
                        }
                        if (duration < 15)
                            duration = 15;
                        else if (duration > 360)
                            duration = 360;
                        Date endTime = new Date(startTime.getTime()+duration*60*1000);
                        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
                        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
                        String recDate = sdfDate.format(startTime);
                        String recTime = sdfTime.format(startTime);
                        String title = context.getString(R.string.title_livetv_recording)
                                + " " + recDate;
                        String subtitle = recTime + " ch " + channel.channum;
                        SimpleDateFormat sdfUTC = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        sdfUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
                        urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/AddRecordSchedule?Title="
                                        + URLEncoder.encode(title, "UTF-8")
                                        + "&Subtitle=" + URLEncoder.encode(subtitle, "UTF-8")
                                        + "&Chanid=" + channel.chanid
                                        + "&Station=" + channel.callsign
                                        + "&StartTime=" + URLEncoder.encode(sdfUTC.format(startTime), "UTF-8")
                                        + "&EndTime=" + URLEncoder.encode(sdfUTC.format(endTime), "UTF-8")
                                        + "&Type=Single+Record"
                                        // Use a nonsense FindDay and FindTime because they are required by the API
                                        // but not used for this type of recording.
                                        + "&FindDay=1&FindTime=21%3A30%3A00"
                                        + "&SearchType=Manual+Search&AutoExpire=true&RecPriority=-99"
                                        + "&RecGroup=LiveTV&StorageGroup=LiveTV"
                                        );
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        String result = xmlResult.getString();
                        Log.i(TAG, CLASS + " Live TV scheduled, RecordId:" + result);
                        mRecordId = Integer.parseInt(result);
                        // Now try to find the recording
                        urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/GetRecordedList?RecGroup=LiveTV"
                                + "&TitleRegEx=" + URLEncoder.encode("^"+title+"$", "UTF-8"));
                        found = false;
                        int ixFound = -1;
                        for (int icount = 0; icount < 15 && !found; icount ++) {
                            xmlResult = XmlNode.fetch(urlString,null);
                            Log.d(TAG, CLASS + " Found " + xmlResult.getString("Count") +" recordings");
                            XmlNode programNode = null;
                            for (ixFound = 0; ; ixFound++ ) {
                                if (programNode == null)
                                    programNode = xmlResult.getNode(VideoDbBuilder.XMLTAGS_PROGRAM, 0);
                                else
                                    programNode = programNode.getNextSibling();
                                if (programNode == null)
                                    break;
                                String tmpRecordId = programNode.getString(XMLTAGS_RECORDID);
                                if (tmpRecordId != null && Integer.parseInt(tmpRecordId) == mRecordId) {
                                    String fileSizeStr = programNode.getString(VideoDbBuilder.XMLTAG_FILESIZE);
                                    String tmpRecordedId = programNode.getString(XMLTAGS_RECORDEDID);
                                    if (tmpRecordedId != null)
                                        mRecordedId = Integer.parseInt(tmpRecordedId);
                                    long fileSize = 0;
                                    if (fileSizeStr != null)
                                        fileSize = Long.parseLong(fileSizeStr);
                                    // Skip dummy LiveTV entry
                                    if (fileSize > 1000) {
                                        Log.i(TAG, CLASS + " Found matching recording " + ixFound + ". RecordedId:" + tmpRecordedId);
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            Thread.sleep(1000);
                        }
                        if (!found || ixFound < 0) {
                            Log.e(TAG, CLASS + " Failed to find matching recording.");
                            return;
                        }
                        VideoDbBuilder builder = new VideoDbBuilder(context);
                        List<ContentValues> contentValuesList = new ArrayList<>();
                        builder.buildMedia(xmlResult, 0, ixFound, contentValuesList);
                        ContentValues[] downloadedVideoContentValues =
                                contentValuesList.toArray(new ContentValues[contentValuesList.size()]);
                        context.getContentResolver().bulkInsert(VideoContract.VideoEntry.CONTENT_URI,
                                downloadedVideoContentValues);

                        // Get recording from DB
                        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
                        SQLiteDatabase db = dbh.getReadableDatabase();

                        // Filter results
                        String selection = VideoContract.VideoEntry.COLUMN_RECORDEDID + " = " + mRecordedId
                                + " AND " + VideoContract.VideoEntry.COLUMN_RECTYPE
                                           + " = " + VideoContract.VideoEntry.RECTYPE_RECORDING;
                        Cursor cursor = db.query(
                                VideoContract.VideoEntry.TABLE_NAME,   // The table to query
                                null,             // The array of columns to return (pass null to get all)
                                selection,              // The columns for the WHERE clause
                                null,          // The values for the WHERE clause
                                null,                   // don't group the rows
                                null,                   // don't filter by row groups
                                null               // The sort order
                        );

                        // We expect one or zero results, never more than one.
                        if (cursor.moveToNext()) {
                            VideoCursorMapper mapper = new VideoCursorMapper();
                            mVideo = (Video) mapper.convert(cursor);
                        }
                        else
                            Log.e(TAG, CLASS + " Failed to find recording on SQLite.");

                        cursor.close();
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception setting up Live TV.", e);
                    }
                    break;
                case Video.ACTION_STOP_RECORDING:
                    // Stop recording
                    if ("-1".equals(mVideo.recordedid))
                        break;
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/StopRecording?RecordedId=" + mVideo.recordedid);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        String result = xmlResult.getString();
                        if ("true".equals(result))
                            Log.i(TAG, CLASS + " Recording Stopped. RecordedId:" + mVideo.recordedid);
                        else
                            Log.e(TAG, CLASS + " Stop Recording Failed.");
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Stopping Recording.", e);
                    }
                    break;

                case Video.ACTION_REMOVE_RECORD_RULE:
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/RemoveRecordSchedule?RecordId=" + mValue);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        String result = xmlResult.getString();
                        if ("true".equals(result))
                            Log.i(TAG, CLASS + " Record Rule Removed. recordId:" + mValue);
                        else
                            Log.e(TAG, CLASS + " Remove Record Rule Failed.");
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Removing Record Rule.", e);
                    }
                    break;
                case Video.ACTION_BACKEND_INFO:
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Status/GetStatus");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting backend Info.", e);
                    }
                    if (xmlResult != null) {
                        String version = xmlResult.getAttribute("version");
                        if (version != null) {
                            int period = version.indexOf('.');
                            if (period > 0) {
                                mythTvVersion = Integer.parseInt(version.substring(0, period));
                                if (mythTvVersion == 0 && period == 1)
                                    // For versions like 0.24
                                    mythTvVersion = Integer.parseInt(version.substring(2,4));
                            }
                        }
                        String dateStr = xmlResult.getAttribute("ISODate");
                        if (dateStr != null) {
                            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
                            try {
                                Date backendTime = dbFormat.parse(dateStr + "+0000");
                                mTimeAdjustment = backendTime.getTime() - System.currentTimeMillis();
                                Log.i(TAG, CLASS + " Time difference " + mTimeAdjustment + " milliseconds");
                            } catch (ParseException e) {
                                Log.e(TAG, CLASS + " Exception getting backend time " + urlString, e);
                            }
                        }
                    }
                    // Find if we support the LastPlayPos API's
                    long tResult = 0;
                    XmlNode testNode = null;
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/GetLastPlayPos?RecordedId=-1");
                        testNode = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception in GetLastPlayPos. " + e);
                    }
                    if (testNode != null) {
                        try {
                            tResult = Long.parseLong(testNode.getString());
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            tResult = 0;
                        }
                    }
                    if (tResult == -1)
                        MainFragment.supportLastPlayPos = true;
                    else
                        MainFragment.supportLastPlayPos = false;
                    Log.i(TAG, CLASS + " Last Play Position Support:" + MainFragment.supportLastPlayPos);
                    break;
                case Video.ACTION_BACKEND_INFO_HTML:
                    InputStream is = null;
                    urlString = null;
                    mStringResult = null;
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Status/GetStatusHTML");
                        URL url = new URL(urlString);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.addRequestProperty("Cache-Control", "no-cache");
                        urlConnection.setConnectTimeout(5000);
                        urlConnection.setReadTimeout(30000);
                        Log.i(TAG, CLASS + " URL: " + urlString);
                        is = urlConnection.getInputStream();
                        Log.i(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                + " " + urlConnection.getResponseMessage());
                        InputStreamReader reader = new InputStreamReader(is);
                        char[] buffer = new char[1024];
                        StringBuilder output = new StringBuilder();
                        int leng = 0;
                        for ( ; ; ) {
                            leng = reader.read(buffer, 0, buffer.length);
                            if (leng == -1)
                                break;
                            if (leng > 0)
                                output.append(buffer, 0, leng);
                        }
                        mStringResult = output.toString();

                    } catch (Exception e) {
                        try {
                            Log.d(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                    + " " + urlConnection.getResponseMessage());
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                        Log.e(TAG, CLASS + " Exception getting backend status. " + urlString, e);
                    } finally {
                        if (urlConnection != null)
                            urlConnection.disconnect();
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException e) {
                                Log.e(TAG, CLASS + " Exception on URL: " + urlString, e);
                            }
                        }
                    }

                    break;

                case Video.ACTION_GUIDE:
                    try {
                        SimpleDateFormat sdfUTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        sdfUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
                        urlString = XmlNode.mythApiUrl(null,
                                "/Guide/GetProgramList?StartTime="
                                        + URLEncoder.encode(sdfUTC.format(mStartTime), "UTF-8")
                                        + "&EndTime=" + URLEncoder.encode(sdfUTC.format(mEndTime), "UTF-8")
                                        + "&Details=1");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting Guide.", e);
                    }
                    break;

                case Video.ACTION_GETPROGRAMDETAILS:
                    try {
                        SimpleDateFormat sdfUTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        sdfUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
                        urlString = XmlNode.mythApiUrl(null,
                                "/Guide/GetProgramDetails?ChanId=" + mId
                                        + "&StartTime=" + URLEncoder.encode(sdfUTC.format(mStartTime), "UTF-8"));
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting Program Details.", e);
                    }
                    break;

                case Video.ACTION_GETRECORDSCHEDULE:
                    // Note - this needs either mId or mName filled in, for
                    // a record rule or a template
                    try {
                        StringBuilder urlBuilder = new StringBuilder
                                (XmlNode.mythApiUrl(null,
                                        "/Dvr/GetRecordSchedule?"));
                        if (mId > 0)
                            urlBuilder.append("RecordId=").append(mId);
                        else
                            urlBuilder.append("Template=").append(mName);
                        xmlResult = XmlNode.fetch(urlBuilder.toString(), null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting Record Schedule.", e);
                    }
                    break;

                case Video.ACTION_ADD_OR_UPDATERECRULE:
                    try {
                        SimpleDateFormat sdfUTC = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        sdfUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
                        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
                        String baseURL;
                        if (mRecordRule.recordId == 0)
                            baseURL = "/Dvr/AddRecordSchedule?";
                        else
                            baseURL = "/Dvr/UpdateRecordSchedule?RecordId="
                                    + mRecordRule.recordId + "&";
                        StringBuilder urlBuilder = new StringBuilder
                                (XmlNode.mythApiUrl(null,
                                        baseURL));
                        urlBuilder.append("Title=").append(URLEncoder.encode(nvl(mRecordRule.title), "UTF-8"))
                                .append("&Subtitle=").append(URLEncoder.encode(nvl(mRecordRule.subtitle), "UTF-8"))
                                .append("&Description=").append(URLEncoder.encode(nvl(mRecordRule.description), "UTF-8"))
                                .append("&Category=").append(URLEncoder.encode(nvl(mRecordRule.category), "UTF-8"))
                                .append("&StartTime=").append(URLEncoder.encode(sdfUTC.format(mRecordRule.startTime), "UTF-8"))
                                .append("&EndTime=").append(URLEncoder.encode(sdfUTC.format(mRecordRule.endTime), "UTF-8"))
                                .append("&SeriesId=").append(nvl(mRecordRule.seriesId))
                                .append("&ProgramId=").append(nvl(mRecordRule.programId))
                                .append("&ChanId=").append(mRecordRule.chanId)
                                .append("&Station=").append(URLEncoder.encode(nvl(mRecordRule.station), "UTF-8"))
                                .append("&FindDay=").append(mRecordRule.findDay)
                                .append("&FindTime=").append(URLEncoder.encode(nvl(mRecordRule.findTime), "UTF-8"))
                                .append("&ParentId=").append(mRecordRule.parentId)
                                .append("&Inactive=").append(mRecordRule.inactive)
                                .append("&Season=").append(mRecordRule.season)
                                .append("&Episode=").append(mRecordRule.episode)
                                .append("&Inetref=").append(URLEncoder.encode(nvl(mRecordRule.inetref),"UTF-8"))
                                .append("&Type=").append(URLEncoder.encode(mRecordRule.type,"UTF-8"))
                                .append("&SearchType=").append(URLEncoder.encode(mRecordRule.searchType,"UTF-8"))
                                .append("&RecPriority=").append(mRecordRule.recPriority)
                                .append("&PreferredInput=").append(mRecordRule.preferredInput)
                                .append("&StartOffset=").append(mRecordRule.startOffset)
                                .append("&EndOffset=").append(mRecordRule.endOffset)
                                .append("&DupMethod=").append(URLEncoder.encode(nvl(mRecordRule.dupMethod),"UTF-8"))
                                .append("&DupIn=").append(URLEncoder.encode(nvl(mRecordRule.dupIn),"UTF-8"))
                                .append("&NewEpisOnly=").append(mRecordRule.newEpisOnly)
                                .append("&Filter=").append(mRecordRule.filter)
                                .append("&RecProfile=").append(URLEncoder.encode(mRecordRule.recProfile,"UTF-8"))
                                .append("&RecGroup=").append(URLEncoder.encode(mRecordRule.recGroup,"UTF-8"))
                                .append("&StorageGroup=").append(URLEncoder.encode(mRecordRule.storageGroup,"UTF-8"))
                                .append("&PlayGroup=").append(URLEncoder.encode(mRecordRule.playGroup,"UTF-8"))
                                .append("&AutoExpire=").append(mRecordRule.autoExpire)
                                .append("&MaxEpisodes=").append(mRecordRule.maxEpisodes)
                                .append("&MaxNewest=").append(mRecordRule.maxNewest)
                                .append("&AutoCommflag=").append(mRecordRule.autoCommflag)
                                .append("&AutoTranscode=").append(mRecordRule.autoTranscode)
                                .append("&AutoMetaLookup=").append(mRecordRule.autoMetaLookup)
                                .append("&AutoUserJob1=").append(mRecordRule.autoUserJob1)
                                .append("&AutoUserJob2=").append(mRecordRule.autoUserJob2)
                                .append("&AutoUserJob3=").append(mRecordRule.autoUserJob3)
                                .append("&AutoUserJob4=").append(mRecordRule.autoUserJob4)
                                .append("&Transcoder=").append(mRecordRule.transcoder);
                        if (mRecordRule.lastRecorded != null)
                            urlBuilder.append("&LastRecorded=").append(URLEncoder.encode(sdfUTC.format(mRecordRule.lastRecorded), "UTF-8"));
                        xmlResult = XmlNode.fetch(urlBuilder.toString(), "POST");
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Updating Record Schedule.", e);
                    }
                    break;

                case Video.ACTION_SEARCHGUIDE:
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Guide/GetProgramList?Sort=starttime&count=500&TitleFilter="
                                        + URLEncoder.encode(mStringParameter, "UTF-8"));
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting Guide.", e);
                    }
                    break;

                case Video.ACTION_DELETERECRULE:
                    try {
                        if (mRecordRule.recordId > 0) {
                            String url = XmlNode.mythApiUrl(null,
                                    "/Dvr/RemoveRecordSchedule?RecordId=" + mRecordRule.recordId);
                            xmlResult = XmlNode.fetch(url, "POST");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception removing Record Schedule.", e);
                    }
                    break;

                case Video.ACTION_DUMMY:
                    break;

                case Video.ACTION_PAUSE:
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;

                case Video.ACTION_ALLOW_RERECORD:
                    if (!isRecording)
                        break;
                    try {
                        urlString = XmlNode.mythApiUrl(mVideo.hostname,
                                "/Dvr/AllowReRecord?RecordedId="
                                        + mVideo.recordedid);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;

                case Video.ACTION_SEEK_BYTES:
                    paramValue = "BYTES";
                case Video.ACTION_SEEK_DURATION:
                    if (paramValue == null)
                        paramValue = "DURATION";
                    if (!isRecording)
                        break;
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/GetRecordedSeek?RecordedId="
                                        + mVideo.recordedid
                                        + "&OffsetType=" + paramValue);
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;

                case Video.ACTION_COMMBREAK_LOAD:
                    if (commBreakTable.entries.length > 0)
                        break;
                    if (isRecording)
                        urlMethod = "/Dvr/GetRecordedCommBreak?RecordedId=";
                    else
                        urlMethod = "/Video/GetVideoCommBreak?Id=";
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + mVideo.recordedid
                                        + "&OffsetType=Duration");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        e.printStackTrace();
                        break;
                    }
                    if (commBreakTable != null)
                        commBreakTable.load(xmlResult);
                    if (commBreakTable.entries.length > 0) {
                        commBreakTable.offSetType = CommBreakTable.OFFSET_DURATION;
                        break;
                    }
                    // If Duration failed, try Frame. This could happen if there is no
                    // seek table
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + mVideo.recordedid);
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        break;
                    }
                    if (commBreakTable != null)
                        commBreakTable.load(xmlResult);
                    if (commBreakTable.entries.length > 0)
                        commBreakTable.offSetType = CommBreakTable.OFFSET_FRAME;
                    break;

                case Video.ACTION_CUTLIST_LOAD:
                    if (commBreakTable.entries.length > 0)
                        break;

                    if (isRecording)
                        urlMethod = "/Dvr/GetRecordedCutList?RecordedId=";
                    else
                        urlMethod = "/Video/GetVideoCutList?Id=";
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + mVideo.recordedid
                                        + "&OffsetType=Duration");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        break;
                    }
                    if (commBreakTable != null)
                        commBreakTable.load(xmlResult);
                    if (commBreakTable.entries.length > 0) {
                        commBreakTable.offSetType = CommBreakTable.OFFSET_DURATION;
                        break;
                    }
                    // If Duration failed, try Frame. This could happen if there is no
                    // seek table
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + mVideo.recordedid);
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        break;
                    }
                    if (commBreakTable.entries.length > 0)
                        commBreakTable.offSetType = CommBreakTable.OFFSET_FRAME;
                    break;

                default:
                    String method = null;
                    switch (task) {
                        case Video.ACTION_GETPLAYGROUPLIST:
                            method = "GetPlayGroupList";
                            break;
                        case Video.ACTION_GETRECGROUPLIST:
                            method = "GetRecGroupList";
                            break;
                        case Video.ACTION_GETRECSTORAGEGROUPLIST:
                            method = "GetRecStorageGroupList";
                            break;
                        case Video.ACTION_GETINPUTLIST:
                            method = "GetInputList";
                            break;
                        case Video.ACTION_GETRECORDSCHEDULELIST:
                            method = "GetRecordScheduleList";
                            break;
                        case Video.ACTION_GETRECRULEFILTERLIST:
                            method = "GetRecRuleFilterList";
                            break;
                        case Video.ACTION_GETUPCOMINGLIST:
                            method = "GetUpcomingList";
                            break;
                    }
                    if (method == null)
                        break;
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                        "/Dvr/" + method);
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception In " + method, e);
                    }
            }
            mXmlResults.add(xmlResult);
        }
        return;
    }

    // method: GetSavedBookmark or GetLastPlayPos
    // Return array has bookmark and posbookmark or lastplaypos values
    private long[] fetchBookmark(String method) throws XmlPullParserException, IOException {
        boolean isRecording = (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
        String urlString;
        XmlNode bkmrkData = null;
        long[] retValue = {0, -1};
        if (isRecording) {
            urlString = XmlNode.mythApiUrl(mVideo.hostname,
                    "/Dvr/" + method + "?OffsetType=duration&RecordedId="
                            + mVideo.recordedid);
            bkmrkData = XmlNode.safeFetch(urlString, null);
            try {
                retValue[0] = Long.parseLong(bkmrkData.getString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                retValue[0] = -1;
            }
            // sanity check bookmark - between 0 and 24 hrs.
            // note -1 means a bookmark but no seek table
            // older version of service returns garbage value when there is
            // no seek table.
            if (retValue[0] > 24 * 60 * 60 * 1000 || retValue[0] < 0)
                retValue[0] = -1;
        }
        if (retValue[0] == -1 || !isRecording) {
            // look for a position bookmark (for recording with no seek table)
            if (isRecording)
                urlString = XmlNode.mythApiUrl(mVideo.hostname,
                        "/Dvr/" + method + "?OffsetType=frame&RecordedId="
                                + mVideo.recordedid);
            else
                urlString = XmlNode.mythApiUrl(mVideo.hostname,
                        "/Video/" + method + "?Id="
                                + mVideo.recordedid);
            bkmrkData = XmlNode.safeFetch(urlString, null);
            try {
                retValue[1] = Long.parseLong(bkmrkData.getString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                retValue[1] = -1;
            }
        }
        return retValue;
    }

    // method: SetSavedBookmark or SetLastPlayPos
    // Return object contains true if successful
    private XmlNode updateBookmark(String method, long mark, long pos)
            throws XmlPullParserException, IOException {
        boolean isRecording = (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
        String urlString;
        XmlNode xmlResult = null;
        boolean found = false;
        // store a mythtv bookmark
        if (isRecording) {
            urlString = XmlNode.mythApiUrl(mVideo.hostname,
                    "/Dvr/"+method+"?OffsetType=duration&RecordedId="
                            + mVideo.recordedid + "&Offset=" + mark);
            xmlResult = XmlNode.safeFetch(urlString, "POST");
            String result = xmlResult.getString();
            if ("true".equals(result))
                found = true;
        }
        if (!found && pos >= 0) {
            // store a mythtv position bookmark (in case there is no seek table)
            if (isRecording)
                urlString = XmlNode.mythApiUrl(mVideo.hostname,
                        "/Dvr/"+method+"?RecordedId="
                                + mVideo.recordedid + "&Offset=" + pos);
            else
                urlString = XmlNode.mythApiUrl(mVideo.hostname,
                        "/Video/"+method+"?Id="
                                + mVideo.recordedid + "&Offset=" + pos);
            xmlResult = XmlNode.safeFetch(urlString, "POST");
        }
        return xmlResult;
    }

    public static String nvl(String value) {
        if (value == null)
            return "";
        return value;
    }
}
