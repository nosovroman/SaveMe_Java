package com.revenger.geohelpbutton;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import static com.revenger.geohelpbutton.MyLocationListener.latitude;
import static com.revenger.geohelpbutton.MyLocationListener.longitude;

public class TimeReceiver extends BroadcastReceiver {

    final String DEFAULT_INTERVAL = "1", DELIMITER_GEO = "/", DELIMITER_LAT_LONG = " ";
    final int MAX_QUANTITY_LOCATIONS = 10;

    private Date currentTimeGeo;
    private int curInterval = 1, divider = 15;
    private String curGeoStr, curGeoInfo;
    private ArrayList<String> geoList;

    private DatabaseReference GeoInfoRef;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override public void onReceive(final Context context, final Intent intent) {
        GeoInfoRef = FirebaseDatabase.getInstance().getReference().child("GeoInfo");
        // узнаем инфу из GeoInfo
        GeoInfoRef.addValueEventListener(new ValueEventListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // узнаем интервал
                    if (dataSnapshot.child("geoInterval").exists() && !dataSnapshot.child("geoInterval").equals("")) {
                        curInterval = Integer.parseInt(dataSnapshot.child("geoInterval").getValue().toString());
                    } else {
                        curInterval = Integer.parseInt(DEFAULT_INTERVAL);
                    }

                    // извлекаем строку с локацией
                    if (dataSnapshot.child("geoString").exists()) {
                        curGeoStr = dataSnapshot.child("geoString").getValue().toString();
                    } else { curGeoStr = ""; }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                curInterval = Integer.parseInt(DEFAULT_INTERVAL);
                // определение очередного местоположения
                MyLocationListener.SetUpLocationListener(context);

                // Установим следующее напоминание.
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES*curInterval/divider, pendingIntent);
            }
        });

        // определение очередного местоположения
        MyLocationListener.SetUpLocationListener(context);

        // обновим время нахождения последних координат
        currentTimeGeo = Calendar.getInstance().getTime();
        curGeoInfo = currentTimeGeo.getDate() + "." + currentTimeGeo.getMonth() + " " + currentTimeGeo.getHours() + ":" + currentTimeGeo.getMinutes() + ":" + currentTimeGeo.getSeconds();
        if (latitude.equals("0")) {
            curGeoInfo = "Не найдено " + curGeoInfo;
        }
        GeoInfoRef.child("time").setValue(curGeoInfo)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                // проверка, были ли найдены координаты ранее
                if (latitude.equals("0")) {
                    // уже все сделали выше
                } else {
                    // проверка найдены ли сейчас координаты. Если нет, то пишем это в time
                    if (curGeoStr.length() == 0) {
                        curGeoStr = latitude + DELIMITER_LAT_LONG + longitude;
                    } else {
                        // преобразуем строку гео в список
                        geoList = new ArrayList<>(Arrays.asList(curGeoStr.split(DELIMITER_GEO)));

                        // удаляем из списка первый элемент в случае превышения нормы
                        if (geoList.size() >= MAX_QUANTITY_LOCATIONS) {
                            geoList.remove(0);
                        }

                        // преобразование списка в строку и добавление новых координат
                        curGeoStr = String.join(DELIMITER_GEO, geoList);
                        curGeoStr += DELIMITER_GEO + latitude + DELIMITER_LAT_LONG + longitude;
                    }

                    // занесение новой строки в бд
                    GeoInfoRef.child("geoString").setValue(curGeoStr);
                }

                // Установим следующее напоминание.
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES * curInterval / divider, pendingIntent);
            }
        });
    }
}