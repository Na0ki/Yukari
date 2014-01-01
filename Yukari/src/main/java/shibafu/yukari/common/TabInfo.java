package shibafu.yukari.common;

import android.content.ContentValues;
import android.database.Cursor;

import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBRecord;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/12/30.
 */
public class TabInfo implements DBRecord {
    //Tabs.Type
    public static final int TABTYPE_HOME = 0;
    public static final int TABTYPE_MENTION = 1;
    public static final int TABTYPE_DM = 2;
    public static final int TABTYPE_HISTORY = 3;
    public static final int TABTYPE_LIST = 4;
    public static final int TABTYPE_SEARCH = 5;
    public static final int TABTYPE_TRACK = 6;
    public static final int TABTYPE_FILTER = 7;

    private int id = -1;
    private int type;
    private int order;
    private AuthUserRecord bindAccount;
    private long bindListId = -1;
    private String searchKeyword;
    private String filterQuery;

    private AttachableList attachableList;

    public TabInfo(int type, int order, AuthUserRecord bindAccount) {
        this.type = type;
        this.order = order;
        this.bindAccount = bindAccount;
    }

    public TabInfo(int type, int order, AuthUserRecord bindAccount, long bindListId) {
        this.type = type;
        this.order = order;
        this.bindAccount = bindAccount;
        this.bindListId = bindListId;
    }

    public TabInfo(int type, int order, AuthUserRecord bindAccount, String word) {
        this.type = type;
        this.order = order;
        this.bindAccount = bindAccount;
        switch (type) {
            case TABTYPE_SEARCH:
            case TABTYPE_TRACK:
                this.searchKeyword = word;
                break;
            case TABTYPE_FILTER:
                this.filterQuery = word;
                break;
        }
    }

    public TabInfo(Cursor cursor) {
        this.id = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_TABS_ID));
        this.type = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_TABS_TYPE));
        this.order = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_TABS_TAB_ORDER));
        long accountId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_TABS_BIND_ACCOUNT_ID));
        if (accountId > -1) {
            bindAccount = new AuthUserRecord(cursor);
        }
        switch (this.type) {
            case TABTYPE_LIST:
                this.bindListId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_TABS_BIND_LIST_ID));
            case TABTYPE_SEARCH:
            case TABTYPE_TRACK:
                this.searchKeyword = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_TABS_SEARCH_KEYWORD));
                break;
            case TABTYPE_FILTER:
                this.filterQuery = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_TABS_FILTER_QUERY));
                break;
        }
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        if (id > -1) values.put(CentralDatabase.COL_TABS_ID, id);
        values.put(CentralDatabase.COL_TABS_TYPE, type);
        values.put(CentralDatabase.COL_TABS_TAB_ORDER, order);
        values.put(CentralDatabase.COL_TABS_BIND_ACCOUNT_ID, bindAccount!=null?bindAccount.NumericId:null);
        values.put(CentralDatabase.COL_TABS_BIND_LIST_ID, (type == TABTYPE_LIST)?bindListId:-1);
        values.put(CentralDatabase.COL_TABS_SEARCH_KEYWORD, (type == TABTYPE_SEARCH || type == TABTYPE_TRACK)? searchKeyword:"");
        values.put(CentralDatabase.COL_TABS_FILTER_QUERY, (type == TABTYPE_FILTER)?filterQuery:"");
        return values;
    }

    public int getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public AuthUserRecord getBindAccount() {
        return bindAccount;
    }

    public void setBindAccount(AuthUserRecord bindAccount) {
        this.bindAccount = bindAccount;
    }

    public long getBindListId() {
        return bindListId;
    }

    public void setBindListId(long bindListId) {
        this.bindListId = bindListId;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
    }

    public String getFilterQuery() {
        return filterQuery;
    }

    public void setFilterQuery(String filterQuery) {
        this.filterQuery = filterQuery;
    }

    public AttachableList getAttachableList() {
        return attachableList;
    }

    public void setAttachableList(AttachableList attachableList) {
        this.attachableList = attachableList;
    }
}