package org.mythtv.leanfront.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.Settings;

import java.util.ArrayList;
import java.util.List;

public class SettingsEntryFragment extends GuidedStepSupportFragment {

    private static final int ID_BACKEND_IP = 1;
    private static final int ID_HTTP_PORT = 2;
    private static final int ID_BOOKMARK_STRATEGY = 3;
    private static final int ID_BOOKMARK_LOCAL = 5;
    private static final int ID_BOOKMARK_FPS = 6;
    private static final int ID_PLAYBACK = 7;
    private static final int ID_SKIP_FWD = 8;
    private static final int ID_SKIP_BACK = 9;
    private static final int ID_JUMP = 10;
    private static final int ID_SORT = 11;
    private static final int ID_SORT_ORIG_AIRDATE = 12;
    private static final int ID_DESCENDING = 13;
    private static final int ID_BACKEND_MAC = 14;
    private static final int ID_BACKEND = 15;

    private SharedPreferences.Editor mEditor;

    private GuidedAction mBackendAction;
    private GuidedAction mBookmarkAction;
    private GuidedAction mSortAction;

    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Activity activity = getActivity();
        String title = getString(R.string.personal_settings);
        String breadcrumb = "";
        String description = getString(R.string.pref_title_settings);
        Drawable icon = activity.getDrawable(R.drawable.ic_settings);
        return new GuidanceStylist.Guidance(title, description, breadcrumb, icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        mEditor = Settings.getEditor();

        List<GuidedAction> subActions = new ArrayList<>();
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_BACKEND_IP)
                .title(R.string.pref_title_backend_ip)
                .description(Settings.getString("pref_backend"))
                .descriptionEditable(true)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_HTTP_PORT)
                .title(R.string.pref_title_http_port)
                .description(Settings.getString("pref_http_port"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_BACKEND_MAC)
                .title(R.string.pref_title_backend_mac)
                .description(Settings.getString("pref_backend_mac"))
                .descriptionEditable(true)
                .build());
        actions.add(mBackendAction = new GuidedAction.Builder(getActivity())
                .id(ID_BACKEND)
                .title(R.string.pref_title_backend)
                .description(backendDesc())
                .subActions(subActions)
                .build());

        subActions = new ArrayList<>();
        String bookmark = Settings.getString("pref_bookmark");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_BOOKMARK_LOCAL)
                .title(R.string.pref_bookmark_local)
                .checked("local".equals(bookmark))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_BOOKMARK_FPS)
                .title(R.string.pref_title_fps)
                .description(Settings.getString("pref_fps"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        actions.add(mBookmarkAction = new GuidedAction.Builder(getActivity())
                .id(ID_BOOKMARK_STRATEGY)
                .title(R.string.pref_title_bookmark_strategy)
                .description(bookmarkDesc())
                .subActions(subActions)
                .build());

        subActions = new ArrayList<GuidedAction>();
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SKIP_FWD)
                .title(R.string.pref_title_skip_fwd)
                .description(Settings.getString("pref_skip_fwd"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SKIP_BACK)
                .title(R.string.pref_title_skip_back)
                .description(Settings.getString("pref_skip_back"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_JUMP)
                .title(R.string.pref_title_jump)
                .description(Settings.getString("pref_jump"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .id(ID_PLAYBACK)
                .title(R.string.pref_title_playback)
                .subActions(subActions)
                .build());

        subActions = new ArrayList<GuidedAction>();
        String seq = Settings.getString("pref_seq");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SORT_ORIG_AIRDATE)
                .title(R.string.pref_seq_orig_airdate)
                .checked("airdate".equals(seq))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        String ascdesc = Settings.getString("pref_seq_ascdesc");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_DESCENDING)
                .title(R.string.pref_seq_descending)
                .checked("desc".equals(ascdesc))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        actions.add(mSortAction = new GuidedAction.Builder(getActivity())
                .id(ID_SORT)
                .title(R.string.pref_sort_order)
                .description(sortDesc())
                .subActions(subActions)
                .build());
    }

    private String backendDesc() {
        return Settings.getString("pref_backend");
    }

    private int bookmarkDesc() {
        String bookmark = Settings.getString("pref_bookmark");
        if ("mythtv".equals(bookmark))
            return R.string.pref_bookmark_mythtv;
        if ("local".equals(bookmark))
            return R.string.pref_bookmark_local;
        return R.string.dummy_empty_string;
    }

    private String sortDesc() {
        StringBuilder builder = new StringBuilder();
        String seq = Settings.getString("pref_seq");
        String ascdesc = Settings.getString("pref_seq_ascdesc");
        if ("airdate".equals(seq))
            builder.append(getActivity().getString(R.string.pref_seq_orig_airdate));
        else
            builder.append(getActivity().getString(R.string.pref_seq_rec_time));
        builder.append(" ");
        if ("desc".equals(ascdesc))
            builder.append(getActivity().getString(R.string.pref_seq_descending));
        else
            builder.append(getActivity().getString(R.string.pref_seq_ascending));
        return builder.toString();
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        super.onGuidedActionClicked(action);
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        switch((int) action.getId()) {
            case ID_BACKEND_IP:
                String newVal = action.getDescription().toString();
                mEditor.putString("pref_backend",newVal);
                mBackendAction.setDescription(newVal);
                notifyActionChanged(findActionPositionById(ID_BACKEND));
                break;
            case ID_HTTP_PORT:
                mEditor.putString("pref_http_port",action.getDescription().toString());
                break;
            case ID_BACKEND_MAC:
                mEditor.putString("pref_backend_mac",action.getDescription().toString());
                break;
            case ID_BOOKMARK_FPS:
                mEditor.putString("pref_fps",action.getDescription().toString());
                break;
            case ID_SKIP_FWD:
                mEditor.putString("pref_skip_fwd",action.getDescription().toString());
                break;
            case ID_SKIP_BACK:
                mEditor.putString("pref_skip_back",action.getDescription().toString());
                break;
            case ID_JUMP:
                mEditor.putString("pref_jump",action.getDescription().toString());
                break;
            default:
                return GuidedAction.ACTION_ID_CURRENT;
        }
        mEditor.apply();
        return GuidedAction.ACTION_ID_CURRENT;
    }

    @Override
    public void onGuidedActionEditCanceled(GuidedAction action) {
        switch((int) action.getId()) {
            case ID_BACKEND_IP:
                action.setDescription(Settings.getString("pref_backend"));
                break;
            case ID_BACKEND_MAC:
                action.setDescription(Settings.getString("pref_backend_mac"));
                break;
            case ID_HTTP_PORT:
                action.setDescription(Settings.getString("pref_http_port"));
                break;
            case ID_BOOKMARK_FPS:
                action.setDescription(Settings.getString("pref_fps"));
                break;
            case ID_SKIP_FWD:
                action.setDescription(Settings.getString("pref_skip_fwd"));
                break;
            case ID_SKIP_BACK:
                action.setDescription(Settings.getString("pref_skip_back"));
                break;
            case ID_JUMP:
                action.setDescription(Settings.getString("pref_jump"));
                break;
        }
    }

    @Override
    public boolean onSubGuidedActionClicked(GuidedAction action) {

        switch((int) action.getId()) {
//            case ID_BOOKMARK_MYTHTV:
//                mEditor.putString("pref_bookmark", "mythtv");
//                break;
            case ID_BOOKMARK_LOCAL:
                if (action.isChecked())
                    mEditor.putString("pref_bookmark", "local");
                else
                    mEditor.putString("pref_bookmark", "mythtv");
                break;
            case ID_SORT_ORIG_AIRDATE:
                if (action.isChecked())
                    mEditor.putString("pref_seq", "airdate");
                else
                    mEditor.putString("pref_seq", "rectime");
                break;
            case ID_DESCENDING:
                if (action.isChecked())
                    mEditor.putString("pref_seq_ascdesc", "desc");
                else
                    mEditor.putString("pref_seq_ascdesc", "asc");
                break;
            default:
                return false;
        }
        mEditor.apply();
        mBookmarkAction.setDescription(getActivity().getString(bookmarkDesc()));
        notifyActionChanged(findActionPositionById(ID_BOOKMARK_STRATEGY));
        mSortAction.setDescription(sortDesc());
        notifyActionChanged(findActionPositionById(ID_SORT));
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity.getContext().getMainFragment().startFetch();
    }
}