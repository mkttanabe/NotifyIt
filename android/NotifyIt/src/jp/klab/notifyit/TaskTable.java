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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.dropbox.sync.android.DbxDatastore;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxRecord;
import com.dropbox.sync.android.DbxTable;

public class TaskTable {
    private DbxDatastore mDatastore;
    private DbxTable mTable;

    public class Task {
        private DbxRecord mRecord;

        public Task(DbxRecord record) {
            mRecord = record;
        }

        public String getId() {
            return mRecord.getId();
        }

        public String getMessage() {
            return mRecord.getString("message");
        }

        public Date getCreated() {
            return mRecord.getDate("created");
        }

        public Date getUpdated() {
            return mRecord.getDate("updated");
        }

        public boolean isSelected() {
            return mRecord.getBoolean("selected");
        }
    }

    public TaskTable(DbxDatastore datastore) {
        mDatastore = datastore;
        mTable = mDatastore.getTable("notification");
    }

    public List<Task> getTasksSorted() throws DbxException {
        List<Task> resultList = new ArrayList<Task>();
        for (DbxRecord result : mTable.query()) {
            resultList.add(new Task(result));
        }
        Collections.sort(resultList, new Comparator<Task>() {
            @Override
            public int compare(Task o1, Task o2) {
                return o1.getCreated().compareTo(o2.getCreated());
            }
        });
        return resultList;
    }
}
