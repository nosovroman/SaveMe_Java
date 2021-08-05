package com.revenger.geohelpbutton;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

class MyLocationListener implements LocationListener{
    static Location imHere; // здесь будет всегда доступна самая последняя информация о местоположении пользователя.
    static String latitude, longitude;

    @SuppressLint("MissingPermission")
    public static void SetUpLocationListener(Context context) // это нужно запустить в самом начале работы программы
    {
        LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new MyLocationListener();

        assert locationManager != null;
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                10,
                locationListener); // здесь можно указать другие более подходящие вам параметры

        imHere = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (imHere != null) {
            latitude = Double.toString(imHere.getLatitude());
            longitude = Double.toString(imHere.getLongitude());
        } else {
            latitude = "0";
            longitude = "0";
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        imHere = loc;
    }
    @Override
    public void onProviderDisabled(String provider) {}
    @Override
    public void onProviderEnabled(String provider) {}
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
