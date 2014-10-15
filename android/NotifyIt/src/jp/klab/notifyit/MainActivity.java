/*
 * Copyright (C) 2014 KLab Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.klab.notifyit;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;

public class MainActivity extends Activity implements OnClickListener {
    private static final String TAG = Constant.APP_NAME;
    private static final int REQUEST_LINK_TO_DBX = 0;

    private DbxAccountManager mDbxAcctMgr;
    private Button mLinkButton;
    private Button mServiceButton;
    private CheckBox mWakeBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        _Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Dropboxアカウントマネージャのインスタンスを取得
        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(),
                Constant.APP_KEY, Constant.APP_SECRET);
        mLinkButton = (Button) findViewById(R.id.link_button);
        mLinkButton.setOnClickListener(this);
        mServiceButton = (Button) findViewById(R.id.service_button);
        mServiceButton.setOnClickListener(this);
        mWakeBox = (CheckBox) findViewById(R.id.wake_box);
    }

    @Override
    public void onClick(View v) {
        if (v == (View) mLinkButton) {
            if (mDbxAcctMgr.hasLinkedAccount()) {
                if (myServiceIsRunning()) {
                    stopService(new Intent(this, MyService.class));
                }
                // Dropboxリンクを解除
                mDbxAcctMgr.getLinkedAccount().unlink();
                showControls();
            } else {
                // OAuth 認証へ
                mDbxAcctMgr.startLink(MainActivity.this, REQUEST_LINK_TO_DBX);
            }
        } else if (v == (View) mServiceButton) {
            Intent it = new Intent(this, MyService.class);
            it.putExtra(Constant.EXTRA_NAME_WAKEDEVICE, mWakeBox.isChecked());
            if (myServiceIsRunning()) {
                stopService(it);
                mServiceButton.setText(R.string.MsgStartWait); // "通知への待機を開始"
                mWakeBox.setEnabled(true);
            } else {
                startService(it);
                mServiceButton.setText(R.string.MsgStopWait);// "通知への待機を停止"
                mWakeBox.setEnabled(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        _Log.d(TAG, "onResume");
        // UI要素の表示 or 非表示を切り替え
        showControls();
    }

    @Override
    protected void onPause() {
        _Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 戻るボタン押下時
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (myServiceIsRunning()) {
                // "バックグラウンドで通知への待機を継続しています。本アプリの「通知の待機を停止」ボタンを押すと待機を終了します"
                showDialogMessage(this,
                        getString(R.string.MsgServiceIsRunning), true);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // OAuth 認証処理後
        if (requestCode == REQUEST_LINK_TO_DBX) {
            if (resultCode == RESULT_OK) { // 認証完了
                showControls();
            } else { // エラーまたはキャンセル
                // "Dropboxとのリンクに失敗しました"
                Toast.makeText(this, R.string.MsgFailedToLinkDropbox,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 0, Menu.NONE, R.string.WordAbout);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case 0: // about
            PackageManager pm = this.getPackageManager();
            String ver = "";
            try {
                PackageInfo packageInfo = pm.getPackageInfo(
                        this.getPackageName(), 0);
                ver = packageInfo.versionName;
            } catch (Exception e) {
                ver = "???";
            }
            showDialogMessage(this, getString(R.string.app_name) + " version "
                    + ver + "\n\n" + getString(R.string.Copyright), false);
            break;

        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean myServiceIsRunning() {
        return serviceIsRunning(MyService.class);
    }

    private boolean serviceIsRunning(Class<?> serviceClass) {
        String serviceName = serviceClass.getName();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : am
                .getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getClassName().equals(serviceName)) {
                return true;
            }
        }
        return false;
    }

    private void showControls() {
        if (myServiceIsRunning()) {
            mServiceButton.setText(R.string.MsgStopWait); // "通知の待機を停止"
            mWakeBox.setEnabled(false);
        } else {
            mServiceButton.setText(R.string.MsgStartWait); // "通知の待機を開始"
            mWakeBox.setEnabled(true);
        }
        if (mDbxAcctMgr.hasLinkedAccount()) {
            mLinkButton.setText(R.string.MsgUnlinkDropbox); // "Dropbox とのリンクを解除"
            mServiceButton.setVisibility(View.VISIBLE);
            mWakeBox.setVisibility(View.VISIBLE);
        } else {
            mLinkButton.setText(R.string.MsgLinkDropbox); // "Dropbox とリンクする"
            mServiceButton.setVisibility(View.GONE);
            mWakeBox.setVisibility(View.GONE);
        }
    }

    // show message
    private void showDialogMessage(Context ctx, String msg,
            final boolean bFinish) {
        new AlertDialog.Builder(ctx).setTitle(R.string.app_name)
                .setIcon(R.drawable.ic_launcher).setMessage(msg)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (bFinish) {
                            finish();
                        }
                    }
                }).show();
    }
}
