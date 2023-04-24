package com.example.bing;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.List;

public class TrailDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "trail_database";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_TRAIL = "trail";
    private static final String KEY_ID = "id";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private boolean isRecordingTrail = false;

    public void startRecordingTrail() {
        isRecordingTrail = true;
    }

    public void stopRecordingTrail() {
        isRecordingTrail = false;
    }
    //um banco de dados que ajuda a quardar os pontos do tracejado
    public TrailDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TRAIL_TABLE = "CREATE TABLE " + TABLE_TRAIL + "("
                + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_LATITUDE + " REAL,"
                + KEY_LONGITUDE + " REAL" + ")";
        db.execSQL(CREATE_TRAIL_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRAIL);
        onCreate(db);
    }
    public void clearAllTrails() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TRAIL, null, null);
        db.close();
    }
//lembra da posicao dos pontos
    public void addPoint(double latitude, double longitude) {
        if (isRecordingTrail) {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(KEY_LATITUDE, latitude);
            values.put(KEY_LONGITUDE, longitude);
            db.insert(TABLE_TRAIL, null, values);
            db.close();
        }
    }

//carrega os pontos salvos
    public List<LatLng> getPoints(LatLngBounds bounds) {
        List<LatLng> points = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_TRAIL;
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToFirst()) {
            do {
                double latitude = cursor.getDouble(cursor.getColumnIndex(KEY_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndex(KEY_LONGITUDE));
                LatLng point = new LatLng(latitude, longitude);
                if (bounds.contains(point)) {
                    points.add(point);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return points;
    }
}
