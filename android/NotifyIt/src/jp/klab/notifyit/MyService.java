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

import java.io.IOException;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxDatastore;
import com.dropbox.sync.android.DbxDatastoreManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxException.Unauthorized;

@SuppressLint("Wakelock")
public class MyService extends Service {
    private static final String TAG = Constant.APP_NAME;
    private static final String SQ = "'";
    private static final String DQ = "\"";

    private String mPrevMessage = "";
    private Date mPrevUpdatedTime = null;
    private boolean mWakeDevice;
    @SuppressWarnings("deprecation")
    private KeyguardManager.KeyguardLock mKlock;

    private DbxAccountManager mDbxAcctMgr;
    private DbxDatastore mDatastore;
    private TaskTable mTaskTable;
    private DbxDatastoreManager mDatastoreManager;

    // データ変更通知リスナ
    private DbxDatastore.SyncStatusListener mDatastoreListener = new DbxDatastore.SyncStatusListener() {
        @Override
        public void onDatastoreStatusChange(DbxDatastore ds) {
            // ストア側データの更新内容からメッセージを取得して処理
            if (!ds.getSyncStatus().hasIncoming) {
                return;
            } else {
                try {
                    mDatastore.sync();
                } catch (DbxException e) {
                    handleException(e);
                }
            }
            doIt();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // サービス開始
    @SuppressWarnings("deprecation")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStart(intent, startId);
        Toast.makeText(this, R.string.MsgStartService, Toast.LENGTH_SHORT)
                .show();
        mWakeDevice = intent.getBooleanExtra(Constant.EXTRA_NAME_WAKEDEVICE,
                false);
        mKlock = null;
        if (mWakeDevice) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            mKlock = km.newKeyguardLock(Constant.APP_NAME);
            mKlock.disableKeyguard();
        }
        // 初期化
        initDropbox();
        EnterForeground(R.string.app_name);
        return super.onStartCommand(intent, flags, startId);
    }

    // サービス終了
    @SuppressWarnings("deprecation")
    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, R.string.MsgStopService, Toast.LENGTH_SHORT)
                .show();
        if (mKlock != null) {
            mKlock.reenableKeyguard();
        }
        // Datastore 変更通知用リスナを解除しデータストアをクローズ
        mDatastore.removeSyncStatusListener(mDatastoreListener);
        mDatastore.close();
        mDatastore = null;
        stopForeground(true);
    }

    // フォアグラウンドサービスに設定
    @SuppressWarnings("deprecation")
    private void EnterForeground(int id) {
        CharSequence text;
        if (mWakeDevice) {
            text = getText(R.string.MsgWaitingB); // "待機中：受信時にスリープ解除"
        } else {
            text = getText(R.string.MsgWaitingA); // "通知を待っています"
        }
        // "バックグラウンドで待機中"
        Notification notification = new Notification(R.drawable.icon_mini,
                getString(R.string.MsgWaitingNow), System.currentTimeMillis());

        Intent it = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, it,
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notification.setLatestEventInfo(this, getText(R.string.app_name), text,
                contentIntent);
        startForeground(id, notification);
    }

    // Dropbox まわりの初期化
    private boolean initDropbox() {
        // Dropbox アカウントマネージャのインスタンスを得る
        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(),
                Constant.APP_KEY, Constant.APP_SECRET);
        try {
            // リンク中のアカウント分のデータストアマネージャを得る
            mDatastoreManager = DbxDatastoreManager.forAccount(mDbxAcctMgr
                    .getLinkedAccount());
        } catch (Unauthorized e) { // 未認証
            handleException(e);
            return false;
        }
        try {
            // デフォルトデータストアを開く
            mDatastore = mDatastoreManager.openDefaultDatastore();
            // notification テーブル参照用
            mTaskTable = new TaskTable(mDatastore);
            // データ変更通知リスナを登録
            mDatastore.addSyncStatusListener(mDatastoreListener);
            mDatastore.sync();
        } catch (DbxException e) {
            handleException(e);
            return false;
        }
        return true;
    }

    // メッセージを処理
    @SuppressWarnings("deprecation")
    private void doIt() {
        PowerManager pm;
        PowerManager.WakeLock wlock;
        if (mWakeDevice) {
            pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wlock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP, Constant.APP_NAME);
            wlock.acquire(3000);
        }
        // notification テーブル上の選択状態のデータを得る
        List<TaskTable.Task> tasks;
        try {
            tasks = mTaskTable.getTasksSorted();
        } catch (DbxException e) {
            handleException(e);
            return;
        }
        String str = "";
        Date updated = null;
        for (final TaskTable.Task task : tasks) {
            if (task.isSelected()) {
                str = task.getMessage();
                updated = task.getUpdated();
                _Log.d(TAG, "message=" + task.getMessage());
                break;
            }
        }
        if (str.length() <= 0) {
            return;
        }
        if (str.equals(mPrevMessage)
                && updated.compareTo(mPrevUpdatedTime) == 0) {
            return;
        }
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
        mPrevMessage = str;
        mPrevUpdatedTime = updated;

        // URL ならブラウザで開く
        if (str.startsWith("http://") || str.startsWith("https://")) {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(str));
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        }
        // それ以外なら am での処理を試行
        else {
            String cmd = "am " + str;
            try {
                // パラメータを加工・整形して実行
                String p[] = CreateParameterArray(cmd);
                Runtime.getRuntime().exec(p);
            } catch (IOException e) {
                _Log.e(TAG, "exec err=" + e.toString());
            }
        }
    }

    private String[] CreateParameterArray(String param) {
        String p = preEdit(param);
        p = p.replaceAll("[\\r\\n]", " "); // CR,LF to space
        String cmdline[] = null;
        String wk[] = p.split(" +"); // split by space(s)

        boolean inQuote = false;
        boolean isSQ = false;
        int sq1, dq1, start = 0;
        int num = wk.length;
        for (int i = 0; i < wk.length; i++) {
            sq1 = wk[i].indexOf(SQ);
            dq1 = wk[i].indexOf(DQ);
            if (inQuote) { // inside quotation marks
                wk[start] += " " + wk[i];
                wk[i] = "";
                num--;
                if ((isSQ && sq1 != -1) || (!isSQ && dq1 != -1)) {
                    wk[start] = wk[start].replaceAll(isSQ ? SQ : DQ, "");
                    inQuote = false;
                }
            } else { // outside quotation marks
                if (sq1 == -1 && dq1 == -1) {
                    continue;
                }
                if (sq1 != -1) {
                    if (dq1 == -1 || sq1 < dq1) {
                        isSQ = true;
                    } else {
                        isSQ = false;
                    }
                } else {
                    isSQ = false;
                }
                if ((isSQ && wk[i].indexOf(SQ, sq1 + 1) != -1)
                        || (!isSQ && wk[i].indexOf(DQ, dq1 + 1) != -1)) {
                    wk[i] = wk[i].replaceAll(isSQ ? SQ : DQ, "");
                    continue;
                }
                inQuote = true;
                start = i;
            }
        }
        cmdline = new String[num];
        int idx = 0;
        for (int i = 0; i < wk.length; i++) {
            if (wk[i].length() > 0) {
                cmdline[idx] = wk[i];
                _Log.d(TAG, i + "[" + cmdline[idx] + "]");
                idx++;
            }
        }
        return cmdline;
    }

    private String preEdit(String str) {
        String retstr = str;
        int check = -1, pos;
        int METACHARS = 5;
        String meta[] = { ";", "|", "&", "<", ">" };
        for (int k = 0; k < METACHARS; k++) {
            pos = str.indexOf(meta[k]);
            if (pos != -1 && !inQuote(str, pos)) {
                if (pos < check || check == -1) {
                    check = pos;
                }
            }
        }
        if (check != -1) {
            retstr = retstr.substring(0, check);
        }
        return retstr;
    }

    private boolean inQuote(String str, int pos) {
        String p1 = str.substring(0, pos);
        int idx, ofs;
        int sqCount = 0, dqCount = 0;
        for (idx = 0; idx < p1.length();) {
            if ((ofs = p1.indexOf(SQ, idx)) == -1) {
                break;
            }
            sqCount++;
            idx = ofs + 1;
        }
        for (idx = 0; idx < p1.length();) {
            if ((ofs = p1.indexOf(DQ, idx)) == -1) {
                break;
            }
            dqCount++;
            idx = ofs + 1;
        }
        if (sqCount % 2 != 0 || dqCount % 2 != 0) {
            return true;
        }
        return false;
    }

    private void handleException(Exception e) {
        _Log.e(TAG, "exception:" + e.toString());
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}
