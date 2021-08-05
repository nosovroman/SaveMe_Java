package com.revenger.geohelpbutton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    final String DEFAULT_INTERVAL = "1";
    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;
    final String CALL_HELP_TEXT = "Для вызова помощи нажми на лисичку", CANCEL_HELP_TEXT = "Помощь в пути";

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference RootRef;

    private DatabaseReference GeoInfoRef, UsersRef, NotificationRef;

    private String tempUid, currentUserId, deviceToken, currentUserAccess, infoHelp = "", tempInterval;

    private ImageButton callHelp;
    private TextView helpText;
    private TextView currentIntervalMinutes;
    private Button minusButton, plusButton;
    public static Calendar currentTime;
    AlarmManager am;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        RootRef = FirebaseDatabase.getInstance().getReference();

        UsersRef = RootRef.child("Users");
        NotificationRef = RootRef.child("Notifications");
        GeoInfoRef = RootRef.child("GeoInfo");

        // обновление токена при входе в приложение
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            updateDeviceToken();
        }

        InitializeControllers();

        // кнопка вызова помощи
        callHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (helpText.getText().toString().equals(CANCEL_HELP_TEXT)) {
                    infoHelp = "1";
                } else {
                    infoHelp = "0";
                }

                // действие при нажатии на кнопку
                if (infoHelp.equals("0")) {

                    // проверка включенных геоданных
                    LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

                        // отправка уведомления
                        SendHelpNotificationForAdmin();

                        // установка интервала слежки
                        if (currentIntervalMinutes.getText().toString().equals("")) {
                            currentIntervalMinutes.setText(DEFAULT_INTERVAL);
                        }
                        GeoInfoRef.child("geoInterval").setValue(currentIntervalMinutes.getText().toString());

                        // берем время для отсчета
                        currentTime = Calendar.getInstance();
                        currentTime.getTime();

                        // запускаем слежку
                        startAlarm();

                        UsersRef.child(currentUserId).child("help").setValue("1");

                        // подсказка текущего статуса помощи
                        Toast.makeText(MainActivity.this, "Помощь в пути", Toast.LENGTH_LONG).show();

                        // меняем название кнопки
                        helpText.setText(CANCEL_HELP_TEXT);

                    } else {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                }
                else {
                    // отправка уведомления
                    SendHelpNotificationForAdmin();

                    // отключаем слежку
                    cancelAlarm();

                    UsersRef.child(currentUserId).child("help").setValue("0");

                    // подсказка текущего статуса помощи
                    Toast.makeText(MainActivity.this, "Запрос о помощи отклонен", Toast.LENGTH_LONG).show();

                    // обновим время нахождения последних координат
                    Date currentTimeGeo = Calendar.getInstance().getTime();
                    GeoInfoRef.child("time").setValue("Уже спасена " + currentTimeGeo.getDate() + "." + currentTimeGeo.getMonth() + " " +
                            currentTimeGeo.getHours() + ":" + currentTimeGeo.getMinutes() + ":" + currentTimeGeo.getSeconds());

                    // меняем название кнопки
                    helpText.setText(CALL_HELP_TEXT);
                }
            }
        });

        // уменьшение интервала
        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int temp = Integer.parseInt(currentIntervalMinutes.getText().toString());
                if (temp > 1) {
                    if (helpText.getText().toString().equals(CANCEL_HELP_TEXT)) {
                        // отключаем старую слежку
                        cancelAlarm();

                        // берем время для отсчета
                        currentTime = Calendar.getInstance();
                        currentTime.getTime();

                        // запускаем новую слежку
                        startAlarm();
                    }
                    temp--;
                    GeoInfoRef.child("geoInterval").setValue(String.valueOf(temp));
                }
            }
        });

        // увеличение интервала
        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int temp = Integer.parseInt(currentIntervalMinutes.getText().toString());
                if (temp < 10) {
                    if (helpText.getText().toString().equals(CANCEL_HELP_TEXT)) {
                        // отключаем старую слежку
                        cancelAlarm();

                        // берем время для отсчета
                        currentTime = Calendar.getInstance();
                        currentTime.getTime();

                        // запускаем новую слежку
                        startAlarm();
                    }
                    temp++;
                    GeoInfoRef.child("geoInterval").setValue(String.valueOf(temp));
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // проверка включенных геоданных
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        } else {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        // проверяем разрешение на местоположение
        if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED  ) {
            requestPermissions(new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_ASK_PERMISSIONS);
        }

        if (currentUser == null) {
            SendUserToLoginActivity();
        } else {
            // обновление времени активности пользователя
            updateUserTimeActivity();

            // доступ пользователя
            UsersRef.child(currentUserId).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists() && dataSnapshot.child("access").exists()) {
                        currentUserAccess = dataSnapshot.child("access").getValue().toString();
                        if (currentUserAccess.equals("admin")) {
                            SendUserToAdminActivity();
                        } else if (currentUserAccess.equals("user")) {

                        }
                        else if (currentUserAccess.equals("update")) {
                            //SendUserToUpdateActivity();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });

            // текст кнопки
            UsersRef.child(currentUserId).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // узнали :D
                        if (dataSnapshot.child("help").exists()) {
                            infoHelp = dataSnapshot.child("help").getValue().toString();
                        } else { UsersRef.child("help").setValue("0"); infoHelp = "0";}

                        if (infoHelp.equals("0")) {
                            helpText.setText(CALL_HELP_TEXT);
                        } else if (infoHelp.equals("1")) {
                            helpText.setText(CANCEL_HELP_TEXT);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });

            // установка интервала из бд
            GeoInfoRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // узнаем интервал
                        if (dataSnapshot.child("geoInterval").exists()) {
                            tempInterval = dataSnapshot.child("geoInterval").getValue().toString();
                            currentIntervalMinutes.setText(tempInterval);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }
    }

    private void InitializeControllers() {
        callHelp = findViewById(R.id.callForHelp);
        helpText = findViewById(R.id.helpText);
        currentIntervalMinutes = findViewById(R.id.currentIntervalMinutes);
        minusButton = findViewById(R.id.minusBtn);
        plusButton = findViewById(R.id.plusBtn);
    }

    private void SendHelpNotificationForAdmin() {
        UsersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot item : dataSnapshot.getChildren()) {
                        // отправка только админам
                        if (item.child("access").getValue().toString().equals("admin")) {
                            // получение id текуущего получателя уведомления
                            tempUid = item.child("uid").getValue().toString();

                            // обновим время нахождения последних координат
                            Date currentTimeGeo = Calendar.getInstance().getTime();

                            // отправка уведомления
                            String notifyText = currentTimeGeo.getHours() + ":" + currentTimeGeo.getMinutes() + ":" + currentTimeGeo.getSeconds();
                            NotificationRef.child(tempUid).setValue(notifyText);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    // отправка пользователя в активность авторизации
    private void SendUserToLoginActivity() {
        Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
        loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(loginIntent);
        finish();
    }

    // отправка пользователя в активность админа
    private void SendUserToAdminActivity() {
        Intent adminIntent = new Intent(MainActivity.this, MainAdminActivity.class);
        adminIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(adminIntent);
        finish();
    }

    // обновление времени активности пользователя
    private void updateUserTimeActivity() {
        String saveCurrentTime, saveCurrentDate;

        Calendar calendar = Calendar.getInstance();

        SimpleDateFormat currentDate = new SimpleDateFormat("MM.dd");
        saveCurrentDate = currentDate.format(calendar.getTime());

        SimpleDateFormat currentTime = new SimpleDateFormat("HH:mm");
        saveCurrentTime = currentTime.format(calendar.getTime());

        HashMap<String, Object> stateTimeMap = new HashMap<>();
        stateTimeMap.put("time", saveCurrentTime);
        stateTimeMap.put("date", saveCurrentDate);

        RootRef.child("Users").child(currentUser.getUid()).updateChildren(stateTimeMap);
    }

    // обновление токена пользователя
    private void updateDeviceToken() {
        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
            @Override
            public void onComplete(@NonNull Task<InstanceIdResult> task) {
                // проверка нашелся ли токен
                if (!task.isSuccessful()) {
                    return;
                }
                deviceToken = task.getResult().getToken();
                UsersRef.child(currentUserId).child("device_token").setValue(deviceToken);
            }
        });
    }

    // обработчик выбора разрешения
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //getLocation();
                } else {
                    // Permission Denied
                    finish();
                    System.exit(0);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void startAlarm() {
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, TimeReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, intent, 0 );
        // На случай, если мы ранее запускали активити, а потом поменяли время,
        // откажемся от уведомления
        //am.cancel(pendingIntent);
        // Устанавливаем разовое напоминание
        am.setExact(AlarmManager.RTC_WAKEUP, currentTime.getTimeInMillis(), pendingIntent);
    }

    private void cancelAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, TimeReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, intent, 0);

        alarmManager.cancel(pendingIntent);
    }
}