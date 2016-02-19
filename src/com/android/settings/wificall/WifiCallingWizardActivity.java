package com.android.settings.wificall;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.settings.R;

public class WifiCallingWizardActivity extends Activity{

    private static final String TAG = "WifiCallingWizardActivity";
    public static final String PRIVTE_PREFERENCE = "wifi_walling_wizard_preference";
    public static final String WIZARD_SHOW_PREFERENCE = "wifi_walling_wizard_preference";
    private static final int FIRST_WIZARD = 1;
    private static final int SECOUND_WIZARD = 2;
    private static final int THIRD_WIZARD = 3;
    private static boolean isWizard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null) {
            isWizard = intent.getBooleanExtra("triggeredFromHelp", true);
        }
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (isWizard) {
            getActionBar().setTitle(getString(R.string.wifi_calling_tutorial_title));
            WizardFragment fragment = new WizardFragment();
            fragmentTransaction.add(com.android.internal.R.id.content, fragment);
        } else {
            getActionBar().setTitle(getString(R.string.wifi_calling_questions_title));
            QuestionFragment fragment = new QuestionFragment();
            fragmentTransaction.add(com.android.internal.R.id.content, fragment);
        }
        fragmentTransaction.commit();
    }

    public class WizardFragment extends Fragment implements OnClickListener{
        private int mWizardIndex = FIRST_WIZARD;
        private Button mLeftButton;
        private Button mRightButton;
        private TextView mContentText;
        private ImageView mImage;
        private TextView mStepText;

        public WizardFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            // TODO Auto-generated method stub
            super.onCreate(savedInstanceState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            super.onCreateView(inflater, container, savedInstanceState);
            View view = inflater.inflate(R.layout.wifi_call_wizard, container, false);
            if(view != null){
                Log.i(TAG, "init view and listener");
                mLeftButton = (Button)view.findViewById(R.id.leftbutton);
                mRightButton = (Button)view.findViewById(R.id.rightbutton);
                mContentText = (TextView)view.findViewById(R.id.content_view);
                mImage = (ImageView)view.findViewById(R.id.content_view_icon);
                mStepText = (TextView)view.findViewById(R.id.content_view_step_4);
                mImage.setVisibility(View.INVISIBLE);
                mStepText.setVisibility(View.INVISIBLE);
                mLeftButton.setOnClickListener(this);
                mRightButton.setOnClickListener(this);
            }
            return view;
        }

        @Override
        public void onClick(View v) {
            Log.i(TAG, "current wizard index : " + mWizardIndex);
            switch (mWizardIndex) {
            case FIRST_WIZARD:
                if(v.getId() == R.id.leftbutton){
                    finish();
                    break;
                }
            case SECOUND_WIZARD:
                changeWizard(++mWizardIndex);
                break;
            case THIRD_WIZARD:
                SharedPreferences sprefence = getSharedPreferences(PRIVTE_PREFERENCE, MODE_PRIVATE);
                Editor editor = sprefence.edit();
                editor.putBoolean(WIZARD_SHOW_PREFERENCE, false);
                editor.commit();
                finish();
                break;
            default:
                Log.i(TAG, "unknow current wizard index");
                break;
            }
        }

        private void changeWizard(int nextWizard){
            switch (nextWizard) {
            case SECOUND_WIZARD:
                mLeftButton.setVisibility(View.GONE);
                mRightButton.setText(R.string.next_label);
                mContentText.setText(R.string.wifi_calling_wizard_content_step_2);
                break;
            case THIRD_WIZARD:
                mImage.setVisibility(View.VISIBLE);
                mStepText.setVisibility(View.VISIBLE);
                mLeftButton.setVisibility(View.GONE);
                mRightButton.setText(R.string.wifi_display_options_done);
                mContentText.setText(R.string.wifi_calling_wizard_content_step_3);
                break;

            default:
                break;
            }
        }
    }

    public class QuestionFragment extends Fragment{

        @Override
        public void onCreate(Bundle savedInstanceState) {
            // TODO Auto-generated method stub
            super.onCreate(savedInstanceState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            super.onCreateView(inflater, container, savedInstanceState);
            View view = inflater.inflate(R.layout.wifi_call_top_questions, container, false);
            if(view != null){
                Log.i(TAG, "init wifi call top questions");
            }
            return view;
        }
    }
}
