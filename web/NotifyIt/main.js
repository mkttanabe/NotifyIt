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


var DROPBOX_APP_KEY = '*** APP_KEY ***';

var client = new Dropbox.Client({key: DROPBOX_APP_KEY});
var taskTable;
var prevUrl;
var prevDate;

$(function () {

    /**** UI 各要素へイベントハンドラをバインド ***/

    // 「Dropbox とのリンクを解除」ボタン
    $('#logoutButton').click(function (e) {
        e.preventDefault();
        // 完了時に reload
        client.signOut({mustInvalidate:false}, location.reload());
    });

    // 「このアプリのデータストアを削除」ボタン
    $('#deleteDatastoreButton').click(function (e) {
        e.preventDefault();
        client.getDatastoreManager().deleteDatastore('default', 
            alert('本アプリのデータストアを削除しました'));
    });

    // 「選択状態を解除」ボタン
    $('#resetButton').click(function (e) {
        e.preventDefault();
        clearSelected();
    });

    // 「エントリを追加」フォーム
    $('#addForm').submit(function (e) {
        e.preventDefault();
        if ($('#newData').val().length > 0) {
            insertTask($('#newData').val());
            $('#newData').val('');
            return true;
        }
        return false;
    });

    // 「Dropbox とリンクする」ボタン
    $('#loginButton').click(function (e) {
        e.preventDefault();
        // OAuth 認証 (interactive) へ
        client.authenticate();
    });

    /**** OAuth 認証 (非interactive) ***/

    client.authenticate({interactive:false}, function (error) {
        // 認証通過ずみなら保持する認証情報が使用される
        // 未認証ならそのまま抜ける (エラーにはならない)
        if (error) {
            alert('認証エラー: ' + error);
        }
    });

    /**** Dropbox とのリンクが有効か ***/

    if (client.isAuthenticated()) {
        // リンクが有効ならメイン UI を表示
        $('#loginButton').hide();
        $('#main').show();

        // 本アプリのデフォルトデータストアをオープン - 引数は完了時ハンドラ
        client.getDatastoreManager().openDefaultDatastore(function (error, datastore) {
            if (error) {
                alert('データストアのオープンに失敗しました: ' + error);
                return;
            }

            // window の onBeforeUnload ハンドラを設定
            $(window).bind('beforeunload', function () {
                // データストアへ未反映のデータがあればページ移動を抑制
                if (datastore.getSyncStatus().uploading) {
                    return "サーバへ未反映のデータがあります";
                }
            });

            // データストア上の notification テーブルを使用する
            taskTable = datastore.getTable('notification');

            // URL 一覧を表示
            updateList();

            // テーブル上のレコード変更発生時のリスナに updateList() を設定
            datastore.recordsChanged.addListener(updateList);
        });
    }

    /**** 以下 関数群 ***/

    // URL 一覧表示を更新
    function updateList() {
        // 表示中の一覧をクリア
        $('#URLs').empty();

        // テーブルから全レコードを抽出し生成日時でソート
        var records = taskTable.query();
        records.sort(function (taskA, taskB) {
            if (taskA.get('created') < taskB.get('created')) return -1;
            if (taskA.get('created') > taskB.get('created')) return 1;
            return 0;
        });

        // URL 各行のコンテンツを再出力
        var url = "";
        var updated;
        for (var i = 0; i < records.length; i++) {
            var record = records[i];
            var selected = record.get('selected');
            var message = record.get('message');
            updated = record.get('updated');
            $('#URLs').append(renderTask(record.getId(), selected, message));
            if (selected) {
                url = message;
            }
        }

        // 各行の 選択・削除ボタンにハンドラを
        bindHandlers();
        $('#newData').focus();

        // 「選択されたページをこのブラウザで開く」がチェックされていれば
        // 現在選択状態のレコードの URL を開く
        if ($('#showPages:checked').val()) {
            if (url.indexOf('http://') == 0 || url.indexOf('https://') == 0) {
                // 前回開いた URL と同一かつ更新日時が同一なら開かない
                if (!(url == prevUrl && updated.getTime() == prevDate.getTime())) {
                    window.open(url, "aaa");
                    prevUrl = url;
                    prevDate = updated;
                }
            }
        }
    }

    // 一件分のレコードのコンテンツを生成
    function renderTask(id, selected, text) {
        // 出力例
        // <li id="_182m32i5b88_js_g_S5G">
        // <button class="push">選択（PUSH）</button>
        // <input readonly="readonly" value="http://hoge/" size="50" type="text">
        // <button class="delete">×削除</button>

        return $('<li>').attr('id', id)
            .append($('<button>').addClass('push').text('選択（PUSH）'))
            .append($('<input>').attr('type','text').attr('size', '70')
                    .attr('value',text).attr('readonly', true))
            .append($('<button>').addClass('delete').html('削除'));
    }

    // 選択・削除ボタンへイベントハンドラをバインド
    function bindHandlers() {
        $('button.push').click(function (e) {
            e.preventDefault();
            var li = $(this).parents('li');
            var id = li.attr('id');
            setSelected(id);
        });
        $('button.delete').click(function (e) {
            e.preventDefault();
            var id = $(this).parents('li').attr('id');
            deleteRecord(id);
        });
    }

    // 一件のエントリをテーブルへ insert
    function insertTask(text) {
        clearSelected();
        taskTable.insert({
            message: text,
            created: new Date(),
            updated: new Date(),
            selected: true
        });
    }

    // 選択状態のレコードがあれば選択解除
    function clearSelected() {
        var records = taskTable.query();
        for (var i = 0; i < records.length; i++) {
            var record = records[i];
            if (record.get('selected')) {
                record.set('selected', false);
            }
        }
    }

    // 所定 id のレコードを選択状態に
    function setSelected(id) {
        clearSelected();
        taskTable.get(id).set('selected', true);
        taskTable.get(id).set('updated', new Date());
    }

    // 所定 id のレコードを削除
    function deleteRecord(id) {
        taskTable.get(id).deleteRecord();
    }
});
