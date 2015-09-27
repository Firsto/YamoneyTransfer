/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 NBCO Yandex.Money LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ru.firsto.yamoneytranfer.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.yandex.money.api.model.Operation;

import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vyasevich
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String NAME = "operations.db";

    private static final int VERSION = 1;

    private static DatabaseHelper instance;

    private DatabaseHelper(Context context) {
        super(context, NAME, null, VERSION);
    }

    public static DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();

        db.execSQL("CREATE TABLE " + TableOperations.NAME + " (" +
//                        "_id integer primary key autoincrement," +
                        TableOperations.COLUMN_OPERATION_ID + " INTEGER PRIMARY KEY, " +
                        TableOperations.COLUMN_OPERATION_DATE + " DATETIME, " +
                        TableOperations.COLUMN_OPERATION_TITLE + " VARCHAR(512), " +
                        TableOperations.COLUMN_OPERATION_AMOUNT + " VARCHAR(127), " +
                        TableOperations.COLUMN_OPERATION_AMOUNT_DUE + " VARCHAR(127), " +
                        TableOperations.COLUMN_OPERATION_DIRECTION + " VARCHAR(127), " +
                        TableOperations.COLUMN_OPERATION_PROTECTION_CODE + " INTEGER, " +
                        TableOperations.COLUMN_OPERATION_MESSAGE + " VARCHAR(512), " +
                        TableOperations.COLUMN_OPERATION_COMMENT + " VARCHAR(512)" +
                        ")"
        );

        db.setTransactionSuccessful();
        db.endTransaction();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // does nothing
    }

    public void clearDatabase() {
        Log.d("TAG", "clearDatabase()");
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(getWritableDatabase().getPath(), null, 0);
            db.delete(TableOperations.NAME, null, null);
            getWritableDatabase().beginTransaction();
            getWritableDatabase().delete(TableOperations.NAME, null, null);
            getWritableDatabase().endTransaction();
            Log.d("TAG", "cleared >>>");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addOperation(Operation operation) {
        if (getOperation(Long.parseLong(operation.operationId)) != null) return;
        ContentValues values = new ContentValues();
        values.put(TableOperations.COLUMN_OPERATION_ID, operation.operationId);
//        values.put(TableOperations.COLUMN_OPERATION_DATE, operation.datetime.toDate().getTime());
//        values.put(TableOperations.COLUMN_OPERATION_DATE, operation.datetime.toString("yyyy-MM-dd hh:mm:ss"));
        values.put(TableOperations.COLUMN_OPERATION_DATE, operation.datetime.toString("yyyy-MM-dd") + 'T' + operation.datetime.toString("hh:mm:ss"));
        values.put(TableOperations.COLUMN_OPERATION_TITLE, operation.title);
        values.put(TableOperations.COLUMN_OPERATION_AMOUNT, operation.amount != null ? operation.amount.toPlainString() : "");
        values.put(TableOperations.COLUMN_OPERATION_AMOUNT_DUE, operation.amountDue != null ? operation.amountDue.toPlainString() : "");
        values.put(TableOperations.COLUMN_OPERATION_DIRECTION, operation.direction == Operation.Direction.INCOMING ? "in" : "out");
        values.put(TableOperations.COLUMN_OPERATION_PROTECTION_CODE, operation.codepro ? operation.protectionCode : "");
        values.put(TableOperations.COLUMN_OPERATION_MESSAGE, operation.message != null ? operation.message : "");
        values.put(TableOperations.COLUMN_OPERATION_COMMENT, operation.comment != null ? operation.comment : "");
        getWritableDatabase().insertOrThrow(TableOperations.NAME, null, values);
    }

    public Operation getOperation(long id) {
        List<Operation> operations = getOperations();
        for (Operation operation : operations) {
            if (operation.operationId.equals(String.valueOf(id))) return operation;
        }
        return null;
    }

    public List<Operation> getOperations() {
        List<Operation> values = new ArrayList<Operation>();
        Cursor cursor = getReadableDatabase().query(TableOperations.NAME, null, null, null, null,
                null, TableOperations.COLUMN_OPERATION_DATE + " desc");

        while (cursor.moveToNext()) {

            String operationId = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_ID));
            DateTime dateTime = new DateTime(cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_DATE)));
            String title = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_TITLE));
            String stringAmount = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_AMOUNT));
            BigDecimal amount = null;
            if (!"".equals(stringAmount)) amount = new BigDecimal(stringAmount);
            String stringAmountDue = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_AMOUNT_DUE));
            BigDecimal amountDue = null;
            if (!"".equals(stringAmountDue)) amountDue = new BigDecimal(stringAmountDue);
            String direction = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_DIRECTION));
            String protectionCode = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_PROTECTION_CODE));
            String message = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_MESSAGE));
            String comment = cursor.getString(cursor.getColumnIndex(TableOperations.COLUMN_OPERATION_COMMENT));

            Operation operation = new Operation.Builder()
                    .setOperationId(operationId)
                    .setTitle(title)
                    .setDatetime(dateTime)
                    .setAmount(amount)
                    .setAmountDue(amountDue)
                    .setDirection(Operation.Direction.parse(direction))
                    .setCodepro(!"".equals(protectionCode))
                    .setProtectionCode(!"".equals(protectionCode) ? protectionCode : null)
                    .setMessage(!"".equals(message) ? message : null)
                    .setMessage(!"".equals(comment) ? comment : null)
                    .createOperation();

            values.add(operation);
        }
        return values;
    }

    private static class TableOperations {
        private static final String NAME = "operations";
        private static final String COLUMN_OPERATION_ID = "_id";
        private static final String COLUMN_OPERATION_DATE = "date";
        private static final String COLUMN_OPERATION_TITLE = "title";
        private static final String COLUMN_OPERATION_AMOUNT = "amount";
        private static final String COLUMN_OPERATION_AMOUNT_DUE = "amount_due";
        private static final String COLUMN_OPERATION_DIRECTION = "direction";
        private static final String COLUMN_OPERATION_PROTECTION_CODE = "protection_code";
        private static final String COLUMN_OPERATION_MESSAGE = "message";
        private static final String COLUMN_OPERATION_COMMENT = "comment";
    }

}
